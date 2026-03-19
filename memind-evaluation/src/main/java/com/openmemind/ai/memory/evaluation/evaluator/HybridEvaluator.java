package com.openmemind.ai.memory.evaluation.evaluator;

import com.openmemind.ai.memory.evaluation.dataset.model.QAPair;
import com.openmemind.ai.memory.evaluation.pipeline.model.AnswerResult;
import com.openmemind.ai.memory.evaluation.pipeline.model.QuestionJudgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Hybrid Evaluator: uses exact matching for multiple choice questions, and LLM Judge for open-ended questions with multiple votes
 *
 */
@Component
public class HybridEvaluator implements AnswerEvaluator {

    private final ExactMatchEvaluator exactMatch;
    private final LlmJudgeEvaluator llmJudge;

    public HybridEvaluator(ExactMatchEvaluator exactMatch, LlmJudgeEvaluator llmJudge) {
        this.exactMatch = exactMatch;
        this.llmJudge = llmJudge;
    }

    @Override
    public Mono<QuestionJudgment> evaluate(AnswerResult answer, QAPair qa) {
        return qa.isMultipleChoice()
                ? exactMatch.evaluate(answer, qa)
                : llmJudge.evaluate(answer, qa);
    }
}
