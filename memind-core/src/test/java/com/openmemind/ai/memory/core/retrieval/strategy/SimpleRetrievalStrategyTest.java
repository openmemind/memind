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
package com.openmemind.ai.memory.core.retrieval.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTreeExpander;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.TierResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpleRetrievalStrategy")
class SimpleRetrievalStrategyTest {

    private static final MemoryId MEMORY_ID = TestMemoryIds.userAgent();
    private static final RetrievalConfig CONFIG = RetrievalConfig.simple();

    @Mock private InsightTierRetriever insightRetriever;
    @Mock private ItemTierRetriever itemRetriever;
    @Mock private MemoryTextSearch textSearch;
    @Mock private MemoryStore memoryStore;
    @Mock private ItemOperations itemOperations;
    @Mock private RawDataOperations rawDataOperations;

    private SimpleRetrievalStrategy strategy;
    private QueryContext context;

    @BeforeEach
    void setUp() {
        lenient().when(memoryStore.itemOperations()).thenReturn(itemOperations);
        lenient().when(memoryStore.rawDataOperations()).thenReturn(rawDataOperations);
        lenient().when(itemOperations.getItemsByIds(any(), any())).thenReturn(List.of());
        lenient().when(rawDataOperations.getRawData(any(), any())).thenReturn(Optional.empty());
        strategy =
                new SimpleRetrievalStrategy(
                        insightRetriever, itemRetriever, textSearch, memoryStore);
        context = new QueryContext(MEMORY_ID, "test query", null, List.of(), Map.of(), null, null);
    }

    @Nested
    @DisplayName("name()")
    class Name {

        @Test
        @DisplayName("returns simple")
        void returnsSimple() {
            assertThat(strategy.name()).isEqualTo("simple");
        }
    }

    @Nested
    @DisplayName("retrieve()")
    class Retrieve {

        @Test
        @DisplayName("Insight and Item retrieved in parallel, merge results and return")
        void insightAndItemRetrievedInParallel() {
            // Insight result
            var insightResult =
                    new TierResult(
                            List.of(
                                    new ScoredResult(
                                            ScoredResult.SourceType.INSIGHT,
                                            "1",
                                            "insight-text",
                                            0.9f,
                                            0.9)),
                            List.of(),
                            List.of(
                                    new InsightTreeExpander.ExpandedInsight(
                                            "2",
                                            "expanded-root",
                                            com.openmemind.ai.memory.core.data.enums.InsightTier
                                                    .ROOT)));
            when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(insightResult));

            // Item vector result
            var itemResult =
                    new TierResult(
                            List.of(
                                    new ScoredResult(
                                            ScoredResult.SourceType.ITEM,
                                            "101",
                                            "item-vec-1",
                                            0.85f,
                                            0.85),
                                    new ScoredResult(
                                            ScoredResult.SourceType.ITEM,
                                            "102",
                                            "item-vec-2",
                                            0.75f,
                                            0.75)),
                            List.of());
            when(itemRetriever.searchByVector(any(), any())).thenReturn(Mono.just(itemResult));

            // BM25 result
            when(textSearch.search(
                            any(), anyString(), anyInt(), eq(MemoryTextSearch.SearchTarget.ITEM)))
                    .thenReturn(
                            Mono.just(List.of(new TextSearchResult("103", "item-bm25-1", 5.0))));

