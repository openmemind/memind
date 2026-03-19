package com.openmemind.ai.memory.evaluation.dataset.model;

import java.util.List;
import java.util.Map;

/**
 * Evaluation question and answer pair, including question, standard answer, category, evidence, and multiple choice options metadata
 *
 */
public record QAPair(
        String questionId,
        String conversationId,
        String question,
        String goldenAnswer,
        String category,
        List<String> evidence,
        Map<String, Object> metadata) {
    public boolean isMultipleChoice() {
        return metadata.containsKey("all_options");
    }

    public String allOptions() {
        return (String) metadata.getOrDefault("all_options", "");
    }
}
