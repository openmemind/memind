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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
    void extractShouldMaterializeGraphAfterItemInsertSucceeds() {
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
        when(graphMaterializer.materialize(eq(memoryId), anyList(), eq(List.of(entry))))
                .thenReturn(Mono.empty());

        StepVerifier.create(
                        layer.extract(
                                memoryId,
                                new RawDataResult(List.of(), List.of(segment), false),
                                config))
                .assertNext(
                        result -> {
                            assertThat(result.newItems()).hasSize(1);
                            assertThat(result.newItems().getFirst().vectorId()).isEqualTo("vec-1");
                        })
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MemoryItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        var inOrder = inOrder(itemOperations, graphMaterializer);
        inOrder.verify(itemOperations).insertItems(eq(memoryId), itemCaptor.capture());
        inOrder.verify(graphMaterializer)
                .materialize(eq(memoryId), eq(itemCaptor.getValue()), eq(List.of(entry)));
    }

    @Test
    void extractShouldKeepPersistenceSuccessfulWhenGraphMaterializerFails() {
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

        StepVerifier.create(
                        layer.extract(
                                memoryId,
                                new RawDataResult(List.of(), List.of(segment), false),
                                config))
                .assertNext(result -> assertThat(result.newItems()).hasSize(1))
                .verifyComplete();
    }
}
