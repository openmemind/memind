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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.dedup.DeduplicationResult;
import com.openmemind.ai.memory.core.extraction.item.dedup.MemoryItemDeduplicator;
import com.openmemind.ai.memory.core.extraction.item.extractor.MemoryItemExtractor;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MemoryItemLayerGraphTest {

    @Test
    void extractShouldDelegateCommittedItemBatchToGraphMaterializerAfterStoreBatch() {
        MemoryItemExtractor extractor = mock(MemoryItemExtractor.class);
        MemoryItemDeduplicator deduplicator = mock(MemoryItemDeduplicator.class);
        MemoryStore memoryStore = mock(MemoryStore.class);
        InsightOperations insightOperations = mock(InsightOperations.class);
        ItemOperations itemOperations = mock(ItemOperations.class);
        MemoryVector vector = mock(MemoryVector.class);
        ItemGraphMaterializer graphMaterializer = mock(ItemGraphMaterializer.class);
        var layer =
                new MemoryItemLayer(
                        extractor, deduplicator, memoryStore, vector, graphMaterializer);
        var memoryId = DefaultMemoryId.of("user1", "agent1");

        var segment =
                new ParsedSegment(
                        "user: remember OpenAI release note",
                        "caption",
                        0,
                        1,
                        "raw-1",
                        Map.of(),
                        new SegmentRuntimeContext(
                                Instant.parse("2026-04-16T10:00:00Z"),
                                Instant.parse("2026-04-16T10:00:00Z"),
                                "User"));
        var entry =
                new ExtractedMemoryEntry(
                        "Remember OpenAI release note",
                        0.95f,
                        Instant.parse("2026-04-16T10:00:00Z"),
                        Instant.parse("2026-04-16T10:00:00Z"),
                        "raw-1",
                        "hash-1",
                        List.of(),
                        Map.of("whenToUse", "Use when asked about release note"),
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
        when(memoryStore.itemOperations()).thenReturn(itemOperations);
        when(insightOperations.listInsightTypes()).thenReturn(DefaultInsightTypes.all());
        when(extractor.extract(eq(List.of(segment)), anyList(), eq(config)))
                .thenReturn(Mono.just(List.of(entry)));
        when(deduplicator.deduplicate(eq(memoryId), anyList()))
                .thenReturn(Mono.just(new DeduplicationResult(List.of(entry), List.of())));
        when(deduplicator.spanName()).thenReturn("test");
        when(vector.storeBatch(eq(memoryId), anyList(), anyList()))
                .thenReturn(Mono.just(List.of("vec-1")));
        var graphResult =
                new ItemGraphMaterializationResult(
                        ItemGraphMaterializationResult.Stats.withTemporalAndSemantic(
                                1, 0, 0, null, null, null, 0, "", 0, 0, 0, 0, 0, 0));
        when(graphMaterializer.materialize(eq(memoryId), anyList(), eq(List.of(entry))))
                .thenReturn(Mono.just(graphResult));

        StepVerifier.create(
                        layer.extract(
                                memoryId,
                                new RawDataResult(List.of(), List.of(segment), false),
                                config))
                .assertNext(
                        result -> {
                            assertThat(result.newItems()).hasSize(1);
                            assertThat(result.newItems().getFirst().vectorId()).isEqualTo("vec-1");
                            assertThat(result.graphMaterializationResult()).isSameAs(graphResult);
                            assertThat(result.graphMaterializationResult().stats().entityCount())
                                    .isEqualTo(1);
                        })
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MemoryItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> vectorTextCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> vectorMetadataCaptor =
                ArgumentCaptor.forClass(List.class);
        var inOrder = inOrder(vector, itemOperations, graphMaterializer);
        inOrder.verify(vector).storeBatch(eq(memoryId), anyList(), anyList());
        inOrder.verify(graphMaterializer)
                .materialize(eq(memoryId), itemCaptor.capture(), eq(List.of(entry)));
        verify(itemOperations, never()).insertItems(any(), anyList());
        verify(vector)
                .storeBatch(
                        eq(memoryId), vectorTextCaptor.capture(), vectorMetadataCaptor.capture());
        assertThat(vectorTextCaptor.getValue()).containsExactly("Remember OpenAI release note");
        assertThat(itemCaptor.getValue().getFirst().metadata()).doesNotContainKey("whenToUse");
        assertThat(vectorMetadataCaptor.getValue())
                .containsExactly(Map.of("memoryId", memoryId.toIdentifier()));
    }

    @Test
    void extractShouldDeleteStoredVectorsAndFailWhenGraphMaterializerFails() {
        MemoryItemExtractor extractor = mock(MemoryItemExtractor.class);
        MemoryItemDeduplicator deduplicator = mock(MemoryItemDeduplicator.class);
        MemoryStore memoryStore = mock(MemoryStore.class);
        InsightOperations insightOperations = mock(InsightOperations.class);
        ItemOperations itemOperations = mock(ItemOperations.class);
        MemoryVector vector = mock(MemoryVector.class);
        ItemGraphMaterializer graphMaterializer = mock(ItemGraphMaterializer.class);
        var layer =
                new MemoryItemLayer(
                        extractor, deduplicator, memoryStore, vector, graphMaterializer);
        var memoryId = DefaultMemoryId.of("user1", "agent1");

        var segment =
                new ParsedSegment(
                        "user: remember release note",
                        "caption",
                        0,
                        1,
                        "raw-1",
                        Map.of(),
                        new SegmentRuntimeContext(
                                Instant.parse("2026-04-16T10:00:00Z"),
                                Instant.parse("2026-04-16T10:00:00Z"),
                                "User"));
        var entry =
                new ExtractedMemoryEntry(
                        "Remember release note",
                        0.95f,
                        Instant.parse("2026-04-16T10:00:00Z"),
                        Instant.parse("2026-04-16T10:00:00Z"),
                        "raw-1",
                        "hash-1",
                        List.of(),
                        Map.of(),
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
        when(memoryStore.itemOperations()).thenReturn(itemOperations);
        when(insightOperations.listInsightTypes()).thenReturn(DefaultInsightTypes.all());
        when(extractor.extract(eq(List.of(segment)), anyList(), eq(config)))
                .thenReturn(Mono.just(List.of(entry)));
        when(deduplicator.deduplicate(eq(memoryId), anyList()))
                .thenReturn(Mono.just(new DeduplicationResult(List.of(entry), List.of())));
        when(deduplicator.spanName()).thenReturn("test");
        when(vector.storeBatch(eq(memoryId), anyList(), anyList()))
                .thenReturn(Mono.just(List.of("vec-1")));
        when(graphMaterializer.materialize(eq(memoryId), anyList(), eq(List.of(entry))))
                .thenReturn(Mono.error(new IllegalStateException("graph failure")));
        when(vector.deleteBatch(eq(memoryId), eq(List.of("vec-1")))).thenReturn(Mono.empty());

        StepVerifier.create(
                        layer.extract(
                                memoryId,
                                new RawDataResult(List.of(), List.of(segment), false),
                                config))
                .expectErrorSatisfies(
                        error -> assertThat(error).hasMessageContaining("graph failure"))
                .verify();

        verify(itemOperations, never()).insertItems(any(), anyList());
        verify(vector).deleteBatch(eq(memoryId), eq(List.of("vec-1")));
    }

    @Test
    void extractShouldIgnoreGraphDiagnosticsAndStillPersistItemsWhenGraphMaterializationSucceeds() {
        MemoryItemExtractor extractor = mock(MemoryItemExtractor.class);
        MemoryItemDeduplicator deduplicator = mock(MemoryItemDeduplicator.class);
        MemoryStore memoryStore = mock(MemoryStore.class);
        InsightOperations insightOperations = mock(InsightOperations.class);
        ItemOperations itemOperations = mock(ItemOperations.class);
        MemoryVector vector = mock(MemoryVector.class);
        ItemGraphMaterializer graphMaterializer = mock(ItemGraphMaterializer.class);
        var layer =
                new MemoryItemLayer(
                        extractor, deduplicator, memoryStore, vector, graphMaterializer);
        var memoryId = DefaultMemoryId.of("user1", "agent1");

        var segment =
                new ParsedSegment(
                        "user: remember OpenAI release note",
                        "caption",
                        0,
                        1,
                        "raw-1",
                        Map.of(),
                        new SegmentRuntimeContext(
                                Instant.parse("2026-04-16T10:00:00Z"),
                                Instant.parse("2026-04-16T10:00:00Z"),
                                "User"));
        var entry =
                new ExtractedMemoryEntry(
                        "Remember OpenAI release note",
                        0.95f,
                        Instant.parse("2026-04-16T10:00:00Z"),
                        Instant.parse("2026-04-16T10:00:00Z"),
                        "raw-1",
                        "hash-1",
                        List.of(),
                        Map.of(),
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
        when(memoryStore.itemOperations()).thenReturn(itemOperations);
        when(insightOperations.listInsightTypes()).thenReturn(DefaultInsightTypes.all());
        when(extractor.extract(eq(List.of(segment)), anyList(), eq(config)))
                .thenReturn(Mono.just(List.of(entry)));
        when(deduplicator.deduplicate(eq(memoryId), anyList()))
                .thenReturn(Mono.just(new DeduplicationResult(List.of(entry), List.of())));
        when(deduplicator.spanName()).thenReturn("test");
        when(vector.storeBatch(eq(memoryId), anyList(), anyList()))
                .thenReturn(Mono.just(List.of("vec-1")));
        when(graphMaterializer.materialize(eq(memoryId), anyList(), eq(List.of(entry))))
                .thenReturn(
                        Mono.just(
                                new ItemGraphMaterializationResult(
                                        stageOneStats(2, 2, 1, "未分类标签=1", 0, 1, 0, 1, 0, 1))));

        StepVerifier.create(
                        layer.extract(
                                memoryId,
                                new RawDataResult(List.of(), List.of(segment), false),
                                config))
                .assertNext(result -> assertThat(result.newItems()).hasSize(1))
                .verifyComplete();

        verify(itemOperations, never()).insertItems(any(), anyList());
    }

    @Test
    void extractShouldPreserveWhenToUseForToolItemsOnly() {
        MemoryItemExtractor extractor = mock(MemoryItemExtractor.class);
        MemoryItemDeduplicator deduplicator = mock(MemoryItemDeduplicator.class);
        MemoryStore memoryStore = mock(MemoryStore.class);
        InsightOperations insightOperations = mock(InsightOperations.class);
        ItemOperations itemOperations = mock(ItemOperations.class);
        MemoryVector vector = mock(MemoryVector.class);
        ItemGraphMaterializer graphMaterializer = mock(ItemGraphMaterializer.class);
        var layer =
                new MemoryItemLayer(
                        extractor, deduplicator, memoryStore, vector, graphMaterializer);
        var memoryId = DefaultMemoryId.of("user1", "agent1");

        var segment =
                new ParsedSegment(
                        "assistant: use web_search with site filters",
                        "caption",
                        0,
                        1,
                        "raw-1",
                        Map.of(),
                        new SegmentRuntimeContext(
                                Instant.parse("2026-04-16T10:00:00Z"),
                                Instant.parse("2026-04-16T10:00:00Z"),
                                "User"));
        var entry =
                new ExtractedMemoryEntry(
                        "web_search works best with site filters and narrow queries",
                        0.95f,
                        Instant.parse("2026-04-16T10:00:00Z"),
                        Instant.parse("2026-04-16T10:00:00Z"),
                        "raw-1",
                        "hash-1",
                        List.of(),
                        Map.of(
                                "whenToUse",
                                "Use when searching documentation",
                                "toolName",
                                "web_search"),
                        MemoryItemType.FACT,
                        "tool");
        var config =
                new ItemExtractionConfig(
                        MemoryScope.AGENT,
                        ConversationContent.TYPE,
                        MemoryCategory.agentCategories(),
                        false,
                        "English");

        when(memoryStore.insightOperations()).thenReturn(insightOperations);
        when(memoryStore.itemOperations()).thenReturn(itemOperations);
        when(insightOperations.listInsightTypes()).thenReturn(DefaultInsightTypes.all());
        when(extractor.extract(eq(List.of(segment)), anyList(), eq(config)))
                .thenReturn(Mono.just(List.of(entry)));
        when(deduplicator.deduplicate(eq(memoryId), anyList()))
                .thenReturn(Mono.just(new DeduplicationResult(List.of(entry), List.of())));
        when(deduplicator.spanName()).thenReturn("test");
        when(vector.storeBatch(eq(memoryId), anyList(), anyList()))
                .thenReturn(Mono.just(List.of("vec-1")));
        when(graphMaterializer.materialize(eq(memoryId), anyList(), eq(List.of(entry))))
                .thenReturn(Mono.just(ItemGraphMaterializationResult.empty()));

        StepVerifier.create(
                        layer.extract(
                                memoryId,
                                new RawDataResult(List.of(), List.of(segment), false),
                                config))
                .assertNext(result -> assertThat(result.newItems()).hasSize(1))
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MemoryItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> vectorMetadataCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(graphMaterializer)
                .materialize(eq(memoryId), itemCaptor.capture(), eq(List.of(entry)));
        verify(vector).storeBatch(eq(memoryId), anyList(), vectorMetadataCaptor.capture());
        verify(itemOperations, never()).insertItems(any(), anyList());
        assertThat(itemCaptor.getValue().getFirst().metadata())
                .containsEntry("whenToUse", "Use when searching documentation");
        assertThat(vectorMetadataCaptor.getValue())
                .containsExactly(
                        Map.of(
                                "memoryId",
                                memoryId.toIdentifier(),
                                "whenToUse",
                                "Use when searching documentation"));
    }

    private static ItemGraphMaterializationResult.Stats stageOneStats(
            int entityCount,
            int mentionCount,
            int typeFallbackToOtherCount,
            String topUnresolvedTypeLabelsSummary,
            int droppedBlankCount,
            int droppedPunctuationOnlyCount,
            int droppedPronounLikeCount,
            int droppedTemporalCount,
            int droppedDateLikeCount,
            int droppedReservedSpecialCollisionCount) {
        return new ItemGraphMaterializationResult.Stats(
                entityCount,
                mentionCount,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                0L,
                0L,
                false,
                typeFallbackToOtherCount,
                topUnresolvedTypeLabelsSummary,
                droppedBlankCount,
                droppedPunctuationOnlyCount,
                droppedPronounLikeCount,
                droppedTemporalCount,
                droppedDateLikeCount,
                droppedReservedSpecialCollisionCount);
    }
}
