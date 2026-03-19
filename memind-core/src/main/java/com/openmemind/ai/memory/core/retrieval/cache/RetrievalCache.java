package com.openmemind.ai.memory.core.retrieval.cache;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import java.util.Optional;

/**
 * Retrieval cache interface
 *
 */
public interface RetrievalCache {

    /**
     * Get cached result
     *
     * @param memoryId   Memory identifier
     * @param queryHash  Query hash
     * @param configHash Configuration hash
     * @return Cached retrieval result
     */
    Optional<RetrievalResult> get(MemoryId memoryId, String queryHash, String configHash);

    /**
     * Write to cache
     *
     * @param memoryId   Memory identifier
     * @param queryHash  Query hash
     * @param configHash Configuration hash
     * @param result     Retrieval result
     */
    void put(MemoryId memoryId, String queryHash, String configHash, RetrievalResult result);

    /**
     * Invalidate all caches for the specified memoryId
     *
     * @param memoryId Memory identifier
     */
    void invalidate(MemoryId memoryId);
}
