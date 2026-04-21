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

import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasClass;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ForesightExtractionResponse;
import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import com.openmemind.ai.memory.core.support.TestDocumentContent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("LlmItemExtractionStrategy")
class LlmItemExtractionStrategyTest {

    @Test
    @DisplayName("toFactEntries should parse graph hints into extracted memory entries")
    void toFactEntriesShouldParseGraphHintsIntoExtractedMemoryEntries() {
        var response =
                new MemoryItemExtractionResponse(
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "User discussed OpenAI launch plans",
                                        0.95f,
                                        null,
                                        null,
                                        List.of("event"),
                                        Map.of(),
                                        "event",
                                        List.of(
                                                new MemoryItemExtractionResponse.ExtractedEntity(
                                                        "OpenAI", "organization", 0.91f)),
                                        List.of(
                                                new MemoryItemExtractionResponse
                                                        .ExtractedCausalRelation(
                                                        0, "enabled_by", 0.88f)))));

        var entries =
                LlmItemExtractionStrategy.toFactEntries(
                        response, sampleSegment(), Instant.parse("2026-04-16T00:00:00Z"));

        assertThat(entries)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.graphHints().entities()).hasSize(1);
                            assertThat(entry.graphHints().causalRelations()).hasSize(1);
                        });
    }

    @Test
    @DisplayName("toFactEntries should preserve raw entity type labels")
    void toFactEntriesShouldPreserveRawEntityTypeLabels() {
        var response =
                new MemoryItemExtractionResponse(
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "用户让我联系张三",
                                        0.95f,
                                        null,
                                        null,
                                        List.of("event"),
                                        Map.of(),
                                        "event",
                                        List.of(
                                                new MemoryItemExtractionResponse.ExtractedEntity(
                                                        "张三", "人物", 0.95f),
                                                new MemoryItemExtractionResponse.ExtractedEntity(
                                                        "用户", "special", 0.90f)),
                                        List.of())));

        var entries =
                LlmItemExtractionStrategy.toFactEntries(
                        response, sampleSegment(), Instant.parse("2026-04-18T00:00:00Z"));

        assertThat(entries.getFirst().graphHints().entities())
                .extracting(ExtractedGraphHints.ExtractedEntityHint::entityType)
                .containsExactly("人物", "special");
    }

    @Test
    @DisplayName("toFactEntries should parse alias observations into graph hints")
    void toFactEntriesShouldParseAliasObservationsIntoGraphHints() {
        var response =
                new MemoryItemExtractionResponse(
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "我在 OpenAI（开放人工智能）团队工作",
                                        0.95f,
                                        null,
                                        null,
                                        List.of("event"),
                                        Map.of(),
                                        "event",
                                        List.of(
                                                new MemoryItemExtractionResponse.ExtractedEntity(
                                                        "OpenAI",
                                                        "organization",
                                                        0.95f,
                                                        List.of(
                                                                new MemoryItemExtractionResponse
                                                                        .ExtractedAliasObservation(
                                                                        "开放人工智能",
                                                                        "explicit_parenthetical",
                                                                        "entity_inline",
                                                                        0.93f)))),
                                        List.of())));

        var entries =
                LlmItemExtractionStrategy.toFactEntries(
                        response, sampleSegment(), Instant.parse("2026-04-18T00:00:00Z"));

        assertThat(entries.getFirst().graphHints().entities().getFirst().aliasObservations())
                .singleElement()
                .extracting(
                        EntityAliasObservation::aliasSurface, EntityAliasObservation::aliasClass)
                .containsExactly("开放人工智能", EntityAliasClass.EXPLICIT_PARENTHETICAL);
    }

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
        var strategy = new LlmItemExtractionStrategy(client, registry);

        StepVerifier.create(
                        strategy.extract(
                                List.of(sampleSegment()),
                                List.of(),
                                new ItemExtractionConfig(
                                        MemoryScope.USER,
                                        ConversationContent.TYPE,
                                        MemoryCategory.userCategories(),
                                        false,
                                        "English")))
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
        var strategy = new LlmItemExtractionStrategy(client, registry);
        var config =
                new ItemExtractionConfig(
                        MemoryScope.USER,
                        ConversationContent.TYPE,
                        MemoryCategory.userCategories(),
                        true,
                        "English");

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
    @DisplayName("extract should render category context from config allowedCategories")
    void extractShouldRenderCategoryContextFromConfigAllowedCategories() {
        var client =
                new FakeStructuredChatClient(
                        new MemoryItemExtractionResponse(List.of()),
                        new ForesightExtractionResponse(List.of()));
        var strategy =
                new LlmItemExtractionStrategy(client, InMemoryPromptRegistry.builder().build());
        var config =
                new ItemExtractionConfig(
                        MemoryScope.USER,
                        TestDocumentContent.TYPE,
                        MemoryCategory.userCategories(),
                        false,
                        "English");

        StepVerifier.create(strategy.extract(List.of(sampleSegment()), List.of(), config))
                .expectNext(List.of())
                .verifyComplete();

        assertThat(client.allMessages()).hasSize(1);
        assertThat(client.allMessages().getFirst().getFirst().content()).contains("[USER Scope]");
        assertThat(client.allMessages().getFirst().getFirst().content())
                .doesNotContain("[AGENT Scope]");
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

    @Test
    @DisplayName("mergeMetadata should preserve prompt-budget audit fields")
    void mergeMetadataShouldPreservePromptBudgetAuditFields() {
        var segment =
                new ParsedSegment(
                        "text",
                        null,
                        0,
                        1,
                        "raw-1",
                        Map.of(
                                "budgetTruncated",
                                true,
                                "truncationReason",
                                "prompt_budget",
                                "retainedTokenCount",
                                100),
                        null);
        var item =
                new MemoryItemExtractionResponse.ExtractedItem(
                        "fact", 0.9f, null, List.of(), Map.of("source", "llm"), null);

        assertThat(LlmItemExtractionStrategy.mergeMetadata(segment, item))
                .containsEntry("budgetTruncated", true)
                .containsEntry("truncationReason", "prompt_budget")
                .containsEntry("retainedTokenCount", 100)
                .containsEntry("source", "llm");
    }

    @Test
    @DisplayName("mergeMetadata should store thread semantics under versioned metadata key")
    void mergeMetadataShouldStoreThreadSemanticsUnderVersionedMetadataKey() {
        var segment =
                new ParsedSegment("text", null, 0, 1, "raw-1", Map.of("channel", "chat"), null);
        var semantics =
                new MemoryItemExtractionResponse.ExtractedThreadSemantics(
                        1,
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedThreadMarker(
                                        "STATE_CHANGE",
                                        "project:memind-v1",
                                        "Memind v1 moved into implementation",
                                        Map.of(
                                                "fromState",
                                                "planning",
                                                "toState",
                                                "implementation"))),
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedCanonicalRef(
                                        "project", "memind-v1")),
                        List.of(
                                new MemoryItemExtractionResponse.ExtractedContinuityLink(
                                        "CONTINUES", 301L)));
        var item =
                new MemoryItemExtractionResponse.ExtractedItem(
                        "fact",
                        0.9f,
                        null,
                        null,
                        List.of(),
                        Map.of("source", "llm"),
                        "event",
                        List.of(),
                        List.of(),
                        semantics);

        assertThat(LlmItemExtractionStrategy.mergeMetadata(segment, item))
                .containsEntry("channel", "chat")
                .containsEntry("source", "llm")
                .containsKey("threadSemantics");
        assertThat(LlmItemExtractionStrategy.mergeMetadata(segment, item).get("threadSemantics"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("version", 1)
                .containsKey("markers")
                .containsKey("canonicalRefs")
                .containsKey("continuityLinks");
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    @DisplayName("extract should normalize structured week time")
    void extractShouldNormalizeStructuredWeekTime() {
        var originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            var client =
                    new FakeStructuredChatClient(
                            new MemoryItemExtractionResponse(
                                    List.of(
                                            new MemoryItemExtractionResponse.ExtractedItem(
                                                    "User traveled to Hangzhou during the week"
                                                            + " of 2026-04-06 to 2026-04-12",
                                                    0.95f,
                                                    null,
                                                    new MemoryItemExtractionResponse.ExtractedTime(
                                                            "上周",
                                                            "2026-04-06T00:00:00Z",
                                                            "2026-04-13T00:00:00Z",
                                                            "week"),
                                                    List.of("experiences"),
                                                    Map.of(),
                                                    "event"))),
                            new ForesightExtractionResponse(List.of()));
            var strategy = new LlmItemExtractionStrategy(client);

            StepVerifier.create(
                            strategy.extract(
                                    List.of(
                                            new ParsedSegment(
                                                    "user: 我上周去了杭州",
                                                    null,
                                                    0,
                                                    1,
                                                    "raw-structured-time",
                                                    Map.of(),
                                                    new SegmentRuntimeContext(
                                                            Instant.parse("2026-04-16T10:00:00Z"),
                                                            Instant.parse("2026-04-16T10:00:00Z"),
                                                            "Alice"))),
                                    List.of(),
                                    new ItemExtractionConfig(
                                            MemoryScope.USER,
                                            ConversationContent.TYPE,
                                            MemoryCategory.userCategories(),
                                            false,
                                            "English")))
                    .assertNext(
                            entries -> {
                                assertThat(entries).hasSize(1);
                                assertThat(entries.getFirst().occurredStart())
                                        .isEqualTo(Instant.parse("2026-04-06T00:00:00Z"));
                                assertThat(entries.getFirst().occurredEnd())
                                        .isEqualTo(Instant.parse("2026-04-13T00:00:00Z"));
                                assertThat(entries.getFirst().timeGranularity()).isEqualTo("week");
                                assertThat(entries.getFirst().occurredAt()).isNull();
                                assertThat(entries.getFirst().metadata())
                                        .containsEntry("timeExpression", "上周");
                            })
                    .verifyComplete();
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
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
