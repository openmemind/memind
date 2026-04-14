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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.chunk;

import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TokenAwareSegmentAssembler;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.config.ToolCallChunkingOptions;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.model.ToolCallRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tool call chunker that groups by tool name, then splits by token budget and time window.
 */
public class ToolCallChunker {

    private final ToolCallChunkingOptions options;
    private final TokenAwareSegmentAssembler tokenAwareSegmentAssembler;

    public ToolCallChunker() {
        this(ToolCallChunkingOptions.defaults());
    }

    public ToolCallChunker(ToolCallChunkingOptions options) {
        this.options = options == null ? ToolCallChunkingOptions.defaults() : options;
        this.tokenAwareSegmentAssembler = new TokenAwareSegmentAssembler();
    }

    public List<Segment> chunk(List<ToolCallRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        var byTool = new LinkedHashMap<String, List<ToolCallRecord>>();
        for (ToolCallRecord record : records) {
            byTool.computeIfAbsent(
                            record.toolName() != null ? record.toolName() : "unknown",
                            key -> new ArrayList<>())
                    .add(record);
        }

        List<Segment> segments = new ArrayList<>();
        for (Map.Entry<String, List<ToolCallRecord>> entry : byTool.entrySet()) {
            segments.addAll(splitGroup(entry.getKey(), entry.getValue()));
        }
        return segments;
    }

    private List<Segment> splitGroup(String toolName, List<ToolCallRecord> records) {
        List<Segment> segments = new ArrayList<>();
        List<ToolCallRecord> current = new ArrayList<>();
        Instant windowStart = null;

        for (ToolCallRecord record : records) {
            String recordText = formatRecord(record);
            int recordTokens = TokenUtils.countTokens(recordText);
            if (recordTokens > options.hardMaxTokens()) {
                if (!current.isEmpty()) {
                    segments.add(buildSegment(toolName, current));
                    current = new ArrayList<>();
                    windowStart = null;
                }
                segments.addAll(splitSingleRecord(toolName, record, recordText));
                continue;
            }

            boolean exceedsBudget =
                    !current.isEmpty()
                            && projectedTokenCount(toolName, current, record)
                                    > options.targetTokens();
            boolean exceedsTimeWindow =
                    !current.isEmpty()
                            && exceedsTimeWindow(
                                    windowStart, record.calledAt(), options.maxTimeWindow());

            if (exceedsBudget || exceedsTimeWindow) {
                segments.add(buildSegment(toolName, current));
                current = new ArrayList<>();
                windowStart = null;
            }

            current.add(record);
            if (windowStart == null && record.calledAt() != null) {
                windowStart = record.calledAt();
            }
        }

        if (!current.isEmpty()) {
            segments.add(buildSegment(toolName, current));
        }
        return segments;
    }

    private boolean exceedsTimeWindow(
            Instant windowStart, Instant candidateCalledAt, Duration maxTimeWindow) {
        return windowStart != null
                && candidateCalledAt != null
                && Duration.between(windowStart, candidateCalledAt).compareTo(maxTimeWindow) > 0;
    }

    private int projectedTokenCount(
            String toolName, List<ToolCallRecord> current, ToolCallRecord nextRecord) {
        List<ToolCallRecord> projected = new ArrayList<>(current);
        projected.add(nextRecord);
        return TokenUtils.countTokens(buildText(projected));
    }

    private List<Segment> splitSingleRecord(
            String toolName, ToolCallRecord record, String recordText) {
        Segment candidate =
                new Segment(
                        recordText,
                        null,
                        new CharBoundary(0, recordText.length()),
                        metadata(toolName, List.of(record)));
        return tokenAwareSegmentAssembler.assemble(
                List.of(candidate),
                new TokenChunkingOptions(options.hardMaxTokens(), options.hardMaxTokens()));
    }

    private Segment buildSegment(String toolName, List<ToolCallRecord> records) {
        String text = buildText(records);
        return new Segment(
                text, null, new CharBoundary(0, text.length()), metadata(toolName, records));
    }

    private String buildText(List<ToolCallRecord> records) {
        return records.stream()
                .map(this::formatRecord)
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private Map<String, Object> metadata(String toolName, List<ToolCallRecord> records) {
        long successCount =
                records.stream()
                        .filter(r -> r.status() != null && r.status().equalsIgnoreCase("success"))
                        .count();
        double avgDurationMs =
                records.stream().mapToLong(ToolCallRecord::durationMs).average().orElse(0.0);

        var meta = new LinkedHashMap<String, Object>();
        meta.put("toolName", toolName);
        meta.put("callCount", records.size());
        meta.put("successCount", (int) successCount);
        meta.put("failCount", records.size() - (int) successCount);
        meta.put("avgDurationMs", String.valueOf(Math.round(avgDurationMs)));
        if (records.getFirst().calledAt() != null) {
            meta.put("windowStart", records.getFirst().calledAt());
        }
        if (records.getLast().calledAt() != null) {
            meta.put("windowEnd", records.getLast().calledAt());
        }

        var recordDetails =
                records.stream()
                        .map(
                                r -> {
                                    var detail = new LinkedHashMap<String, Object>();
                                    detail.put("status", r.status());
                                    detail.put("durationMs", r.durationMs());
                                    detail.put("input", r.input() != null ? r.input() : "");
                                    detail.put("output", truncate(r.output(), 2000));
                                    return Map.<String, Object>copyOf(detail);
                                })
                        .toList();
        meta.put("records", recordDetails);
        return Map.copyOf(meta);
    }

    private String formatRecord(ToolCallRecord record) {
        return """
        [Tool Call] toolName=%s status=%s
        Input: %s
        Output: %s
        Duration: %dms  InputTokens: %d  OutputTokens: %d\
        """
                .formatted(
                        record.toolName(),
                        record.status(),
                        Objects.requireNonNullElse(record.input(), ""),
                        truncate(record.output(), 500),
                        record.durationMs(),
                        record.inputTokens(),
                        record.outputTokens());
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
