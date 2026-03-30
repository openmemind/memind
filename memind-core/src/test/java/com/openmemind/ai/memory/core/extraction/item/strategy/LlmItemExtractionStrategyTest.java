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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.support.ForesightExtractionResponse;
import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("LlmItemExtractionStrategy")
class LlmItemExtractionStrategyTest {

    @Test
    @DisplayName("extract should use unified prompt override instruction")
    void extractShouldUseUnifiedPromptOverrideInstruction() {
        var client =
                new FakeStructuredChatClient(
                        new MemoryItemExtractionResponse(List.of()),
                        new ForesightExtractionResponse(List.of()));
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(
                                PromptType.MEMORY_ITEM_UNIFIED,
                                "Custom unified extraction instruction")
                        .build();
        var strategy = new LlmItemExtractionStrategy(client, Set.of(), registry);

        StepVerifier.create(
                        strategy.extract(
                                List.of(sampleSegment()),
                                List.of(),
                                ItemExtractionConfig.defaults()))
                .expectNext(List.of())
                .verifyComplete();

        assertThat(
                        client.allMessages().stream()
                                .anyMatch(
                                        messages ->
                                                messages.getFirst()
                                                        .content()
                                                        .contains(
                                                                "Custom unified extraction"
                                                                        + " instruction")))
                .isTrue();
    }

    @Test
    @DisplayName("extract should use foresight prompt override instruction")
    void extractShouldUseForesightPromptOverrideInstruction() {
        var client =
                new FakeStructuredChatClient(
                        new MemoryItemExtractionResponse(List.of()),
                        new ForesightExtractionResponse(List.of()));
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.FORESIGHT, "Custom foresight instruction")
                        .build();
        var strategy = new LlmItemExtractionStrategy(client, Set.of(), registry);
        var config =
                new ItemExtractionConfig(
                        MemoryScope.USER, ContentTypes.CONVERSATION, true, "English");

        StepVerifier.create(strategy.extract(List.of(sampleSegment()), List.of(), config))
                .expectNext(List.of())
                .verifyComplete();

        assertThat(
                        client.allMessages().stream()
                                .anyMatch(
                                        messages ->
                                                messages.getFirst()
                                                        .content()
                                                        .contains("Custom foresight instruction")))
                .isTrue();
    }

    @Test
    @DisplayName("resolveOccurredAt should return null when LLM omits the time")
    void resolveOccurredAtShouldReturnNullWhenLlmOmitsTheTime() {
        assertThat(LlmItemExtractionStrategy.resolveOccurredAt(null)).isNull();
        assertThat(LlmItemExtractionStrategy.resolveOccurredAt("  ")).isNull();
    }

    @Test
    @DisplayName("resolveOccurredAt should parse LLM provided ISO timestamp")
    void resolveOccurredAtShouldParseLlmProvidedIsoTimestamp() {
        Instant occurredAt = LlmItemExtractionStrategy.resolveOccurredAt("2026-03-27T02:18:00Z");

        assertThat(occurredAt).isEqualTo(Instant.parse("2026-03-27T02:18:00Z"));
    }

    @Test
    @DisplayName("resolveObservedAt should read runtime context instead of metadata messages")
    void resolveObservedAtShouldReadRuntimeContextInsteadOfMetadataMessages() {
        var segment =
                new ParsedSegment(
                        "text",
                        null,
                        0,
                        1,
                        "raw-1",
                        Map.of(),
                        new SegmentRuntimeContext(
                                Instant.parse("2026-03-27T02:17:00Z"),
                                Instant.parse("2026-03-27T02:18:00Z"),
                                "Alice"));

        assertThat(LlmItemExtractionStrategy.resolveObservedAt(segment))
                .isEqualTo(Instant.parse("2026-03-27T02:18:00Z"));
        assertThat(LlmItemExtractionStrategy.resolveUserName(segment)).isEqualTo("Alice");
    }

    @Test
    @DisplayName("resolveObservedAt should return null when runtime context is missing")
    void resolveObservedAtShouldReturnNullWhenRuntimeContextIsMissing() {
        var segment = new ParsedSegment("text", null, 0, 1, "raw-1", Map.of());

        assertThat(LlmItemExtractionStrategy.resolveObservedAt(segment)).isNull();
    }

    @Test
    @DisplayName("mergeMetadata should still strip legacy messages payloads")
    void mergeMetadataShouldStillStripLegacyMessagesPayloads() {
        var segment =
                new ParsedSegment(
                        "text",
                        null,
                        0,
                        1,
                        "raw-1",
                        Map.of("messages", List.of("legacy"), "channel", "chat"),
                        null);
        var item =
                new MemoryItemExtractionResponse.ExtractedItem(
                        "fact", 0.9f, null, List.of(), Map.of("source", "llm"), null);

        assertThat(LlmItemExtractionStrategy.mergeMetadata(segment, item))
                .containsEntry("channel", "chat")
                .containsEntry("source", "llm")
                .doesNotContainKey("messages");
    }

    private static ParsedSegment sampleSegment() {
        return new ParsedSegment(
                "user: I work on Spring Boot services.",
                null,
                0,
                1,
                "raw-1",
                Map.of(),
                new SegmentRuntimeContext(
                        Instant.parse("2024-03-15T10:00:00Z"),
                        Instant.parse("2024-03-15T10:00:00Z"),
                        "Alice"));
    }

    private static final class FakeStructuredChatClient implements StructuredChatClient {

        private final Object factResponse;
        private final Object foresightResponse;
        private final List<List<ChatMessage>> allMessages = new CopyOnWriteArrayList<>();

        private FakeStructuredChatClient(Object factResponse, Object foresightResponse) {
            this.factResponse = factResponse;
            this.foresightResponse = foresightResponse;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("Not used in this test"));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            allMessages.add(new ArrayList<>(messages));
            if (responseType == MemoryItemExtractionResponse.class) {
                return Mono.justOrEmpty((T) factResponse);
            }
            if (responseType == ForesightExtractionResponse.class) {
                return Mono.justOrEmpty((T) foresightResponse);
            }
            return Mono.empty();
        }

        private List<List<ChatMessage>> allMessages() {
            return allMessages;
        }
    }
}
