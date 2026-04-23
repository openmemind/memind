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
package com.openmemind.ai.memory.core.vector;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Vector storage interface
 *
 */
public interface MemoryVector {

    /**
     * Store text vector
     *
     * @param memoryId Memory identifier
     * @param text     Text to be vectorized
     * @param metadata Additional metadata (can be used for filtering)
     * @return Vector ID
     */
    Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata);

    /**
     * Store text vector with a caller-supplied preferred vector id.
     *
     * <p>Backends that do not support deterministic ids may ignore the supplied id and return a
     * backend-generated id instead.
     */
    default Mono<String> store(
            MemoryId memoryId, String vectorId, String text, Map<String, Object> metadata) {
        return store(memoryId, text, metadata);
    }

    /**
     * Batch store text vectors
     *
     * @param memoryId     Memory identifier
     * @param texts        List of texts to be vectorized
     * @param metadataList Corresponding list of metadata
     * @return List of vector IDs
     */
    Mono<List<String>> storeBatch(
            MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList);

    /**
     * Batch store text vectors with caller-supplied preferred vector ids.
     *
     * <p>Backends that do not support deterministic ids may ignore the supplied ids and return
     * backend-generated ids instead.
     */
    default Mono<List<String>> storeBatch(
            MemoryId memoryId,
            List<String> vectorIds,
            List<String> texts,
            List<Map<String, Object>> metadataList) {
        Objects.requireNonNull(texts, "texts");
        if (vectorIds != null && !vectorIds.isEmpty() && vectorIds.size() != texts.size()) {
            throw new IllegalArgumentException("vectorIds must match texts size");
        }
        return storeBatch(memoryId, texts, metadataList);
    }

    /**
     * Delete vector
     *
     * @param memoryId Memory identifier
     * @param vectorId Vector ID
     */
    Mono<Void> delete(MemoryId memoryId, String vectorId);

    /**
     * Batch delete vectors
     *
     * @param memoryId  Memory identifier
     * @param vectorIds List of vector IDs
     */
    Mono<Void> deleteBatch(MemoryId memoryId, List<String> vectorIds);

    /**
     * Similarity search
     *
     * @param memoryId Memory identifier
     * @param query    Query text
     * @param topK     Number of results to return
     * @return Stream of search results
     */
    Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK);

    /**
     * Similarity search with filtering conditions
     *
     * @param memoryId Memory identifier
     * @param query    Query text
     * @param topK     Number of results to return
     * @param filter   Metadata filtering conditions
     * @return Stream of search results
     */
    Flux<VectorSearchResult> search(
            MemoryId memoryId, String query, int topK, Map<String, Object> filter);

    /**
     * Similarity search with minimum score threshold and filtering conditions
     *
     * @param memoryId Memory identifier
     * @param query    Query text
     * @param topK     Number of results to return
     * @param minScore Minimum similarity score (0.0-1.0), results below this score will not be returned
     * @param filter   Metadata filtering conditions (can be null)
     * <p>Stage 3 semantic-linking contract: when implementations are used for shared
     * semantic-link normalization, the scores returned here must be numerically comparable to
     * in-memory cosine similarity computed over embeddings returned by {@link #embed(String)},
     * {@link #embedAll(List)}, and {@link #fetchEmbeddings(List)}.
     * @return Stream of search results
     */
    default Flux<VectorSearchResult> search(
            MemoryId memoryId,
            String query,
            int topK,
            double minScore,
            Map<String, Object> filter) {
        return search(memoryId, query, topK, filter).filter(vr -> vr.score() >= minScore);
    }

    /**
     * Ordered batch similarity search.
     *
     * <p>The default implementation safely decomposes into the existing single-request search path.
     *
     * <p>Stage 3 semantic-linking contract: request ordering and score semantics must remain
     * consistent with repeated calls to
     * {@link #search(MemoryId, String, int, double, Map)}.
     */
    default Mono<VectorBatchSearchResult> searchBatch(
            MemoryId memoryId, List<VectorSearchRequest> requests, int maxConcurrency) {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(requests, "requests");
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency must be positive");
        }
        if (requests.isEmpty()) {
            return Mono.just(new VectorBatchSearchResult(List.of(), 0));
        }

        var immutableRequests = List.copyOf(requests);
        int effectiveConcurrency = Math.min(maxConcurrency, immutableRequests.size());
        var attemptedInvocationCount = new AtomicInteger();

        return Flux.fromIterable(immutableRequests)
                .flatMapSequential(
                        request ->
                                Mono.defer(
                                        () -> {
                                            attemptedInvocationCount.incrementAndGet();
                                            return search(
                                                            memoryId,
                                                            request.query(),
                                                            request.topK(),
                                                            request.minScore(),
                                                            request.filter())
                                                    .collectList();
                                        }),
                        effectiveConcurrency,
                        1)
                .collectList()
                .map(results -> new VectorBatchSearchResult(results, immutableRequests.size()))
                .onErrorMap(
                        error ->
                                error instanceof VectorBatchSearchException
                                        ? error
                                        : new VectorBatchSearchException(
                                                "Ordered batch search failed",
                                                error,
                                                attemptedInvocationCount.get()));
    }

    // ===== Embedding operations =====

    /**
     * Calculate the embedding vector of the text
     *
     * <p>Used for MMR and other reordering algorithms, requires obtaining the vector representation of the query and candidates
     *
     * @param text Text to calculate embedding
     * @return Embedding vector
     */
    Mono<List<Float>> embed(String text);

    /**
     * Batch calculate the embedding vectors of the texts
     *
     * <p>Used for MMR and other reordering algorithms, batch obtain the vector representation of candidates
     *
     * @param texts List of texts to calculate embedding
     * <p>Stage 3 semantic-linking contract: returned embeddings must live in the same vector space
     * as the scores returned by {@link #search(MemoryId, String, int, double, Map)} and
     * {@link #searchBatch(MemoryId, List, int)} when those values are merged in one semantic-link
     * normalization path.
     * @return List of embedding vectors, corresponding to the input texts
     */
    Mono<List<List<Float>>> embedAll(List<String> texts);

    /**
     * Batch fetch stored embedding vectors by vectorId.
     *
     * <p>Stage 3 semantic-linking contract: embeddings returned here and by {@link #embed(String)}
     * / {@link #embedAll(List)} must live in the same vector space as the scores returned by
     * {@link #search(MemoryId, String, int, double, Map)} and
     * {@link #searchBatch(MemoryId, List, int)} when those values participate in shared
     * semantic-link normalization. In a Stage 3-conforming implementation, search scores and
     * in-memory cosine similarity over these embeddings must be numerically comparable without a
     * backend-specific calibration layer.
     *
     * <p>Defaults to returning an empty map so callers can fall back to {@code embedAll(...)}
     * safely.
     *
     * @param vectorIds List of vector IDs
     * @return Mapping of vectorId → embedding
     */
    default Mono<Map<String, List<Float>>> fetchEmbeddings(List<String> vectorIds) {
        return Mono.just(Map.of());
    }
}
