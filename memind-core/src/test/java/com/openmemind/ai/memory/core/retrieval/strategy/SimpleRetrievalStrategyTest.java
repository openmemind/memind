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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistResult;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphSettings;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistResult;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistant;
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
import java.time.Instant;
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
    @Mock private RetrievalGraphAssistant graphAssistant;
    @Mock private MemoryThreadAssistant memoryThreadAssistant;

    private SimpleRetrievalStrategy strategy;
    private QueryContext context;

    @BeforeEach
    void setUp() {
        lenient().when(memoryStore.itemOperations()).thenReturn(itemOperations);
        lenient().when(memoryStore.rawDataOperations()).thenReturn(rawDataOperations);
        lenient().when(itemOperations.getItemsByIds(any(), any())).thenReturn(List.of());
        lenient().when(rawDataOperations.getRawData(any(), any())).thenReturn(Optional.empty());
        lenient()
                .when(graphAssistant.assist(any(), any(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            var graphSettings = invocation.<RetrievalGraphSettings>getArgument(2);
                            boolean enabled = graphSettings != null && graphSettings.enabled();
                            return Mono.just(
                                    RetrievalGraphAssistResult.directOnly(
                                            invocation.getArgument(3), enabled));
                        });
        lenient()
                .when(memoryThreadAssistant.assist(any(), any(), any(), any()))
                .thenAnswer(
                        invocation ->
                                Mono.just(
                                        MemoryThreadAssistResult.directOnly(
                                                invocation.getArgument(3), false)));
        strategy =
                new SimpleRetrievalStrategy(
                        insightRetriever,
                        itemRetriever,
                        textSearch,
                        memoryStore,
                        graphAssistant,
                        memoryThreadAssistant);
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
                    new SimpleRetrievalStrategy(
                            insightRetriever, itemRetriever, null, memoryStore, graphAssistant);

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

        @Test
        @DisplayName("Keyword search flag skips BM25 even when textSearch bean exists")
        void keywordSearchFlagSkipsBm25EvenWhenTextSearchBeanExists() {
            var config =
                    RetrievalConfig.simple(
                            SimpleStrategyConfig.defaults().withKeywordSearch(false));
            when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(TierResult.empty()));
            when(itemRetriever.searchByVector(any(), any()))
                    .thenReturn(
                            Mono.just(
                                    new TierResult(
                                            List.of(
                                                    new ScoredResult(
                                                            ScoredResult.SourceType.ITEM,
                                                            "101",
                                                            "vector-only",
                                                            0.85f,
                                                            0.85)),
                                            List.of())));

            StepVerifier.create(strategy.retrieve(context, config))
                    .assertNext(result -> assertThat(result.items()).hasSize(1))
                    .verifyComplete();

            verifyNoInteractions(textSearch);
        }

        @Test
        @DisplayName("Graph assist runs before rawData aggregation and keeps pinned prefix stable")
        void graphAssistRunsBeforeRawDataAggregationAndKeepsPinnedPrefixStable() {
            var graphConfig =
                    SimpleStrategyConfig.defaults()
                            .withGraphAssist(
                                    SimpleStrategyConfig.GraphAssistConfig.defaults()
                                            .withEnabled(true));
            var config = RetrievalConfig.simple(graphConfig);
            var graphAssistantResult =
                    new RetrievalGraphAssistResult(
                            List.of(scored("101", 1.0d), scored("102", 0.9d), scored("201", 0.8d)),
                            new RetrievalGraphAssistResult.GraphAssistStats(
                                    true, false, false, 2, 1, 0, 1, 1, 0, 0, 0));
            when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(TierResult.empty()));
            when(itemRetriever.searchByVector(any(), any()))
                    .thenReturn(
                            Mono.just(
                                    new TierResult(
                                            List.of(scored("101", 1.0d), scored("102", 0.9d)),
                                            List.of())));
            when(textSearch.search(
                            any(), anyString(), anyInt(), eq(MemoryTextSearch.SearchTarget.ITEM)))
                    .thenReturn(Mono.just(List.of()));
            when(graphAssistant.assist(any(), any(), any(), any()))
                    .thenReturn(Mono.just(graphAssistantResult));

            StepVerifier.create(strategy.retrieve(context, config))
                    .assertNext(
                            result ->
                                    assertThat(result.items())
                                            .extracting(ScoredResult::sourceId)
                                            .startsWith("101", "102")
                                            .contains("201"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Graph assist receives direct items resorted after BM25 time decay")
        void graphAssistReceivesDirectItemsResortedAfterBm25TimeDecay() {
            var graphConfig =
                    SimpleStrategyConfig.defaults()
                            .withGraphAssist(
                                    SimpleStrategyConfig.GraphAssistConfig.defaults()
                                            .withEnabled(true));
            var config = RetrievalConfig.simple(graphConfig);

            when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(TierResult.empty()));
            when(itemRetriever.searchByVector(any(), any()))
                    .thenReturn(Mono.just(TierResult.empty()));
            when(textSearch.search(
                            any(), anyString(), anyInt(), eq(MemoryTextSearch.SearchTarget.ITEM)))
                    .thenReturn(
                            Mono.just(
                                    List.of(
                                            new TextSearchResult("101", "older", 9.0d),
                                            new TextSearchResult("102", "newer", 8.5d))));
            when(itemOperations.getItemsByIds(any(), any()))
                    .thenReturn(
                            List.of(
                                    item("101", Instant.parse("2024-01-01T00:00:00Z")),
                                    item("102", Instant.parse("2026-04-16T00:00:00Z"))));
            when(graphAssistant.assist(any(), any(), any(), any()))
                    .thenAnswer(
                            invocation -> {
                                assertThat(invocation.<List<ScoredResult>>getArgument(3))
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("102", "101");
                                return Mono.just(
                                        RetrievalGraphAssistResult.directOnly(
                                                invocation.getArgument(3), true));
                            });

            StepVerifier.create(strategy.retrieve(context, config))
                    .assertNext(
                            result ->
                                    assertThat(result.items())
                                            .extracting(ScoredResult::sourceId)
                                            .containsExactly("102", "101"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Graph assist failure degrades to direct retrieval")
        void graphAssistFailureDegradesToDirectRetrieval() {
            when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(TierResult.empty()));
            when(itemRetriever.searchByVector(any(), any()))
                    .thenReturn(
                            Mono.just(
                                    new TierResult(
                                            List.of(scored("101", 1.0d), scored("102", 0.9d)),
                                            List.of())));
            when(textSearch.search(
                            any(), anyString(), anyInt(), eq(MemoryTextSearch.SearchTarget.ITEM)))
                    .thenReturn(Mono.just(List.of()));
            when(graphAssistant.assist(any(), any(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("graph failed")));

            StepVerifier.create(
                            strategy.retrieve(
                                    context,
                                    RetrievalConfig.simple(
                                            SimpleStrategyConfig.defaults()
                                                    .withGraphAssist(
                                                            SimpleStrategyConfig.GraphAssistConfig
                                                                    .defaults()
                                                                    .withEnabled(true)))))
                    .assertNext(
                            result ->
                                    assertThat(result.items())
                                            .extracting(ScoredResult::sourceId)
                                            .containsExactly("101", "102"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Disabled graph assist leaves direct result ordering unchanged")
        void disabledGraphAssistLeavesDirectResultOrderingUnchanged() {
            when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(TierResult.empty()));
            when(itemRetriever.searchByVector(any(), any()))
                    .thenReturn(
                            Mono.just(
                                    new TierResult(
                                            List.of(scored("101", 1.0d), scored("102", 0.9d)),
                                            List.of())));
            when(textSearch.search(
                            any(), anyString(), anyInt(), eq(MemoryTextSearch.SearchTarget.ITEM)))
                    .thenReturn(Mono.just(List.of()));

            StepVerifier.create(
                            strategy.retrieve(
                                    context,
                                    RetrievalConfig.simple(SimpleStrategyConfig.defaults())))
                    .assertNext(
                            result ->
                                    assertThat(result.items())
                                            .extracting(ScoredResult::sourceId)
                                            .containsExactly("101", "102"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("memory thread assist replaces only the unpinned tail before graph assist")
        void memoryThreadAssistReplacesOnlyTheUnpinnedTailBeforeGraphAssist() {
            var config =
                    RetrievalConfig.simple(
                                    new SimpleStrategyConfig(
                                            true,
                                            SimpleStrategyConfig.GraphAssistConfig.defaults()
                                                    .withEnabled(false),
                                            new SimpleStrategyConfig.MemoryThreadAssistConfig(
                                                    true,
                                                    1,
                                                    2,
                                                    2,
                                                    java.time.Duration.ofMillis(150))))
                            .withTier2(
                                    new RetrievalConfig.TierConfig(
                                            true,
                                            4,
                                            RetrievalConfig.simple().tier2().minScore(),
                                            RetrievalConfig.simple().tier2().truncation()));
            when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(TierResult.empty()));
            when(itemRetriever.searchByVector(any(), any()))
                    .thenReturn(
                            Mono.just(
                                    new TierResult(
                                            List.of(
                                                    scored("101", 0.95d),
                                                    scored("102", 0.90d),
                                                    scored("103", 0.80d),
                                                    scored("104", 0.75d)),
                                            List.of())));
            when(textSearch.search(any(), anyString(), anyInt(), any()))
                    .thenReturn(Mono.just(List.of()));
            when(memoryThreadAssistant.assist(any(), any(), any(), any()))
                    .thenReturn(
                            Mono.just(
                                    new MemoryThreadAssistResult(
                                            List.of(
                                                    scored("101", 0.95d),
                                                    scored("102", 0.90d),
                                                    scored("201", 0.81d),
                                                    scored("202", 0.79d)),
                                            MemoryThreadAssistResult.Stats.success(
                                                    1, 2, 2, false))));

            StepVerifier.create(strategy.retrieve(context, config))
                    .assertNext(
                            result ->
                                    assertThat(result.items())
                                            .extracting(ScoredResult::sourceId)
                                            .containsExactly("101", "102", "201", "202"))
                    .verifyComplete();
        }
    }

    private static ScoredResult scored(String sourceId, double score) {
        return new ScoredResult(
                ScoredResult.SourceType.ITEM, sourceId, "item-" + sourceId, 0.8f, score);
    }

    private static MemoryItem item(String sourceId, Instant occurredAt) {
        return new MemoryItem(
                Long.parseLong(sourceId),
                MEMORY_ID.toIdentifier(),
                "item-" + sourceId,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vector-" + sourceId,
                "raw-" + sourceId,
                "hash-" + sourceId,
                occurredAt,
                occurredAt,
                Map.of(),
                occurredAt,
                MemoryItemType.FACT);
    }
}
