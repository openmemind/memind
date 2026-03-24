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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.extraction.rawdata.ConversationSegmentationPrompts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * LLM Conversation Chunker
 *
 * <p>Call LLM to analyze conversation content, identify topic switching points, and segment by semantic boundaries. Degrade to fixed segmentation in case of exceptions.
 *
 */
public class LlmConversationChunker {

    private static final Logger log = LoggerFactory.getLogger(LlmConversationChunker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StructuredChatClient structuredChatClient;
    private final ConversationChunker fallback;

    public LlmConversationChunker(
            StructuredChatClient structuredChatClient, ConversationChunker fallback) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
    }

    /**
     * Use LLM for semantic segmentation of conversation content
     *
     * @param messages List of messages
     * @param config Segmentation configuration
     * @return Segmentation result
     */
    public Mono<List<Segment>> chunk(List<Message> messages, ConversationChunkingConfig config) {
        if (messages.isEmpty()) {
            return Mono.just(List.of());
        }

        var prompt =
                ConversationSegmentationPrompts.build(messages, config.minMessagesPerSegment())
                        .render(null);
        var llmMessages = ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt());

        return structuredChatClient
                .call(llmMessages)
                .subscribeOn(Schedulers.boundedElastic())
                .map(json -> parseAndBuildSegments(json, messages))
                .onErrorResume(
                        ex -> {
                            log.warn(
                                    "LLM segmentation failed, degrading to fixed segmentation: {}",
                                    ex.getMessage(),
                                    ex);
                            return Mono.just(fallback.chunk(messages, config));
                        });
    }

    private List<Segment> parseAndBuildSegments(String json, List<Message> messages) {
        var boundaries = repairBoundaries(parseBoundaries(json), messages.size());
        return boundaries.stream()
                .map(
                        b -> {
                            int start = b.start();
                            int end = Math.min(b.end(), messages.size());
                            List<Message> segmentMessages = messages.subList(start, end);
                            String text = formatMessages(segmentMessages);
                            return new Segment(
                                    text,
                                    null,
                                    new MessageBoundary(start, end),
                                    Map.of("messages", segmentMessages));
                        })
                .toList();
    }

    List<SegmentBoundary> parseBoundaries(String json) {
        try {
            // Extract JSON part (LLM may return JSON wrapped in markdown)
            String cleaned = extractJson(json);
            var result = MAPPER.readValue(cleaned, SegmentationResult.class);
            return result.segments();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to parse LLM segmentation response: " + json, e);
        }
    }

    /**
     * Repair boundaries returned by LLM: ensure the first segment starts from 0, the last segment covers totalMessages, with no gaps.
     */
    List<SegmentBoundary> repairBoundaries(List<SegmentBoundary> raw, int totalMessages) {
        if (raw == null || raw.isEmpty()) {
            return List.of(new SegmentBoundary(0, totalMessages));
        }

        var sorted = raw.stream().sorted((a, b) -> a.start() - b.start()).toList();
        var repaired = new ArrayList<SegmentBoundary>();

        int cursor = 0;
        for (var seg : sorted) {
            int start = Math.max(seg.start(), cursor);
            int end = Math.min(seg.end(), totalMessages);
            if (end <= start) {
                continue;
            }
            if (start > cursor) {
                // Fill gaps: extend the current segment to cover forward
                start = cursor;
            }
            repaired.add(new SegmentBoundary(start, end));
            cursor = end;
        }

        // Ensure coverage to the last message
        if (cursor < totalMessages) {
            if (repaired.isEmpty()) {
                repaired.add(new SegmentBoundary(0, totalMessages));
            } else {
                var last = repaired.removeLast();
                repaired.add(new SegmentBoundary(last.start(), totalMessages));
            }
            log.info(
                    "LLM segmentation did not cover the last message [{}-{}], fixed by extending"
                            + " the last segment",
                    cursor,
                    totalMessages);
        }

        return List.copyOf(repaired);
    }

    private String extractJson(String text) {
        // Remove markdown code block wrapping
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return trimmed;
    }

    private String formatMessages(List<Message> messages) {
        return messages.stream()
                .map(ConversationContent::formatMessage)
                .collect(Collectors.joining("\n"));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SegmentationResult(List<SegmentBoundary> segments) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SegmentBoundary(int start, int end) {}
}
