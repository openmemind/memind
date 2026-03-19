package com.openmemind.ai.memory.core.retrieval.scoring;

import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Result Merger
 *
 * <p>Provides RRF (Reciprocal Rank Fusion) merging capability:
 * <ul>
 *   <li>Merging multiple strategy ranking lists (for HybridStrategy)
 *   <li>Vector + BM25 dual-channel Weighted RRF fusion
 * </ul>
 *
 */
public final class ResultMerger {

    private static final Logger log = LoggerFactory.getLogger(ResultMerger.class);

    private ResultMerger() {}

    /**
     * BM25 Sigmoid normalization
     *
     * <p>Maps BM25 raw score to [0, 1), query-independent, does not depend on the size of the result set.
     * Reference implementation of QMD: |x| / (1 + |x|)
     *
     * @param rawBm25Score BM25 raw score
     * @return normalized score [0, 1)
     */
    public static double sigmoidNormalize(double rawBm25Score) {
        double abs = Math.abs(rawBm25Score);
        return abs / (1.0 + abs);
    }

    /**
     * Weighted RRF merges multiple ranking lists
     *
     * <p>Formula: finalScore = Σ weight_i / (K + rank_i)
     *
     * @param rankedLists multiple ranking lists
     * @param weights     weights for each list (optional, defaults to all 1.0 if not provided or length does not match)
     * @return merged results (sorted by finalScore in descending order)
     */
    public static List<ScoredResult> merge(
            ScoringConfig scoring, List<List<ScoredResult>> rankedLists, double... weights) {
        if (rankedLists.isEmpty()) {
            return List.of();
        }
        if (rankedLists.size() == 1) {
            return rankedLists.get(0);
        }

        boolean useWeights = weights.length == rankedLists.size();

        // dedupKey -> accumulated RRF score
        Map<String, Double> rrfScores = new HashMap<>();
        // dedupKey -> best ScoredResult (keep the highest vectorScore)
        Map<String, ScoredResult> bestResults = new HashMap<>();

        for (int i = 0; i < rankedLists.size(); i++) {
            double weight = useWeights ? weights[i] : 1.0;
            List<ScoredResult> rankedList = rankedLists.get(i);
            for (int rank = 0; rank < rankedList.size(); rank++) {
                ScoredResult result = rankedList.get(rank);
                String key = result.dedupKey();

                double rrfScore = weight / (scoring.fusion().k() + rank + 1);
                rrfScores.merge(key, rrfScore, Double::sum);

                bestResults.merge(
                        key,
                        result,
                        (existing, incoming) -> {
                            // Prefer to keep those with vector scores (from vector search)
                            if (existing.vectorScore() > 0 && incoming.vectorScore() == 0)
                                return existing;
                            if (incoming.vectorScore() > 0 && existing.vectorScore() == 0)
                                return incoming;
                            return incoming.vectorScore() > existing.vectorScore()
                                    ? incoming
                                    : existing;
                        });
            }
        }

        // Sort by RRF score in descending order, reconstruct ScoredResult using RRF score as
        // finalScore
        List<ScoredResult> merged = new ArrayList<>(rrfScores.size());
        rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(
                        entry -> {
                            ScoredResult best = bestResults.get(entry.getKey());
                            merged.add(
                                    new ScoredResult(
                                            best.sourceType(),
                                            best.sourceId(),
                                            best.text(),
                                            best.vectorScore(),
                                            entry.getValue(),
                                            best.occurredAt()));
                        });

        // Normalize RRF scores to [0, 1], so minScore filtering works properly
        List<ScoredResult> normalized = normalize(merged);
        return applyTopRankBonus(scoring, normalized);
    }

    /** Max-ratio normalize finalScore to (0, 1], keeping the true ratio */
    private static List<ScoredResult> normalize(List<ScoredResult> results) {
        if (results.size() <= 1) {
            if (results.size() == 1) {
                ScoredResult r = results.get(0);
                return List.of(
                        new ScoredResult(
                                r.sourceType(),
                                r.sourceId(),
                                r.text(),
                                r.vectorScore(),
                                1.0,
                                r.occurredAt()));
            }
            return List.copyOf(results);
        }

        double maxScore = results.stream().mapToDouble(ScoredResult::finalScore).max().orElse(1.0);
        if (maxScore <= 0) {
            return List.copyOf(results);
        }

        return results.stream()
                .map(
                        r ->
                                new ScoredResult(
                                        r.sourceType(),
                                        r.sourceId(),
                                        r.text(),
                                        r.vectorScore(),
                                        r.finalScore() / maxScore,
                                        r.occurredAt()))
                .toList();
    }

