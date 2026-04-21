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
package com.openmemind.ai.memory.core.store.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.EntityNormalizationVersions;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticEvidenceSource;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticItemRelation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InMemoryGraphOperationsTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant NOW = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void noOpGraphOperationsSwallowWritesAndExposeEmptyViews() {
        GraphOperations ops = NoOpGraphOperations.INSTANCE;

        ops.upsertEntities(MEMORY_ID, List.of(entity("organization:openai", "OpenAI")));
        ops.upsertItemEntityMentions(MEMORY_ID, List.of(mention(101L, "organization:openai")));
        ops.upsertItemLinks(MEMORY_ID, List.of(link(101L, 102L, ItemLinkType.CAUSAL)));

        assertThat(ops.listEntities(MEMORY_ID)).isEmpty();
        assertThat(ops.listItemEntityMentions(MEMORY_ID)).isEmpty();
        assertThat(ops.listItemLinks(MEMORY_ID)).isEmpty();
    }

    @Test
    void inMemoryGraphOperationsShouldAccumulateEvidenceForSameAliasIdentityAndPreserveAmbiguity() {
        var ops = new InMemoryGraphOperations();

        ops.upsertEntityAliases(
                MEMORY_ID,
                List.of(
                        alias(
                                "organization:google",
                                GraphEntityType.ORGANIZATION,
                                "谷歌",
                                1,
                                NOW,
                                NOW)));
        ops.upsertEntityAliases(
                MEMORY_ID,
                List.of(
                        alias(
                                "organization:google",
                                GraphEntityType.ORGANIZATION,
                                "谷歌",
                                2,
                                NOW.plusSeconds(10),
                                NOW.plusSeconds(10)),
                        alias(
                                "organization:google_hk",
                                GraphEntityType.ORGANIZATION,
                                "谷歌",
                                1,
                                NOW.plusSeconds(20),
                                NOW.plusSeconds(20))));

        assertThat(
                        ops.listEntityAliasesByNormalizedAlias(
                                MEMORY_ID, GraphEntityType.ORGANIZATION, "谷歌"))
                .extracting(
                        GraphEntityAlias::entityKey,
                        GraphEntityAlias::evidenceCount,
                        GraphEntityAlias::createdAt,
                        GraphEntityAlias::updatedAt)
                .containsExactly(
                        tuple("organization:google", 3, NOW, NOW.plusSeconds(10)),
                        tuple(
                                "organization:google_hk",
                                1,
                                NOW.plusSeconds(20),
                                NOW.plusSeconds(20)));
    }

    @Test
    void noOpGraphOperationsShouldExposeEmptyHistoricalAliasViews() {
        GraphOperations ops = NoOpGraphOperations.INSTANCE;

        ops.upsertEntityAliases(
                MEMORY_ID,
                List.of(
                        alias(
                                "organization:google",
                                GraphEntityType.ORGANIZATION,
                                "谷歌",
                                1,
                                NOW,
                                NOW)));

        assertThat(ops.listEntityAliases(MEMORY_ID)).isEmpty();
        assertThat(
                        ops.listEntityAliasesByNormalizedAlias(
                                MEMORY_ID, GraphEntityType.ORGANIZATION, "谷歌"))
                .isEmpty();
    }

    @Test
    void applyGraphWritePlanStagesFactsUntilBatchPromotion() {
        var ops = new InMemoryGraphOperations();
        var batchId = new ExtractionBatchId("batch-1");
        var plan = planWithDuplicateMentionAliasAndSemanticRelation();

        ops.applyGraphWritePlan(MEMORY_ID, batchId, plan);
        ops.applyGraphWritePlan(MEMORY_ID, batchId, plan);

        assertThat(ops.listItemEntityMentions(MEMORY_ID)).isEmpty();
        assertThat(ops.listEntityAliases(MEMORY_ID)).isEmpty();
        assertThat(ops.listItemLinks(MEMORY_ID)).isEmpty();

        var nextView = ops.previewPromotedBatch(MEMORY_ID, batchId);
        ops.installCommittedBatch(MEMORY_ID, batchId, nextView);

        assertThat(ops.listItemEntityMentions(MEMORY_ID)).hasSize(1);
        assertThat(ops.listEntityAliases(MEMORY_ID))
                .singleElement()
                .extracting(GraphEntityAlias::evidenceCount)
                .isEqualTo(2);
        assertThat(ops.listItemLinks(MEMORY_ID))
                .singleElement()
                .extracting(
                        ItemLink::sourceItemId, ItemLink::targetItemId, ItemLink::evidenceSource)
                .containsExactly(101L, 102L, "vector_search");
    }

    @Test
    void applyingDifferentBatchesAccumulatesAliasEvidenceOnlyOncePerBatch() {
        var ops = new InMemoryGraphOperations();

        ops.applyGraphWritePlan(MEMORY_ID, new ExtractionBatchId("batch-1"), aliasOnlyPlan());
        ops.applyGraphWritePlan(MEMORY_ID, new ExtractionBatchId("batch-2"), aliasOnlyPlan());
        ops.installCommittedBatch(
                MEMORY_ID,
                new ExtractionBatchId("batch-1"),
                ops.previewPromotedBatch(MEMORY_ID, new ExtractionBatchId("batch-1")));
        ops.installCommittedBatch(
                MEMORY_ID,
                new ExtractionBatchId("batch-2"),
                ops.previewPromotedBatch(MEMORY_ID, new ExtractionBatchId("batch-2")));

        assertThat(ops.listEntityAliases(MEMORY_ID))
                .singleElement()
                .extracting(GraphEntityAlias::evidenceCount)
                .isEqualTo(2);
    }

    @Test
    void discardingPendingBatchLeavesCommittedViewUntouched() {
        var ops = new InMemoryGraphOperations();
        var batchId = new ExtractionBatchId("batch-1");

        ops.applyGraphWritePlan(MEMORY_ID, batchId, aliasOnlyPlan());
        ops.discardPendingBatch(MEMORY_ID, batchId);

        assertThat(ops.listEntityAliases(MEMORY_ID)).isEmpty();
        assertThat(ops.listItemEntityMentions(MEMORY_ID)).isEmpty();
        assertThat(ops.listItemLinks(MEMORY_ID)).isEmpty();
    }

    @Test
    void inMemoryGraphOperationsUpsertEntitiesMentionsAndLinksIdempotently() {
        var ops = new InMemoryGraphOperations();

        ops.upsertEntities(MEMORY_ID, List.of(entity("organization:openai", "OpenAI")));
        ops.upsertItemEntityMentions(MEMORY_ID, List.of(mention(101L, "organization:openai")));
        ops.upsertItemLinks(MEMORY_ID, List.of(link(101L, 102L, ItemLinkType.CAUSAL)));
        ops.upsertEntities(MEMORY_ID, List.of(entity("organization:openai", "OpenAI")));
        ops.upsertItemEntityMentions(MEMORY_ID, List.of(mention(101L, "organization:openai")));
        ops.upsertItemLinks(MEMORY_ID, List.of(link(101L, 102L, ItemLinkType.CAUSAL)));

        assertThat(ops.listEntities(MEMORY_ID)).hasSize(1);
        assertThat(ops.listItemEntityMentions(MEMORY_ID)).hasSize(1);
        assertThat(ops.listItemLinks(MEMORY_ID)).hasSize(1);
    }

    @Test
    void inMemoryGraphOperationsShouldLookupEntitiesByRequestedKeysWithoutDuplicates() {
        var ops = new InMemoryGraphOperations();
        ops.upsertEntities(
                MEMORY_ID,
                List.of(
                        entity("organization:openai", "OpenAI"),
                        new GraphEntity(
                                "person:sam_altman",
                                MEMORY_ID.toIdentifier(),
                                "Sam Altman",
                                GraphEntityType.PERSON,
                                Map.of("source", "test"),
                                NOW,
                                NOW)));

        assertThat(
                        ops.listEntitiesByEntityKeys(
                                MEMORY_ID,
                                List.of(
                                        "person:sam_altman",
                                        "organization:openai",
                                        "person:sam_altman",
                                        "missing:key")))
                .extracting(GraphEntity::entityKey)
                .containsExactlyInAnyOrder("organization:openai", "person:sam_altman");
    }

    @Test
    void rebuildEntityCooccurrencesShouldBeDeterministicAcrossRepeatedCalls() {
        var ops = new InMemoryGraphOperations();

        ops.upsertItemEntityMentions(
                MEMORY_ID,
                List.of(
                        mention(101L, "organization:openai"),
                        mention(101L, "person:sam_altman"),
                        mention(102L, "organization:openai"),
                        mention(102L, "person:sam_altman")));

        ops.rebuildEntityCooccurrences(
                MEMORY_ID, List.of("organization:openai", "person:sam_altman"));
        ops.rebuildEntityCooccurrences(
                MEMORY_ID, List.of("organization:openai", "person:sam_altman"));

        assertThat(ops.listEntityCooccurrences(MEMORY_ID))
                .singleElement()
                .extracting(EntityCooccurrence::cooccurrenceCount)
                .isEqualTo(2);
    }

    @Test
    void boundedGraphReadsShouldReturnOnlyLocalSubgraphEdgesForRequestedItemIdsAndLinkTypes() {
        var ops = new InMemoryGraphOperations();

        ops.upsertItemEntityMentions(
                MEMORY_ID,
                List.of(
                        mention(101L, "organization:openai"),
                        mention(102L, "person:sam_altman"),
                        mention(103L, "organization:anthropic")));
        ops.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(101L, 102L, ItemLinkType.CAUSAL),
                        link(101L, 103L, ItemLinkType.CAUSAL),
                        link(102L, 103L, ItemLinkType.SEMANTIC)));

        assertThat(ops.listItemEntityMentions(MEMORY_ID, List.of(101L)))
                .extracting(ItemEntityMention::itemId)
                .containsExactly(101L);
        assertThat(ops.listItemLinks(MEMORY_ID, List.of(101L, 102L), List.of(ItemLinkType.CAUSAL)))
                .allMatch(
                        link ->
                                Set.of(101L, 102L).contains(link.sourceItemId())
                                        && Set.of(101L, 102L).contains(link.targetItemId()))
                .allMatch(link -> link.linkType() == ItemLinkType.CAUSAL)
                .extracting(ItemLink::sourceItemId, ItemLink::targetItemId)
                .containsExactly(tuple(101L, 102L));
    }

    @Test
    void listAdjacentItemLinksTreatsEitherEndpointAsAdjacentWithoutChangingLocalSubgraphRead() {
        var ops = seededGraph();

        assertThat(ops.listItemLinks(MEMORY_ID, List.of(101L, 102L), List.of(ItemLinkType.CAUSAL)))
                .extracting(ItemLink::sourceItemId, ItemLink::targetItemId)
                .containsExactly(tuple(101L, 102L));

        assertThat(
                        ops.listAdjacentItemLinks(
                                MEMORY_ID, List.of(101L), List.of(ItemLinkType.CAUSAL)))
                .extracting(ItemLink::sourceItemId, ItemLink::targetItemId)
                .containsExactly(tuple(101L, 102L), tuple(103L, 101L));
    }

    @Test
    void reverseMentionLookupReturnsAtMostLimitPlusOneRowsPerEntityKey() {
        var ops = new InMemoryGraphOperations();

        ops.upsertItemEntityMentions(
                MEMORY_ID,
                List.of(
                        mention(101L, "organization:openai"),
                        mention(102L, "organization:openai"),
                        mention(103L, "organization:openai"),
                        mention(104L, "organization:openai"),
                        mention(101L, "person:sam_altman"),
                        mention(102L, "person:sam_altman")));

        assertThat(
                        ops.listItemEntityMentionsByEntityKeys(
                                MEMORY_ID, List.of("organization:openai", "person:sam_altman"), 3))
                .extracting(ItemEntityMention::entityKey, ItemEntityMention::itemId)
                .containsExactly(
                        tuple("organization:openai", 101L),
                        tuple("organization:openai", 102L),
                        tuple("organization:openai", 103L),
                        tuple("person:sam_altman", 101L),
                        tuple("person:sam_altman", 102L));
    }

    @Test
    void inMemoryGraphOperationsShouldRoundTripStage1EntityMetadata() {
        var ops = new InMemoryGraphOperations();
        var entity =
                new GraphEntity(
                        "person:张三",
                        MEMORY_ID.toIdentifier(),
                        "张三",
                        GraphEntityType.PERSON,
                        Map.of(
                                "source",
                                "item_extraction",
                                "normalizationVersion",
                                EntityNormalizationVersions.STAGE1A_V1),
                        NOW,
                        NOW);
        var mention =
                new ItemEntityMention(
                        MEMORY_ID.toIdentifier(),
                        101L,
                        "person:张三",
                        0.95f,
                        Map.of(
                                "rawName",
                                "张三",
                                "rawTypeLabel",
                                "人物",
                                "normalizedName",
                                "张三",
                                "normalizationVersion",
                                EntityNormalizationVersions.STAGE1A_V1),
                        NOW);

        ops.upsertEntities(MEMORY_ID, List.of(entity));
        ops.upsertItemEntityMentions(MEMORY_ID, List.of(mention));

        assertThat(ops.listEntities(MEMORY_ID).getFirst().metadata())
                .containsEntry("normalizationVersion", EntityNormalizationVersions.STAGE1A_V1);
        assertThat(ops.listItemEntityMentions(MEMORY_ID).getFirst().metadata())
                .containsEntry("rawName", "张三")
                .containsEntry("rawTypeLabel", "人物")
                .containsEntry("normalizedName", "张三");
    }

    @Test
    void inMemoryGraphOperationsShouldRoundTripStage2ResolutionMetadata() {
        var ops = new InMemoryGraphOperations();
        var entity =
                new GraphEntity(
                        "organization:openai",
                        MEMORY_ID.toIdentifier(),
                        "OpenAI",
                        GraphEntityType.ORGANIZATION,
                        Map.of(
                                "source",
                                "item_extraction",
                                "topAliases",
                                List.of("开放人工智能"),
                                "aliasEvidenceCount",
                                1,
                                "aliasClasses",
                                List.of("explicit_parenthetical")),
                        NOW,
                        NOW);
        var mention =
                new ItemEntityMention(
                        MEMORY_ID.toIdentifier(),
                        101L,
                        "organization:openai",
                        0.95f,
                        Map.of(
                                "source",
                                "item_extraction",
                                "resolutionMode",
                                "conservative",
                                "resolutionSource",
                                "explicit_alias_evidence_hit",
                                "resolvedViaAliasClass",
                                "explicit_parenthetical"),
                        NOW);

        ops.upsertEntities(MEMORY_ID, List.of(entity));
        ops.upsertItemEntityMentions(MEMORY_ID, List.of(mention));

        assertThat(
                        ((Number)
                                        ops.listEntities(MEMORY_ID)
                                                .getFirst()
                                                .metadata()
                                                .get("aliasEvidenceCount"))
                                .intValue())
                .isEqualTo(1);
        assertThat(ops.listEntities(MEMORY_ID).getFirst().metadata())
                .containsEntry("aliasClasses", List.of("explicit_parenthetical"));
        assertThat(ops.listItemEntityMentions(MEMORY_ID).getFirst().metadata())
                .containsEntry("resolutionMode", "conservative")
                .containsEntry("resolvedViaAliasClass", "explicit_parenthetical");
    }

    private static InMemoryGraphOperations seededGraph() {
        var ops = new InMemoryGraphOperations();
        ops.upsertItemLinks(
                MEMORY_ID,
                List.of(
                        link(101L, 102L, ItemLinkType.CAUSAL),
                        link(103L, 101L, ItemLinkType.CAUSAL),
                        link(101L, 104L, ItemLinkType.SEMANTIC)));
        return ops;
    }

    private static GraphEntity entity(String entityKey, String canonicalName) {
        return new GraphEntity(
                entityKey,
                MEMORY_ID.toIdentifier(),
                canonicalName,
                GraphEntityType.ORGANIZATION,
                Map.of("source", "test"),
                NOW,
                NOW);
    }

    private static GraphEntityAlias alias(
            String entityKey,
            GraphEntityType entityType,
            String normalizedAlias,
            int evidenceCount,
            Instant createdAt,
            Instant updatedAt) {
        return new GraphEntityAlias(
                MEMORY_ID.toIdentifier(),
                entityKey,
                entityType,
                normalizedAlias,
                evidenceCount,
                Map.of("source", "item_extraction"),
                createdAt,
                updatedAt);
    }

    private static ItemEntityMention mention(long itemId, String entityKey) {
        return new ItemEntityMention(
                MEMORY_ID.toIdentifier(), itemId, entityKey, 1.0f, Map.of("source", "test"), NOW);
    }

    private static ItemLink link(long sourceItemId, long targetItemId, ItemLinkType linkType) {
        return new ItemLink(
                MEMORY_ID.toIdentifier(),
                sourceItemId,
                targetItemId,
                linkType,
                defaultRelationCode(linkType),
                defaultEvidenceSource(linkType),
                1.0d,
                Map.of(),
                NOW);
    }

    private static String defaultRelationCode(ItemLinkType linkType) {
        return switch (linkType) {
            case SEMANTIC -> null;
            case TEMPORAL -> "before";
            case CAUSAL -> "caused_by";
        };
    }

    private static String defaultEvidenceSource(ItemLinkType linkType) {
        return linkType == ItemLinkType.SEMANTIC ? "vector_search" : null;
    }

    private static ItemGraphWritePlan planWithDuplicateMentionAliasAndSemanticRelation() {
        return ItemGraphWritePlan.builder()
                .mentions(
                        List.of(
                                new ItemEntityMention(
                                        MEMORY_ID.toIdentifier(),
                                        101L,
                                        "person:sam_altman",
                                        0.40f,
                                        Map.of(),
                                        NOW),
                                new ItemEntityMention(
                                        MEMORY_ID.toIdentifier(),
                                        101L,
                                        "person:sam_altman",
                                        0.95f,
                                        Map.of(),
                                        NOW)))
                .aliases(
                        List.of(
                                alias(
                                        "person:sam_altman",
                                        GraphEntityType.PERSON,
                                        "sam altman",
                                        1,
                                        NOW,
                                        NOW),
                                alias(
                                        "person:sam_altman",
                                        GraphEntityType.PERSON,
                                        "sam altman",
                                        1,
                                        NOW,
                                        NOW)))
                .semanticRelations(
                        List.of(
                                new SemanticItemRelation(
                                        101L,
                                        102L,
                                        SemanticEvidenceSource.VECTOR_SEARCH_FALLBACK,
                                        0.81d),
                                new SemanticItemRelation(
                                        101L, 102L, SemanticEvidenceSource.VECTOR_SEARCH, 0.92d)))
                .build();
    }

    private static ItemGraphWritePlan aliasOnlyPlan() {
        return ItemGraphWritePlan.builder()
                .aliases(
                        List.of(
                                alias(
                                        "person:sam_altman",
                                        GraphEntityType.PERSON,
                                        "sam altman",
                                        1,
                                        NOW,
                                        NOW)))
                .build();
    }
}
