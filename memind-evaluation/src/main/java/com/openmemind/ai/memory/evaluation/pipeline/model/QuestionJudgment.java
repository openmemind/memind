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
package com.openmemind.ai.memory.evaluation.pipeline.model;

import java.util.List;
import java.util.Map;

/**
 * Single question judgment result, including the score statistics of each run of LLM Judge
 *
 */
public record QuestionJudgment(
        String questionId,
        String question,
        String goldenAnswer,
        String generatedAnswer,
        String category,
        boolean correct,
        Map<String, Boolean> llmJudgments,
        /** Score of each independent LLM judgment [1.0, 0.0, 1.0] */
        List<Double> runScores,
        /** Mean of multiple judgments */
        double meanScore,
        /** Standard deviation of multiple judgments */
        double stdScore) {

    public static QuestionJudgment exact(AnswerResult ar, boolean correct) {
        double score = correct ? 1.0 : 0.0;
        return new QuestionJudgment(
                ar.questionId(),
                ar.question(),
                ar.goldenAnswer(),
                ar.generatedAnswer(),
                ar.category(),
                correct,
                null,
                List.of(score),
                score,
                0.0);
    }

    public static QuestionJudgment llm(
            AnswerResult ar,
            boolean correct,
            Map<String, Boolean> judgments,
            List<Double> runScores,
            double mean,
            double std) {
        return new QuestionJudgment(
                ar.questionId(),
                ar.question(),
                ar.goldenAnswer(),
                ar.generatedAnswer(),
                ar.category(),
                correct,
                judgments,
                runScores,
                mean,
                std);
    }
}
