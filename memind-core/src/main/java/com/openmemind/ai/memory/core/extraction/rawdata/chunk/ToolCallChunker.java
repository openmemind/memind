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
package com.openmemind.ai.memory.core.extraction.rawdata.chunk;

import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool call chunker — groups tool call records by toolName.
 *
 * <p>Each tool produces one {@link Segment} containing all its call records concatenated as text,
 * with aggregated metadata (callCount, successCount, failCount, avgDurationMs) and individual
 * record details.
 */
public class ToolCallChunker {

    /**
     * Chunk tool call records by toolName.
     *
     * @param records tool call records
     * @return list of segments, one per distinct toolName
     */
    public List<Segment> chunk(List<ToolCallRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        // Group by toolName, preserving insertion order
        var byTool = new LinkedHashMap<String, List<ToolCallRecord>>();
        for (var record : records) {
            byTool.computeIfAbsent(
                            record.toolName() != null ? record.toolName() : "unknown",
                            k -> new ArrayList<>())
                    .add(record);
        }

        return byTool.entrySet().stream().map(ToolCallChunker::toSegment).toList();
    }

    private static Segment toSegment(Map.Entry<String, List<ToolCallRecord>> entry) {
        String toolName = entry.getKey();
        List<ToolCallRecord> records = entry.getValue();

        // Concatenate all records into one text block
        String text =
                records.stream()
                        .map(ToolCallChunker::formatRecord)
                        .collect(Collectors.joining("\n\n"));

        // Aggregate statistics
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

        // Keep individual record details for item extraction
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

        return new Segment(text, null, new CharBoundary(0, text.length()), Map.copyOf(meta));
    }

    private static String formatRecord(ToolCallRecord record) {
        return """
        [Tool Call] toolName=%s status=%s
        Input: %s
        Output: %s
        Duration: %dms  InputTokens: %d  OutputTokens: %d\
        """
                .formatted(
                        record.toolName(),
                        record.status(),
                        record.input(),
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
