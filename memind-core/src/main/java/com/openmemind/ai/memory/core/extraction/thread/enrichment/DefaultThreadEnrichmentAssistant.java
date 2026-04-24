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
package com.openmemind.ai.memory.core.extraction.thread.enrichment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.extraction.thread.ThreadEnrichmentPrompts;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * LLM-backed assistant that proposes replay-safe thread enrichment outputs.
 */
public final class DefaultThreadEnrichmentAssistant implements ThreadEnrichmentAssistant {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultThreadEnrichmentAssistant.class);

    private final StructuredChatClient structuredChatClient;
    private final PromptRegistry promptRegistry;

    public DefaultThreadEnrichmentAssistant(StructuredChatClient structuredChatClient) {
        this(structuredChatClient, PromptRegistry.EMPTY);
    }

    public DefaultThreadEnrichmentAssistant(
            StructuredChatClient structuredChatClient, PromptRegistry promptRegistry) {
        this.structuredChatClient =
                Objects.requireNonNull(structuredChatClient, "structuredChatClient");
        this.promptRegistry = Objects.requireNonNull(promptRegistry, "promptRegistry");
    }

    @Override
    public Mono<List<ThreadEnrichmentResult>> enrich(
            MemoryThreadProjection thread, List<MemoryThreadEvent> itemBackedEvents) {
        var prompt =
                ThreadEnrichmentPrompts.build(promptRegistry, thread, itemBackedEvents)
                        .render(null);
        var messages = ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt());
        return structuredChatClient
                .call(messages, LlmResponse.class)
                .map(DefaultThreadEnrichmentAssistant::toResults)
                .switchIfEmpty(Mono.just(List.of()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(
                        error -> {
                            log.warn(
                                    "Thread enrichment assistant failed open for threadKey={}",
                                    thread != null ? thread.threadKey() : null,
                                    error);
                            return Mono.just(List.of());
                        });
    }

    private static List<ThreadEnrichmentResult> toResults(LlmResponse response) {
        if (response == null || response.events() == null || response.events().isEmpty()) {
            return List.of();
        }
        return response.events().stream()
                .filter(Objects::nonNull)
                .filter(event -> event.eventType() != null && !event.eventType().isBlank())
                .filter(event -> event.basisEventKey() != null && !event.basisEventKey().isBlank())
                .map(
                        event ->
                                new ThreadEnrichmentResult(
                                        event.eventType(),
                                        Boolean.TRUE.equals(event.meaningful()),
                                        event.basisEventKey(),
                                        event.payloadJson() == null
                                                ? Map.of()
                                                : event.payloadJson()))
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmResponse(List<LlmEvent> events) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmEvent(
            String eventType,
            Boolean meaningful,
            String basisEventKey,
            Map<String, Object> payloadJson) {}
}
