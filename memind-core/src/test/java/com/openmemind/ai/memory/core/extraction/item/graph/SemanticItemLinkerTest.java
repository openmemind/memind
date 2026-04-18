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
package com.openmemind.ai.memory.core.extraction.item.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SemanticItemLinkerTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void linkNormalizesSearchHitsBeforeApplyingFinalSemanticCap() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        itemOps.insertItems(
                MEMORY_ID,
                List.of(
                        item(101L, "vector-101", "source item"),
                        item(201L, "vector-201", "neighbor a"),
                        item(202L, "vector-202", "neighbor b"),
                        item(203L, "vector-203", "neighbor c"),
                        item(204L, "vector-204", "neighbor d"),
                        item(205L, "vector-205", "neighbor e")));
        var vector = new StubMemoryVector();
        vector.register(
                "source item",
                List.of(
                        result("vector-101", 0.99f),
                        result("missing-vector", 0.98f),
                        result("vector-201", 0.91f),
                        result("vector-201", 0.89f),
                        result("vector-205", 0.83f),
                        result("vector-203", 0.88f),
                        result("vector-204", 0.87f),
                        result("vector-202", 0.90f)));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        ItemGraphOptions.defaults()
                                .withEnabled(true)
                                .withSemanticSearchHeadroom(4));

        StepVerifier.create(
                        linker.link(MEMORY_ID, List.of(item(101L, "vector-101", "source item"))))
                .assertNext(
                        stats -> {
                            assertThat(stats.searchHitCount()).isEqualTo(8);
                            assertThat(stats.createdLinkCount()).isEqualTo(5);
                            assertThat(stats.sameBatchHitCount()).isZero();
                        })
                .verifyComplete();

        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .satisfiesExactly(
                        link -> {
                            assertThat(link.targetItemId()).isEqualTo(201L);
                            assertThat(link.strength()).isCloseTo(0.91d, within(0.000001d));
                        },
                        link -> {
                            assertThat(link.targetItemId()).isEqualTo(202L);
                            assertThat(link.strength()).isCloseTo(0.90d, within(0.000001d));
                        },
                        link -> {
                            assertThat(link.targetItemId()).isEqualTo(203L);
                            assertThat(link.strength()).isCloseTo(0.88d, within(0.000001d));
                        },
                        link -> {
                            assertThat(link.targetItemId()).isEqualTo(204L);
                            assertThat(link.strength()).isCloseTo(0.87d, within(0.000001d));
                        },
                        link -> {
                            assertThat(link.targetItemId()).isEqualTo(205L);
                            assertThat(link.strength()).isCloseTo(0.83d, within(0.000001d));
                        });
    }

    @Test
    void sameBatchHitCountCountsNormalizedCandidatesBeforeFinalTruncation() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        var batchItems =
                List.of(
                        item(101L, "vector-101", "source item"),
                        item(201L, "vector-201", "neighbor a"),
                        item(202L, "vector-202", "neighbor b"),
                        item(203L, "vector-203", "neighbor c"),
                        item(204L, "vector-204", "neighbor d"),
                        item(205L, "vector-205", "neighbor e"),
                        item(206L, "vector-206", "neighbor f"));
        itemOps.insertItems(MEMORY_ID, batchItems);
        var vector = new StubMemoryVector();
        vector.register(
                "source item",
                List.of(
                        result("vector-201", 0.96f),
                        result("vector-202", 0.95f),
                        result("vector-203", 0.94f),
                        result("vector-204", 0.93f),
                        result("vector-205", 0.92f),
                        result("vector-206", 0.91f)));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        ItemGraphOptions.defaults()
                                .withEnabled(true)
                                .withSemanticSearchHeadroom(4));

        StepVerifier.create(linker.link(MEMORY_ID, batchItems))
                .assertNext(
                        stats -> {
                            assertThat(stats.createdLinkCount()).isEqualTo(5);
                            assertThat(stats.sameBatchHitCount()).isEqualTo(6);
                        })
                .verifyComplete();
    }

    @Test
    void linkHonorsConfiguredConcurrencyWithoutChangingSemanticStats() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        var inFlight = new AtomicInteger();
        var maxInFlight = new AtomicInteger();
        var vector = new DelayedMemoryVector(inFlight, maxInFlight);
        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        ItemGraphOptions.defaults()
                                .withEnabled(true)
                                .withSemanticLinkConcurrency(2));

        StepVerifier.create(
                        linker.link(
                                MEMORY_ID,
                                List.of(
                                        item(101L, "vector-101", "first item"),
                                        item(102L, "vector-102", "second item"),
                                        item(103L, "vector-103", "third item"))))
                .assertNext(
                        stats -> {
                            assertThat(stats.searchHitCount()).isEqualTo(3);
                            assertThat(stats.createdLinkCount()).isZero();
                            assertThat(stats.sameBatchHitCount()).isZero();
                        })
                .verifyComplete();

        assertThat(maxInFlight.get()).isEqualTo(2);
    }

    private static MemoryItem item(Long id, String vectorId, String content) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                vectorId,
                "raw-" + id,
                "hash-" + id,
                Instant.parse("2026-04-16T09:00:00Z").plusSeconds(id),
                CREATED_AT,
                Map.of(),
                CREATED_AT,
                MemoryItemType.FACT);
    }

    private static VectorSearchResult result(String vectorId, float score) {
        return new VectorSearchResult(vectorId, vectorId, score, Map.of());
    }

    private static class StubMemoryVector implements MemoryVector {

        private final Map<String, List<VectorSearchResult>> results = new HashMap<>();

        private void register(String query, List<VectorSearchResult> searchResults) {
            results.put(query, List.copyOf(searchResults));
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.fromIterable(results.getOrDefault(query, List.of()));
        }

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Void> delete(MemoryId memoryId, String vectorId) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Void> deleteBatch(MemoryId memoryId, List<String> vectorIds) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK) {
            return search(memoryId, query, topK, Map.of());
        }

        @Override
        public Mono<List<Float>> embed(String text) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.error(new UnsupportedOperationException());
        }
    }

    private static final class DelayedMemoryVector extends StubMemoryVector {

        private final AtomicInteger inFlight;
        private final AtomicInteger maxInFlight;

        private DelayedMemoryVector(AtomicInteger inFlight, AtomicInteger maxInFlight) {
            this.inFlight = inFlight;
            this.maxInFlight = maxInFlight;
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.defer(
                    () -> {
                        int current = inFlight.incrementAndGet();
                        maxInFlight.accumulateAndGet(current, Math::max);
                        return Mono.delay(Duration.ofMillis(20))
                                .thenMany(Flux.just(result("missing-" + query, 0.90f)))
                                .doFinally(ignored -> inFlight.decrementAndGet());
                    });
        }
    }
}
