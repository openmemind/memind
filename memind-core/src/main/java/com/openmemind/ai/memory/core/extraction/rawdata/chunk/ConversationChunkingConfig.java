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
