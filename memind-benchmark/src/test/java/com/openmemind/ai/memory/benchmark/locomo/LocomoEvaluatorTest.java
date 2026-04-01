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
package com.openmemind.ai.memory.benchmark.locomo;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.benchmark.core.QuestionResult;
import com.openmemind.ai.memory.benchmark.core.evaluator.Judgment;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocomoEvaluatorTest {

    @Test
    void aggregateComputesPerCategoryAndOverallAccuracy() {
        LocomoEvaluator evaluator = new LocomoEvaluator(null, null, null, "gpt-4o-mini", "gpt-4o");

        var report =
                evaluator.aggregate(
                        List.of(
                                new QuestionResult(
                                        "q1",
                                        "where",
                                        "hangzhou",
                                        "memory",
                                        "hangzhou",
                                        new Judgment("CORRECT", 1.0, "ok"),
                                        "single-hop",
                                        Duration.ofMillis(10),
                                        Duration.ofMillis(20)),
                                new QuestionResult(
                                        "q2",
                                        "when",
                                        "monday",
                                        "memory",
                                        "friday",
                                        new Judgment("WRONG", 0.0, "bad"),
                                        "temporal",
                                        Duration.ofMillis(10),
                                        Duration.ofMillis(20))));

        assertThat(report.metrics().get("single-hop").score()).isEqualTo(1.0);
        assertThat(report.metrics().get("temporal").score()).isEqualTo(0.0);
        assertThat(report.metrics().get("overall").score()).isEqualTo(0.5);
    }
}
