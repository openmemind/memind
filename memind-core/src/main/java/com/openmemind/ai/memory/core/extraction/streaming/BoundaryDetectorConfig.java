package com.openmemind.ai.memory.core.extraction.streaming;

/**
 * Boundary detection configuration
 *
 * @param maxMessages Maximum number of messages (hard limit, archive when reached)
 * @param maxTokens Maximum number of tokens (hard limit, archive when reached)
 * @param minMessagesForLlm Minimum number of messages for LLM detection (do not call LLM if below this number)
 */
public record BoundaryDetectorConfig(int maxMessages, int maxTokens, int minMessagesForLlm) {

    private static final int DEFAULT_MAX_MESSAGES = 50;
    private static final int DEFAULT_MAX_TOKENS = 8192;
    private static final int DEFAULT_MIN_MESSAGES_FOR_LLM = 10;

    /**
     * Default configuration
     *
     * @return Default configuration (maxMessages=50, maxTokens=8192, minMessagesForLlm=10)
     */
    public static BoundaryDetectorConfig defaults() {
        return new BoundaryDetectorConfig(
                DEFAULT_MAX_MESSAGES, DEFAULT_MAX_TOKENS, DEFAULT_MIN_MESSAGES_FOR_LLM);
    }

    /**
     * Modify maximum number of messages
     *
     * @param maxMessages Maximum number of messages
     * @return New configuration
     */
    public BoundaryDetectorConfig withMaxMessages(int maxMessages) {
        return new BoundaryDetectorConfig(maxMessages, maxTokens, minMessagesForLlm);
    }

    /**
     * Modify maximum number of tokens
     *
     * @param maxTokens Maximum number of tokens
     * @return New configuration
     */
    public BoundaryDetectorConfig withMaxTokens(int maxTokens) {
        return new BoundaryDetectorConfig(maxMessages, maxTokens, minMessagesForLlm);
    }

    /**
     * Modify minimum number of messages for LLM detection
     *
     * @param minMessagesForLlm Minimum number of messages
     * @return New configuration
     */
    public BoundaryDetectorConfig withMinMessagesForLlm(int minMessagesForLlm) {
        return new BoundaryDetectorConfig(maxMessages, maxTokens, minMessagesForLlm);
    }
}
