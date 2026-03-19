package com.openmemind.ai.memory.core.retrieval.intent;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Always Retrieve Router
 *
 */
public class AlwaysRetrieveRouter implements IntentionRouter {

    @Override
    public Mono<RetrievalIntent> route(
            MemoryId memoryId, String query, List<String> conversationHistory) {
        return Mono.just(RetrievalIntent.RETRIEVE);
    }
}
