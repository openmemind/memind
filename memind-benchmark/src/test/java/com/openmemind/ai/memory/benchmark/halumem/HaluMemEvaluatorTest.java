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
package com.openmemind.ai.memory.benchmark.halumem;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.benchmark.core.QuestionResult;
import com.openmemind.ai.memory.benchmark.core.evaluator.Judgment;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class HaluMemEvaluatorTest {

    @Test
    void aggregateBuildsIntegrityAccuracyAndQaSummaries() {
        HaluMemEvaluator evaluator = new HaluMemEvaluator(null, null, null, null);

        HaluMemReport report =
                evaluator.aggregate(
                        List.of(
                                new QuestionResult(
                                        "integrity-1",
                                        "integrity",
                                        "2",
                                        "memory",
                                        "memory",
                                        new Judgment("INTEGRITY", 2.0, "full"),
                                        "integrity",
                                        Duration.ofMillis(10),
                                        Duration.ZERO),
                                new QuestionResult(
                                        "qa-1",
                                        "qa",
                                        "Hangzhou",
                                        "memory",
                                        "Hangzhou",
                                        new Judgment("CORRECT", 1.0, "matched"),
                                        "qa",
                                        Duration.ofMillis(10),
                                        Duration.ofMillis(20))));

        assertThat(report.integrityAverage()).isEqualTo(2.0);
        assertThat(report.qaVerdicts()).containsEntry("CORRECT", 1L);
    }
}
