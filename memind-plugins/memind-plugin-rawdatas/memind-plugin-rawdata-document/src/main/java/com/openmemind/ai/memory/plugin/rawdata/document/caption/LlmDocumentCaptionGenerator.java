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
package com.openmemind.ai.memory.plugin.rawdata.document.caption;

import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Document-specific caption generator backed by the shared caption LLM slot.
 */
public final class LlmDocumentCaptionGenerator implements CaptionGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmDocumentCaptionGenerator.class);

    private final StructuredChatClient structuredChatClient;
    private final boolean llmCaptionEnabled;
    private final int captionConcurrency;
    private final int fallbackCaptionMaxLength;

    public LlmDocumentCaptionGenerator(
            StructuredChatClient structuredChatClient,
            boolean llmCaptionEnabled,
            int captionConcurrency,
            int fallbackCaptionMaxLength) {
        this.structuredChatClient =
                Objects.requireNonNull(structuredChatClient, "structuredChatClient");
        if (captionConcurrency <= 0) {
            throw new IllegalArgumentException("captionConcurrency must be positive");
        }
        if (fallbackCaptionMaxLength <= 0) {
            throw new IllegalArgumentException("fallbackCaptionMaxLength must be positive");
        }
        this.llmCaptionEnabled = llmCaptionEnabled;
        this.captionConcurrency = captionConcurrency;
        this.fallbackCaptionMaxLength = fallbackCaptionMaxLength;
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        return generate(content, metadata, null);
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata, String language) {
        if (content == null || content.isBlank()) {
            return Mono.just("");
        }
        if (!llmCaptionEnabled) {
            return Mono.just(
                    DocumentCaptionFallbacks.build(content, metadata, fallbackCaptionMaxLength));
        }

        var prompt = DocumentCaptionPrompts.build(content, metadata, language);
        var messages = ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt());

        return structuredChatClient
                .call(messages, CaptionResponse.class)
                .map(LlmDocumentCaptionGenerator::toCaption)
                .filter(caption -> !caption.isBlank())
                .switchIfEmpty(
                        Mono.fromSupplier(
                                () ->
                                        DocumentCaptionFallbacks.build(
                                                content, metadata, fallbackCaptionMaxLength)))
                .onErrorResume(
                        error -> {
                            log.warn(
                                    "Document caption generation failed, falling back: {}",
                                    error.getMessage());
                            return Mono.just(
                                    DocumentCaptionFallbacks.build(
                                            content, metadata, fallbackCaptionMaxLength));
                        });
    }

    @Override
    public Mono<List<Segment>> generateForSegments(List<Segment> segments, String language) {
        if (segments.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(segments)
                .buffer(captionConcurrency)
                .concatMap(
                        batch ->
                                Flux.fromIterable(batch)
                                        .flatMapSequential(
                                                segment ->
                                                        generate(
                                                                        segment.content(),
                                                                        segment.metadata(),
                                                                        language)
                                                                .map(segment::withCaption),
                                                batch.size(),
                                                1))
                .collectList();
    }

    static String toCaption(CaptionResponse response) {
        if (response == null) {
            return "";
        }
        String title = response.title() == null ? "" : response.title().trim();
        String content = response.content() == null ? "" : response.content().trim();
        if (title.isEmpty()) {
            return content;
        }
        if (content.isEmpty()) {
            return title;
        }
        return title + "\n\n" + content;
    }

    public record CaptionResponse(String title, String content) {}
}
