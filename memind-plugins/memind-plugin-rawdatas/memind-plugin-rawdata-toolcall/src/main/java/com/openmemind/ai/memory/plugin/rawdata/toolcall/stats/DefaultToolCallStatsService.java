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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.model.ToolCallStats;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * Default metadata-backed ToolCall stats implementation.
 */
public class DefaultToolCallStatsService implements ToolCallStatsService {

    private final MemoryStore memoryStore;

    public DefaultToolCallStatsService(MemoryStore memoryStore) {
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore must not be null");
    }

    @Override
    public Mono<ToolCallStats> getToolStats(MemoryId memoryId, String toolName) {
        return Mono.fromCallable(
                () ->
                        aggregateFromMetadata(memoryId)
                                .getOrDefault(
                                        toolName, new ToolCallStats(0, 0, 0.0, 0.0, 0.0, 0.0)));
    }

    @Override
    public Mono<Map<String, ToolCallStats>> getAllToolStats(MemoryId memoryId) {
        return Mono.fromCallable(() -> Map.copyOf(aggregateFromMetadata(memoryId)));
    }

    private Map<String, ToolCallStats> aggregateFromMetadata(MemoryId memoryId) {
        var grouped =
                memoryStore.itemOperations().listItems(memoryId).stream()
                        .filter(
                                item ->
                                        item.metadata() != null
                                                && item.metadata().get("toolName") != null)
                        .collect(
                                Collectors.groupingBy(
                                        item -> item.metadata().get("toolName").toString()));
        return grouped.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> aggregateMetadataItems(entry.getValue())));
    }

    private ToolCallStats aggregateMetadataItems(List<MemoryItem> items) {
        int totalCalls = 0;
        int totalSuccess = 0;
        long totalDuration = 0L;
        double totalScore = 0.0;
        int scoreCount = 0;

        for (MemoryItem item : items) {
            Map<String, Object> metadata = item.metadata() == null ? Map.of() : item.metadata();
            int callCount = parseInt(metadata.get("callCount"));
            int successCount = parseInt(metadata.get("successCount"));
            long avgDurationMs = parseLong(metadata.get("avgDurationMs"));
            totalCalls += callCount;
            totalSuccess += successCount;
            totalDuration += avgDurationMs * callCount;
            if (metadata.get("score") != null) {
                totalScore += Double.parseDouble(metadata.get("score").toString());
                scoreCount++;
            }
        }

        return new ToolCallStats(
                totalCalls,
                totalCalls,
                totalCalls == 0 ? 0.0 : (double) totalSuccess / totalCalls,
                totalCalls == 0 ? 0.0 : (double) totalDuration / totalCalls,
                scoreCount == 0 ? 0.0 : totalScore / scoreCount,
                0.0);
    }

    private int parseInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(value.toString());
    }

    private long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(value.toString());
    }
}
