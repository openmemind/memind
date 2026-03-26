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
package com.openmemind.ai.memory.core.extraction.context;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import java.util.Objects;

/**
 * Request for building a context window.
 *
 * @param memoryId         the memory identity to retrieve context for
 * @param maxTokens        maximum token budget for the assembled context window
 * @param includeMemories  whether to retrieve relevant memories via vector search
 * @param strategy         retrieval strategy to use when {@code includeMemories} is true
 * @param recentMessageLimit maximum number of recent conversation messages to load
 */
public record ContextRequest(
        MemoryId memoryId,
        int maxTokens,
        boolean includeMemories,
        RetrievalConfig.Strategy strategy,
        int recentMessageLimit) {

    private static final int DEFAULT_RECENT_MESSAGE_LIMIT = 10;

    public ContextRequest {
        Objects.requireNonNull(memoryId, "memoryId is required");
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (recentMessageLimit <= 0) {
            throw new IllegalArgumentException("recentMessageLimit must be positive");
        }
    }

    /**
     * Creates a request that includes memory retrieval with the SIMPLE strategy.
     *
     * @param memoryId  the memory identity
     * @param maxTokens maximum token budget
     * @return context request with memory retrieval enabled
     */
    public static ContextRequest of(MemoryId memoryId, int maxTokens) {
        return new ContextRequest(
                memoryId,
                maxTokens,
                true,
                RetrievalConfig.Strategy.SIMPLE,
                DEFAULT_RECENT_MESSAGE_LIMIT);
    }

    /**
     * Creates a request with memory retrieval using the specified strategy.
     *
     * @param memoryId  the memory identity
     * @param maxTokens maximum token budget
     * @param strategy  retrieval strategy
     * @return context request with memory retrieval enabled
     */
    public static ContextRequest of(
            MemoryId memoryId, int maxTokens, RetrievalConfig.Strategy strategy) {
        return new ContextRequest(
                memoryId, maxTokens, true, strategy, DEFAULT_RECENT_MESSAGE_LIMIT);
    }

    /**
     * Creates a request that only returns the current buffer without memory retrieval.
     *
     * @param memoryId  the memory identity
     * @param maxTokens maximum token budget
     * @return context request with memory retrieval disabled
     */
    public static ContextRequest bufferOnly(MemoryId memoryId, int maxTokens) {
        return new ContextRequest(
                memoryId,
                maxTokens,
                false,
                RetrievalConfig.Strategy.SIMPLE,
                DEFAULT_RECENT_MESSAGE_LIMIT);
    }
}
