package com.openmemind.ai.memory.core.retrieval.query;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Passthrough Query Rewriter
 *
 * <p>Does not perform any rewriting, directly returns the original query
 *
 */
public class PassthroughQueryRewriter implements QueryRewriter {

    @Override
    public Mono<String> rewrite(MemoryId memoryId, String query, List<String> conversationHistory) {
        return Mono.just(query);
    }
}
