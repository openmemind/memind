package com.openmemind.ai.memory.core.retrieval.rerank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig.RerankConfig;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Reranker based on Rerank API
 *
 * <p>Call the /v1/rerank endpoint (Cohere compatible format) to refine the recall results using a dedicated reranking model
 *
 */
public class LlmReranker implements Reranker {

    private static final Logger log = LoggerFactory.getLogger(LlmReranker.class);
    private static final String DEFAULT_MODEL = "qwen3-reranker-8b";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String rerankUrl;
    private final String apiKey;
    private final String model;

    public LlmReranker(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, DEFAULT_MODEL);
    }

    public LlmReranker(String baseUrl, String apiKey, String model) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.rerankUrl = baseUrl.replaceAll("/+$", "") + "/v1/rerank";
        this.apiKey = apiKey;
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Mono<List<ScoredResult>> rerank(String query, List<ScoredResult> results, int topK) {
        if (results.isEmpty()) {
            return Mono.just(results);
        }

        return Mono.fromCallable(() -> doRerank(query, results, topK))
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(2)).maxBackoff(Duration.ofSeconds(10)))
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "Rerank failed after 3 retries, returning original sorted topK"
                                            + " slice",
                                    e);
                            return Mono.just(fallback(results, topK));
                        });
    }

    private List<ScoredResult> doRerank(String query, List<ScoredResult> results, int topK)
            throws Exception {
        List<String> documents = results.stream().map(ScoredResult::text).toList();

        int topN = Math.min(topK, documents.size());
        var requestBody = new RerankRequest(model, query, topN, documents, false);
        String json = objectMapper.writeValueAsString(requestBody);

        var request =
                HttpRequest.newBuilder()
                        .uri(URI.create(rerankUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(60))
                        .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Rerank API returned non-200 status code: "
                            + response.statusCode()
                            + ", body: "
                            + response.body());
        }

        var rerankResponse = objectMapper.readValue(response.body(), RerankApiResponse.class);
        return applyScores(results, rerankResponse);
    }

    private List<ScoredResult> applyScores(List<ScoredResult> results, RerankApiResponse response) {
        if (response == null || response.results == null || response.results.isEmpty()) {
            log.warn("Rerank response format is abnormal, returning original sorting");
            return results;
        }

        return response.results.stream()
                .filter(r -> r.index >= 0 && r.index < results.size())
                .map(
                        r -> {
                            var original = results.get(r.index);
                            int retrievalRank = r.index + 1;
                            double blended = legacyBlendScore(retrievalRank, r.relevanceScore);
                            return new ScoredResult(
                                    original.sourceType(),
                                    original.sourceId(),
                                    original.text(),
                                    original.vectorScore(),
                                    blended,
                                    original.occurredAt());
                        })
                .sorted(Comparator.comparingDouble(ScoredResult::finalScore).reversed())
                .toList();
    }

    /**
     * Position-aware blended scoring
     *
     * <p>Mix the original retrieval ranking (RRF inverse weight) with the Reranker semantic score. Higher ranked results are given more weight from the original ranking to avoid high-quality results being drowned out by single-point misjudgments of the Reranker.
     *
     * @param retrievalRank Original retrieval ranking (1-indexed)
     * @param rerankerScore Relevance score returned by Rerank API
     * @return Blended final score
     */
    double legacyBlendScore(int retrievalRank, double rerankerScore) {
        double rrfWeight;
        if (retrievalRank <= 3) {
            rrfWeight = 0.7;
        } else if (retrievalRank <= 10) {
            rrfWeight = 0.5;
        } else {
            rrfWeight = 0.3;
        }
        return rrfWeight * (1.0 / retrievalRank) + (1.0 - rrfWeight) * rerankerScore;
    }

    /**
     * Position-aware blended scoring (based on RerankConfig)
     *
     * <p>Mix the RRF retrieval score with the Reranker semantic score. Higher ranked results are given more weight from the original retrieval to avoid single-point misjudgments of the Reranker.
     *
     * @param rrfScore RRF blended retrieval score
     * @param rerankerScore Relevance score returned by Rerank API
     * @param rrfRank RRF ranking (1-indexed)
     * @param config Rerank configuration
     * @return Blended final score
     */
    static double blendScore(
            double rrfScore, double rerankerScore, int rrfRank, RerankConfig config) {
        double rrfWeight;
        if (rrfRank <= 3) rrfWeight = config.top3Weight();
        else if (rrfRank <= 10) rrfWeight = config.top10Weight();
        else rrfWeight = config.otherWeight();
        return rrfWeight * rrfScore + (1 - rrfWeight) * rerankerScore;
    }

    /**
     * Rerank using the specified RerankConfig
     *
     * @param query Query text
     * @param results Candidate results list
     * @param rerankConfig Rerank configuration
     * @return Reranked results
     */
    public Mono<List<ScoredResult>> rerank(
            String query, List<ScoredResult> results, RerankConfig rerankConfig) {
        if (results.isEmpty() || !rerankConfig.enabled()) {
            return Mono.just(results);
        }

        return Mono.fromCallable(() -> doRerank(query, results, rerankConfig))
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(2)).maxBackoff(Duration.ofSeconds(10)))
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "Rerank failed after 3 retries, returning original sorted topK"
                                            + " slice",
                                    e);
                            return Mono.just(fallback(results, rerankConfig.topK()));
                        });
    }

    private List<ScoredResult> doRerank(
            String query, List<ScoredResult> results, RerankConfig rerankConfig) throws Exception {
        List<String> documents = results.stream().map(ScoredResult::text).toList();

        int topN = Math.min(rerankConfig.topK(), documents.size());
        var requestBody = new RerankRequest(model, query, topN, documents, false);
        String json = objectMapper.writeValueAsString(requestBody);

        var request =
                HttpRequest.newBuilder()
                        .uri(URI.create(rerankUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(60))
                        .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Rerank API returned non-200 status code: "
                            + response.statusCode()
                            + ", body: "
                            + response.body());
        }

        var rerankResponse = objectMapper.readValue(response.body(), RerankApiResponse.class);
        return applyScores(results, rerankResponse, rerankConfig);
    }

    private List<ScoredResult> applyScores(
            List<ScoredResult> results, RerankApiResponse response, RerankConfig rerankConfig) {
        if (response == null || response.results == null || response.results.isEmpty()) {
            log.warn("Rerank response format is abnormal, returning original sorting");
            return results;
        }

        return response.results.stream()
                .filter(r -> r.index >= 0 && r.index < results.size())
                .map(
                        r -> {
                            var original = results.get(r.index);
                            double finalScore;
                            if (rerankConfig.blendWithRetrieval()) {
                                int rrfRank = r.index + 1;
                                finalScore =
                                        blendScore(
                                                original.finalScore(),
                                                r.relevanceScore,
                                                rrfRank,
                                                rerankConfig);
                            } else {
                                // Pure mode: reranker score is the final score
                                finalScore = r.relevanceScore;
                            }
                            return new ScoredResult(
                                    original.sourceType(),
                                    original.sourceId(),
                                    original.text(),
                                    original.vectorScore(),
                                    finalScore,
                                    original.occurredAt());
                        })
                .sorted(Comparator.comparingDouble(ScoredResult::finalScore).reversed())
                .toList();
    }

    private List<ScoredResult> fallback(List<ScoredResult> results, int topK) {
        if (results.size() <= topK) {
            return results;
        }
        return results.subList(0, topK);
    }

    // -- Request / Response --

    private record RerankRequest(
            String model,
            String query,
            @JsonProperty("top_n") int topN,
            List<String> documents,
            @JsonProperty("return_documents") boolean returnDocuments) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RerankApiResponse(List<RerankResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RerankResult(
            int index, @JsonProperty("relevance_score") double relevanceScore) {}
}
