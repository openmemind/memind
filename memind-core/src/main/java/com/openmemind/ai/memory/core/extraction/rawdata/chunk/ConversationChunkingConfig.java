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
package com.openmemind.ai.memory.core.extraction.rawdata.chunk;

/**
 * Conversation chunking configuration
 *
 * @param messagesPerChunk Number of messages per chunk in fixed segment mode
 * @param strategy Segmentation strategy (FIXED_SIZE or LLM)
 * @param minMessagesPerSegment Minimum number of messages per segment in LLM mode
 */
public record ConversationChunkingConfig(
        int messagesPerChunk, ConversationSegmentStrategy strategy, int minMessagesPerSegment)
        implements ChunkingConfig {

    public enum ConversationSegmentStrategy {
        FIXED_SIZE,
        LLM
    }

    public ConversationChunkingConfig {
        if (messagesPerChunk <= 0) {
            throw new IllegalArgumentException("messagesPerChunk must be positive");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }
        if (minMessagesPerSegment <= 0) {
            throw new IllegalArgumentException("minMessagesPerSegment must be positive");
        }
    }

    /** Backward compatible single-parameter constructor */
    public ConversationChunkingConfig(int messagesPerChunk) {
        this(messagesPerChunk, ConversationSegmentStrategy.FIXED_SIZE, 20);
    }

    public static final ConversationChunkingConfig DEFAULT = new ConversationChunkingConfig(10);
}
