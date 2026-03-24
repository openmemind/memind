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
package com.openmemind.ai.memory.core.retrieval.tier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
@DisplayName("InsightTierRetriever Unit Test")
class InsightTierRetrieverTest {

    @Mock private MemoryStore memoryStore;
    @Mock private InsightOperations insightOperations;
    @Mock private MemoryVector memoryVector;
    @Mock private InsightTypeRouter router;

    private final MemoryId memoryId = DefaultMemoryId.of("user1", "agent1");

    private InsightTierRetriever retriever;
    private RetrievalConfig config;

    @BeforeEach
    void setUp() {
        lenient().when(memoryStore.insightOperations()).thenReturn(insightOperations);
        retriever = new InsightTierRetriever(memoryStore, memoryVector, router);
        config =
                RetrievalConfig.deep()
                        .withTier1(RetrievalConfig.TierConfig.enabled(5))
                        .withTier2(RetrievalConfig.TierConfig.enabled(10))
                        .withTier3(RetrievalConfig.TierConfig.enabled(5))
                        .withTimeout(Duration.ofSeconds(30))
                        .withoutCache();
    }

    private MemoryInsight buildInsight(
            Long id,
            String type,
            String summary,
            InsightTier tier,
            Long parentInsightId,
            List<Long> childInsightIds) {
        return new MemoryInsight(
                id,
                memoryId.toIdentifier(),
                type,
                MemoryScope.USER,
                type,
                List.of(),
                List.of(new InsightPoint(InsightPoint.PointType.SUMMARY, summary, 1.0f, List.of())),
                null,
                0.8f,
                Instant.now(),
                null,
                Instant.now(),
                Instant.now(),
                tier,
                parentInsightId,
                childInsightIds,
                1);
    }

    private MemoryInsightType buildType(String name, String description, InsightAnalysisMode mode) {
        return buildType(name, description, mode, MemoryScope.USER);
    }

    private MemoryInsightType buildType(
            String name, String description, InsightAnalysisMode mode, MemoryScope scope) {
        return new MemoryInsightType(
                1L,
                name,
                description,
                null,
                List.of(),
                600,
                null,
                null,
                null,
                null,
                mode,
                null,
                scope,
                null);
    }

    @Nested
    @DisplayName("Type Routing Test")
    class TypeRoutingTests {

