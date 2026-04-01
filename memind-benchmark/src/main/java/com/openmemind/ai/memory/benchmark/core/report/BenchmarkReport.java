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
package com.openmemind.ai.memory.benchmark.core.report;

import com.openmemind.ai.memory.benchmark.core.QuestionResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record BenchmarkReport(
        String benchmarkName,
        Instant generatedAt,
        String memoryModel,
        String evalModel,
        int sampleCount,
        int questionCount,
        Map<String, MetricSummary> metrics,
        Duration addDuration,
        Duration evaluateDuration,
        Duration totalDuration,
        Map<String, Object> metadata,
        List<QuestionResult> details) {

    public record MetricSummary(double score, int count) {}
}
