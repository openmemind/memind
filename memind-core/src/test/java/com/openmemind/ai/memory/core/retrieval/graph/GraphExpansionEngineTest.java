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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GraphExpansionEngineTest {

    private static final MemoryId MEMORY_ID = TestMemoryIds.userAgent();
    private static final RetrievalConfig CONFIG = RetrievalConfig.simple();
    private static final QueryContext CONTEXT =
            new QueryContext(
                    MEMORY_ID, "what changed this week", null, List.of(), Map.of(), null, null);
    private static final SimpleStrategyConfig.GraphAssistConfig SETTINGS =
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
                    Duration.ofMillis(200));
    private static final Instant NOW = Instant.parse("2026-04-17T00:00:00Z");

    @Mock private MemoryStore store;
    @Mock private ItemOperations itemOperations;

    private InMemoryGraphOperations graphOperations;
    private Map<Long, MemoryItem> itemsById;
    private GraphExpansionEngine engine;

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

        engine = new GraphExpansionEngine(store);
    }

    @Test
    void disabledSettingsReturnsEmpty() {
        var result = engine.expand(CONTEXT, CONFIG, SETTINGS.withEnabled(false), directSeeds());

        assertThat(result.enabled()).isFalse();
        assertThat(result.graphItems()).isEmpty();
        assertThat(result.seedCount()).isZero();
    }

    @Test
    void emptySeedsReturnsEmpty() {
        var result = engine.expand(CONTEXT, CONFIG, SETTINGS, List.of());

        assertThat(result.enabled()).isTrue();
        assertThat(result.graphItems()).isEmpty();
        assertThat(result.seedCount()).isZero();
    }

    @Test
    void expandsGraphCandidatesAndExcludesDirectInputIds() {
        var result = engine.expand(CONTEXT, CONFIG, SETTINGS, directSeeds());

        assertThat(result.graphItems())
                .extracting(ScoredResult::sourceId)
                .contains("201", "301")
                .doesNotContain("101", "102", "103");
        assertThat(result.seedCount()).isEqualTo(2);
        assertThat(result.linkExpansionCount()).isGreaterThanOrEqualTo(2);
        assertThat(result.dedupedCandidateCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void tracksDirectOverlapWithoutReturningOverlapItems() {
        var oneSeed = SETTINGS.withMaxSeedItems(1);

        var result = engine.expand(CONTEXT, CONFIG, oneSeed, directWithOverlap());

        assertThat(result.overlapCount()).isEqualTo(1);
        assertThat(result.graphItems()).extracting(ScoredResult::sourceId).doesNotContain("102");
    }

    @Test
    void skipsOverFanoutEntityExpansion() {
        var result = engine.expand(CONTEXT, CONFIG, SETTINGS, directSeeds());

        assertThat(result.skippedOverFanoutEntityCount()).isEqualTo(1);
        assertThat(result.graphItems())
                .extracting(ScoredResult::sourceId)
                .doesNotContain("401", "402", "403");
    }

    private List<ScoredResult> directSeeds() {
        return List.of(scored("101", 1.0d), scored("102", 0.9d), scored("103", 0.8d));
    }

    private List<ScoredResult> directWithOverlap() {
        return List.of(scored("101", 1.0d), scored("102", 0.9d));
    }

    private static ScoredResult scored(String sourceId, double score) {
        return new ScoredResult(
                ScoredResult.SourceType.ITEM,
                sourceId,
                "result " + sourceId,
                (float) score,
                score,
                NOW);
    }

    private MemoryItem item(long id, String content, Instant occurredAt) {
        return new MemoryItem(
                id,
                MEMORY_ID.toIdentifier(),
                content,
                MemoryScope.USER,
                MemoryCategory.EVENT,
                null,
                null,
                null,
                null,
                occurredAt,
                null,
                Map.of(),
                occurredAt,
                MemoryItemType.FACT);
    }

    private ItemLink link(long sourceId, long targetId, ItemLinkType type, double strength) {
        return new ItemLink(
                MEMORY_ID.toIdentifier(),
                sourceId,
                targetId,
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
        itemsById.put(301L, item(301L, "temporal neighbor", Instant.parse("2026-04-16T13:00:00Z")));
        itemsById.put(
                401L, item(401L, "OpenAI sibling item A", Instant.parse("2026-04-16T14:00:00Z")));
        itemsById.put(
                402L, item(402L, "OpenAI sibling item B", Instant.parse("2026-04-16T15:00:00Z")));
        itemsById.put(
                403L, item(403L, "OpenAI sibling item C", Instant.parse("2026-04-16T16:00:00Z")));
    }

    private void seedGraph() {
        graphOperations.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(101L, 201L, ItemLinkType.CAUSAL, 0.95d),
                        link(101L, 102L, ItemLinkType.SEMANTIC, 0.91d),
                        link(102L, 301L, ItemLinkType.TEMPORAL, 0.90d)));
        graphOperations.upsertItemEntityMentions(
                MEMORY_ID,
                List.of(
                        mention(101L, "organization:openai", 0.95f),
                        mention(401L, "organization:openai", 0.95f),
                        mention(402L, "organization:openai", 0.95f),
                        mention(403L, "organization:openai", 0.95f)));
    }

    private ItemEntityMention mention(long itemId, String entityKey, float confidence) {
        return new ItemEntityMention(
                MEMORY_ID.toIdentifier(), itemId, entityKey, confidence, Map.of(), NOW);
    }
}
