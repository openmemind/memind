package com.openmemind.ai.memory.evaluation.dataset.model;

import java.util.List;
import java.util.Map;

/**
 * Evaluation conversation model, including conversation ID, message list, and speaker metadata
 *
 */
public record EvalConversation(
        String conversationId, List<EvalMessage> messages, Map<String, Object> metadata) {
    public String speakerA() {
        return (String) metadata.getOrDefault("speaker_a", "");
    }

    public String speakerB() {
        return (String) metadata.getOrDefault("speaker_b", "");
    }
}
