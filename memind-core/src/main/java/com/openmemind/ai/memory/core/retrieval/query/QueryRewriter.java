package com.openmemind.ai.memory.core.retrieval.query;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Query rewriting interface
 *
 * <p>Parse pronouns, supplement context, and generate query text more suitable for vector search
 *
 */
public interface QueryRewriter {

    /**
     * Rewrite query
     *
     * @param memoryId            Memory identifier
     * @param query               Original query
     * @param conversationHistory Recent conversation history
     * @return Rewritten query text
     */
    Mono<String> rewrite(MemoryId memoryId, String query, List<String> conversationHistory);
}
