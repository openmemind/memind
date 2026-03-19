package com.openmemind.ai.memory.evaluation.pipeline.model;

/**
 * Single question answer result, including question, standard answer, generated answer, and formatted context
 *
 */
public record AnswerResult(
        String questionId,
        String question,
        String goldenAnswer,
        String generatedAnswer,
        String category,
        String conversationId,
        String formattedContext) {}
