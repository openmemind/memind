/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.openmemind.ai.memory.plugin.ai.spring;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * MemoryVector implementation based on Spring AI VectorStore.
 */
public class SpringAiMemoryVector implements MemoryVector {

    private static final Logger log = LoggerFactory.getLogger(SpringAiMemoryVector.class);

    private static final Retry VECTOR_RETRY =
            Retry.backoff(3, Duration.ofSeconds(2))
                    .maxBackoff(Duration.ofSeconds(15))
                    .doBeforeRetry(
                            signal ->
                                    log.warn(
                                            "Vector operation failed, retrying {} time: {}",
                                            signal.totalRetries() + 1,
                                            signal.failure().getMessage()));

    private static final String MEMORY_ID_KEY = "memoryId";
    private static final String ORIGINAL_TEXT_KEY = "originalText";

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    public SpringAiMemoryVector(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
        this.embeddingModel =
                Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
    }

    @Override
    public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
        return store(memoryId, UUID.randomUUID().toString(), text, metadata);
    }

    @Override
    public Mono<String> store(
            MemoryId memoryId, String vectorId, String text, Map<String, Object> metadata) {
        return Mono.fromCallable(
                        () -> {
                            Document document =
                                    new Document(
                                            vectorId,
                                            text,
                                            buildMetadata(memoryId, text, metadata));
                            vectorStore.add(List.of(document));

                            return vectorId;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(VECTOR_RETRY);
    }

    @Override
    public Mono<List<String>> storeBatch(
            MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
        List<String> vectorIds =
                texts.stream().map(ignored -> UUID.randomUUID().toString()).toList();
        return storeBatch(memoryId, vectorIds, texts, metadataList);
    }

    @Override
    public Mono<List<String>> storeBatch(
            MemoryId memoryId,
            List<String> vectorIds,
            List<String> texts,
            List<Map<String, Object>> metadataList) {
        return Mono.fromCallable(
                        () -> {
                            if (vectorIds != null && vectorIds.size() != texts.size()) {
                                throw new IllegalArgumentException(
                                        "vectorIds must match texts size");
                            }
                            List<String> resolvedVectorIds =
                                    vectorIds == null
                                            ? texts.stream()
                                                    .map(ignored -> UUID.randomUUID().toString())
                                                    .toList()
                                            : List.copyOf(vectorIds);
                            List<Document> documents = new ArrayList<>();

                            for (int i = 0; i < texts.size(); i++) {
                                documents.add(
                                        new Document(
                                                resolvedVectorIds.get(i),
                                                texts.get(i),
                                                buildMetadata(
                                                        memoryId,
                                                        texts.get(i),
                                                        metadataList != null
                                                                        && i < metadataList.size()
                                                                ? metadataList.get(i)
                                                                : null)));
                            }

                            vectorStore.add(documents);
                            return resolvedVectorIds;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(VECTOR_RETRY);
    }

    @Override
    public Mono<Void> delete(MemoryId memoryId, String vectorId) {
        return Mono.fromRunnable(() -> vectorStore.delete(List.of(vectorId)))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> deleteBatch(MemoryId memoryId, List<String> vectorIds) {
        return Mono.fromRunnable(() -> vectorStore.delete(vectorIds))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK) {
        return search(memoryId, query, topK, null);
    }

    @Override
    public Flux<VectorSearchResult> search(
            MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
        return Mono.fromCallable(
                        () -> {
                            FilterExpressionBuilder b = new FilterExpressionBuilder();

                            FilterExpressionBuilder.Op combinedOp =
                                    b.eq(MEMORY_ID_KEY, memoryId.toIdentifier());
                            if (filter != null && !filter.isEmpty()) {
                                for (Map.Entry<String, Object> entry : filter.entrySet()) {
                                    combinedOp =
                                            b.and(
                                                    combinedOp,
                                                    b.eq(entry.getKey(), entry.getValue()));
                                }
                            }

                            Filter.Expression finalFilter = combinedOp.build();
                            var request =
                                    SearchRequest.builder()
                                            .query(query)
                                            .topK(topK)
                                            .filterExpression(finalFilter)
                                            .build();

                            return vectorStore.similaritySearch(request);
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(VECTOR_RETRY)
                .flatMapMany(
                        results ->
                                Flux.fromIterable(
                                        results.stream()
                                                .map(
                                                        doc ->
                                                                new VectorSearchResult(
                                                                        doc.getId(),
                                                                        getOriginalText(doc),
                                                                        doc.getScore() != null
                                                                                ? doc.getScore()
                                                                                        .floatValue()
                                                                                : 0f,
                                                                        doc.getMetadata()))
                                                .toList()));
    }

    @Override
    public Mono<List<Float>> embed(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return Mono.fromCallable(
                        () -> {
                            float[] embedding = embeddingModel.embed(text);
                            return toFloatList(embedding);
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(VECTOR_RETRY);
    }

    @Override
    public Mono<List<List<Float>>> embedAll(List<String> texts) {
        Objects.requireNonNull(texts, "texts must not be null");
        if (texts.isEmpty()) {
            return Mono.just(List.of());
        }
        return Mono.fromCallable(
                        () ->
                                embeddingModel.embed(texts).stream()
                                        .map(SpringAiMemoryVector::toFloatList)
                                        .toList())
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(VECTOR_RETRY);
    }

    private String getOriginalText(Document doc) {
        Object originalText = doc.getMetadata().get(ORIGINAL_TEXT_KEY);
        if (originalText != null) {
            return originalText.toString();
        }
        return doc.getText();
    }

    private Map<String, Object> buildMetadata(
            MemoryId memoryId, String text, Map<String, Object> metadata) {
        Map<String, Object> fullMetadata = new HashMap<>();
        if (metadata != null) {
            fullMetadata.putAll(metadata);
        }
        fullMetadata.put(MEMORY_ID_KEY, memoryId.toIdentifier());
        fullMetadata.put(ORIGINAL_TEXT_KEY, text);
        return fullMetadata;
    }

    private static List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }
}
