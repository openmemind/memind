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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Exact match evaluator, used for multiple-choice scenarios, extracts option letters by priority using regex and compares
 *
 */
@Component
public class ExactMatchEvaluator implements AnswerEvaluator {

    /** Regex option extraction patterns, tried in order of priority from high to low */
    private static final Pattern[] OPTION_PATTERNS = {
        Pattern.compile("\\(([a-zA-Z])\\)"), // (a) — highest priority
        Pattern.compile("([a-zA-Z])\\)"), // a)
        Pattern.compile("([a-zA-Z])\\."), // a.
        Pattern.compile("^([a-zA-Z])$"), // single letter A — lowest priority
    };

    @Override
    public String strategy() {
        return "exact_match";
    }

    @Override
    public Mono<QuestionJudgment> evaluate(AnswerResult answer, QAPair qa) {
        String gen = extractOption(answer.generatedAnswer());
        String golden = extractOption(qa.goldenAnswer());
        boolean correct = gen.equalsIgnoreCase(golden);
        return Mono.just(QuestionJudgment.exact(answer, correct));
    }

    /**
     * Tries to extract option letters using regex in order of priority, if all fail, fallback to trim+toLowerCase
     *
     * @param s original answer string
     * @return extracted letter (lowercase), or the result of trim+toLowerCase
     */
    String extractOption(String s) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim();
        for (Pattern p : OPTION_PATTERNS) {
            Matcher m = p.matcher(trimmed);
            if (m.find()) {
                return m.group(1).toLowerCase();
            }
        }
        return trimmed.toLowerCase();
    }
}
