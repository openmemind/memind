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
package com.openmemind.ai.memory.core.retrieval.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DefaultRetrievalGraphAssistantTest {

    private static final MemoryId MEMORY_ID = TestMemoryIds.userAgent();
    private static final RetrievalConfig CONFIG = RetrievalConfig.simple();
    private static final QueryContext CONTEXT =
            new QueryContext(
                    MEMORY_ID, "what changed this week", null, List.of(), Map.of(), null, null);
    private static final Instant NOW = Instant.parse("2026-04-17T00:00:00Z");
    private static final SimpleStrategyConfig SIMPLE_CONFIG =
            new SimpleStrategyConfig(
                    true,
                    new SimpleStrategyConfig.GraphAssistConfig(
                            true,
                            RetrievalGraphMode.ASSIST,
                            2,
                            6,
                            2,
                            2,
                            2,
                            2,
                            2,
                            0.35d,
                            0.55d,
                            0.70f,
                            2,
                            0.5d,
                            Duration.ofMillis(200)));
    private static final SimpleStrategyConfig OVERLAP_CONFIG =
            SIMPLE_CONFIG.withGraphAssist(SIMPLE_CONFIG.graphAssist().withMaxSeedItems(1));
    private static final SimpleStrategyConfig NON_SEMANTIC_MERGE_CONFIG =
            new SimpleStrategyConfig(
                    true,
                    new SimpleStrategyConfig.GraphAssistConfig(
                            SIMPLE_CONFIG.graphAssist().enabled(),
                            RetrievalGraphMode.ASSIST,
                            SIMPLE_CONFIG.graphAssist().maxSeedItems(),
                            SIMPLE_CONFIG.graphAssist().maxExpandedItems(),
                            SIMPLE_CONFIG.graphAssist().maxSemanticNeighborsPerSeed(),
                            SIMPLE_CONFIG.graphAssist().maxTemporalNeighborsPerSeed(),
                            4,
                            SIMPLE_CONFIG.graphAssist().maxEntitySiblingItemsPerSeed(),
                            SIMPLE_CONFIG.graphAssist().maxItemsPerEntity(),
                            SIMPLE_CONFIG.graphAssist().graphChannelWeight(),
                            SIMPLE_CONFIG.graphAssist().minLinkStrength(),
                            SIMPLE_CONFIG.graphAssist().minMentionConfidence(),
                            SIMPLE_CONFIG.graphAssist().protectDirectTopK(),
                            SIMPLE_CONFIG.graphAssist().semanticEvidenceDecayFactor(),
                            SIMPLE_CONFIG.graphAssist().timeout()));

    @Mock private MemoryStore store;
    @Mock private ItemOperations itemOperations;

    private InMemoryGraphOperations graphOperations;
    private DefaultRetrievalGraphAssistant assistant;
    private Map<Long, MemoryItem> itemsById;

    @BeforeEach
    void setUp() {
        graphOperations = new InMemoryGraphOperations();
        itemsById = new ConcurrentHashMap<>();
        seedItems();
        seedGraph();

        lenient().when(store.itemOperations()).thenReturn(itemOperations);
        lenient().when(store.graphOperations()).thenReturn(graphOperations);
        lenient()
                .when(itemOperations.getItemsByIds(eq(MEMORY_ID), any(Collection.class)))
                .thenAnswer(
                        invocation ->
                                invocation.<Collection<Long>>getArgument(1).stream()
                                        .map(itemsById::get)
                                        .filter(java.util.Objects::nonNull)
                                        .toList());

        assistant = new DefaultRetrievalGraphAssistant(store);
    }

    @Test
    void assistModePinsProtectedDirectPrefixAndInterleavesGraphTailOnly() {
        var direct = List.of(scored("101", 1.00d), scored("102", 0.85d), scored("103", 0.75d));
        var assistConfig =
                SIMPLE_CONFIG.withGraphAssist(
                        SIMPLE_CONFIG
                                .graphAssist()
                                .withMode(RetrievalGraphMode.ASSIST)
                                .withProtectDirectTopK(2));

        StepVerifier.create(assistant.assist(CONTEXT, CONFIG, assistConfig.graphAssist(), direct))
                .assertNext(
                        result -> {
                            assertThat(result.items().get(0).sourceId()).isEqualTo("101");
                            assertThat(result.items().get(1).sourceId()).isEqualTo("102");
                            assertThat(result.items())
                                    .extracting(ScoredResult::sourceId)
                                    .contains("201");
                        })
                .verifyComplete();
    }

    @Test
    void expandModeAllowsGraphCandidatesToDisplaceDirectTail() {
        var direct = List.of(scored("101", 1.00d), scored("102", 0.85d), scored("103", 0.75d));
        var expandConfig =
                SIMPLE_CONFIG.withGraphAssist(
                        SIMPLE_CONFIG
                                .graphAssist()
                                .withMode(RetrievalGraphMode.EXPAND)
                                .withProtectDirectTopK(0));

        StepVerifier.create(assistant.assist(CONTEXT, CONFIG, expandConfig.graphAssist(), direct))
                .assertNext(
                        result ->
                                assertThat(result.items())
                                        .extracting(ScoredResult::sourceId)
                                        .contains("201"))
                .verifyComplete();
    }

    @Test
    void overlappingDirectCandidatesAreTrackedButNeverBoosted() {
        StepVerifier.create(
                        assistant.assist(
                                CONTEXT, CONFIG, OVERLAP_CONFIG.graphAssist(), directWithOverlap()))
                .assertNext(
                        result -> {
                            assertThat(result.items().get(0).sourceId()).isEqualTo("101");
                            assertThat(
                                            result.items().stream()
                                                    .filter(item -> item.sourceId().equals("101")))
                                    .hasSize(1);
                            assertThat(result.stats().overlapCount()).isEqualTo(1);
                        })
                .verifyComplete();
    }

    @Test
    void overFanoutEntityIsDroppedWhenReverseMentionReadReturnsLimitPlusOne() {
        StepVerifier.create(
                        assistant.assist(
                                CONTEXT, CONFIG, SIMPLE_CONFIG.graphAssist(), directSeeds()))
                .assertNext(
                        result -> {
                            assertThat(result.items())
                                    .extracting(ScoredResult::sourceId)
                                    .doesNotContain("401", "402", "403");
                            assertThat(result.stats().skippedOverFanoutEntityCount()).isEqualTo(1);
                        })
                .verifyComplete();
    }

    @Test
    void timeoutDegradesToDirectOnlyResult() {
        var slowAssistant =
                new DefaultRetrievalGraphAssistant(
                        storeWithSlowGraphReads(
                                Duration.ofMillis(250), itemsById, graphOperations));
        var timeoutConfig = SIMPLE_CONFIG.graphAssist().withTimeout(Duration.ofMillis(50));

        StepVerifier.create(slowAssistant.assist(CONTEXT, CONFIG, timeoutConfig, directSeeds()))
                .assertNext(
                        result -> {
                            assertThat(result.items()).isEqualTo(directSeeds());
                            assertThat(result.stats().timedOut()).isTrue();
                            assertThat(result.stats().degraded()).isTrue();
                        })
                .verifyComplete();
    }

    @Test
    void graphAssistDisabledReturnsExactDirectListInstanceOrder() {
        var direct = List.of(scored("101", 1.0d), scored("102", 0.9d));

        StepVerifier.create(
                        assistant.assist(
                                CONTEXT,
                                CONFIG,
                                SimpleStrategyConfig.GraphAssistConfig.defaults()
                                        .withEnabled(false),
                                direct))
                .assertNext(
                        result -> {
                            assertThat(result.items()).containsExactlyElementsOf(direct);
                            assertThat(result.stats().graphEnabled()).isFalse();
                        })
                .verifyComplete();
    }

    @Test
    void graphAssistReadsAdjacentLinksOnceForAllSeeds() {
        var recordingGraphOperations = new RecordingGraphOperations();
        recordingGraphOperations.upsertItemLinks(
                MEMORY_ID, List.of(link(101L, 201L, ItemLinkType.CAUSAL, 0.95d)));
        lenient().when(store.graphOperations()).thenReturn(recordingGraphOperations);
        assistant = new DefaultRetrievalGraphAssistant(store);

        StepVerifier.create(
                        assistant.assist(
                                CONTEXT, CONFIG, SIMPLE_CONFIG.graphAssist(), directSeeds()))
                .assertNext(result -> assertThat(result.stats().seedCount()).isEqualTo(2))
                .verifyComplete();

        assertThat(recordingGraphOperations.adjacentReadSeedSets())
                .containsExactly(List.of(101L, 102L));
    }

    @Test
    void graphAssistAppliesPerSeedCapsAfterDeterministicOrderingInsteadOfStoreReturnOrder() {
        itemsById.put(
                202L,
                item(
                        202L,
                        "second deterministic graph candidate",
                        Instant.parse("2026-04-16T12:05:00Z")));
        var recordingGraphOperations = new RecordingGraphOperations();
        recordingGraphOperations.overrideAdjacentRead(
                List.of(101L, 102L),
                List.of(
                        link(101L, 202L, ItemLinkType.SEMANTIC, 0.81d),
                        link(102L, 202L, ItemLinkType.SEMANTIC, 0.90d),
                        link(101L, 201L, ItemLinkType.SEMANTIC, 0.91d)));
        lenient().when(store.graphOperations()).thenReturn(recordingGraphOperations);
        assistant = new DefaultRetrievalGraphAssistant(store);
        var deterministicConfig =
                SIMPLE_CONFIG.withGraphAssist(
                        new SimpleStrategyConfig.GraphAssistConfig(
                                SIMPLE_CONFIG.graphAssist().enabled(),
                                SIMPLE_CONFIG.graphAssist().mode(),
                                SIMPLE_CONFIG.graphAssist().maxSeedItems(),
                                SIMPLE_CONFIG.graphAssist().maxExpandedItems(),
                                1,
                                0,
                                0,
                                0,
                                SIMPLE_CONFIG.graphAssist().maxItemsPerEntity(),
                                SIMPLE_CONFIG.graphAssist().graphChannelWeight(),
                                SIMPLE_CONFIG.graphAssist().minLinkStrength(),
                                SIMPLE_CONFIG.graphAssist().minMentionConfidence(),
                                SIMPLE_CONFIG.graphAssist().protectDirectTopK(),
                                SIMPLE_CONFIG.graphAssist().semanticEvidenceDecayFactor(),
                                SIMPLE_CONFIG.graphAssist().timeout()));

        StepVerifier.create(
                        assistant.assist(
                                CONTEXT, CONFIG, deterministicConfig.graphAssist(), directSeeds()))
                .assertNext(
                        result ->
                                assertThat(
                                                result.items().stream()
                                                        .map(ScoredResult::sourceId)
                                                        .toList())
                                        .containsSubsequence("201", "202"))
                .verifyComplete();
    }

    @Test
    void defaultRetrievalFloorStillAdmitsLegacyAndFirstRolloutTemporalLinks() {
        itemsById.put(
                801L,
                item(
                        801L,
                        "legacy temporal link candidate",
                        Instant.parse("2026-04-16T12:00:00Z")));
        itemsById.put(
                802L,
                item(
                        802L,
                        "first rollout floor temporal link candidate",
                        Instant.parse("2026-04-16T12:05:00Z")));
        var temporalGraphOperations = new InMemoryGraphOperations();
        temporalGraphOperations.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(101L, 801L, ItemLinkType.TEMPORAL, 1.0d),
                        link(102L, 802L, ItemLinkType.TEMPORAL, 0.60d)));
        lenient().when(store.graphOperations()).thenReturn(temporalGraphOperations);
        assistant = new DefaultRetrievalGraphAssistant(store);

        StepVerifier.create(
                        assistant.assist(
                                CONTEXT, CONFIG, SIMPLE_CONFIG.graphAssist(), directSeeds()))
                .assertNext(
                        result ->
                                assertThat(
                                                result.items().stream()
                                                        .map(ScoredResult::sourceId)
                                                        .toList())
                                        .containsSubsequence("801", "802"))
                .verifyComplete();
    }

    @Test
    void semanticConvergenceAcrossSeedsOutranksSingleSeedSemanticHit() {
        itemsById.put(
                301L,
                item(301L, "shared semantic convergence", Instant.parse("2026-04-16T12:30:00Z")));
        itemsById.put(
                302L,
                item(302L, "single stronger semantic hit", Instant.parse("2026-04-16T12:30:00Z")));
        graphOperations.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(101L, 301L, ItemLinkType.SEMANTIC, 0.82d),
                        link(102L, 301L, ItemLinkType.SEMANTIC, 0.80d),
                        link(101L, 302L, ItemLinkType.SEMANTIC, 0.93d)));

        StepVerifier.create(
                        assistant.assist(
                                CONTEXT, CONFIG, SIMPLE_CONFIG.graphAssist(), directSeeds()))
                .assertNext(
                        result ->
                                assertThat(
                                                result.items().stream()
                                                        .map(ScoredResult::sourceId)
                                                        .toList())
                                        .containsSubsequence("301", "302"))
                .verifyComplete();
    }

    @Test
    void repeatedSemanticEvidenceFromSameSeedUsesStrongestPathUnderDeterministicOrdering() {
        itemsById.put(
                501L,
                item(
                        501L,
                        "duplicate semantic path candidate",
                        Instant.parse("2026-04-16T12:30:00Z")));
        itemsById.put(
                502L,
                item(502L, "single best path candidate", Instant.parse("2026-04-16T12:30:00Z")));
        graphOperations.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(101L, 501L, ItemLinkType.SEMANTIC, 0.60d),
                        link(501L, 101L, ItemLinkType.SEMANTIC, 0.92d),
                        link(101L, 502L, ItemLinkType.SEMANTIC, 0.91d)));

        StepVerifier.create(
                        assistant.assist(
                                CONTEXT, CONFIG, OVERLAP_CONFIG.graphAssist(), directWithOverlap()))
                .assertNext(
                        result ->
                                assertThat(
                                                result.items().stream()
                                                        .map(ScoredResult::sourceId)
                                                        .toList())
                                        .containsSubsequence("501", "502"))
                .verifyComplete();
    }

    @Test
    void nonSemanticFamiliesStillMergeByMaxInsteadOfAccumulating() {
        itemsById.put(
                601L,
                item(601L, "mixed non-semantic candidate", Instant.parse("2026-04-16T12:00:00Z")));
        itemsById.put(
                602L,
                item(
                        602L,
                        "single stronger causal candidate",
                        Instant.parse("2026-04-16T12:00:00Z")));
        graphOperations.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(101L, 601L, ItemLinkType.CAUSAL, 0.81d),
                        link(102L, 601L, ItemLinkType.TEMPORAL, 0.80d),
                        link(101L, 602L, ItemLinkType.CAUSAL, 0.90d)));

        StepVerifier.create(
                        assistant.assist(
                                CONTEXT,
                                CONFIG,
                                NON_SEMANTIC_MERGE_CONFIG.graphAssist(),
                                directSeeds()))
                .assertNext(
                        result ->
                                assertThat(
                                                result.items().stream()
                                                        .map(ScoredResult::sourceId)
                                                        .toList())
                                        .containsSubsequence("602", "601"))
                .verifyComplete();
    }

    @Test
    void semanticAccumulationUsesBoundedGraphScores() {
        itemsById.put(
                701L,
                item(701L, "bounded semantic candidate", Instant.parse("2026-04-16T12:45:00Z")));
        graphOperations.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(101L, 701L, ItemLinkType.SEMANTIC, 0.99d),
                        link(102L, 701L, ItemLinkType.SEMANTIC, 0.98d),
                        link(103L, 701L, ItemLinkType.SEMANTIC, 0.97d)));

        StepVerifier.create(
                        assistant.assist(
                                CONTEXT, CONFIG, SIMPLE_CONFIG.graphAssist(), directSeeds()))
                .assertNext(
                        result -> {
                            var candidate =
                                    result.items().stream()
                                            .filter(item -> item.sourceId().equals("701"))
                                            .findFirst()
                                            .orElseThrow();
                            assertThat(candidate.finalScore()).isLessThan(1.0d);
                        })
                .verifyComplete();
    }

    private List<ScoredResult> directSeeds() {
        return List.of(scored("101", 1.0d), scored("102", 0.9d), scored("103", 0.8d));
    }

    private List<ScoredResult> directWithOverlap() {
        return List.of(scored("101", 1.0d), scored("102", 0.9d));
    }

    private void seedItems() {
        itemsById.put(
                101L,
                item(
                        101L,
                        "rolled out the OpenAI migration",
                        Instant.parse("2026-04-16T10:00:00Z")));
        itemsById.put(
                102L,
                item(
                        102L,
                        "documented the deployment outcome",
                        Instant.parse("2026-04-16T11:00:00Z")));
        itemsById.put(
                103L, item(103L, "older direct tail item", Instant.parse("2026-04-10T10:00:00Z")));
        itemsById.put(
                201L,
                item(
                        201L,
                        "the migration caused a latency drop",
                        Instant.parse("2026-04-16T12:00:00Z")));
        itemsById.put(
                401L, item(401L, "OpenAI sibling item A", Instant.parse("2026-04-16T13:00:00Z")));
        itemsById.put(
                402L, item(402L, "OpenAI sibling item B", Instant.parse("2026-04-16T14:00:00Z")));
        itemsById.put(
                403L, item(403L, "OpenAI sibling item C", Instant.parse("2026-04-16T15:00:00Z")));
    }

    private void seedGraph() {
        graphOperations.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(101L, 201L, ItemLinkType.CAUSAL, 0.95d),
                        link(101L, 102L, ItemLinkType.SEMANTIC, 0.91d)));
        graphOperations.upsertItemEntityMentions(
                MEMORY_ID,
                List.of(
                        mention(101L, "organization:openai", 0.95f),
                        mention(102L, "organization:openai", 0.95f),
                        mention(401L, "organization:openai", 0.95f),
                        mention(402L, "organization:openai", 0.95f),
                        mention(403L, "organization:openai", 0.95f)));
    }

    private static MemoryStore storeWithSlowGraphReads(
            Duration delay, Map<Long, MemoryItem> itemsById, InMemoryGraphOperations delegate) {
        return new MemoryStore() {
            @Override
            public com.openmemind.ai.memory.core.store.rawdata.RawDataOperations
                    rawDataOperations() {
                return null;
            }

            @Override
            public ItemOperations itemOperations() {
                return new ItemOperations() {
                    @Override
                    public void insertItems(MemoryId id, List<MemoryItem> items) {}

                    @Override
                    public List<MemoryItem> getItemsByIds(MemoryId id, Collection<Long> itemIds) {
                        return itemIds.stream()
                                .map(itemsById::get)
                                .filter(java.util.Objects::nonNull)
                                .toList();
                    }

                    @Override
                    public List<MemoryItem> getItemsByVectorIds(
                            MemoryId id, Collection<String> vectorIds) {
                        return List.of();
                    }

                    @Override
                    public List<MemoryItem> getItemsByContentHashes(
                            MemoryId id, Collection<String> contentHashes) {
                        return List.of();
                    }

                    @Override
                    public List<MemoryItem> listItems(MemoryId id) {
                        return List.copyOf(itemsById.values());
                    }

                    @Override
                    public boolean hasItems(MemoryId id) {
                        return !itemsById.isEmpty();
                    }

                    @Override
                    public void deleteItems(MemoryId id, Collection<Long> itemIds) {}
                };
            }

            @Override
            public com.openmemind.ai.memory.core.store.insight.InsightOperations
                    insightOperations() {
                return null;
            }

            @Override
            public com.openmemind.ai.memory.core.store.graph.GraphOperations graphOperations() {
                return new InMemoryGraphOperations() {
                    @Override
                    public List<ItemEntityMention> listItemEntityMentions(
                            MemoryId memoryId, Collection<Long> itemIds) {
                        sleep(delay);
                        return delegate.listItemEntityMentions(memoryId, itemIds);
                    }

                    @Override
                    public List<ItemEntityMention> listItemEntityMentionsByEntityKeys(
                            MemoryId memoryId,
                            Collection<String> entityKeys,
                            int perEntityLimitPlusOne) {
                        sleep(delay);
                        return delegate.listItemEntityMentionsByEntityKeys(
                                memoryId, entityKeys, perEntityLimitPlusOne);
                    }

                    @Override
                    public List<ItemLink> listAdjacentItemLinks(
                            MemoryId memoryId,
                            Collection<Long> seedItemIds,
                            Collection<ItemLinkType> linkTypes) {
                        sleep(delay);
                        return delegate.listAdjacentItemLinks(memoryId, seedItemIds, linkTypes);
                    }
                };
            }
        };
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during slow graph read", e);
        }
    }

    private static MemoryItem item(Long id, String content, Instant occurredAt) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                "conversation",
                "vector-" + id,
                "raw-" + id,
                "hash-" + id,
                occurredAt,
                occurredAt,
                Map.of(),
                NOW,
                MemoryItemType.FACT);
    }

    private static ScoredResult scored(String itemId, double score) {
        return new ScoredResult(
                ScoredResult.SourceType.ITEM, itemId, "item-" + itemId, 0.8f, score);
    }

    private static ItemLink link(
            long sourceItemId, long targetItemId, ItemLinkType type, double strength) {
        return new ItemLink(
                MEMORY_ID.toIdentifier(),
                sourceItemId,
                targetItemId,
                type,
                defaultRelationCode(type),
                defaultEvidenceSource(type),
                strength,
                Map.of(),
                NOW);
    }

    private static String defaultRelationCode(ItemLinkType type) {
        return switch (type) {
            case SEMANTIC -> null;
            case TEMPORAL -> "before";
            case CAUSAL -> "caused_by";
        };
    }

    private static String defaultEvidenceSource(ItemLinkType type) {
        return type == ItemLinkType.SEMANTIC ? "vector_search" : null;
    }

    private static ItemEntityMention mention(long itemId, String entityKey, float confidence) {
        return new ItemEntityMention(
                MEMORY_ID.toIdentifier(), itemId, entityKey, confidence, Map.of(), NOW);
    }

    private static final class RecordingGraphOperations extends InMemoryGraphOperations {
        private final List<List<Long>> adjacentReadSeedSets = new ArrayList<>();
        private final Map<List<Long>, List<ItemLink>> adjacentOverrides = new LinkedHashMap<>();

        @Override
        public List<ItemLink> listAdjacentItemLinks(
                MemoryId memoryId,
                Collection<Long> seedItemIds,
                Collection<ItemLinkType> linkTypes) {
            var seedSet = List.copyOf(seedItemIds);
            adjacentReadSeedSets.add(seedSet);
            var override = adjacentOverrides.get(seedSet);
            if (override != null) {
                return override;
            }
            return super.listAdjacentItemLinks(memoryId, seedItemIds, linkTypes);
        }

        private void overrideAdjacentRead(List<Long> seedIds, List<ItemLink> links) {
            adjacentOverrides.put(List.copyOf(seedIds), List.copyOf(links));
        }

        private List<List<Long>> adjacentReadSeedSets() {
            return List.copyOf(adjacentReadSeedSets);
        }
    }
}
