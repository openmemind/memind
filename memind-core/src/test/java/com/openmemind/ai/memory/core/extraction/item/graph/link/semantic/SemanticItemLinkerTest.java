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
package com.openmemind.ai.memory.core.extraction.item.graph.link.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorBatchSearchException;
import com.openmemind.ai.memory.core.vector.VectorBatchSearchResult;
import com.openmemind.ai.memory.core.vector.VectorSearchRequest;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
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
                                .withSemanticSearchHeadroom(4)
                                .withSemanticSourceWindowSize(1));

        StepVerifier.create(
                        linker.link(MEMORY_ID, List.of(item(101L, "vector-101", "source item"))))
                .assertNext(
                        stats -> {
                            assertThat(stats.searchRequestCount()).isEqualTo(1);
                            assertThat(stats.searchInvocationCount()).isEqualTo(1);
                            assertThat(stats.searchHitCount()).isEqualTo(8);
                            assertThat(stats.resolvedCandidateCount()).isEqualTo(5);
                            assertThat(stats.createdLinkCount()).isEqualTo(5);
                            assertThat(stats.sameBatchHitCount()).isZero();
                            assertThat(stats.sourceWindowCount()).isEqualTo(1);
                            assertThat(stats.upsertBatchCount()).isEqualTo(1);
                            assertThat(stats.failedResolveChunkCount()).isZero();
                            assertThat(stats.failedWindowCount()).isZero();
                            assertThat(stats.failedUpsertBatchCount()).isZero();
                            assertThat(stats.searchFallbackCount()).isZero();
                            assertThat(stats.degraded()).isFalse();
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
    void linkProcessesWindowsSequentiallyAndBatchesResolveAndUpsertPerWindow() throws Exception {
        var graphOps = new RecordingGraphOperations();
        var itemOps = new RecordingItemOperations();
        itemOps.insertItems(
                MEMORY_ID,
                List.of(
                        item(101L, "vector-101", "first item"),
                        item(102L, "vector-102", "second item"),
                        item(103L, "vector-103", "third item"),
                        item(201L, "vector-201", "neighbor a"),
                        item(202L, "vector-202", "neighbor b"),
                        item(203L, "vector-203", "neighbor c")));

        var vector = new CoordinatedMemoryVector("first item", "second item");
        vector.register("first item", List.of(result("vector-201", 0.93f)));
        vector.register("second item", List.of(result("vector-202", 0.92f)));
        vector.register("third item", List.of(result("vector-203", 0.91f)));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.82d, 4, 2, 2));

        StepVerifier.create(
                        linker.link(
                                MEMORY_ID,
                                List.of(
                                        item(101L, "vector-101", "first item"),
                                        item(102L, "vector-102", "second item"),
                                        item(103L, "vector-103", "third item"))))
                .then(
                        () -> {
                            try {
                                assertThat(vector.awaitGatedQueriesStarted()).isTrue();
                            } catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(error);
                            }
                            assertThat(vector.hasStarted("third item")).isFalse();
                            vector.releaseGatedQueries();
                        })
                .assertNext(
                        stats -> {
                            assertThat(stats.searchRequestCount()).isEqualTo(3);
                            assertThat(stats.searchInvocationCount()).isEqualTo(3);
                            assertThat(stats.searchHitCount()).isEqualTo(3);
                            assertThat(stats.resolvedCandidateCount()).isEqualTo(3);
                            assertThat(stats.createdLinkCount()).isEqualTo(3);
                            assertThat(stats.sourceWindowCount()).isEqualTo(2);
                            assertThat(stats.upsertBatchCount()).isEqualTo(2);
                            assertThat(stats.failedResolveChunkCount()).isZero();
                            assertThat(stats.failedWindowCount()).isZero();
                            assertThat(stats.failedUpsertBatchCount()).isZero();
                            assertThat(stats.searchFallbackCount()).isZero();
                            assertThat(stats.degraded()).isFalse();
                        })
                .verifyComplete();

        assertThat(itemOps.requestedVectorIds()).hasSize(2);
        assertThat(graphOps.semanticWriteSizes()).containsExactly(2, 1);
        assertThat(vector.maxInFlight()).isEqualTo(2);
        assertThat(vector.startedQueries()).contains("first item", "second item", "third item");
    }

    @Test
    void linkUsesSearchBatchOnHappyPathAndPreservesInvocationMatrix() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        itemOps.insertItems(
                MEMORY_ID,
                List.of(
                        item(101L, "vector-101", "first item"),
                        item(102L, "vector-102", "second item"),
                        item(201L, "vector-201", "neighbor a"),
                        item(202L, "vector-202", "neighbor b")));

        var vector = new BatchAwareMemoryVector();
        vector.setBatchResult(
                new VectorBatchSearchResult(
                        List.of(
                                List.of(result("vector-201", 0.93f)),
                                List.of(result("vector-202", 0.92f))),
                        1));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.82d, 4, 2, 2));

        StepVerifier.create(
                        linker.link(
                                MEMORY_ID,
                                List.of(
                                        item(101L, "vector-101", "first item"),
                                        item(102L, "vector-102", "second item"))))
                .assertNext(
                        stats -> {
                            assertThat(stats.searchRequestCount()).isEqualTo(2);
                            assertThat(stats.searchInvocationCount()).isEqualTo(1);
                            assertThat(stats.searchFallbackCount()).isZero();
                            assertThat(stats.searchHitCount()).isEqualTo(2);
                        })
                .verifyComplete();

        assertThat(vector.batchQueries()).containsExactly(List.of("first item", "second item"));
        assertThat(vector.singleSearchQueries()).isEmpty();
    }

    @Test
    void blankOnlyWindowSkipsSearchBatchAndLeavesSearchCountsZero() {
        var linker =
                new SemanticItemLinker(
                        new InMemoryItemOperations(),
                        new InMemoryGraphOperations(),
                        new BatchAwareMemoryVector(),
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.82d, 2, 1, 2));

        StepVerifier.create(
                        linker.link(
                                MEMORY_ID,
                                List.of(
                                        item(101L, "vector-101", "   "),
                                        item(102L, "vector-102", ""))))
                .assertNext(
                        stats -> {
                            assertThat(stats.searchRequestCount()).isZero();
                            assertThat(stats.searchInvocationCount()).isZero();
                            assertThat(stats.searchFallbackCount()).isZero();
                        })
                .verifyComplete();
    }

    @Test
    void batchFailureFallsBackToSingleSearchAndHonorsStageOneConcurrency() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        itemOps.insertItems(
                MEMORY_ID,
                List.of(
                        item(101L, "vector-101", "first item"),
                        item(102L, "vector-102", "second item"),
                        item(103L, "vector-103", "third item"),
                        item(201L, "vector-201", "neighbor a"),
                        item(202L, "vector-202", "neighbor b"),
                        item(203L, "vector-203", "neighbor c")));

        var vector = new FallbackCoordinatedBatchMemoryVector("first item", "second item");
        vector.register("first item", List.of(result("vector-201", 0.93f)));
        vector.register("second item", List.of(result("vector-202", 0.92f)));
        vector.register("third item", List.of(result("vector-203", 0.91f)));
        vector.setBatchFailure(
                new VectorBatchSearchException("boom", new IllegalStateException("boom"), 1));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.82d, 4, 2, 3));

        StepVerifier.create(
                        linker.link(
                                MEMORY_ID,
                                List.of(
                                        item(101L, "vector-101", "first item"),
                                        item(102L, "vector-102", "second item"),
                                        item(103L, "vector-103", "third item"))))
                .then(
                        () -> {
                            assertThat(vector.awaitGatedFallbackQueriesStarted()).isTrue();
                            assertThat(vector.hasStarted("third item")).isFalse();
                            vector.releaseGatedQueries();
                        })
                .assertNext(
                        stats -> {
                            assertThat(stats.searchRequestCount()).isEqualTo(3);
                            assertThat(stats.searchInvocationCount()).isEqualTo(4);
                            assertThat(stats.searchFallbackCount()).isEqualTo(3);
                            assertThat(stats.searchHitCount()).isEqualTo(3);
                            assertThat(stats.degraded()).isTrue();
                        })
                .verifyComplete();

        assertThat(vector.maxInFlight()).isEqualTo(2);
    }

    @Test
    void malformedBatchBundleFallsBackToSingleSearchAndPreservesBatchInvocationCount() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        itemOps.insertItems(
                MEMORY_ID,
                List.of(
                        item(101L, "vector-101", "first item"),
                        item(102L, "vector-102", "second item"),
                        item(201L, "vector-201", "neighbor a"),
                        item(202L, "vector-202", "neighbor b")));

        var vector = new BatchAwareMemoryVector();
        vector.register("first item", List.of(result("vector-201", 0.93f)));
        vector.register("second item", List.of(result("vector-202", 0.92f)));
        vector.setBatchResult(
                new VectorBatchSearchResult(List.of(List.of(result("vector-201", 0.93f))), 1));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.82d, 4, 2, 2));

        StepVerifier.create(
                        linker.link(
                                MEMORY_ID,
                                List.of(
                                        item(101L, "vector-101", "first item"),
                                        item(102L, "vector-102", "second item"))))
                .assertNext(
                        stats -> {
                            assertThat(stats.searchRequestCount()).isEqualTo(2);
                            assertThat(stats.searchInvocationCount()).isEqualTo(3);
                            assertThat(stats.searchFallbackCount()).isEqualTo(2);
                            assertThat(stats.searchHitCount()).isEqualTo(2);
                            assertThat(stats.degraded()).isTrue();
                        })
                .verifyComplete();
    }

    @Test
    void rawBatchFailureTreatsAttemptedInvocationsAsZeroBeforeFallbackReplay() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        itemOps.insertItems(
                MEMORY_ID,
                List.of(
                        item(101L, "vector-101", "first item"),
                        item(201L, "vector-201", "neighbor a")));

        var vector = new BatchAwareMemoryVector();
        vector.register("first item", List.of(result("vector-201", 0.93f)));
        vector.setRawBatchFailure(new IllegalStateException("untyped batch failure"));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.82d, 4, 1, 1));

        StepVerifier.create(linker.link(MEMORY_ID, List.of(item(101L, "vector-101", "first item"))))
                .assertNext(
                        stats -> {
                            assertThat(stats.searchRequestCount()).isEqualTo(1);
                            assertThat(stats.searchInvocationCount()).isEqualTo(1);
                            assertThat(stats.searchFallbackCount()).isEqualTo(1);
                            assertThat(stats.degraded()).isTrue();
                        })
                .verifyComplete();
    }

    @Test
    void sameBatchHitCountUsesFullBatchScopeAcrossSourceWindows() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        var batchItems =
                List.of(
                        item(101L, "vector-101", "first item"),
                        item(102L, "vector-102", "second item"),
                        item(103L, "vector-103", "third item"),
                        item(104L, "vector-104", "fourth item"),
                        item(105L, "vector-105", "fifth item"));
        itemOps.insertItems(MEMORY_ID, batchItems);

        var vector = new StubMemoryVector();
        vector.register(
                "fifth item",
                List.of(
                        result("vector-101", 0.95f),
                        result("vector-102", 0.94f),
                        result("vector-103", 0.93f),
                        result("vector-104", 0.92f)));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.82d, 4, 1, 2));

        StepVerifier.create(linker.link(MEMORY_ID, batchItems))
                .assertNext(stats -> assertThat(stats.sameBatchHitCount()).isEqualTo(4))
                .verifyComplete();
    }

    @Test
    void linkAddsExplicitSameBatchCandidatesAcrossFullBatchScopeAndSharesQuota() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        var vector = new EmbeddingAwareMemoryVector();

        var first = item(101L, "vector-101", "alpha note");
        var second = item(102L, "vector-102", "alpha note");
        var external = item(201L, "vector-201", "external alpha");
        itemOps.insertItems(MEMORY_ID, List.of(first, second, external));

        vector.register("alpha note", List.of(result("vector-201", 0.94f)));
        vector.fetchReturns(
                Map.of(
                        "vector-101", List.of(1f, 0f, 0f),
                        "vector-102", List.of(1f, 0f, 0f)));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 1, 0.80d, 0, 1, 1));

        StepVerifier.create(linker.link(MEMORY_ID, List.of(first, second)))
                .assertNext(
                        stats -> {
                            assertThat(stats.intraBatchCandidateCount()).isEqualTo(2);
                            assertThat(stats.sameBatchHitCount()).isEqualTo(2);
                            assertThat(stats.createdLinkCount()).isEqualTo(2);
                            assertThat(stats.degraded()).isFalse();
                        })
                .verifyComplete();

        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .filteredOn(link -> link.linkType() == ItemLinkType.SEMANTIC)
                .extracting(ItemLink::sourceItemId, ItemLink::targetItemId)
                .containsExactlyInAnyOrder(tuple(101L, 102L), tuple(102L, 101L));
    }

    @Test
    void linkPrefersFetchEmbeddingsAndRecomputesOnlyMissingBatchEmbeddings() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        var vector = new EmbeddingAwareMemoryVector();

        var first = item(101L, "vector-101", "alpha note");
        var second = item(102L, "vector-102", "alpha note");
        itemOps.insertItems(MEMORY_ID, List.of(first, second));

        vector.fetchReturns(Map.of("vector-101", List.of(1f, 0f, 0f)));
        vector.embedAllReturns(List.of(List.of(1f, 0f, 0f)));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.80d, 4, 1, 2));

        StepVerifier.create(linker.link(MEMORY_ID, List.of(first, second)))
                .assertNext(
                        stats -> {
                            assertThat(stats.intraBatchCandidateCount()).isEqualTo(2);
                            assertThat(stats.degraded()).isFalse();
                        })
                .verifyComplete();

        assertThat(vector.fetchEmbeddingCalls()).isEqualTo(1);
        assertThat(vector.embedAllInputs()).containsExactly(List.of("alpha note"));
    }

    @Test
    void linkFallsBackToFullBatchEmbedAllWhenFetchEmbeddingsFails() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        var vector = new EmbeddingAwareMemoryVector();

        var first = item(101L, "vector-101", "alpha note");
        var second = item(102L, "vector-102", "alpha note");
        itemOps.insertItems(MEMORY_ID, List.of(first, second));

        vector.failFetchEmbeddings(new IllegalStateException("fetch failed"));
        vector.embedAllReturns(List.of(List.of(1f, 0f, 0f), List.of(1f, 0f, 0f)));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.80d, 4, 1, 2));

        StepVerifier.create(linker.link(MEMORY_ID, List.of(first, second)))
                .assertNext(
                        stats -> {
                            assertThat(stats.intraBatchCandidateCount()).isEqualTo(2);
                            assertThat(stats.sameBatchHitCount()).isEqualTo(2);
                            assertThat(stats.createdLinkCount()).isEqualTo(2);
                            assertThat(stats.degraded()).isFalse();
                        })
                .verifyComplete();

        assertThat(vector.fetchEmbeddingCalls()).isEqualTo(1);
        assertThat(vector.embedAllInputs()).containsExactly(List.of("alpha note", "alpha note"));
    }

    @Test
    void linkFallsBackToExternalOnlyWhenBatchEmbeddingAcquisitionFails() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        var vector = new EmbeddingAwareMemoryVector();

        var source = item(101L, "vector-101", "source note");
        var existing = item(201L, "vector-201", "existing note");
        itemOps.insertItems(MEMORY_ID, List.of(source, existing));

        vector.register("source note", List.of(result("vector-201", 0.91f)));
        vector.failFetchEmbeddings(new IllegalStateException("fetch failed"));
        vector.failEmbedAll(new IllegalStateException("embedAll failed"));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.80d, 4, 1, 1));

        StepVerifier.create(linker.link(MEMORY_ID, List.of(source)))
                .assertNext(
                        stats -> {
                            assertThat(stats.searchHitCount()).isEqualTo(1);
                            assertThat(stats.intraBatchCandidateCount()).isZero();
                            assertThat(stats.sameBatchHitCount()).isZero();
                            assertThat(stats.createdLinkCount()).isEqualTo(1);
                            assertThat(stats.degraded()).isTrue();
                        })
                .verifyComplete();
    }

    @Test
    void linkSkipsSameBatchContributionWhenCosineComparisonFails() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        var vector = new EmbeddingAwareMemoryVector();

        var first = item(101L, "vector-101", "source note");
        var second = item(102L, "vector-102", "sibling note");
        var existing = item(201L, "vector-201", "existing note");
        itemOps.insertItems(MEMORY_ID, List.of(first, second, existing));

        vector.register("source note", List.of(result("vector-201", 0.91f)));
        vector.fetchReturns(
                Map.of(
                        "vector-101", List.of(1f, 0f),
                        "vector-102", List.of(1f, 0f, 0f)));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.80d, 4, 1, 1));

        StepVerifier.create(linker.link(MEMORY_ID, List.of(first, second)))
                .assertNext(
                        stats -> {
                            assertThat(stats.searchHitCount()).isEqualTo(1);
                            assertThat(stats.intraBatchCandidateCount()).isZero();
                            assertThat(stats.sameBatchHitCount()).isZero();
                            assertThat(stats.createdLinkCount()).isEqualTo(1);
                            assertThat(stats.degraded()).isTrue();
                        })
                .verifyComplete();
    }

    @Test
    void linkPreCapsSameBatchCandidatesBeforeMergeAndReportsThePreCappedCount() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        var vector = new EmbeddingAwareMemoryVector();

        var source = item(101L, "vector-101", "source note");
        var candidateA = item(201L, "vector-201", "candidate a");
        var candidateB = item(202L, "vector-202", "candidate b");
        var candidateC = item(203L, "vector-203", "candidate c");
        var candidateD = item(204L, "vector-204", "candidate d");
        var candidateE = item(205L, "vector-205", "candidate e");
        itemOps.insertItems(
                MEMORY_ID,
                List.of(source, candidateA, candidateB, candidateC, candidateD, candidateE));

        vector.fetchReturns(
                Map.of(
                        "vector-101", List.of(1f, 0f, 0f, 0f, 0f, 0f),
                        "vector-201", List.of(0.81f, 0.58643f, 0f, 0f, 0f, 0f),
                        "vector-202", List.of(0.81f, 0f, 0.58643f, 0f, 0f, 0f),
                        "vector-203", List.of(0.81f, 0f, 0f, 0.58643f, 0f, 0f),
                        "vector-204", List.of(0.81f, 0f, 0f, 0f, 0.58643f, 0f),
                        "vector-205", List.of(0.81f, 0f, 0f, 0f, 0f, 0.58643f)));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 2, 0.80d, 1, 1, 1));

        StepVerifier.create(
                        linker.link(
                                MEMORY_ID,
                                List.of(
                                        source,
                                        candidateA,
                                        candidateB,
                                        candidateC,
                                        candidateD,
                                        candidateE)))
                .assertNext(
                        stats -> {
                            assertThat(stats.intraBatchCandidateCount()).isEqualTo(9);
                            assertThat(stats.sameBatchHitCount()).isEqualTo(7);
                            assertThat(stats.createdLinkCount()).isEqualTo(7);
                            assertThat(stats.degraded()).isFalse();
                        })
                .verifyComplete();

        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .filteredOn(
                        link ->
                                link.linkType() == ItemLinkType.SEMANTIC
                                        && link.sourceItemId() == 101L)
                .extracting(ItemLink::sourceItemId, ItemLink::targetItemId)
                .containsExactly(tuple(101L, 201L), tuple(101L, 202L));
    }

    @Test
    void resolveChunkFailureDropsOnlyThatChunkAndContinuesNormalization() {
        int resolveBatchSize = SemanticItemLinker.SEMANTIC_RESOLVE_BATCH_SIZE;
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new ChunkFailingItemOperations("vector-001");
        itemOps.insertItems(
                MEMORY_ID,
                java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(
                                        item(500L, "vector-source", "source item")),
                                java.util.stream.IntStream.rangeClosed(1, resolveBatchSize + 2)
                                        .mapToObj(
                                                i ->
                                                        item(
                                                                1000L + i,
                                                                "vector-%03d".formatted(i),
                                                                "neighbor-%03d".formatted(i))))
                        .toList());

        var vector = new StubMemoryVector();
        vector.register(
                "source item",
                java.util.stream.IntStream.rangeClosed(1, resolveBatchSize + 2)
                        .mapToObj(i -> result("vector-%03d".formatted(i), 1.0f - (i * 0.0001f)))
                        .toList());

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.82d, 4, 1, 1));

        StepVerifier.create(
                        linker.link(MEMORY_ID, List.of(item(500L, "vector-source", "source item"))))
                .assertNext(
                        stats -> {
                            assertThat(stats.failedResolveChunkCount()).isEqualTo(1);
                            assertThat(stats.failedWindowCount()).isZero();
                            assertThat(stats.resolvedCandidateCount()).isEqualTo(2);
                            assertThat(stats.createdLinkCount()).isEqualTo(2);
                            assertThat(stats.degraded()).isTrue();
                        })
                .verifyComplete();
    }

    @Test
    void allResolveChunksFailMarksWindowFailedAndContinuesNextWindow() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new PrefixFailingItemOperations("fail-");
        itemOps.insertItems(
                MEMORY_ID,
                List.of(
                        item(800L, "vector-first-source", "first source"),
                        item(801L, "vector-second-source", "second source"),
                        item(900L, "vector-900", "healthy neighbor")));

        var vector = new StubMemoryVector();
        vector.register(
                "first source", List.of(result("fail-001", 0.95f), result("fail-002", 0.94f)));
        vector.register("second source", List.of(result("vector-900", 0.93f)));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.82d, 4, 1, 1));

        StepVerifier.create(
                        linker.link(
                                MEMORY_ID,
                                List.of(
                                        item(800L, "vector-first-source", "first source"),
                                        item(801L, "vector-second-source", "second source"))))
                .assertNext(
                        stats -> {
                            assertThat(stats.failedResolveChunkCount()).isEqualTo(1);
                            assertThat(stats.failedWindowCount()).isEqualTo(1);
                            assertThat(stats.sourceWindowCount()).isEqualTo(2);
                            assertThat(stats.resolvedCandidateCount()).isEqualTo(1);
                            assertThat(stats.createdLinkCount()).isEqualTo(1);
                            assertThat(stats.degraded()).isTrue();
                        })
                .verifyComplete();

        assertThat(graphOps.listItemLinks(MEMORY_ID)).hasSize(1);
    }

    @Test
    void successfulResolveChunksWithoutMatchesDoNotCountAsFailedWindows() {
        var graphOps = new InMemoryGraphOperations();
        var itemOps = new InMemoryItemOperations();
        itemOps.insertItems(MEMORY_ID, List.of(item(600L, "vector-source", "source item")));

        var vector = new StubMemoryVector();
        vector.register(
                "source item", List.of(result("missing-001", 0.93f), result("missing-002", 0.92f)));

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, 5, 0.82d, 4, 1, 1));

        StepVerifier.create(
                        linker.link(MEMORY_ID, List.of(item(600L, "vector-source", "source item"))))
                .assertNext(
                        stats -> {
                            assertThat(stats.failedResolveChunkCount()).isZero();
                            assertThat(stats.failedWindowCount()).isZero();
                            assertThat(stats.resolvedCandidateCount()).isZero();
                            assertThat(stats.createdLinkCount()).isZero();
                            assertThat(stats.degraded()).isFalse();
                        })
                .verifyComplete();
    }

    @Test
    void failedSemanticUpsertChunkDoesNotBlockLaterChunks() {
        int upsertBatchSize = SemanticItemLinker.SEMANTIC_UPSERT_BATCH_SIZE;
        var graphOps = new FailingSemanticGraphOperations(1);
        var itemOps = new InMemoryItemOperations();
        itemOps.insertItems(
                MEMORY_ID,
                java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(
                                        item(700L, "vector-source", "source item")),
                                java.util.stream.IntStream.rangeClosed(1, upsertBatchSize + 1)
                                        .mapToObj(
                                                i ->
                                                        item(
                                                                2000L + i,
                                                                "candidate-%03d".formatted(i),
                                                                "candidate-%03d".formatted(i))))
                        .toList());

        var vector = new StubMemoryVector();
        vector.register(
                "source item",
                java.util.stream.IntStream.rangeClosed(1, upsertBatchSize + 1)
                        .mapToObj(i -> result("candidate-%03d".formatted(i), 0.99f - (i * 0.0001f)))
                        .toList());

        var linker =
                new SemanticItemLinker(
                        itemOps,
                        graphOps,
                        vector,
                        new ItemGraphOptions(true, 8, 2, 10, upsertBatchSize + 1, 0.82d, 4, 1, 1));

        StepVerifier.create(
                        linker.link(MEMORY_ID, List.of(item(700L, "vector-source", "source item"))))
                .assertNext(
                        stats -> {
                            assertThat(stats.createdLinkCount()).isEqualTo(upsertBatchSize + 1);
                            assertThat(stats.upsertBatchCount()).isEqualTo(2);
                            assertThat(stats.failedUpsertBatchCount()).isEqualTo(1);
                            assertThat(stats.degraded()).isTrue();
                        })
                .verifyComplete();

        assertThat(graphOps.listItemLinks(MEMORY_ID)).hasSize(1);
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

        protected final Map<String, List<VectorSearchResult>> results = new HashMap<>();

        void register(String query, List<VectorSearchResult> searchResults) {
            results.put(query, List.copyOf(searchResults));
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.fromIterable(results.getOrDefault(query, List.of()));
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId,
                String query,
                int topK,
                double minScore,
                Map<String, Object> filter) {
            return search(memoryId, query, topK, filter)
                    .filter(result -> result.score() >= minScore);
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

    private static class RecordingItemOperations extends InMemoryItemOperations {

        private final List<Set<String>> requestedVectorIds = new java.util.ArrayList<>();

        @Override
        public List<MemoryItem> getItemsByVectorIds(
                MemoryId memoryId, Collection<String> vectorIds) {
            requestedVectorIds.add(new LinkedHashSet<>(vectorIds));
            return super.getItemsByVectorIds(memoryId, vectorIds);
        }

        List<Set<String>> requestedVectorIds() {
            return requestedVectorIds;
        }
    }

    private static final class RecordingGraphOperations extends InMemoryGraphOperations {

        private final List<Integer> semanticWriteSizes = new java.util.ArrayList<>();

        @Override
        public void upsertItemLinks(MemoryId memoryId, List<ItemLink> links) {
            if (!links.isEmpty() && links.getFirst().linkType() == ItemLinkType.SEMANTIC) {
                semanticWriteSizes.add(links.size());
            }
            super.upsertItemLinks(memoryId, links);
        }

        List<Integer> semanticWriteSizes() {
            return semanticWriteSizes;
        }
    }

    private static class BatchAwareMemoryVector extends StubMemoryVector {

        private final List<List<String>> batchQueries = new CopyOnWriteArrayList<>();
        private final List<String> singleSearchQueries = new CopyOnWriteArrayList<>();
        private VectorBatchSearchResult batchResult;
        private Throwable batchFailure;

        void setBatchResult(VectorBatchSearchResult batchResult) {
            this.batchResult = batchResult;
            this.batchFailure = null;
        }

        void setBatchFailure(VectorBatchSearchException batchFailure) {
            this.batchFailure = batchFailure;
            this.batchResult = null;
        }

        void setRawBatchFailure(Throwable batchFailure) {
            this.batchFailure = batchFailure;
            this.batchResult = null;
        }

        @Override
        public Mono<VectorBatchSearchResult> searchBatch(
                MemoryId memoryId, List<VectorSearchRequest> requests, int maxConcurrency) {
            batchQueries.add(requests.stream().map(VectorSearchRequest::query).toList());
            if (batchFailure != null) {
                return Mono.error(batchFailure);
            }
            if (batchResult == null) {
                return Mono.error(new IllegalStateException("batch result not configured"));
            }
            return Mono.just(batchResult);
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId,
                String query,
                int topK,
                double minScore,
                Map<String, Object> filter) {
            singleSearchQueries.add(query);
            return super.search(memoryId, query, topK, minScore, filter);
        }

        List<List<String>> batchQueries() {
            return batchQueries;
        }

        List<String> singleSearchQueries() {
            return singleSearchQueries;
        }
    }

    private static final class ChunkFailingItemOperations extends RecordingItemOperations {

        private final String failingVectorId;

        private ChunkFailingItemOperations(String failingVectorId) {
            this.failingVectorId = failingVectorId;
        }

        @Override
        public List<MemoryItem> getItemsByVectorIds(
                MemoryId memoryId, Collection<String> vectorIds) {
            if (vectorIds.contains(failingVectorId)) {
                throw new IllegalStateException("simulated resolve chunk failure");
            }
            return super.getItemsByVectorIds(memoryId, vectorIds);
        }
    }

    private static final class CoordinatedMemoryVector extends StubMemoryVector {

        private final Set<String> gatedQueries;
        private final CountDownLatch gatedQueriesStarted;
        private final Sinks.One<Void> gatedQueriesRelease = Sinks.one();
        private final List<String> startedQueries = new CopyOnWriteArrayList<>();
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxInFlight = new AtomicInteger();

        private CoordinatedMemoryVector(String... gatedQueries) {
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
            return Flux.defer(
                    () -> {
                        startedQueries.add(query);
                        int current = inFlight.incrementAndGet();
                        maxInFlight.accumulateAndGet(current, Math::max);
                        Mono<Void> gate =
                                gatedQueries.contains(query)
                                        ? Mono.fromRunnable(gatedQueriesStarted::countDown)
                                                .then(gatedQueriesRelease.asMono())
                                        : Mono.empty();
                        return gate.thenMany(
                                        search(
                                                        memoryId,
                                                        query,
                                                        topK,
                                                        filter != null ? filter : Map.of())
                                                .filter(result -> result.score() >= minScore))
                                .doFinally(ignored -> inFlight.decrementAndGet());
                    });
        }

        int maxInFlight() {
            return maxInFlight.get();
        }

        boolean awaitGatedQueriesStarted() throws InterruptedException {
            return gatedQueriesStarted.await(1, TimeUnit.SECONDS);
        }

        boolean hasStarted(String query) {
            return startedQueries.contains(query);
        }

        void releaseGatedQueries() {
            gatedQueriesRelease.tryEmitEmpty();
        }

        List<String> startedQueries() {
            return startedQueries;
        }
    }

    private static final class FallbackCoordinatedBatchMemoryVector extends BatchAwareMemoryVector {

        private final Set<String> gatedQueries;
        private final CountDownLatch gatedQueriesStarted;
        private final Sinks.One<Void> gatedQueriesRelease = Sinks.one();
        private final List<String> startedQueries = new CopyOnWriteArrayList<>();
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxInFlight = new AtomicInteger();

        private FallbackCoordinatedBatchMemoryVector(String... gatedQueries) {
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
            return Flux.defer(
                    () -> {
                        var finished = new java.util.concurrent.atomic.AtomicBoolean();
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
                        return gate.thenMany(super.search(memoryId, query, topK, minScore, filter))
                                .doOnComplete(finish)
                                .doOnError(ignored -> finish.run())
                                .doOnCancel(finish);
                    });
        }

        boolean awaitGatedFallbackQueriesStarted() {
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
    }

    private static final class EmbeddingAwareMemoryVector extends StubMemoryVector {

        private final AtomicInteger fetchEmbeddingCalls = new AtomicInteger();
        private final List<List<String>> embedAllInputs = new java.util.ArrayList<>();
        private Map<String, List<Float>> fetchedEmbeddings = Map.of();
        private List<List<Float>> embedAllResults = List.of();
        private Throwable fetchFailure;
        private Throwable embedAllFailure;

        void fetchReturns(Map<String, List<Float>> embeddings) {
            fetchedEmbeddings = Map.copyOf(embeddings);
            fetchFailure = null;
        }

        void embedAllReturns(List<List<Float>> embeddings) {
            embedAllResults = List.copyOf(embeddings);
            embedAllFailure = null;
        }

        void failFetchEmbeddings(Throwable error) {
            fetchFailure = error;
        }

        void failEmbedAll(Throwable error) {
            embedAllFailure = error;
        }

        int fetchEmbeddingCalls() {
            return fetchEmbeddingCalls.get();
        }

        List<List<String>> embedAllInputs() {
            return embedAllInputs;
        }

        @Override
        public Mono<Map<String, List<Float>>> fetchEmbeddings(List<String> vectorIds) {
            fetchEmbeddingCalls.incrementAndGet();
            if (fetchFailure != null) {
                return Mono.error(fetchFailure);
            }
            return Mono.just(fetchedEmbeddings);
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            if (embedAllFailure != null) {
                return Mono.error(embedAllFailure);
            }
            embedAllInputs.add(List.copyOf(texts));
            return Mono.just(embedAllResults);
        }
    }

    private static final class PrefixFailingItemOperations extends RecordingItemOperations {

        private final String failingPrefix;

        private PrefixFailingItemOperations(String failingPrefix) {
            this.failingPrefix = failingPrefix;
        }

        @Override
        public List<MemoryItem> getItemsByVectorIds(
                MemoryId memoryId, Collection<String> vectorIds) {
            if (vectorIds.stream().anyMatch(vectorId -> vectorId.startsWith(failingPrefix))) {
                throw new IllegalStateException("simulated full resolve window failure");
            }
            return super.getItemsByVectorIds(memoryId, vectorIds);
        }
    }

    private static final class FailingSemanticGraphOperations extends InMemoryGraphOperations {

        private final int failCallIndex;
        private int semanticCallCount;

        private FailingSemanticGraphOperations(int failCallIndex) {
            this.failCallIndex = failCallIndex;
        }

        @Override
        public void upsertItemLinks(MemoryId memoryId, List<ItemLink> links) {
            if (!links.isEmpty() && links.getFirst().linkType() == ItemLinkType.SEMANTIC) {
                semanticCallCount++;
                if (semanticCallCount == failCallIndex) {
                    throw new IllegalStateException("simulated semantic upsert failure");
                }
            }
            super.upsertItemLinks(memoryId, links);
        }
    }
}
