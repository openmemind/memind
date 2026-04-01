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
package com.openmemind.ai.memory.benchmark.longmemeval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LongMemEvalJudgeRulesTest {

    @Test
    void acceptsTemporalAnswersWithinOneDayTolerance() {
        LongMemEvalJudgeRules rules = new LongMemEvalJudgeRules();

        assertThat(rules.accepts("temporal-reasoning", "19 days", "18 days")).isTrue();
        assertThat(rules.accepts("temporal-reasoning", "20 days", "18 days")).isFalse();
    }

    @Test
    void selectsPromptByQuestionType() {
        LongMemEvalJudgeRules rules = new LongMemEvalJudgeRules();

        assertThat(rules.promptPath("temporal-reasoning"))
                .isEqualTo("prompts/longmemeval/judge-temporal.txt");
        assertThat(rules.promptPath("knowledge-update"))
                .isEqualTo("prompts/longmemeval/judge-knowledge-update.txt");
        assertThat(rules.promptPath("single-session-preference"))
                .isEqualTo("prompts/longmemeval/judge-preference.txt");
        assertThat(rules.promptPath("single-session-user"))
                .isEqualTo("prompts/longmemeval/judge-default.txt");
    }
}
