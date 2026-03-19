package com.openmemind.ai.memory.core.retrieval;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retrieval request
 *
 * @param memoryId            Memory identifier
 * @param query               Query text
 * @param conversationHistory Recent conversation history (for intent determination and query rewriting)
 * @param config              Retrieval configuration (use default configuration when null)
 * @param metadata            Additional metadata
 * @param scope               Memory scope filter (null means no filter)
 * @param categories          Memory category filter (null means no filter)
 */
public record RetrievalRequest(
        MemoryId memoryId,
        String query,
        List<String> conversationHistory,
        RetrievalConfig config,
        Map<String, Object> metadata,
        MemoryScope scope,
        Set<MemoryCategory> categories) {

    /**
     * Convenient constructor: specify memoryId, query, and conversation history
     */
    public static RetrievalRequest of(
            MemoryId memoryId, String query, List<String> conversationHistory) {
        return new RetrievalRequest(
                memoryId, query, conversationHistory, null, Map.of(), null, null);
    }

    /**
     * Convenient constructor: only retrieve user memory (scope=USER)
     */
    public static RetrievalRequest userMemory(
            MemoryId memoryId, String query, RetrievalConfig.Strategy strategy) {
        RetrievalConfig config =
                switch (strategy) {
                    case SIMPLE -> RetrievalConfig.simple();
                    case DEEP -> RetrievalConfig.deep();
                };
        return new RetrievalRequest(
                memoryId, query, List.of(), config, Map.of(), MemoryScope.USER, null);
    }

    /**
     * Convenient constructor: only retrieve Agent memory (scope=AGENT)
     */
    public static RetrievalRequest agentMemory(
            MemoryId memoryId, String query, RetrievalConfig.Strategy strategy) {
        RetrievalConfig config =
                switch (strategy) {
                    case SIMPLE -> RetrievalConfig.simple();
                    case DEEP -> RetrievalConfig.deep();
                };
        return new RetrievalRequest(
                memoryId, query, List.of(), config, Map.of(), MemoryScope.AGENT, null);
    }

    /**
     * Convenient constructor: specify memoryId, query, and retrieval strategy
     */
    public static RetrievalRequest of(
            MemoryId memoryId, String query, RetrievalConfig.Strategy strategy) {
        RetrievalConfig config =
                switch (strategy) {
                    case SIMPLE -> RetrievalConfig.simple();
                    case DEEP -> RetrievalConfig.deep();
                };
        return new RetrievalRequest(memoryId, query, List.of(), config, Map.of(), null, null);
    }

    /**
     * Convenient constructor: filter retrieval by specified categories
     */
    public static RetrievalRequest byCategories(
            MemoryId memoryId,
            String query,
            Set<MemoryCategory> categories,
            RetrievalConfig.Strategy strategy) {
        RetrievalConfig config =
                switch (strategy) {
                    case SIMPLE -> RetrievalConfig.simple();
                    case DEEP -> RetrievalConfig.deep();
                };
        return new RetrievalRequest(memoryId, query, List.of(), config, Map.of(), null, categories);
    }
}
