package com.openmemind.ai.memory.evaluation.adapter.model;

/**
 * Memory retrieval request, including conversation ID, both userIds, query text, and topK parameter
 *
 */
public record SearchRequest(
        String conversationId,
        String speakerAUserId,
        String speakerBUserId,
        String query,
        int topK) {}
