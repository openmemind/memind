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
package com.openmemind.ai.memory.plugin.ai.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.graph.link.semantic.SemanticItemLinker;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.vector.VectorBatchSearchException;
import com.openmemind.ai.memory.core.vector.VectorSearchRequest;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@DisplayName("SpringAiMemoryVector")
class SpringAiMemoryVectorTest {

    @TempDir Path tempDir;

    private SpringAiMemoryVector memoryVector;
    private MemoryId memoryId;

    @BeforeEach
    void setUp() {
        memoryId = DefaultMemoryId.of("user1", "agent1");
        var embeddingModel = new FakeEmbeddingModel();
        var vectorStore =
                new FileSimpleVectorStore(embeddingModel, tempDir.resolve("test-vectors.json"));
        memoryVector = new SpringAiMemoryVector(vectorStore, embeddingModel);
    }

    @Nested
    @DisplayName("Store and Search")
    class StoreAndSearch {

        @Test
        @DisplayName("store should return vectorId")
        void storeShouldReturnVectorId() {
            StepVerifier.create(memoryVector.store(memoryId, "hello world", Map.of()))
                    .assertNext(vectorId -> assertThat(vectorId).isNotBlank())
                    .verifyComplete();
        }

        @Test
        @DisplayName("storeBatch should return all vectorId and be searchable")
        void storeBatchShouldReturnAllVectorIds() {
            StepVerifier.create(
                            memoryVector
                                    .storeBatch(
                                            memoryId,
                                            List.of("first document", "second document"),
                                            List.of(Map.of(), Map.of()))
                                    .flatMap(
                                            vectorIds -> {
                                                assertThat(vectorIds).hasSize(2);
                                                return memoryVector
                                                        .search(memoryId, "first", 10)
                                                        .collectList();
                                            }))
                    .assertNext(results -> assertThat(results).isNotEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("storeBatch should make fresh vectors immediately searchable")
        void storeBatchShouldMakeFreshVectorsImmediatelySearchable() {
            StepVerifier.create(
                            memoryVector
                                    .storeBatch(
                                            memoryId,
                                            List.of("alpha note", "beta note"),
                                            List.of(Map.of(), Map.of()))
                                    .then(
                                            memoryVector
                                                    .search(memoryId, "alpha note", 10)
                                                    .collectList()))
                    .assertNext(
                            results -> {
                                assertThat(results).isNotEmpty();
                                assertThat(results)
                                        .extracting(result -> result.text())
                                        .contains("alpha note");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("storeBatch should honor caller supplied vector ids when provided")
        void storeBatchShouldHonorCallerSuppliedVectorIdsWhenProvided() {
            var requestedVectorIds = List.of("rawdata:alpha", "rawdata:beta");

            StepVerifier.create(
                            memoryVector.storeBatch(
                                    memoryId,
                                    requestedVectorIds,
                                    List.of("alpha note", "beta note"),
                                    List.of(
                                            Map.<String, Object>of("kind", "alpha"),
                                            Map.<String, Object>of("kind", "beta"))))
                    .assertNext(
                            ids -> assertThat(ids).containsExactlyElementsOf(requestedVectorIds))
                    .verifyComplete();

            StepVerifier.create(memoryVector.search(memoryId, "alpha note", 10).collectList())
                    .assertNext(
                            results ->
                                    assertThat(results)
                                            .extracting(VectorSearchResult::vectorId)
                                            .contains("rawdata:alpha"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should be able to search after storing")
        void shouldFindStoredDocument() {
            var vectorId = memoryVector.store(memoryId, "hello world", Map.of()).block();
            assertThat(vectorId).isNotBlank();

            StepVerifier.create(memoryVector.search(memoryId, "hello", 10).collectList())
                    .assertNext(
                            results -> {
                                assertThat(results).isNotEmpty();
                                assertThat(results.getFirst().text()).isEqualTo("hello world");
                            })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("should not find after delete")
        void shouldNotFindAfterDelete() {
            var vectorId = memoryVector.store(memoryId, "to be deleted", Map.of()).block();
            memoryVector.delete(memoryId, vectorId).block();

            StepVerifier.create(memoryVector.search(memoryId, "deleted", 10).collectList())
                    .assertNext(results -> assertThat(results).isEmpty())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Embedding")
    class Embedding {

        @Test
        @DisplayName("embed should return vector")
        void embedShouldReturnVector() {
            StepVerifier.create(memoryVector.embed("test text"))
                    .assertNext(vector -> assertThat(vector).isNotEmpty())
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("searchBatch should preserve request order and report delegated invocation count")
    void searchBatchShouldPreserveRequestOrderAndReportDelegatedInvocationCount() {
        StepVerifier.create(
                        memoryVector
                                .storeBatch(
                                        memoryId,
                                        List.of("alpha note", "beta note"),
                                        List.of(Map.of(), Map.of()))
                                .then(
                                        memoryVector.searchBatch(
                                                memoryId,
                                                List.of(
                                                        new VectorSearchRequest(
                                                                "alpha note", 5, 0.0d, Map.of()),
                                                        new VectorSearchRequest(
                                                                "beta note", 5, 0.0d, Map.of())),
                                                2)))
                .assertNext(
                        bundle -> {
                            assertThat(bundle.invocationCount()).isEqualTo(2);
                            assertThat(bundle.results()).hasSize(2);
                            assertThat(bundle.results().get(0))
                                    .extracting(VectorSearchResult::text)
                                    .contains("alpha note");
                            assertThat(bundle.results().get(1))
                                    .extracting(VectorSearchResult::text)
                                    .contains("beta note");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("searchBatch should preserve exact request shaping on the baseline adapter path")
    void searchBatchShouldPreserveExactRequestShapingOnBaselineAdapterPath() {
        var embeddingModel = new FakeEmbeddingModel();
        var trackingVector =
                new TrackingSpringAiMemoryVector(
                        new FileSimpleVectorStore(
                                embeddingModel, tempDir.resolve("request-shaped-vectors.json")),
                        embeddingModel);
        var otherMemoryId = DefaultMemoryId.of("user2", "agent2");

        trackingVector
                .storeBatch(
                        memoryId,
                        List.of("alpha note", "alpha note"),
                        List.of(Map.of("topic", "semantic"), Map.of("topic", "other")))
                .block();
        trackingVector
                .storeBatch(
                        otherMemoryId, List.of("alpha note"), List.of(Map.of("topic", "semantic")))
                .block();

        var mutableFilter = new HashMap<String, Object>();
        mutableFilter.put("topic", "semantic");
        var request = new VectorSearchRequest("alpha note", 1, 0.75d, mutableFilter);
        mutableFilter.put("topic", "mutated");

        StepVerifier.create(trackingVector.searchBatch(memoryId, List.of(request), 1))
                .assertNext(
                        bundle -> {
                            assertThat(bundle.invocationCount()).isEqualTo(1);
                            assertThat(bundle.results()).hasSize(1);
                            assertThat(bundle.results().getFirst())
                                    .extracting(VectorSearchResult::text)
                                    .containsExactly("alpha note");
                            assertThat(bundle.results().getFirst())
                                    .allSatisfy(
                                            result ->
                                                    assertThat(result.metadata())
                                                            .containsEntry("topic", "semantic")
                                                            .containsEntry(
                                                                    "memoryId",
                                                                    memoryId.toIdentifier()));
                        })
                .verifyComplete();

        assertThat(trackingVector.recordedSearches())
                .containsExactly(
                        new SearchCall(
                                memoryId, "alpha note", 1, 0.75d, Map.of("topic", "semantic")));
    }

    @Test
    @DisplayName("searchBatch should honor maxConcurrency on the inherited default path")
    void searchBatchShouldHonorMaxConcurrencyOnInheritedDefaultPath() {
        var embeddingModel = new FakeEmbeddingModel();
        var trackingVector =
                new TrackingSpringAiMemoryVector(
                        new FileSimpleVectorStore(
                                embeddingModel, tempDir.resolve("tracked-vectors.json")),
                        embeddingModel,
                        "alpha note",
                        "beta note");
        trackingVector
                .storeBatch(
                        memoryId,
                        List.of("alpha note", "beta note", "gamma note"),
                        List.of(Map.of(), Map.of(), Map.of()))
                .block();

        StepVerifier.create(
                        trackingVector.searchBatch(
                                memoryId,
                                List.of(
                                        new VectorSearchRequest("alpha note", 5, 0.0d, Map.of()),
                                        new VectorSearchRequest("beta note", 5, 0.0d, Map.of()),
                                        new VectorSearchRequest("gamma note", 5, 0.0d, Map.of())),
                                2))
                .then(
                        () -> {
                            assertThat(trackingVector.awaitGatedQueriesStarted()).isTrue();
                            assertThat(trackingVector.hasStarted("gamma note")).isFalse();
                            trackingVector.releaseGatedQueries();
                        })
                .assertNext(bundle -> assertThat(bundle.invocationCount()).isEqualTo(3))
                .verifyComplete();

        assertThat(trackingVector.maxInFlight()).isEqualTo(2);
    }

    @Test
    @DisplayName("searchBatch should wrap delegated search failure in VectorBatchSearchException")
    void searchBatchShouldWrapDelegatedSearchFailure() {
        var embeddingModel = new FakeEmbeddingModel();
        var failingVector =
                new FailingSearchSpringAiMemoryVector(
                        new FileSimpleVectorStore(
                                embeddingModel, tempDir.resolve("failing-vectors.json")),
                        embeddingModel,
                        "beta note");
        failingVector
                .storeBatch(
                        memoryId, List.of("alpha note", "beta note"), List.of(Map.of(), Map.of()))
                .block();

        StepVerifier.create(
                        failingVector.searchBatch(
                                memoryId,
                                List.of(
                                        new VectorSearchRequest("alpha note", 5, 0.0d, Map.of()),
                                        new VectorSearchRequest("beta note", 5, 0.0d, Map.of())),
                                2))
                .expectErrorSatisfies(
                        error -> {
                            assertThat(error).isInstanceOf(VectorBatchSearchException.class);
                            assertThat(
                                            ((VectorBatchSearchException) error)
                                                    .attemptedInvocationCount())
                                    .isEqualTo(2);
                        })
                .verify();
    }

    @Test
    @DisplayName(
            "searchBatch should preserve delegated retry semantics on the baseline adapter path")
    void searchBatchShouldPreserveDelegatedRetrySemanticsOnBaselineAdapterPath() {
        var embeddingModel = new FakeEmbeddingModel();
        var flakyStore =
                new FlakySearchFileSimpleVectorStore(
                        embeddingModel, tempDir.resolve("retry-vectors.json"), 2);
        var retryingVector = new SpringAiMemoryVector(flakyStore, embeddingModel);
        retryingVector.storeBatch(memoryId, List.of("alpha note"), List.of(Map.of())).block();

        StepVerifier.create(
                        retryingVector.searchBatch(
                                memoryId,
                                List.of(new VectorSearchRequest("alpha note", 5, 0.0d, Map.of())),
                                1))
                .assertNext(
                        bundle -> {
                            assertThat(bundle.invocationCount()).isEqualTo(1);
                            assertThat(bundle.results()).hasSize(1);
                            assertThat(bundle.results().getFirst())
                                    .extracting(VectorSearchResult::text)
                                    .contains("alpha note");
                        })
                .verifyComplete();

        assertThat(flakyStore.similaritySearchAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName(
            "stage3 baseline should remain correct when fetchEmbeddings falls back to embedAll")
    void stageThreeBaselineShouldRemainCorrectWhenFetchEmbeddingsFallsBackToEmbedAll() {
        var embeddingModel = new FakeEmbeddingModel();
        var vector =
                new SpringAiMemoryVector(
                        new FileSimpleVectorStore(
                                embeddingModel, tempDir.resolve("stage3-baseline-vectors.json")),
                        embeddingModel);
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();

        var vectorIds =
                vector.storeBatch(
                                memoryId,
                                List.of("alpha note", "alpha note"),
                                List.of(Map.of(), Map.of()))
                        .block();
        var first = item(101L, vectorIds.get(0), "alpha note");
        var second = item(102L, vectorIds.get(1), "alpha note");
        itemOps.insertItems(memoryId, List.of(first, second));

        StepVerifier.create(vector.fetchEmbeddings(vectorIds))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 1, 0.80d, 0, 1, 1));

        StepVerifier.create(linker.link(memoryId, List.of(first, second)))
                .assertNext(
                        stats -> {
                            assertThat(stats.intraBatchCandidateCount()).isEqualTo(2);
                            assertThat(stats.sameBatchHitCount()).isEqualTo(2);
                            assertThat(stats.degraded()).isFalse();
                        })
                .verifyComplete();

        assertThat(graphOps.listItemLinks(memoryId))
                .filteredOn(link -> link.linkType() == ItemLinkType.SEMANTIC)
                .extracting(ItemLink::sourceItemId, ItemLink::targetItemId)
                .containsExactlyInAnyOrder(tuple(101L, 102L), tuple(102L, 101L));
    }

    @Test
    @DisplayName("stage3 baseline search ranking should match controlled cosine ordering")
    void stageThreeBaselineSearchRankingShouldMatchControlledCosineOrdering() {
        var embeddingModel = new ControlledStageThreeEmbeddingModel();
        var vector =
                new SpringAiMemoryVector(
                        new FileSimpleVectorStore(
                                embeddingModel, tempDir.resolve("stage3-ranking-vectors.json")),
                        embeddingModel);

        var texts = List.of("alpha clone", "alpha near", "beta far");
        vector.storeBatch(memoryId, texts, List.of(Map.of(), Map.of(), Map.of())).block();

        var queryEmbedding = vector.embed("alpha seed").block();
        var textEmbeddings = vector.embedAll(texts).block();
        var expectedOrder =
                java.util.stream.IntStream.range(0, texts.size())
                        .mapToObj(
                                index ->
                                        new RankedText(
                                                texts.get(index),
                                                cosine(queryEmbedding, textEmbeddings.get(index))))
                        .sorted(
                                Comparator.comparingDouble(RankedText::score)
                                        .reversed()
                                        .thenComparing(RankedText::text))
                        .map(RankedText::text)
                        .toList();

        var searchResults = vector.search(memoryId, "alpha seed", 3).collectList().block();

        assertThat(searchResults)
                .extracting(VectorSearchResult::text)
                .containsExactlyElementsOf(expectedOrder);
        assertThat(searchResults)
                .extracting(VectorSearchResult::score)
                .isSortedAccordingTo(Comparator.<Float>reverseOrder());
    }

    private static float cosine(List<Float> left, List<Float> right) {
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < left.size(); index++) {
            double leftValue = left.get(index);
            double rightValue = right.get(index);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        return (float) (dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)));
    }

    private MemoryItem item(long id, String vectorId, String content) {
        return new MemoryItem(
                id,
                memoryId.toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                vectorId,
                "raw-" + id,
                "hash-" + id,
                Instant.parse("2026-04-16T10:00:00Z"),
                Instant.parse("2026-04-16T10:00:00Z"),
                Map.of(),
                Instant.parse("2026-04-16T10:00:00Z"),
                MemoryItemType.FACT);
    }

    private record RankedText(String text, float score) {}

    private record SearchCall(
            MemoryId memoryId,
            String query,
            int topK,
            double minScore,
            Map<String, Object> filter) {}

    private static final class ControlledStageThreeEmbeddingModel extends FakeEmbeddingModel {

        @Override
        public float[] embed(String text) {
            return switch (text) {
                case "alpha seed" -> new float[] {1f, 0f, 0f};
                case "alpha clone" -> new float[] {1f, 0f, 0f};
                case "alpha near" -> new float[] {0.8f, 0.6f, 0f};
                case "beta far" -> new float[] {0f, 1f, 0f};
                default -> super.embed(text);
            };
        }
    }

    private static class TrackingSpringAiMemoryVector extends SpringAiMemoryVector {

        private final Set<String> gatedQueries;
        private final CountDownLatch gatedQueriesStarted;
        private final Sinks.One<Void> gatedQueriesRelease = Sinks.one();
        private final List<String> startedQueries = new CopyOnWriteArrayList<>();
        private final List<SearchCall> recordedSearches = new CopyOnWriteArrayList<>();
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxInFlight = new AtomicInteger();

        private TrackingSpringAiMemoryVector(
                FileSimpleVectorStore vectorStore,
                FakeEmbeddingModel embeddingModel,
                String... gatedQueries) {
            super(vectorStore, embeddingModel);
            this.gatedQueries = Set.of(gatedQueries);
            this.gatedQueriesStarted = new CountDownLatch(this.gatedQueries.size());
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId,
                String query,
                int topK,
                double minScore,
                Map<String, Object> filter) {
            recordedSearches.add(
                    new SearchCall(
                            memoryId,
                            query,
                            topK,
                            minScore,
                            filter == null ? Map.of() : Map.copyOf(filter)));
            return search(memoryId, query, topK, filter)
                    .filter(result -> result.score() >= minScore);
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.defer(
                    () -> {
                        var finished = new AtomicBoolean();
                        Runnable finish =
                                () -> {
                                    if (finished.compareAndSet(false, true)) {
                                        inFlight.decrementAndGet();
                                    }
                                };
                        startedQueries.add(query);
                        int current = inFlight.incrementAndGet();
                        maxInFlight.accumulateAndGet(current, Math::max);
                        Mono<Void> gate =
                                gatedQueries.contains(query)
                                        ? Mono.fromRunnable(gatedQueriesStarted::countDown)
                                                .then(gatedQueriesRelease.asMono())
                                        : Mono.empty();
                        return gate.thenMany(super.search(memoryId, query, topK, filter))
                                .doOnComplete(finish)
                                .doOnError(ignored -> finish.run())
                                .doOnCancel(finish);
                    });
        }

        boolean awaitGatedQueriesStarted() {
            try {
                return gatedQueriesStarted.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            }
        }

        boolean hasStarted(String query) {
            return startedQueries.contains(query);
        }

        void releaseGatedQueries() {
            gatedQueriesRelease.tryEmitEmpty();
        }

        int maxInFlight() {
            return maxInFlight.get();
        }

        List<SearchCall> recordedSearches() {
            return recordedSearches;
        }
    }

    private static final class FailingSearchSpringAiMemoryVector
            extends TrackingSpringAiMemoryVector {

        private final String failingQuery;

        private FailingSearchSpringAiMemoryVector(
                FileSimpleVectorStore vectorStore,
                FakeEmbeddingModel embeddingModel,
                String failingQuery) {
            super(vectorStore, embeddingModel);
            this.failingQuery = failingQuery;
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            if (query.equals(failingQuery)) {
                return Flux.error(new IllegalStateException("simulated delegated search failure"));
            }
            return super.search(memoryId, query, topK, filter);
        }
    }

    private static final class FlakySearchFileSimpleVectorStore extends FileSimpleVectorStore {

        private final AtomicInteger remainingFailures;
        private final AtomicInteger similaritySearchAttempts = new AtomicInteger();

        private FlakySearchFileSimpleVectorStore(
                FakeEmbeddingModel embeddingModel, Path filePath, int failuresBeforeSuccess) {
            super(embeddingModel, filePath);
            this.remainingFailures = new AtomicInteger(failuresBeforeSuccess);
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            similaritySearchAttempts.incrementAndGet();
            if (remainingFailures.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                throw new IllegalStateException("simulated retryable similaritySearch failure");
            }
            return super.similaritySearch(request);
        }

        int similaritySearchAttempts() {
            return similaritySearchAttempts.get();
        }
    }
}
