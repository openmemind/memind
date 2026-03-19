package com.openmemind.ai.memory.core.vector;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.List;
import java.util.Map;
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
     * @return List of embedding vectors, corresponding to the input texts
     */
    Mono<List<List<Float>>> embedAll(List<String> texts);

    /**
     * Batch fetch stored embedding vectors by vectorId.
     *
     * <p>Defaults to returning an empty map (triggers embedAll fallback), subclasses can override to fetch directly from the vector database.
     *
     * @param vectorIds List of vector IDs
     * @return Mapping of vectorId → embedding
     */
    default Mono<Map<String, List<Float>>> fetchEmbeddings(List<String> vectorIds) {
        return Mono.just(Map.of());
    }
}