        @Test
        @DisplayName("ROOT insight should always be included, not routed through router")
        void shouldAlwaysIncludeRootInsights() {
            var rootInsight =
                    buildInsight(
                            100L,
                            "profile",
                            "User portrait content",
                            InsightTier.ROOT,
                            null,
                            List.of());
            var branchInsight =
                    buildInsight(
                            10L,
                            "identity",
                            "Personal information",
                            InsightTier.BRANCH,
                            null,
                            List.of());

            when(insightOperations.listInsights(memoryId))
                    .thenReturn(List.of(rootInsight, branchInsight));
            when(insightOperations.listInsightTypes())
                    .thenReturn(
                            List.of(
                                    buildType("profile", "User portrait", InsightAnalysisMode.ROOT),
                                    buildType(
                                            "identity",
                                            "Personal information",
                                            InsightAnalysisMode.BRANCH)));
            when(router.route(anyString(), anyList(), anyMap()))
                    .thenReturn(Mono.just(List.of("identity")));

            var context =
                    new QueryContext(memoryId, "Query", "Query", List.of(), Map.of(), null, null);

            StepVerifier.create(retriever.retrieve(context, config))
                    .assertNext(
                            result -> {
                                // ROOT insight should always be in the result
                                assertThat(result.results())
                                        .anyMatch(r -> r.sourceId().equals("100"));
                                // BRANCH insight should also be in the result (selected by router)
                                assertThat(result.results())
                                        .anyMatch(r -> r.sourceId().equals("10"));
                                // All scores should be 1.0
                                assertThat(result.results()).allMatch(r -> r.finalScore() == 1.0);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("When router returns an empty list, only return ROOT insight")
        void shouldReturnOnlyRootWhenRouterReturnsEmpty() {
            var rootInsight =
                    buildInsight(
                            100L,
                            "profile",
                            "User portrait content",
                            InsightTier.ROOT,
                            null,
                            List.of());
            var branchInsight =
                    buildInsight(
                            10L,
                            "identity",
                            "Personal information",
                            InsightTier.BRANCH,
                            null,
                            List.of());

            when(insightOperations.listInsights(memoryId))
                    .thenReturn(List.of(rootInsight, branchInsight));
            when(insightOperations.listInsightTypes())
                    .thenReturn(
                            List.of(
                                    buildType("profile", "User portrait", InsightAnalysisMode.ROOT),
                                    buildType(
                                            "identity",
                                            "Personal information",
                                            InsightAnalysisMode.BRANCH)));
            when(router.route(anyString(), anyList(), anyMap())).thenReturn(Mono.just(List.of()));

            var context =
                    new QueryContext(
                            memoryId,
                            "Fuzzy query",
                            "Fuzzy query",
                            List.of(),
                            Map.of(),
                            null,
                            null);

            StepVerifier.create(retriever.retrieve(context, config))
                    .assertNext(
                            result -> {
                                assertThat(result.results()).hasSize(1);
                                assertThat(result.results().getFirst().sourceId()).isEqualTo("100");
                                assertThat(result.expandedInsights()).isEmpty();
                            })
                    .verifyComplete();

            // Should not call embed (no LEAF to sort)
            verify(memoryVector, never()).embed(any());
        }

        @Test
        @DisplayName(
                "The type selected by the router should include the corresponding BRANCH insight")
        void shouldIncludeMatchedBranchInsights() {
            var branchProfile =
                    buildInsight(
                            10L,
                            "identity",
                            "Personal information",
                            InsightTier.BRANCH,
                            null,
                            List.of());
            var branchPrefs =
                    buildInsight(
                            20L,
                            "preferences",
                            "Preference information",
                            InsightTier.BRANCH,
                            null,
                            List.of());

            when(insightOperations.listInsights(memoryId))
                    .thenReturn(List.of(branchProfile, branchPrefs));
            when(insightOperations.listInsightTypes())
                    .thenReturn(
                            List.of(
                                    buildType(
                                            "identity",
                                            "Personal information",
                                            InsightAnalysisMode.BRANCH),
                                    buildType(
                                            "preferences",
                                            "Preference information",
                                            InsightAnalysisMode.BRANCH)));
            // Only select identity
            when(router.route(anyString(), anyList(), anyMap()))
                    .thenReturn(Mono.just(List.of("identity")));

            var context =
                    new QueryContext(memoryId, "Name", "Name", List.of(), Map.of(), null, null);

            StepVerifier.create(retriever.retrieve(context, config))
                    .assertNext(
                            result -> {
                                assertThat(result.results()).hasSize(1);
                                assertThat(result.results().getFirst().sourceId()).isEqualTo("10");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fallback to return empty result on router failure")
        void shouldFallbackOnRouterFailure() {
            var branchInsight =
                    buildInsight(
                            10L,
                            "identity",
                            "Personal information",
                            InsightTier.BRANCH,
                            null,
                            List.of());

            when(insightOperations.listInsights(memoryId)).thenReturn(List.of(branchInsight));
            when(insightOperations.listInsightTypes())
                    .thenReturn(
                            List.of(
                                    buildType(
                                            "identity",
                                            "Personal information",
                                            InsightAnalysisMode.BRANCH)));
            when(router.route(anyString(), anyList(), anyMap()))
                    .thenReturn(Mono.error(new RuntimeException("LLM call failed")));

            var context =
                    new QueryContext(memoryId, "Query", "Query", List.of(), Map.of(), null, null);

            StepVerifier.create(retriever.retrieve(context, config))
                    .assertNext(result -> assertThat(result.results()).isEmpty())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("LEAF Expansion Test")
    class LeafExpansionTests {

        @Test
        @DisplayName(
                "When BRANCH is selected and has child LEAFs, it should expand and sort by cosine")
        void shouldExpandLeafsWithCosineSorting() {
            var branch =
                    buildInsight(
                            10L,
                            "identity",
                            "Personal information",
                            InsightTier.BRANCH,
                            null,
                            List.of(1L, 2L));
            var leaf1 =
                    buildInsight(1L, "identity", "Likes coffee", InsightTier.LEAF, 10L, List.of());
            var leaf2 = buildInsight(2L, "identity", "Likes tea", InsightTier.LEAF, 10L, List.of());

            when(insightOperations.listInsights(memoryId))
                    .thenReturn(List.of(branch, leaf1, leaf2));
            when(insightOperations.listInsightTypes())
                    .thenReturn(
                            List.of(
                                    buildType(
                                            "identity",
                                            "Personal information",
                                            InsightAnalysisMode.BRANCH)));
            when(router.route(anyString(), anyList(), anyMap()))
                    .thenReturn(Mono.just(List.of("identity")));

            // Query embedding is more similar to leaf1
            List<Float> queryEmb = List.of(1.0f, 0.0f, 0.0f);
            List<Float> leaf1Emb = List.of(0.9f, 0.1f, 0.0f);
            List<Float> leaf2Emb = List.of(0.0f, 0.0f, 1.0f);

            when(memoryVector.embed(any())).thenReturn(Mono.just(queryEmb));
            when(memoryVector.embedAll(any())).thenReturn(Mono.just(List.of(leaf1Emb, leaf2Emb)));

            // Enable LEAF expansion
            var treeRetriever = new InsightTierRetriever(memoryStore, memoryVector, router, 2);
            var context =
                    new QueryContext(memoryId, "Coffee", "Coffee", List.of(), Map.of(), null, null);

            StepVerifier.create(treeRetriever.retrieve(context, config))
                    .assertNext(
                            result -> {
                                // BRANCH should be in results
                                assertThat(result.results())
                                        .anyMatch(r -> r.sourceId().equals("10"));

                                // LEAFs should be in expandedInsights
                                assertThat(result.expandedInsights()).hasSize(2);
                                // leaf1 should be first (higher cosine similarity)
                                assertThat(result.expandedInsights().getFirst().id())
                                        .isEqualTo("1");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("When maxExpandedLeafsPerBranch=0, LEAF should not expand")
        void shouldNotExpandWhenMaxLeafsIsZero() {
            var branch =
                    buildInsight(
                            10L,
                            "identity",
                            "Personal information",
                            InsightTier.BRANCH,
                            null,
                            List.of(1L));
            var leaf =
                    buildInsight(1L, "identity", "Likes coffee", InsightTier.LEAF, 10L, List.of());

            when(insightOperations.listInsights(memoryId)).thenReturn(List.of(branch, leaf));
            when(insightOperations.listInsightTypes())
                    .thenReturn(
                            List.of(
                                    buildType(
                                            "identity",
                                            "Personal information",
                                            InsightAnalysisMode.BRANCH)));
            when(router.route(anyString(), anyList(), anyMap()))
                    .thenReturn(Mono.just(List.of("identity")));

            var context =
                    new QueryContext(memoryId, "Coffee", "Coffee", List.of(), Map.of(), null, null);

            StepVerifier.create(retriever.retrieve(context, config))
                    .assertNext(
                            result -> {
                                assertThat(result.results()).hasSize(1);
                                assertThat(result.expandedInsights()).isEmpty();
                            })
                    .verifyComplete();

            // Should not call embed (no expansion needed)
            verify(memoryVector, never()).embed(any());
        }

        @Test
        @DisplayName("maxExpandedLeafsPerBranch limits the number of expansions")
        void shouldRespectMaxExpandedLeafsPerBranch() {
            var branch =
                    buildInsight(
                            10L,
                            "identity",
                            "Personal information",
                            InsightTier.BRANCH,
                            null,
                            List.of(1L, 2L, 3L));
            var leaf1 = buildInsight(1L, "identity", "Coffee", InsightTier.LEAF, 10L, List.of());
            var leaf2 = buildInsight(2L, "identity", "Tea", InsightTier.LEAF, 10L, List.of());
            var leaf3 = buildInsight(3L, "identity", "Cola", InsightTier.LEAF, 10L, List.of());

            when(insightOperations.listInsights(memoryId))
                    .thenReturn(List.of(branch, leaf1, leaf2, leaf3));
            when(insightOperations.listInsightTypes())
                    .thenReturn(
                            List.of(
                                    buildType(
                                            "identity",
                                            "Personal information",
                                            InsightAnalysisMode.BRANCH)));
            when(router.route(anyString(), anyList(), anyMap()))
                    .thenReturn(Mono.just(List.of("identity")));

            List<Float> queryEmb = List.of(1.0f, 0.0f, 0.0f);
            when(memoryVector.embed(any())).thenReturn(Mono.just(queryEmb));
            when(memoryVector.embedAll(any()))
                    .thenReturn(
                            Mono.just(
                                    List.of(
                                            List.of(0.9f, 0.1f, 0.0f),
                                            List.of(0.5f, 0.5f, 0.0f),
                                            List.of(0.0f, 0.0f, 1.0f))));

            // maxExpandedLeafsPerBranch=1 only takes the most similar 1
            var treeRetriever = new InsightTierRetriever(memoryStore, memoryVector, router, 1);
            var context =
                    new QueryContext(memoryId, "Coffee", "Coffee", List.of(), Map.of(), null, null);

            StepVerifier.create(treeRetriever.retrieve(context, config))
                    .assertNext(
                            result -> {
                                assertThat(result.expandedInsights()).hasSize(1);
                                assertThat(result.expandedInsights().getFirst().id())
                                        .isEqualTo("1");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should reuse stored leaf summary embeddings before calling embedAll")
        void shouldReuseStoredLeafSummaryEmbeddings() {
            var branch =
                    buildInsight(
                            10L,
                            "identity",
                            "Personal information",
                            InsightTier.BRANCH,
                            null,
                            List.of(1L, 2L));
            var leaf1 =
                    buildInsight(1L, "identity", "Likes coffee", InsightTier.LEAF, 10L, List.of())
                            .withSummaryEmbedding(List.of(0.9f, 0.1f, 0.0f));
            var leaf2 =
                    buildInsight(2L, "identity", "Likes tea", InsightTier.LEAF, 10L, List.of())
                            .withSummaryEmbedding(List.of(0.0f, 0.0f, 1.0f));

            when(insightOperations.listInsights(memoryId))
                    .thenReturn(List.of(branch, leaf1, leaf2));
            when(insightOperations.listInsightTypes())
                    .thenReturn(
                            List.of(
                                    buildType(
                                            "identity",
                                            "Personal information",
                                            InsightAnalysisMode.BRANCH)));
            when(router.route(anyString(), anyList(), anyMap()))
                    .thenReturn(Mono.just(List.of("identity")));
            when(memoryVector.embed(any())).thenReturn(Mono.just(List.of(1.0f, 0.0f, 0.0f)));

            var treeRetriever = new InsightTierRetriever(memoryStore, memoryVector, router, 2);
            var context =
                    new QueryContext(memoryId, "Coffee", "Coffee", List.of(), Map.of(), null, null);

            StepVerifier.create(treeRetriever.retrieve(context, config))
                    .assertNext(
                            result -> {
                                assertThat(result.expandedInsights()).hasSize(2);
                                assertThat(result.expandedInsights().getFirst().id())
                                        .isEqualTo("1");
                            })
                    .verifyComplete();

            verify(memoryVector, never()).embedAll(any());
        }
    }

    @Nested
    @DisplayName("Caffeine Cache Test")
    class CacheTests {

        @Test
        @DisplayName("Consecutive two retrieves should only call getAllInsights once")
        void shouldCacheInsightsOnSecondCall() {
            var rootInsight =
                    buildInsight(
                            100L, "profile", "User portrait", InsightTier.ROOT, null, List.of());

            when(insightOperations.listInsights(memoryId)).thenReturn(List.of(rootInsight));
            when(insightOperations.listInsightTypes())
                    .thenReturn(
                            List.of(
                                    buildType(
                                            "profile", "User portrait", InsightAnalysisMode.ROOT)));

            var context =
                    new QueryContext(memoryId, "Query", "Query", List.of(), Map.of(), null, null);

            StepVerifier.create(retriever.retrieve(context, config))
                    .assertNext(result -> assertThat(result.results()).isNotEmpty())
                    .verifyComplete();

            StepVerifier.create(retriever.retrieve(context, config))
                    .assertNext(result -> assertThat(result.results()).isNotEmpty())
                    .verifyComplete();

            verify(insightOperations, times(1)).listInsights(memoryId);
        }

        @Test
        @DisplayName("After invalidateCache, should re-query")
        void shouldReQueryAfterCacheInvalidation() {
            var rootInsight =
                    buildInsight(
                            100L, "profile", "User portrait", InsightTier.ROOT, null, List.of());

            when(insightOperations.listInsights(memoryId)).thenReturn(List.of(rootInsight));
            when(insightOperations.listInsightTypes())
                    .thenReturn(
                            List.of(
                                    buildType(
                                            "profile", "User portrait", InsightAnalysisMode.ROOT)));

            var context =
                    new QueryContext(memoryId, "Query", "Query", List.of(), Map.of(), null, null);

            StepVerifier.create(retriever.retrieve(context, config))
                    .assertNext(result -> assertThat(result.results()).isNotEmpty())
                    .verifyComplete();

            retriever.invalidateCache(memoryId);

            StepVerifier.create(retriever.retrieve(context, config))
                    .assertNext(result -> assertThat(result.results()).isNotEmpty())
                    .verifyComplete();

            verify(insightOperations, times(2)).listInsights(memoryId);
        }

        @Test
        @DisplayName("invalidateCache(null) should not throw an exception")
        void shouldHandleNullMemoryIdGracefully() {
            retriever.invalidateCache(null);
        }
    }

    @Nested
    @DisplayName("Boundary Condition Test")
    class EdgeCaseTests {

        @Test
        @DisplayName("When tier1 is disabled, should return empty result")
        void shouldReturnEmptyWhenTier1Disabled() {
            var disabledConfig =
                    RetrievalConfig.deep().withTier1(RetrievalConfig.TierConfig.disabled());

            var context =
                    new QueryContext(memoryId, "Query", "Query", List.of(), Map.of(), null, null);

            StepVerifier.create(retriever.retrieve(context, disabledConfig))
                    .assertNext(result -> assertThat(result.results()).isEmpty())
                    .verifyComplete();
        }

        @Test
        @DisplayName("When there are no insights, should return empty result")
        void shouldReturnEmptyWhenNoInsights() {
            when(insightOperations.listInsights(memoryId)).thenReturn(List.of());

            var context =
                    new QueryContext(memoryId, "Query", "Query", List.of(), Map.of(), null, null);

            StepVerifier.create(retriever.retrieve(context, config))
                    .assertNext(result -> assertThat(result.results()).isEmpty())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Scope/Category Filter Test")
    class ScopeCategoryFilterTests {

        @Test
        @DisplayName("When context.scope=USER, AGENT scope's insight type should be filtered out")
        void shouldFilterOutAgentScopeInsightWhenContextScopeIsUser() {
            var agentInsight =
                    buildInsight(
                            10L,
                            "procedural",
                            "Procedural memory",
                            InsightTier.BRANCH,
                            null,
                            List.of());
            var userInsight =
                    buildInsight(
                            20L,
                            "identity",
                            "User information",
                            InsightTier.BRANCH,
                            null,
                            List.of());

            when(insightOperations.listInsights(memoryId))
                    .thenReturn(List.of(agentInsight, userInsight));
            when(insightOperations.listInsightTypes())
                    .thenReturn(
                            List.of(
                                    buildType(
                                            "procedural",
                                            "Procedural memory",
                                            InsightAnalysisMode.BRANCH,
                                            MemoryScope.AGENT),
                                    buildType(
                                            "identity",
                                            "User information",
                                            InsightAnalysisMode.BRANCH,
                                            MemoryScope.USER)));
            when(router.route(anyString(), anyList(), anyMap()))
                    .thenReturn(Mono.just(List.of("identity")));

            var context =
                    new QueryContext(
                            memoryId,
                            "Query",
                            "Query",
                            List.of(),
                            Map.of(),
                            MemoryScope.USER,
                            null);

            StepVerifier.create(retriever.retrieve(context, config))
                    .assertNext(
                            result -> {
                                assertThat(result.results()).hasSize(1);
                                assertThat(result.results().getFirst().sourceId()).isEqualTo("20");
                            })
                    .verifyComplete();
        }
    }
}
