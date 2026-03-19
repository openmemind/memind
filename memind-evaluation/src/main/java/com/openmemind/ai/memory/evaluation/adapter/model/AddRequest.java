package com.openmemind.ai.memory.evaluation.adapter.model;

import com.openmemind.ai.memory.evaluation.dataset.model.EvalMessage;
import java.util.List;

/**
 * Memory add request, including conversation ID, both userIds and message list
 *
 */
public record AddRequest(
        String conversationId,
        String speakerAUserId,
        String speakerBUserId,
        List<EvalMessage> messages) {}
