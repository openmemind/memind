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
