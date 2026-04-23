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
package com.openmemind.ai.memory.core.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class MemoryVectorDefaultBatchSearchTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");

    @Test
    void vectorSearchRequestCopiesFilterAndRejectsInvalidArguments() {
        var mutableFilter = new HashMap<String, Object>();
        mutableFilter.put("scope", "user");

        var request = new VectorSearchRequest("query", 3, 0.75d, mutableFilter);
        mutableFilter.put("scope", "agent");

        assertThat(request.filter()).containsEntry("scope", "user");
        assertThatThrownBy(() -> request.filter().put("stage", "two"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new VectorSearchRequest(null, 3, 0.75d, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
        assertThatThrownBy(() -> new VectorSearchRequest("query", 0, 0.75d, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");
        assertThatThrownBy(() -> new VectorSearchRequest("query", 3, 1.01d, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minScore");
    }

    @Test
    void vectorBatchSearchResultCopiesContainersAndRejectsMalformedArguments() {
        var mutableInner =
                new ArrayList<>(
                        List.of(new VectorSearchResult("vector-1", "alpha", 0.91f, Map.of())));
        var mutableOuter = new ArrayList<List<VectorSearchResult>>();
        mutableOuter.add(mutableInner);

        var bundle = new VectorBatchSearchResult(mutableOuter, 1);
        mutableInner.clear();
        mutableOuter.clear();

        assertThat(bundle.results()).hasSize(1);
        assertThat(bundle.results().getFirst()).hasSize(1);
        assertThatThrownBy(() -> bundle.results().add(List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new VectorBatchSearchResult(null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("results");
        var malformedOuter = new ArrayList<List<VectorSearchResult>>();
        malformedOuter.add(null);
        assertThatThrownBy(() -> new VectorBatchSearchResult(malformedOuter, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("results");
        assertThatThrownBy(() -> new VectorBatchSearchResult(List.of(List.of()), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invocationCount");
    }

    @Test
    void vectorBatchSearchExceptionRejectsNegativeAttemptedInvocationCount() {
        assertThat(
                        new VectorBatchSearchException("boom", new IllegalStateException("boom"), 0)
                                .attemptedInvocationCount())
                .isZero();
        assertThatThrownBy(
                        () ->
                                new VectorBatchSearchException(
                                        "boom", new IllegalStateException("boom"), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attemptedInvocationCount");
    }

    @Test
    void searchBatchReturnsEmptyBundleWithoutIssuingDelegatedSearches() {
        var vector = new TrackingMemoryVector();

        StepVerifier.create(vector.searchBatch(MEMORY_ID, List.of(), 3))
                .assertNext(
                        bundle -> {
                            assertThat(bundle.results()).isEmpty();
                            assertThat(bundle.invocationCount()).isZero();
                        })
                .verifyComplete();

        assertThat(vector.recordedSearches()).isEmpty();
    }

    @Test
    void searchBatchDelegatesExactPerRequestParametersToSingleSearchPath() {
        var vector = new TrackingMemoryVector();
        vector.register(
                "alpha", List.of(result("vector-a-high", 0.96f), result("vector-a-low", 0.72f)));
        vector.register(
                "beta", List.of(result("vector-b-high", 0.91f), result("vector-b-low", 0.65f)));

        var mutableFilter = new HashMap<String, Object>();
        mutableFilter.put("scope", "user");
        var alphaRequest = new VectorSearchRequest("alpha", 1, 0.80d, mutableFilter);
        mutableFilter.put("scope", "mutated");
        var betaRequest = new VectorSearchRequest("beta", 2, 0.90d, Map.of("topic", "semantic"));

        StepVerifier.create(vector.searchBatch(MEMORY_ID, List.of(alphaRequest, betaRequest), 1))
                .assertNext(
                        bundle -> {
                            assertThat(bundle.invocationCount()).isEqualTo(2);
                            assertThat(bundle.results()).hasSize(2);
                            assertThat(bundle.results().get(0))
                                    .extracting(VectorSearchResult::vectorId)
                                    .containsExactly("vector-a-high");
                            assertThat(bundle.results().get(1))
                                    .extracting(VectorSearchResult::vectorId)
                                    .containsExactly("vector-b-high");
                        })
                .verifyComplete();

        assertThat(vector.recordedSearches())
                .containsExactly(
                        new SearchCall(MEMORY_ID, "alpha", 1, 0.80d, Map.of("scope", "user")),
                        new SearchCall(MEMORY_ID, "beta", 2, 0.90d, Map.of("topic", "semantic")));
    }

    @Test
    void searchBatchPreservesOrderAndHonorsEffectiveConcurrency() {
        var vector = new TrackingMemoryVector("alpha", "beta");
        vector.register("alpha", List.of(result("vector-a", 0.95f)));
        vector.register("beta", List.of(result("vector-b", 0.94f)));
        vector.register("gamma", List.of(result("vector-c", 0.93f)));

        StepVerifier.create(
                        vector.searchBatch(
                                MEMORY_ID,
                                List.of(request("alpha"), request("beta"), request("gamma")),
                                2))
                .then(
                        () -> {
                            assertThat(vector.awaitGatedQueriesStarted()).isTrue();
                            assertThat(vector.hasStarted("gamma")).isFalse();
                            vector.releaseGatedQueries();
                        })
                .assertNext(
                        bundle -> {
                            assertThat(bundle.invocationCount()).isEqualTo(3);
                            assertThat(bundle.results()).hasSize(3);
                            assertThat(bundle.results().get(0))
                                    .extracting(VectorSearchResult::vectorId)
                                    .containsExactly("vector-a");
                            assertThat(bundle.results().get(1))
                                    .extracting(VectorSearchResult::vectorId)
                                    .containsExactly("vector-b");
                            assertThat(bundle.results().get(2))
                                    .extracting(VectorSearchResult::vectorId)
                                    .containsExactly("vector-c");
                        })
                .verifyComplete();

        assertThat(vector.maxInFlight()).isEqualTo(2);
    }

    @Test
    void searchBatchWrapsDelegatedFailureWithAttemptedInvocationCount() {
        var vector = new FailingTrackingMemoryVector("alpha", "beta");
        vector.register("alpha", List.of(result("vector-a", 0.95f)));

        StepVerifier.create(
                        vector.searchBatch(
                                MEMORY_ID, List.of(request("alpha"), request("beta")), 2))
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
    void storeBatchWithCallerSuppliedVectorIdsFallsBackToLegacyBackendIdsWhenNotOverridden() {
        var vector = new LegacyStoreBatchOnlyMemoryVector(List.of("generated-1", "generated-2"));

        StepVerifier.create(
                        vector.storeBatch(
                                MEMORY_ID,
                                List.of("requested-1", "requested-2"),
                                List.of("alpha", "beta"),
                                List.of(
                                        Map.<String, Object>of("order", 1),
                                        Map.<String, Object>of("order", 2))))
                .assertNext(ids -> assertThat(ids).containsExactly("generated-1", "generated-2"))
                .verifyComplete();

        assertThat(vector.recordedTexts()).containsExactly("alpha", "beta");
    }

    private static VectorSearchRequest request(String query) {
        return new VectorSearchRequest(query, 5, 0.0d, Map.of());
    }

    private static VectorSearchResult result(String vectorId, float score) {
        return new VectorSearchResult(vectorId, vectorId, score, Map.of());
    }

    private record SearchCall(
            MemoryId memoryId,
            String query,
            int topK,
            double minScore,
            Map<String, Object> filter) {}

    private static class TrackingMemoryVector implements MemoryVector {

        private final Map<String, List<VectorSearchResult>> results = new ConcurrentHashMap<>();
        private final Set<String> gatedQueries;
        private final CountDownLatch gatedQueriesStarted;
        private final Sinks.One<Void> gatedQueriesRelease = Sinks.one();
        private final List<String> startedQueries = new CopyOnWriteArrayList<>();
        private final List<SearchCall> recordedSearches = new CopyOnWriteArrayList<>();
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxInFlight = new AtomicInteger();

        private TrackingMemoryVector(String... gatedQueries) {
            this.gatedQueries = Set.of(gatedQueries);
            this.gatedQueriesStarted = new CountDownLatch(this.gatedQueries.size());
        }

        void register(String query, List<VectorSearchResult> searchResults) {
            results.put(query, List.copyOf(searchResults));
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId,
                String query,
                int topK,
                double minScore,
                Map<String, Object> filter) {
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
                        recordedSearches.add(
                                new SearchCall(
                                        memoryId,
                                        query,
                                        topK,
                                        minScore,
                                        filter == null ? Map.of() : Map.copyOf(filter)));
                        int current = inFlight.incrementAndGet();
                        maxInFlight.accumulateAndGet(current, Math::max);
                        Mono<Void> gate =
                                gatedQueries.contains(query)
                                        ? Mono.fromRunnable(gatedQueriesStarted::countDown)
                                                .then(gatedQueriesRelease.asMono())
                                        : Mono.empty();
                        return gate.thenMany(
                                        Flux.fromIterable(results.getOrDefault(query, List.of()))
                                                .filter(result -> result.score() >= minScore)
                                                .take(topK))
                                .doOnComplete(finish)
                                .doOnError(ignored -> finish.run())
                                .doOnCancel(finish);
                    });
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return search(memoryId, query, topK, 0.0d, filter);
        }

        @Override
        public Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK) {
            return search(memoryId, query, topK, Map.of());
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
        public Mono<List<Float>> embed(String text) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.error(new UnsupportedOperationException());
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

    private static final class FailingTrackingMemoryVector extends TrackingMemoryVector {

        private final String failingQuery;

        private FailingTrackingMemoryVector(String... queries) {
            super(queries);
            this.failingQuery = queries[queries.length - 1];
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId,
                String query,
                int topK,
                double minScore,
                Map<String, Object> filter) {
            if (query.equals(failingQuery)) {
                return Flux.error(new IllegalStateException("simulated delegated search failure"));
            }
            return super.search(memoryId, query, topK, minScore, filter);
        }
    }

    private static final class LegacyStoreBatchOnlyMemoryVector implements MemoryVector {

        private final List<String> vectorIds;
        private final List<String> recordedTexts = new CopyOnWriteArrayList<>();

        private LegacyStoreBatchOnlyMemoryVector(List<String> vectorIds) {
            this.vectorIds = List.copyOf(vectorIds);
        }

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            recordedTexts.addAll(texts);
            return Mono.just(vectorIds);
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
            return Flux.error(new UnsupportedOperationException());
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<List<Float>> embed(String text) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.error(new UnsupportedOperationException());
        }

        private List<String> recordedTexts() {
            return recordedTexts;
        }
    }
}
