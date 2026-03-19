package com.openmemind.ai.memory.evaluation.adapter.model;

import com.openmemind.ai.memory.core.retrieval.RetrievalResult;

/**
 * Memory retrieval result, containing RetrievalResult and formatted context text
 *
 */
public record SearchResult(
        String questionId,
        String conversationId,
        String query,
        RetrievalResult retrievalResult,
        String formattedContext) {
    public SearchResult withQuestionId(String qid) {
        return new SearchResult(qid, conversationId, query, retrievalResult, formattedContext);
    }
}
