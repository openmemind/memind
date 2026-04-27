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
package com.openmemind.ai.memory.plugin.tracing.otel;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.openmemind.ai.memory.core.metrics.ExtractionMetrics;
import com.openmemind.ai.memory.core.metrics.RetrievalMergeMetrics;
import com.openmemind.ai.memory.core.metrics.RetrievalStageMetrics;
import com.openmemind.ai.memory.core.metrics.RetrievalSummaryMetrics;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.junit.jupiter.api.Test;

class OpenTelemetryMemoryMetricsRecorderTest {

    @Test
    void recordsAllBusinessMetricPayloads() {
        try (SdkMeterProvider provider = SdkMeterProvider.builder().build()) {
            Meter meter = provider.get("memind-test");
            var recorder = new OpenTelemetryMemoryMetricsRecorder(meter);

            assertThatCode(
                            () -> {
                                recorder.recordExtractionSummary(
                                        new ExtractionMetrics(
                                                "success", 1, null, 2, null, 3, 4, 5, 6, "core"));
                                recorder.recordRetrievalStage(
                                        new RetrievalStageMetrics(
                                                "simple", "tier", "item", "vector", "success", null,
                                                10, 5, false, false, "core"));
                                recorder.recordRetrievalStage(
                                        new RetrievalStageMetrics(
                                                "simple",
                                                "channel",
                                                "item",
                                                "temporal",
                                                "degraded",
                                                10,
                                                3,
                                                2,
                                                true,
                                                false,
                                                "core"));
                                recorder.recordRetrievalMerge(
                                        new RetrievalMergeMetrics(
                                                "simple", 20, 12, 8, 3, "success", "core"));
                                recorder.recordRetrievalSummary(
                                        new RetrievalSummaryMetrics(
                                                "simple", "empty", 0, 0, 0, 0, "core"));
                            })
                    .doesNotThrowAnyException();
        }
    }
}
