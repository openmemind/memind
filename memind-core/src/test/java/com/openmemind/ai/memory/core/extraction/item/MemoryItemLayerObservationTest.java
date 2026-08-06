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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.dedup.DeduplicationResult;
import com.openmemind.ai.memory.core.extraction.item.dedup.MemoryItemDeduplicator;
import com.openmemind.ai.memory.core.extraction.item.extractor.MemoryItemExtractor;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.support.RecordingObservationRegistry;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MemoryItemLayerObservationTest {

    @Test
    void extractPublishesItemObservationWithItemCounts() {
        var registry = new RecordingObservationRegistry();
        var extractor = mock(MemoryItemExtractor.class);
        var deduplicator = mock(MemoryItemDeduplicator.class);
        var store = new InMemoryMemoryStore();
        var layer =
                new MemoryItemLayer(
                        extractor, deduplicator, store, new StubMemoryVector(), registry);
        var memoryId = DefaultMemoryId.of("user1", "agent1");
        var segment = new ParsedSegment("hello", "hello caption", 0, 5, "raw-1", Map.of(), null);
        var entry =
                new ExtractedMemoryEntry(
                        "User likes Java",
                        0.9f,
                        Instant.parse("2026-03-20T00:00:00Z"),
                        Instant.parse("2026-03-20T00:00:00Z"),
                        "raw-1",
                        null,
                        List.of("profile"),
                        Map.of(),
                        MemoryItemType.FACT,
                        MemoryCategory.PROFILE.name());
        var config =
                new ItemExtractionConfig(
                        MemoryScope.USER, ConversationContent.TYPE, false, "English");
        when(extractor.extract(eq(List.of(segment)), anyList(), eq(config)))
                .thenReturn(Mono.just(List.of(entry)));
        when(deduplicator.deduplicate(any(), any()))
                .thenReturn(Mono.just(new DeduplicationResult(List.of(entry), List.of())));

        StepVerifier.create(
                        layer.extract(
                                memoryId,
                                new RawDataResult(List.of(), List.of(segment), false),
                                config))
                .assertNext(result -> assertThat(result.newItems()).hasSize(1))
                .verifyComplete();

        assertThat(registry.observations()).hasSize(1);
        var observation = registry.observations().getFirst();
        assertThat(observation.observationName()).isEqualTo("memind.extraction.item");
        assertThat(observation.requestAttributes())
                .containsEntry("memind.memory_id", memoryId.toIdentifier());
        assertThat(observation.resultAttributes())
                .containsEntry("memind.extraction.item_count", "1")
                .containsEntry("memind.extraction.new_item_count", "1");
    }

    private static final class StubMemoryVector implements MemoryVector {

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            return Mono.just("vector-1");
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            return Mono.just(
                    java.util.stream.IntStream.range(0, texts.size())
                            .mapToObj(i -> "vector-" + i)
                            .toList());
        }

        @Override
        public Mono<Void> delete(MemoryId memoryId, String vectorId) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteBatch(MemoryId memoryId, List<String> vectorIds) {
            return Mono.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK) {
            return Flux.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.empty();
        }

        @Override
        public Mono<List<Float>> embed(String text) {
            return Mono.just(List.of(0.1f));
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.just(texts.stream().map(ignored -> List.of(0.1f)).toList());
        }
    }
}
