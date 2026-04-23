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
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistResult;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphSettings;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyResult;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistResult;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistant;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.TierResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeepRetrievalStrategy MemoryThread Assist Rollout Gate")
class DeepRetrievalStrategyMemoryThreadAssistTest {

    private static final MemoryId MEMORY_ID = TestMemoryIds.userAgent();

    @Mock private InsightTierRetriever insightRetriever;
    @Mock private ItemTierRetriever itemRetriever;
    @Mock private SufficiencyGate sufficiencyGate;
    @Mock private TypedQueryExpander typedQueryExpander;
    @Mock private Reranker reranker;
    @Mock private MemoryTextSearch textSearch;
    @Mock private MemoryStore memoryStore;
    @Mock private ItemOperations itemOperations;
    @Mock private RetrievalGraphAssistant graphAssistant;
    @Mock private MemoryThreadAssistant memoryThreadAssistant;

    private DeepRetrievalStrategy strategy;
    private QueryContext context;

    @BeforeEach
    void setUp() {
        lenient().when(itemRetriever.textSearch()).thenReturn(textSearch);
        lenient().when(memoryStore.itemOperations()).thenReturn(itemOperations);
        lenient().when(itemOperations.getItemsByIds(any(), any())).thenReturn(List.of());
        lenient()
                .when(textSearch.search(any(), anyString(), anyInt(), any()))
                .thenReturn(Mono.just(List.of()));
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
                                                invocation.getArgument(3), true)));

        strategy =
                new DeepRetrievalStrategy(
                        insightRetriever,
                        itemRetriever,
                        sufficiencyGate,
                        typedQueryExpander,
                        reranker,
                        memoryStore,
                        graphAssistant,
                        memoryThreadAssistant);
        context =
                new QueryContext(MEMORY_ID, "what changed", null, List.of(), Map.of(), null, null);
    }

    @Test
    @DisplayName("preserves the bounded direct window after assist dedup fills the tail")
    void deepStrategyPreservesWindowSizeAfterAssistAndDedup() {
        var config =
                RetrievalConfig.deep(
                                new DeepStrategyConfig(
                                        DeepStrategyConfig.defaults().queryExpansion(),
                                        DeepStrategyConfig.defaults().sufficiency(),
                                        50,
                                        50,
                                        0.3d,
                                        DeepStrategyConfig.GraphAssistConfig.defaults()
                                                .withEnabled(false),
                                        new DeepStrategyConfig.MemoryThreadAssistConfig(
                                                true, 1, 3, 2, Duration.ofMillis(150))))
                        .withTier2(new RetrievalConfig.TierConfig(true, 4, 0.2d, null));
        when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(TierResult.empty()));
        when(itemRetriever.searchByVector(any(), any()))
                .thenReturn(
                        Mono.just(
                                new TierResult(
                                        List.of(
                                                scored("101", 0.95d),
                                                scored("102", 0.90d),
                                                scored("103", 0.85d),
                                                scored("104", 0.80d)),
                                        List.of())));
        when(textSearch.search(
                        any(), anyString(), anyInt(), eq(MemoryTextSearch.SearchTarget.ITEM)))
                .thenReturn(Mono.just(List.of()));
        when(sufficiencyGate.check(any(), any()))
                .thenReturn(
                        Mono.just(
                                new SufficiencyResult(
                                        false,
                                        "need more",
                                        List.of(),
                                        List.of("timeline"),
                                        List.of("project rollout"))));
        when(typedQueryExpander.expand(anyString(), any(), any(), any(), anyInt()))
                .thenReturn(Mono.just(List.of()));
        when(memoryThreadAssistant.assist(any(), any(), any(), any()))
                .thenReturn(
                        Mono.just(
                                new MemoryThreadAssistResult(
                                        List.of(
                                                scored("101", 0.95d),
                                                scored("102", 0.90d),
                                                scored("102", 0.89d),
                                                scored("201", 0.88d),
                                                scored("103", 0.87d)),
                                        MemoryThreadAssistResult.Stats.success(
                                                1, 1, 2, false))));
        when(reranker.rerank(anyString(), any(), anyInt()))
                .thenAnswer(
                        invocation -> {
                            assertThat(invocation.<List<ScoredResult>>getArgument(1))
                                    .extracting(ScoredResult::sourceId)
                                    .containsExactly("101", "102", "201", "103");
                            return Mono.just(invocation.getArgument(1));
                        });

        StepVerifier.create(strategy.retrieve(context, config))
                .assertNext(
                        result ->
                                assertThat(result.items())
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("101", "102", "201", "103"))
                .verifyComplete();
    }

    @Test
    @DisplayName("falls back to the direct window when thread assist fails on the slow path")
    void strategyFallsBackToDirectWindowOnAssistFailure() {
        var config =
                RetrievalConfig.deep(
                                new DeepStrategyConfig(
                                        DeepStrategyConfig.defaults().queryExpansion(),
                                        DeepStrategyConfig.defaults().sufficiency(),
                                        50,
                                        50,
                                        0.3d,
                                        DeepStrategyConfig.GraphAssistConfig.defaults()
                                                .withEnabled(false),
                                        new DeepStrategyConfig.MemoryThreadAssistConfig(
                                                true, 1, 3, 1, Duration.ofMillis(150))))
                        .withTier2(new RetrievalConfig.TierConfig(true, 3, 0.2d, null));
        when(insightRetriever.retrieve(any(), any())).thenReturn(Mono.just(TierResult.empty()));
        when(itemRetriever.searchByVector(any(), any()))
                .thenReturn(
                        Mono.just(
                                new TierResult(
                                        List.of(
                                                scored("101", 0.95d),
                                                scored("102", 0.90d),
                                                scored("103", 0.85d)),
                                        List.of())));
        when(textSearch.search(
                        any(), anyString(), anyInt(), eq(MemoryTextSearch.SearchTarget.ITEM)))
                .thenReturn(Mono.just(List.of()));
        when(sufficiencyGate.check(any(), any()))
                .thenReturn(
                        Mono.just(
                                new SufficiencyResult(
                                        false,
                                        "need more",
                                        List.of(),
                                        List.of("timeline"),
                                        List.of("project rollout"))));
        when(typedQueryExpander.expand(anyString(), any(), any(), any(), anyInt()))
                .thenReturn(Mono.just(List.of()));
        when(memoryThreadAssistant.assist(any(), any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("assist failed")));
        when(reranker.rerank(anyString(), any(), anyInt()))
                .thenAnswer(
                        invocation -> {
                            assertThat(invocation.<List<ScoredResult>>getArgument(1))
                                    .extracting(ScoredResult::sourceId)
                                    .containsExactly("101", "102", "103");
                            return Mono.just(invocation.getArgument(1));
                        });

        StepVerifier.create(strategy.retrieve(context, config))
                .assertNext(
                        result ->
                                assertThat(result.items())
                                        .extracting(ScoredResult::sourceId)
                                        .containsExactly("101", "102", "103"))
                .verifyComplete();
    }

    private static ScoredResult scored(String sourceId, double score) {
        return new ScoredResult(
                ScoredResult.SourceType.ITEM, sourceId, "item-" + sourceId, 0.8f, score);
    }
}
