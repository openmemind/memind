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
package com.openmemind.ai.memory.plugin.rawdata.agent.chunk;

import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

final class AgentToolTelemetry {

    private static final int MAX_TOOL_RECORDS = 40;
    private static final int MAX_TOOL_STATS = 20;
    private static final int MAX_TOOL_GROUPS = 20;
    private static final int MAX_GROUP_VALUES = 20;
    private static final int MAX_OUTPUT_PREVIEW_CHARS = 240;

    private AgentToolTelemetry() {}

    static Map<String, Object> metadata(List<AgentEvent> events) {
        List<AgentEvent> toolEvents =
                events == null
                        ? List.of()
                        : events.stream().filter(AgentToolTelemetry::hasToolEvidence).toList();
        if (toolEvents.isEmpty()) {
            return Map.of();
        }

        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("toolRecords", toolRecords(toolEvents));
        metadata.put("toolStats", toolStats(toolEvents));
        metadata.put("toolGroups", toolGroups(toolEvents));
        return Map.copyOf(metadata);
    }

    private static boolean hasToolEvidence(AgentEvent event) {
        return event != null && hasText(event.toolName());
    }

    private static List<Map<String, Object>> toolRecords(List<AgentEvent> events) {
        var records = new ArrayList<Map<String, Object>>();
        events.stream().limit(MAX_TOOL_RECORDS).forEach(event -> records.add(toolRecord(event)));
        return List.copyOf(records);
    }

    private static Map<String, Object> toolRecord(AgentEvent event) {
        var record = new LinkedHashMap<String, Object>();
        put(record, "eventId", event.eventId());
        put(record, "seq", event.seq());
        put(record, "toolName", event.toolName());
        put(record, "kind", event.kind() == null ? null : event.kind().wireValue());
        put(record, "status", event.status() == null ? null : event.status().wireValue());
        put(record, "durationMs", event.durationMs());
        put(record, "inputTokens", event.inputTokens());
        put(record, "outputTokens", event.outputTokens());
        put(record, "contentHash", event.contentHash());
        put(record, "path", event.path());
        put(record, "operation", event.operation());
        put(record, "command", event.command());
        put(record, "outputPreview", concise(event.output()));
        return Map.copyOf(record);
    }

    private static Map<String, Map<String, Object>> toolStats(List<AgentEvent> events) {
        var grouped = groupByToolName(events);
        var stats = new LinkedHashMap<String, Map<String, Object>>();
        grouped.entrySet().stream()
                .limit(MAX_TOOL_STATS)
                .forEach(
                        groupEntry -> {
                            String toolName = groupEntry.getKey();
                            List<AgentEvent> toolEvents = groupEntry.getValue();
                            int success = countStatus(toolEvents, AgentEventStatus.SUCCESS);
                            int failed = countStatus(toolEvents, AgentEventStatus.FAILED);
                            var durations =
                                    toolEvents.stream()
                                            .map(AgentEvent::durationMs)
                                            .filter(value -> value != null)
                                            .mapToLong(Long::longValue)
                                            .summaryStatistics();
                            var stat = new LinkedHashMap<String, Object>();
                            stat.put("callCount", toolEvents.size());
                            stat.put("successCount", success);
                            stat.put("failCount", failed);
                            if (durations.getCount() > 0) {
                                stat.put("avgDurationMs", Math.round(durations.getAverage()));
                            }
                            sum(toolEvents, AgentEvent::inputTokens)
                                    .ifPresent(value -> stat.put("inputTokens", value));
                            sum(toolEvents, AgentEvent::outputTokens)
                                    .ifPresent(value -> stat.put("outputTokens", value));
                            stats.put(toolName, Map.copyOf(stat));
                        });
        return Map.copyOf(stats);
    }

    private static List<Map<String, Object>> toolGroups(List<AgentEvent> events) {
        var grouped = groupByToolName(events);
        var groups = new ArrayList<Map<String, Object>>();
        grouped.entrySet().stream()
                .limit(MAX_TOOL_GROUPS)
                .forEach(
                        groupEntry -> {
                            String toolName = groupEntry.getKey();
                            List<AgentEvent> toolEvents = groupEntry.getValue();
                            var group = new LinkedHashMap<String, Object>();
                            group.put("toolName", toolName);
                            group.put("callCount", toolEvents.size());
                            group.put(
                                    "successCount",
                                    countStatus(toolEvents, AgentEventStatus.SUCCESS));
                            group.put(
                                    "failCount", countStatus(toolEvents, AgentEventStatus.FAILED));
                            putCollection(
                                    group,
                                    "commands",
                                    distinctValues(toolEvents, AgentEvent::command));
                            putCollection(
                                    group, "paths", distinctValues(toolEvents, AgentEvent::path));
                            groups.add(Map.copyOf(group));
                        });
        return List.copyOf(groups);
    }

    private static LinkedHashMap<String, List<AgentEvent>> groupByToolName(
            List<AgentEvent> events) {
        var grouped = new LinkedHashMap<String, List<AgentEvent>>();
        events.forEach(
                event ->
                        grouped.computeIfAbsent(event.toolName(), key -> new ArrayList<>())
                                .add(event));
        return grouped;
    }

    private static List<String> distinctValues(
            List<AgentEvent> events, Function<AgentEvent, String> getter) {
        var values = new LinkedHashSet<String>();
        for (AgentEvent event : events) {
            String value = getter.apply(event);
            if (hasText(value)) {
                values.add(value);
            }
            if (values.size() >= MAX_GROUP_VALUES) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private static int countStatus(List<AgentEvent> events, AgentEventStatus status) {
        return (int) events.stream().filter(event -> event.status() == status).count();
    }

    private static Optional<Integer> sum(
            List<AgentEvent> events, Function<AgentEvent, Integer> getter) {
        var values = events.stream().map(getter).filter(value -> value != null).toList();
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(values.stream().mapToInt(Integer::intValue).sum());
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            target.put(key, value);
        }
    }

    private static void putCollection(Map<String, Object> target, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            target.put(key, values);
        }
    }

    private static String concise(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_OUTPUT_PREVIEW_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_OUTPUT_PREVIEW_CHARS);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
