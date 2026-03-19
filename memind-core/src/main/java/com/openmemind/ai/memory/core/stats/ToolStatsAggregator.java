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
package com.openmemind.ai.memory.core.stats;

import com.openmemind.ai.memory.core.data.ToolCallStats;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import java.util.List;

/**
 * Tool call statistics aggregator (pure computation, does not rely on LLM)
 *
 */
public final class ToolStatsAggregator {

    private ToolStatsAggregator() {}

    public static ToolCallStats aggregate(List<ToolCallRecord> records, int recentN) {
        if (records.isEmpty()) {
            return new ToolCallStats(0, 0, 0.0, 0.0, 0.0, 0.0);
        }

        int total = records.size();
        var recent =
                records.size() <= recentN
                        ? records
                        : records.subList(records.size() - recentN, records.size());

        long successCount = recent.stream().filter(r -> "success".equals(r.status())).count();
        double successRate = (double) successCount / recent.size();
        double avgTime =
                recent.stream().mapToDouble(ToolCallRecord::durationMs).average().orElse(0.0);
        // avgScore approximates per-call LLM evaluation score using successRate
        // (success=1.0, failure=0.0). Replace with real scores when item metadata is available.
        double avgTokens =
                recent.stream()
                        .mapToDouble(r -> r.inputTokens() + r.outputTokens())
                        .average()
                        .orElse(0.0);

        return new ToolCallStats(
                total, recent.size(), successRate, avgTime, successRate, avgTokens);
    }
}
