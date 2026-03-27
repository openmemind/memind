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
package com.openmemind.ai.memory.core.extraction.item.strategy;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.item.support.ToolItemResponse;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.extraction.item.ToolItemPrompts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Item extraction strategy for tool-call content.
 *
 * <p>Each segment is already grouped by toolName (via ToolCallChunker),
 * so one LLM call per segment produces one tool usage insight.
 */
public class LlmToolCallItemExtractionStrategy implements ItemExtractionStrategy {

    private final StructuredChatClient structuredChatClient;

    public LlmToolCallItemExtractionStrategy(StructuredChatClient structuredChatClient) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
    }

    @Override
    public Mono<List<ExtractedMemoryEntry>> extract(
            List<ParsedSegment> segments,
            List<MemoryInsightType> insightTypes,
            ItemExtractionConfig config) {

        var language = config.language();
        return Flux.fromIterable(segments)
                .flatMap(segment -> extractToolItem(segment, language))
                .collectList();
    }

    private Mono<ExtractedMemoryEntry> extractToolItem(ParsedSegment segment, String language) {
        var meta = segment.metadata() != null ? segment.metadata() : Map.<String, Object>of();
        var toolName = String.valueOf(meta.getOrDefault("toolName", "unknown"));
        var callCount = meta.get("callCount") instanceof Number n ? n.intValue() : 1;
        var successCount = meta.get("successCount") instanceof Number n ? n.intValue() : 0;
        var failCount = meta.get("failCount") instanceof Number n ? n.intValue() : 0;
        var avgDurationMs = String.valueOf(meta.getOrDefault("avgDurationMs", "0"));

        String statistics =
                "Total calls: %d, Success: %d, Failed: %d, Avg duration: %sms"
                        .formatted(callCount, successCount, failCount, avgDurationMs);
        String records = formatToolCallRecords(meta);
        String historicalContent = null;

        var prompt =
                ToolItemPrompts.build(toolName, records, statistics, historicalContent)
                        .render(language);
        var messages = ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt());

        return structuredChatClient
                .call(messages, ToolItemResponse.class)
                .flatMap(
                        response ->
                                Mono.justOrEmpty(
                                        toEntry(
                                                response,
                                                segment,
                                                segment.rawDataId(),
                                                toolName,
                                                callCount,
                                                successCount,
                                                failCount,
                                                avgDurationMs)))
                .switchIfEmpty(Mono.empty())
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.empty());
    }

    private ExtractedMemoryEntry toEntry(
            ToolItemResponse response,
            ParsedSegment segment,
            String rawDataId,
            String toolName,
            int callCount,
            int successCount,
            int failCount,
            String avgDurationMs) {
        if (response == null || response.content() == null || response.content().isBlank()) {
            return null;
        }

        var itemMetadata = new LinkedHashMap<String, Object>();
        itemMetadata.put("toolName", toolName);
        itemMetadata.put("callCount", String.valueOf(callCount));
        itemMetadata.put("successCount", String.valueOf(successCount));
        itemMetadata.put("failCount", String.valueOf(failCount));
        itemMetadata.put("avgDurationMs", avgDurationMs);
        itemMetadata.put("score", String.valueOf(response.score()));
        var observedAt = LlmItemExtractionStrategy.resolveObservedAt(segment);

        return new ExtractedMemoryEntry(
                response.content(),
                1.0f,
                null,
                observedAt,
                rawDataId,
                null,
                List.of(),
                Map.copyOf(itemMetadata),
                MemoryItemType.FACT,
                MemoryCategory.TOOL.categoryName());
    }

    @SuppressWarnings("unchecked")
    static String formatToolCallRecords(Map<String, Object> meta) {
        var records = meta.get("records");
        if (!(records instanceof List<?> recordList) || recordList.isEmpty()) {
            return "(no records)";
        }

        var sb = new StringBuilder();
        int maxIo = ToolItemPrompts.maxIoLength();
        for (int i = 0; i < recordList.size(); i++) {
            if (!(recordList.get(i) instanceof Map<?, ?> record)) {
                continue;
            }
            sb.append("### Call #").append(i + 1).append("\n");
            var status = record.get("status");
            sb.append("- Status: ").append(status != null ? status : "unknown").append("\n");
            var duration = record.get("durationMs");
            sb.append("- Duration: ").append(duration != null ? duration : 0).append("ms\n");
            var input = record.get("input");
            sb.append("- Input: ")
                    .append(
                            ToolItemPrompts.truncate(
                                    input != null ? input.toString() : null, maxIo))
                    .append("\n");
            var output = record.get("output");
            sb.append("- Output: ")
                    .append(
                            ToolItemPrompts.truncate(
                                    output != null ? output.toString() : null, maxIo))
                    .append("\n\n");
        }
        return sb.toString();
    }
}