    /** Top-rank bonus: rank 1 +0.05, rank 2-3 +0.02 (reference QMD) */
    private static List<ScoredResult> applyTopRankBonus(
            ScoringConfig scoring, List<ScoredResult> results) {
        if (results.isEmpty()) {
            return results;
        }
        List<ScoredResult> boosted = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            ScoredResult r = results.get(i);
            double bonus = 0;
            if (i == 0) bonus = scoring.positionBonus().top1();
            else if (i <= 2) bonus = scoring.positionBonus().top3();
            boosted.add(
                    new ScoredResult(
                            r.sourceType(),
                            r.sourceId(),
                            r.text(),
                            r.vectorScore(),
                            r.finalScore() + bonus,
                            r.occurredAt()));
        }
        return boosted;
    }

    /**
     * Relative Score Fusion merges multiple ranking lists
     *
     * <p>Each list's finalScore is first max-ratio normalized to [0, 1],
     * then the normalized scores of the same document are summed as the final score, and finally globally normalized.
     *
     * <p>Difference from RRF: RSF retains original score information, high-scoring documents can still receive high weight even if ranked lower.
     *
     * @param rankedLists multiple ranking lists
     * @return merged results (sorted by finalScore in descending order, normalized to [0, 1])
     */
    public static List<ScoredResult> mergeByRelativeScore(List<List<ScoredResult>> rankedLists) {
        if (rankedLists.isEmpty()) {
            return List.of();
        }
        if (rankedLists.size() == 1) {
            return rankedLists.get(0);
        }

        // dedupKey -> accumulated normalized score
        Map<String, Double> fusedScores = new HashMap<>();
        // dedupKey -> best ScoredResult (keep the highest vectorScore)
        Map<String, ScoredResult> bestResults = new HashMap<>();

        for (List<ScoredResult> rankedList : rankedLists) {
            if (rankedList.isEmpty()) {
                continue;
            }

            // Max-ratio normalization within each list
            double maxScore =
                    rankedList.stream().mapToDouble(ScoredResult::finalScore).max().orElse(1.0);
            double divisor = maxScore > 0 ? maxScore : 1.0;

            for (ScoredResult result : rankedList) {
                String key = result.dedupKey();
                double normalizedScore = result.finalScore() / divisor;

                fusedScores.merge(key, normalizedScore, Double::sum);
                bestResults.merge(
                        key,
                        result,
                        (existing, incoming) -> {
                            if (existing.vectorScore() > 0 && incoming.vectorScore() == 0)
                                return existing;
                            if (incoming.vectorScore() > 0 && existing.vectorScore() == 0)
                                return incoming;
                            return incoming.vectorScore() > existing.vectorScore()
                                    ? incoming
                                    : existing;
                        });
            }
        }

        // Sort by fused score in descending order
        List<ScoredResult> merged = new ArrayList<>(fusedScores.size());
        fusedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(
                        entry -> {
                            ScoredResult best = bestResults.get(entry.getKey());
                            merged.add(
                                    new ScoredResult(
                                            best.sourceType(),
                                            best.sourceId(),
                                            best.text(),
                                            best.vectorScore(),
                                            entry.getValue(),
                                            best.occurredAt()));
                        });

        return normalize(merged);
    }

    /**
     * RRF fuses vector results with BM25 results
     *
     * <p>Treats the vector search result list and the BM25 search result list as two separate ranking lists,
     * and merges them using Weighted RRF. Vector list weight 1.5, BM25 list weight 1.0.
     *
     * @param vectorResults vector search results (sorted by Salience score)
     * @param keywordHits   BM25 search results
     * @param topK          limit on the number of results returned
     * @param sourceType    source type of BM25-only results
     * @return list of results after RRF fusion (sorted by finalScore in descending order)
     */
    public static List<ScoredResult> rrfMergeWithKeywords(
            ScoringConfig scoring,
            List<ScoredResult> vectorResults,
            List<TextSearchResult> keywordHits,
            int topK,
            ScoredResult.SourceType sourceType) {

        // Convert BM25 results to ScoredResult (using sigmoid normalized score as finalScore)
        List<ScoredResult> bm25List =
                keywordHits.stream()
                        .map(
                                tr ->
                                        new ScoredResult(
                                                sourceType,
                                                tr.documentId(),
                                                tr.text(),
                                                0f,
                                                sigmoidNormalize(tr.score())))
                        .toList();

        if (bm25List.isEmpty()) {
            return vectorResults;
        }
        if (vectorResults.isEmpty()) {
            return merge(scoring, List.of(bm25List), scoring.fusion().keywordWeight());
        }

        List<ScoredResult> merged =
                merge(
                        scoring,
                        List.of(vectorResults, bm25List),
                        scoring.fusion().vectorWeight(),
                        scoring.fusion().keywordWeight());

        return merged.size() > topK ? merged.subList(0, topK) : merged;
    }

    /**
     * Detects if BM25 has a strong signal
     *
     * <p>A strong signal is when the top-1 sigmoid score ≥ 0.85 and the gap with the second place ≥ 0.15.
     * A strong signal means the keyword has been accurately hit, allowing skipping multiple query expansions to save LLM calls.
     */
    public static boolean isStrongSignal(
            ScoringConfig scoring, List<TextSearchResult> bm25Results) {
        if (bm25Results == null || bm25Results.size() < 2) {
            return false;
        }
        double top = sigmoidNormalize(bm25Results.get(0).score());
        double second = sigmoidNormalize(bm25Results.get(1).score());
        return top >= scoring.keywordSearch().strongSignalMinScore()
                && (top - second) >= scoring.keywordSearch().strongSignalMinGap();
    }

    public static Mono<List<ScoredResult>> mergeKeywordResults(
            MemoryTextSearch textSearch,
            QueryContext context,
            ScoringConfig scoring,
            List<ScoredResult> vectorResults,
            int topK,
            MemoryTextSearch.SearchTarget target,
            ScoredResult.SourceType sourceType) {
        if (textSearch == null) {
            return Mono.just(vectorResults);
        }
        return textSearch
                .search(context.memoryId(), context.searchQuery(), topK, target)
                .map(
                        keywordHits -> {
                            if (keywordHits == null || keywordHits.isEmpty()) {
                                return vectorResults;
                            }
                            return rrfMergeWithKeywords(
                                    scoring, vectorResults, keywordHits, topK, sourceType);
                        })
                .onErrorResume(
                        e -> {
                            log.warn("BM25 keyword search failed, using pure vector results", e);
                            return Mono.just(vectorResults);
                        });
    }
}
