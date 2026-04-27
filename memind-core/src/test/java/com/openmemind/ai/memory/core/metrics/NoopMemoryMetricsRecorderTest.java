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
package com.openmemind.ai.memory.core.metrics;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class NoopMemoryMetricsRecorderTest {

    @Test
    void acceptsAllMetricPayloadsWithoutSideEffects() {
        MemoryMetricsRecorder recorder = NoopMemoryMetricsRecorder.INSTANCE;

        assertThatCode(
                        () -> {
                            recorder.recordExtractionSummary(
                                    new ExtractionMetrics(
                                            "success", 1, null, 2, null, 3, 4, 5, 6, "core"));
                            recorder.recordRetrievalStage(
                                    new RetrievalStageMetrics(
                                            "simple", "tier", "item", "vector", "success", null, 10,
                                            5, false, false, "core"));
                            recorder.recordRetrievalMerge(
                                    new RetrievalMergeMetrics(
                                            "simple", 20, 12, 8, 3, "success", "core"));
                            recorder.recordRetrievalSummary(
                                    new RetrievalSummaryMetrics(
                                            "simple", "success", 5, 1, 0, 0, "core"));
                        })
                .doesNotThrowAnyException();
    }
}
