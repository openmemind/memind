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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.stats;

import com.openmemind.ai.memory.plugin.rawdata.toolcall.model.ToolCallRecord;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.model.ToolCallStats;
import java.util.List;

/**
 * Tool call statistics aggregator.
 */
public final class ToolCallStatsAggregator {

    private ToolCallStatsAggregator() {}

    public static ToolCallStats aggregate(List<ToolCallRecord> records, int recentN) {
        if (records.isEmpty()) {
            return new ToolCallStats(0, 0, 0.0, 0.0, 0.0, 0.0);
        }

        int total = records.size();
        List<ToolCallRecord> recent =
                records.size() <= recentN
                        ? records
                        : records.subList(records.size() - recentN, records.size());

        long successCount =
                recent.stream()
                        .filter(
                                record ->
                                        record.status() != null
                                                && "success".equalsIgnoreCase(record.status()))
                        .count();
        double successRate = (double) successCount / recent.size();
        double avgTime =
                recent.stream().mapToDouble(ToolCallRecord::durationMs).average().orElse(0.0);
        double avgTokens =
                recent.stream()
                        .mapToDouble(record -> record.inputTokens() + record.outputTokens())
                        .average()
                        .orElse(0.0);

        return new ToolCallStats(
                total, recent.size(), successRate, avgTime, successRate, avgTokens);
    }
}
