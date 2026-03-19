package com.openmemind.ai.memory.core.retrieval;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import reactor.core.publisher.Mono;

/**
 * Top-level memory retrieval interface
 *
 */
public interface MemoryRetriever {

    /**
     * Execute memory retrieval
     *
     * @param request Retrieval request
     * @return Retrieval result
     */
    Mono<RetrievalResult> retrieve(RetrievalRequest request);

    /**
     * Register custom retrieval strategy
     *
     * @param strategy Retrieval strategy
     */
    void registerStrategy(RetrievalStrategy strategy);

    /**
     * Data change notification (used for cache invalidation)
     *
     * @param memoryId Memory identifier that has changed
     */
    default void onDataChanged(MemoryId memoryId) {
        // Default empty implementation
    }
}
