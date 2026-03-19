package com.openmemind.ai.memory.evaluation.dataset.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Evaluation dataset, containing a list of conversations and a list of QA pairs, providing convenient methods indexed by conversation ID
 *
 */
public record EvalDataset(String name, List<EvalConversation> conversations, List<QAPair> qaPairs) {
    public Map<String, EvalConversation> conversationIndex() {
        return conversations.stream()
                .collect(Collectors.toMap(EvalConversation::conversationId, c -> c));
    }

    public Map<String, List<QAPair>> qaPairsByConversation() {
        return qaPairs.stream().collect(Collectors.groupingBy(QAPair::conversationId));
    }
}