            StepVerifier.create(strategy.retrieve(context, CONFIG))
                    .assertNext(
                            result -> {
                                assertThat(result.strategy()).isEqualTo("simple");
                                assertThat(result.query()).isEqualTo("test query");

                                // insights: 1 scored + 1 expanded
                                assertThat(result.insights()).hasSize(2);
                                assertThat(result.insights().getFirst().id()).isEqualTo("1");
                                assertThat(result.insights().getLast().id()).isEqualTo("2");

                                // items: merged from vector + BM25
                                assertThat(result.items()).isNotEmpty();

                                // evidences is empty
                                assertThat(result.evidences()).isEmpty();
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Empty results return empty RetrievalResult")
        void emptyResultsReturnEmpty() {
            when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(TierResult.empty()));
            when(itemRetriever.searchByVector(any(), any()))
                    .thenReturn(Mono.just(TierResult.empty()));
            when(textSearch.search(any(), anyString(), anyInt(), any()))
                    .thenReturn(Mono.just(List.of()));

            StepVerifier.create(strategy.retrieve(context, CONFIG))
                    .assertNext(
                            result -> {
                                assertThat(result.items()).isEmpty();
                                assertThat(result.insights()).isEmpty();
                                assertThat(result.strategy()).isEqualTo("simple");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("When BM25 is disabled (textSearch is null), only use vector results")
        void bm25DisabledWorksWithVectorOnly() {
            var strategyNoBm25 =
                    new SimpleRetrievalStrategy(insightRetriever, itemRetriever, null, memoryStore);

            when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(TierResult.empty()));

            var itemResult =
                    new TierResult(
                            List.of(
                                    new ScoredResult(
                                            ScoredResult.SourceType.ITEM,
                                            "101",
                                            "item-vec",
                                            0.8f,
                                            0.8)),
                            List.of());
            when(itemRetriever.searchByVector(any(), any())).thenReturn(Mono.just(itemResult));

            StepVerifier.create(strategyNoBm25.retrieve(context, CONFIG))
                    .assertNext(
                            result -> {
                                assertThat(result.items()).hasSize(1);
                                assertThat(result.items().getFirst().sourceId()).isEqualTo("101");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Error in one channel does not affect the overall pipeline")
        void errorInOneChannelDoesNotFailPipeline() {
            // Insight normal
            when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(TierResult.empty()));

            // Item vector search fails
            when(itemRetriever.searchByVector(any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("vector search failed")));

            // BM25 normal
            when(textSearch.search(any(), anyString(), anyInt(), any()))
                    .thenReturn(Mono.just(List.of(new TextSearchResult("201", "bm25-item", 3.0))));

            StepVerifier.create(strategy.retrieve(context, CONFIG))
                    .assertNext(
                            result -> {
                                assertThat(result.strategy()).isEqualTo("simple");
                                // BM25 result should exist
                                assertThat(result.items()).isNotEmpty();
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Expanded insights are appended in ROOT -> BRANCH -> LEAF order")
        void expandedInsightsAreAppendedInRootBranchLeafOrder() {
            var insightResult =
                    new TierResult(
                            List.of(),
                            List.of(),
                            List.of(
                                    new InsightTreeExpander.ExpandedInsight(
                                            "leaf", "leaf", InsightTier.LEAF),
                                    new InsightTreeExpander.ExpandedInsight(
                                            "root", "root", InsightTier.ROOT),
                                    new InsightTreeExpander.ExpandedInsight(
                                            "branch", "branch", InsightTier.BRANCH)));
            when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(insightResult));
            when(itemRetriever.searchByVector(any(), any()))
                    .thenReturn(Mono.just(TierResult.empty()));
            when(textSearch.search(any(), anyString(), anyInt(), any()))
                    .thenReturn(Mono.just(List.of()));

            StepVerifier.create(strategy.retrieve(context, CONFIG))
                    .assertNext(
                            result ->
                                    assertThat(result.insights())
                                            .extracting(
                                                    com.openmemind.ai.memory.core.retrieval
                                                                    .RetrievalResult.InsightResult
                                                            ::id)
                                            .containsExactly("root", "branch", "leaf"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Item retrieval failure still returns insight results")
        void itemFailuresStillReturnInsightResults() {
            when(insightRetriever.retrieve(any(), any()))
                    .thenReturn(
                            Mono.just(
                                    new TierResult(
                                            List.of(
                                                    new ScoredResult(
                                                            ScoredResult.SourceType.INSIGHT,
                                                            "i1",
                                                            "insight",
                                                            1f,
                                                            1.0)),
                                            List.of())));
            when(itemRetriever.searchByVector(any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("vector failed")));
            when(textSearch.search(any(), anyString(), anyInt(), any()))
                    .thenReturn(Mono.just(List.of()));

            StepVerifier.create(strategy.retrieve(context, CONFIG))
                    .assertNext(
                            result -> {
                                assertThat(result.insights()).hasSize(1);
                                assertThat(result.items()).isEmpty();
                            })
                    .verifyComplete();
        }
    }
}
