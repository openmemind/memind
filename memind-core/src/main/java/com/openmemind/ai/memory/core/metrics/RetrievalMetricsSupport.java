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

import com.openmemind.ai.memory.core.retrieval.RetrievalResult;

public final class RetrievalMetricsSupport {

    private RetrievalMetricsSupport() {}

    public static RetrievalSummaryMetrics summary(
            String strategy, RetrievalResult result, String source) {
        String status = result == null || result.isEmpty() ? "empty" : "success";
        return new RetrievalSummaryMetrics(
                strategyOrUnknown(strategy),
                status,
                result == null || result.items() == null ? 0 : result.items().size(),
                result == null || result.insights() == null ? 0 : result.insights().size(),
                result == null || result.rawData() == null ? 0 : result.rawData().size(),
                result == null || result.evidences() == null ? 0 : result.evidences().size(),
                sourceOrCore(source));
    }

    public static int deduplicatedCount(int inputCount, int outputCount) {
        return Math.max(0, inputCount - outputCount);
    }

    public static void safeRecord(Runnable recorder) {
        try {
            recorder.run();
        } catch (RuntimeException ignored) {
            // Metrics collection must never affect memory operations.
        }
    }

    public static String strategyOrUnknown(String strategy) {
        return strategy == null || strategy.isBlank() ? "unknown" : strategy;
    }

    public static String sourceOrCore(String source) {
        return source == null || source.isBlank() ? "core" : source;
    }
}
