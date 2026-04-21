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
package com.openmemind.ai.memory.core.extraction.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.dedup.DeduplicationResult;
import com.openmemind.ai.memory.core.extraction.item.dedup.MemoryItemDeduplicator;
import com.openmemind.ai.memory.core.extraction.item.extractor.MemoryItemExtractor;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.support.TestDocumentContent;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("MemoryItemLayer language forwarding")
class MemoryItemLayerLanguageTest {

    @Test
    @DisplayName("Self verification receives extraction language")
    void selfVerification_receives_extraction_language() {
        MemoryItemExtractor extractor = mock(MemoryItemExtractor.class);
        MemoryItemDeduplicator deduplicator = mock(MemoryItemDeduplicator.class);
        MemoryStore memoryStore = mock(MemoryStore.class);
        InsightOperations insightOperations = mock(InsightOperations.class);
        ItemOperations itemOperations = mock(ItemOperations.class);
        MemoryVector vector = mock(MemoryVector.class);
        var selfVerificationStep = new CapturingSelfVerificationStep();
        when(memoryStore.itemOperations()).thenReturn(itemOperations);
        var layer =
                new MemoryItemLayer(
                        extractor, deduplicator, memoryStore, vector, selfVerificationStep);

        var segment =
                new ParsedSegment(
                        "user: hello",
                        "caption",
                        0,
                        1,
                        "raw-001",
                        java.util.Map.of(),
                        new SegmentRuntimeContext(
                                Instant.parse("2024-03-15T10:00:00Z"),
                                Instant.parse("2024-03-15T10:00:00Z"),
                                "User"));
        var extractedEntry =
                new ExtractedMemoryEntry(
                        "hello",
                        1.0f,
                        Instant.parse("2024-03-15T10:00:00Z"),
                        Instant.parse("2024-03-15T10:00:00Z"),
                        "raw-001",
                        null,
                        List.of(),
                        java.util.Map.of(),
                        null,
                        null);
        var config =
                new ItemExtractionConfig(
                        MemoryScope.USER, ConversationContent.TYPE, false, "zh-CN");

        when(memoryStore.insightOperations()).thenReturn(insightOperations);
        when(insightOperations.listInsightTypes()).thenReturn(DefaultInsightTypes.all());
        when(extractor.extract(eq(List.of(segment)), anyList(), eq(config)))
                .thenReturn(Mono.just(List.of(extractedEntry)));
        when(deduplicator.deduplicate(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(new DeduplicationResult(List.of(), List.of())));
        when(deduplicator.spanName()).thenReturn("test");

        StepVerifier.create(
                        layer.extract(
                                DefaultMemoryId.of("user1", "agent1"),
                                new RawDataResult(List.of(), List.of(segment), false),
                                config))
                .assertNext(result -> assertThat(result.isEmpty()).isTrue())
                .verifyComplete();

        assertThat(selfVerificationStep.capturedLanguage()).isEqualTo("zh-CN");
        assertThat(selfVerificationStep.capturedObservedAt())
                .isEqualTo(Instant.parse("2024-03-15T10:00:00Z"));
    }

    @Test
    @DisplayName("Entries outside allowed categories should be dropped before deduplication")
    void entriesOutsideAllowedCategoriesAreDroppedBeforeDeduplication() {
        MemoryItemExtractor extractor = mock(MemoryItemExtractor.class);
        MemoryItemDeduplicator deduplicator = mock(MemoryItemDeduplicator.class);
        MemoryStore memoryStore = mock(MemoryStore.class);
        InsightOperations insightOperations = mock(InsightOperations.class);
        ItemOperations itemOperations = mock(ItemOperations.class);
        MemoryVector vector = mock(MemoryVector.class);
        when(memoryStore.itemOperations()).thenReturn(itemOperations);
        var layer = new MemoryItemLayer(extractor, deduplicator, memoryStore, vector);

        var segment =
                new ParsedSegment(
                        "document text",
                        "caption",
                        0,
                        1,
                        "raw-001",
                        java.util.Map.of(),
                        new SegmentRuntimeContext(
                                Instant.parse("2024-03-15T10:00:00Z"),
                                Instant.parse("2024-03-15T10:00:00Z"),
                                "User"));
        var directiveEntry =
                new ExtractedMemoryEntry(
                        "always answer in Chinese",
                        0.9f,
                        null,
                        Instant.parse("2024-03-15T10:00:00Z"),
                        "raw-001",
                        null,
                        List.of(),
                        java.util.Map.of(),
                        null,
                        "directive");
        var profileEntry =
                new ExtractedMemoryEntry(
                        "User writes Java",
                        0.9f,
                        null,
                        Instant.parse("2024-03-15T10:00:00Z"),
                        "raw-001",
                        null,
                        List.of(),
                        java.util.Map.of(),
                        null,
                        "profile");
        var config =
                new ItemExtractionConfig(
                        MemoryScope.USER,
                        TestDocumentContent.TYPE,
                        MemoryCategory.userCategories(),
                        false,
                        "zh-CN");

        when(memoryStore.insightOperations()).thenReturn(insightOperations);
        when(insightOperations.listInsightTypes()).thenReturn(DefaultInsightTypes.all());
        when(extractor.extract(eq(List.of(segment)), anyList(), eq(config)))
                .thenReturn(Mono.just(List.of(directiveEntry, profileEntry)));
        when(deduplicator.deduplicate(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(new DeduplicationResult(List.of(), List.of())));
        when(deduplicator.spanName()).thenReturn("test");

        StepVerifier.create(
                        layer.extract(
                                DefaultMemoryId.of("user1", "agent1"),
                                new RawDataResult(List.of(), List.of(segment), false),
                                config))
                .assertNext(result -> assertThat(result.isEmpty()).isTrue())
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExtractedMemoryEntry>> dedupEntriesCaptor =
                ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(deduplicator)
                .deduplicate(org.mockito.ArgumentMatchers.any(), dedupEntriesCaptor.capture());
        assertThat(dedupEntriesCaptor.getValue())
                .extracting(ExtractedMemoryEntry::category)
                .containsExactly("profile");
    }

    @Test
    @DisplayName("Extract should persist normalized temporal fields and legacy anchor")
    void extractShouldPersistNormalizedTemporalFieldsAndLegacyAnchor() {
        MemoryItemExtractor extractor = mock(MemoryItemExtractor.class);
        MemoryItemDeduplicator deduplicator = mock(MemoryItemDeduplicator.class);
        MemoryStore memoryStore = mock(MemoryStore.class);
        InsightOperations insightOperations = mock(InsightOperations.class);
        ItemOperations itemOperations = mock(ItemOperations.class);
        MemoryVector vector = mock(MemoryVector.class);
        when(memoryStore.itemOperations()).thenReturn(itemOperations);
        var layer = new MemoryItemLayer(extractor, deduplicator, memoryStore, vector);
        var memoryId = DefaultMemoryId.of("user1", "agent1");

        var segment =
                new ParsedSegment(
                        "user: 我上周去了杭州",
                        "caption",
                        0,
                        1,
                        "raw-1",
                        Map.of(),
                        new SegmentRuntimeContext(
                                Instant.parse("2026-04-16T10:00:00Z"),
                                Instant.parse("2026-04-16T10:00:00Z"),
                                "User"));
        var temporalEntry =
                new ExtractedMemoryEntry(
                        "User traveled to Hangzhou during the week of 2026-04-06 to"
                                + " 2026-04-12",
                        0.95f,
                        null,
                        Instant.parse("2026-04-06T00:00:00Z"),
                        Instant.parse("2026-04-13T00:00:00Z"),
                        "week",
                        Instant.parse("2026-04-16T10:00:00Z"),
                        "raw-1",
                        "hash-1",
                        List.of("experiences"),
                        Map.of("timeExpression", "上周"),
                        MemoryItemType.FACT,
                        "event");
        var config =
                new ItemExtractionConfig(
                        MemoryScope.USER,
                        ConversationContent.TYPE,
                        MemoryCategory.userCategories(),
                        false,
                        "English");

        when(memoryStore.insightOperations()).thenReturn(insightOperations);
        when(insightOperations.listInsightTypes()).thenReturn(DefaultInsightTypes.all());
        when(extractor.extract(eq(List.of(segment)), anyList(), eq(config)))
                .thenReturn(Mono.just(List.of(temporalEntry)));
        when(deduplicator.deduplicate(eq(memoryId), anyList()))
                .thenReturn(Mono.just(new DeduplicationResult(List.of(temporalEntry), List.of())));
        when(deduplicator.spanName()).thenReturn("test");
        when(vector.storeBatch(eq(memoryId), anyList(), anyList()))
                .thenReturn(Mono.just(List.of("vec-1")));

        StepVerifier.create(
                        layer.extract(
                                memoryId,
                                new RawDataResult(List.of(), List.of(segment), false),
                                config))
                .assertNext(
                        result -> {
                            var item = result.newItems().getFirst();
                            assertThat(item.occurredAt()).isNull();
                            assertThat(item.occurredStart())
                                    .isEqualTo(Instant.parse("2026-04-06T00:00:00Z"));
                            assertThat(item.occurredEnd())
                                    .isEqualTo(Instant.parse("2026-04-13T00:00:00Z"));
                            assertThat(item.timeGranularity()).isEqualTo("week");
                        })
                .verifyComplete();
    }

    private static final class CapturingSelfVerificationStep extends LlmSelfVerificationStep {

        private String capturedLanguage;
        private Instant capturedObservedAt;

        private CapturingSelfVerificationStep() {
            super(mock(StructuredChatClient.class));
        }

        @Override
        public Mono<List<ExtractedMemoryEntry>> verify(
                String originalText,
                List<ExtractedMemoryEntry> existingEntries,
                String rawDataId,
                Instant referenceTime,
                List<com.openmemind.ai.memory.core.data.MemoryInsightType> insightTypes,
                String userName,
                java.util.Set<com.openmemind.ai.memory.core.data.enums.MemoryCategory> categories,
                String language,
                Instant observedAt) {
            capturedLanguage = language;
            capturedObservedAt = observedAt;
            return Mono.just(List.of());
        }

        private String capturedLanguage() {
            return capturedLanguage;
        }

        private Instant capturedObservedAt() {
            return capturedObservedAt;
        }
    }
}
