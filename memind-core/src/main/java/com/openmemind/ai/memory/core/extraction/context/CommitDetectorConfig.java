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

/**
 * Boundary detection configuration.
 *
 * @param maxMessages maximum number of messages before sealing
 * @param maxTokens maximum number of tokens before sealing
 * @param minMessagesForLlm minimum number of messages before running LLM topic-shift detection
 */
public record CommitDetectorConfig(int maxMessages, int maxTokens, int minMessagesForLlm) {

    private static final int DEFAULT_MAX_MESSAGES = 50;
    private static final int DEFAULT_MAX_TOKENS = 8192;
    private static final int DEFAULT_MIN_MESSAGES_FOR_LLM = 5;

    /**
     * Default configuration.
     */
    public static CommitDetectorConfig defaults() {
        return new CommitDetectorConfig(
                DEFAULT_MAX_MESSAGES, DEFAULT_MAX_TOKENS, DEFAULT_MIN_MESSAGES_FOR_LLM);
    }

    /**
     * Return a copy with a different maximum number of messages.
     */
    public CommitDetectorConfig withMaxMessages(int maxMessages) {
        return new CommitDetectorConfig(maxMessages, maxTokens, minMessagesForLlm);
    }

    /**
     * Return a copy with a different maximum number of tokens.
     */
    public CommitDetectorConfig withMaxTokens(int maxTokens) {
        return new CommitDetectorConfig(maxMessages, maxTokens, minMessagesForLlm);
    }

    /**
     * Return a copy with a different LLM threshold.
     */
    public CommitDetectorConfig withMinMessagesForLlm(int minMessagesForLlm) {
        return new CommitDetectorConfig(maxMessages, maxTokens, minMessagesForLlm);
    }
}
