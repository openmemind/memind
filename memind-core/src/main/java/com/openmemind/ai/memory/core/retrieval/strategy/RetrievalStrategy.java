package com.openmemind.ai.memory.core.retrieval.strategy;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import reactor.core.publisher.Mono;

/**
 * Retrieval strategy interface
 *
 * <p>Ordinary interface (non-sealed), anyone can implement new strategies and register them to DefaultMemoryRetriever
 *
 */
public interface RetrievalStrategy {

    /**
     * Strategy name (used for registration and routing)
     *
     * @return Strategy name
     */
    String name();

    /**
     * Execute retrieval
     *
     * @param context Query context
     * @param config  Retrieval configuration
     * @return Retrieval result
     */
    Mono<RetrievalResult> retrieve(QueryContext context, RetrievalConfig config);

    /**
     * Data change notification (used for invalidating internal caches, etc.)
     *
     * @param memoryId Changed memory identifier
     */
    default void onDataChanged(MemoryId memoryId) {}
}
