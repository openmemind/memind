package com.openmemind.ai.memory.evaluation.evaluator;

import com.openmemind.ai.memory.evaluation.dataset.model.QAPair;
import com.openmemind.ai.memory.evaluation.pipeline.model.AnswerResult;
import com.openmemind.ai.memory.evaluation.pipeline.model.QuestionJudgment;
import reactor.core.publisher.Mono;

/**
 * Answer evaluator interface, compares generated answers with standard answers and returns judgment results
 *
 */
public interface AnswerEvaluator {
    Mono<QuestionJudgment> evaluate(AnswerResult answer, QAPair qa);
}
