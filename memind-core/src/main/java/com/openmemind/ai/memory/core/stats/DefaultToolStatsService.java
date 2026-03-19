package com.openmemind.ai.memory.core.stats;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.ToolCallStats;
import com.openmemind.ai.memory.core.store.MemoryStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * Default implementation of the tool call statistics service
 *
 */
public class DefaultToolStatsService implements ToolStatsService {

    private final MemoryStore store;

    public DefaultToolStatsService(MemoryStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public Mono<ToolCallStats> getToolStats(MemoryId memoryId, String toolName) {
        return Mono.fromCallable(
                () -> {
                    var items =
                            store.getAllItems(memoryId).stream()
                                    .filter(
                                            item ->
                                                    toolName.equals(
                                                            extractMetadataString(
                                                                    item, "toolName")))
                                    .toList();
                    return aggregateFromMetadata(items);
                });
    }

    @Override
    public Mono<Map<String, ToolCallStats>> getAllToolStats(MemoryId memoryId) {
        return Mono.fromCallable(
                () -> {
                    var grouped =
                            store.getAllItems(memoryId).stream()
                                    .filter(
                                            item ->
                                                    !extractMetadataString(item, "toolName")
                                                            .isEmpty())
                                    .collect(
                                            Collectors.groupingBy(
                                                    item ->
                                                            extractMetadataString(
                                                                    item, "toolName")));
                    return grouped.entrySet().stream()
                            .collect(
                                    Collectors.toMap(
                                            Map.Entry::getKey,
                                            e -> aggregateFromMetadata(e.getValue())));
                });
    }

    /**
     * Aggregate stats directly from pre-computed metadata fields stored by ToolCallItemExtractionStrategy.
     * Supports multiple MemoryItems for the same tool (accumulated across multiple reportToolCalls batches).
     */
    private ToolCallStats aggregateFromMetadata(List<MemoryItem> items) {
        int totalCalls = 0;
        int totalSuccess = 0;
        long totalDuration = 0;
        double totalScore = 0;
        int scoreCount = 0;

        for (var item : items) {
            var meta = item.metadata() != null ? item.metadata() : Map.<String, Object>of();
            int callCount = extractMetadataInt(meta, "callCount");
            int successCount = extractMetadataInt(meta, "successCount");
            long avgDurationMs = extractMetadataLong(meta, "avgDurationMs");

            totalCalls += callCount;
            totalSuccess += successCount;
            totalDuration += avgDurationMs * callCount;

            String scoreStr = extractMetadataString(item, "score");
            if (!scoreStr.isEmpty()) {
                try {
                    totalScore += Double.parseDouble(scoreStr);
                    scoreCount++;
                } catch (NumberFormatException ignored) {
                }
            }
        }

        double successRate = totalCalls > 0 ? (double) totalSuccess / totalCalls : 0.0;
        double avgTime = totalCalls > 0 ? (double) totalDuration / totalCalls : 0.0;
        double avgScore = scoreCount > 0 ? totalScore / scoreCount : 0.0;

        return new ToolCallStats(totalCalls, totalCalls, successRate, avgTime, avgScore, 0.0);
    }

    private String extractMetadataString(MemoryItem item, String key) {
        if (item.metadata() == null) {
            return "";
        }
        var value = item.metadata().get(key);
        return value != null ? value.toString() : "";
    }

    private long extractMetadataLong(Map<String, Object> meta, String key) {
        var value = meta.get(key);
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private int extractMetadataInt(Map<String, Object> meta, String key) {
        var value = meta.get(key);
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
