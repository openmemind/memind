package com.openmemind.ai.memory.core.retrieval.intent;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Intention routing interface
 *
 * <p>Determine whether the current query needs to retrieve memory, and can also override query
 *
 */
public interface IntentionRouter {

    /**
     * Determine retrieval intention
     *
     * @param memoryId            Memory identifier
     * @param query               User query
     * @param conversationHistory Recent conversation history
     * @return Retrieval intention
     */
    Mono<RetrievalIntent> route(MemoryId memoryId, String query, List<String> conversationHistory);
}
