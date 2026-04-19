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
package com.openmemind.ai.memory.core.extraction.item.graph.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasClass;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityResolutionMode;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import com.openmemind.ai.memory.core.extraction.item.graph.UserAliasDictionary;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.alias.EntityAliasIndexPlanner;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.ConservativeHeuristicEntityResolutionStrategy;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.DefaultEntityCandidateRetriever;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityVariantKeyGenerator;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.ExactCanonicalEntityResolutionStrategy;
import com.openmemind.ai.memory.core.extraction.item.graph.link.semantic.SemanticItemLinker;
import com.openmemind.ai.memory.core.extraction.item.graph.link.temporal.TemporalItemLinker;
import com.openmemind.ai.memory.core.extraction.item.graph.link.temporal.TemporalRelationClassifier;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.item.InMemoryItemOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultItemGraphMaterializerTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void semanticLinksShouldUseSameEmbeddingTextRuleAsItemVectorization() {
        var graphOps = new InMemoryGraphOperations();
        var vector = new StubMemoryVector();
        vector.register(
                "User discussed OpenAI deployment",
                new VectorSearchResult("vector-77", "existing note", 0.93f, Map.of()));
        var itemOps = new InMemoryItemOperations();
        itemOps.insertItems(
                MEMORY_ID,
                List.of(existingItem(77L, "vector-77", "Existing OpenAI deployment note")));

        var materializer =
                new DefaultItemGraphMaterializer(
                        new GraphHintNormalizer(),
                        new ExactCanonicalEntityResolutionStrategy(),
                        new EntityAliasIndexPlanner(),
                        new StructuredGraphPersister(graphOps),
                        new TemporalItemLinker(
                                itemOps,
                                graphOps,
                                new TemporalRelationClassifier(),
                                ItemGraphOptions.defaults().withEnabled(true)),
                        new SemanticItemLinker(
                                itemOps,
                                graphOps,
                                vector,
                                ItemGraphOptions.defaults().withEnabled(true)),
                        ItemGraphOptions.defaults().withEnabled(true));

        var item =
                newItem(
                        101L,
                        "vector-101",
                        "User discussed OpenAI deployment",
                        Map.of("whenToUse", "Use when asked about OpenAI rollout"));

        StepVerifier.create(materializer.materialize(MEMORY_ID, List.of(item), List.of(newEntry())))
                .assertNext(
                        result ->
                                assertThat(result.stats())
                                        .extracting(
                                                ItemGraphMaterializationResult.Stats::entityCount,
                                                ItemGraphMaterializationResult.Stats::mentionCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::structuredItemLinkCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::semanticSearchHitCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::semanticLinkCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::semanticSameBatchHitCount)
                                        .containsExactly(0, 0, 0, 1, 1, 0))
                .verifyComplete();

        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .extracting(ItemLink::linkType, ItemLink::sourceItemId, ItemLink::targetItemId)
                .contains(tuple(ItemLinkType.SEMANTIC, 101L, 77L));
    }

    @Test
    void materializeShouldPropagateStage3IntraBatchSemanticStats() {
        var graphOps = new InMemoryGraphOperations();
        var materializer =
                new DefaultItemGraphMaterializer(
                        new GraphHintNormalizer(),
                        new ExactCanonicalEntityResolutionStrategy(),
                        new EntityAliasIndexPlanner(),
                        new StructuredGraphPersister(graphOps),
                        new TemporalItemLinker(
                                new InMemoryItemOperations(),
                                graphOps,
                                new TemporalRelationClassifier(),
                                ItemGraphOptions.defaults().withEnabled(true)),
                        new SemanticItemLinker(
                                new InMemoryItemOperations(),
                                graphOps,
                                new StubMemoryVector(),
                                ItemGraphOptions.defaults().withEnabled(true)) {
                            @Override
                            public Mono<SemanticLinkingStats> link(
                                    MemoryId memoryId, List<MemoryItem> items) {
                                return Mono.just(
                                        new SemanticLinkingStats(
                                                4, 4, 8, 6, 5, 2, 2, 1, 1, 1, 3, 0, 4, 15L, 9L, 6L,
                                                12L, true));
                            }
                        },
                        ItemGraphOptions.defaults().withEnabled(true));

        StepVerifier.create(
                        materializer.materialize(
                                MEMORY_ID,
                                List.of(newItem(101L, "vector-101", "alpha", Map.of())),
                                List.of(newEntry())))
                .assertNext(
                        result -> {
                            assertThat(result.stats().semanticSameBatchHitCount()).isEqualTo(3);
                            assertThat(result.stats().semanticIntraBatchCandidateCount())
                                    .isEqualTo(4);
                            assertThat(result.stats().semanticIntraBatchPhaseDurationMs())
                                    .isEqualTo(12L);
                            assertThat(result.stats().semanticDegraded()).isTrue();
                        })
                .verifyComplete();
    }

    @Test
    void materializeShouldReportStage1EntityDiagnostics() {
        var graphOps = new InMemoryGraphOperations();
        var materializer = materializer(graphOps);
        var items = List.of(newItem(101L, "vector-101", "用户让我联系张三", Map.of()));
        var entries =
                List.of(
                        new ExtractedMemoryEntry(
                                "用户让我联系张三",
                                1.0f,
                                null,
                                null,
                                null,
                                null,
                                Instant.parse("2026-04-18T09:00:00Z"),
                                "raw-1",
                                null,
                                List.of(),
                                Map.of(),
                                MemoryItemType.FACT,
                                "event",
                                new ExtractedGraphHints(
                                        List.of(
                                                new ExtractedGraphHints.ExtractedEntityHint(
                                                        "张三", "人物", 0.95f),
                                                new ExtractedGraphHints.ExtractedEntityHint(
                                                        "今天", "concept", 0.80f),
                                                new ExtractedGraphHints.ExtractedEntityHint(
                                                        "未知机构", "未分类标签", 0.60f),
                                                new ExtractedGraphHints.ExtractedEntityHint(
                                                        "...", "concept", 0.40f),
                                                new ExtractedGraphHints.ExtractedEntityHint(
                                                        "助手", "organization", 0.30f)),
                                        List.of())));

        StepVerifier.create(materializer.materialize(MEMORY_ID, items, entries))
                .assertNext(
                        result ->
                                assertThat(result.stats())
                                        .extracting(
                                                ItemGraphMaterializationResult.Stats::entityCount,
                                                ItemGraphMaterializationResult.Stats::mentionCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::resolutionExactFallbackCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::typeFallbackToOtherCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::topUnresolvedTypeLabelsSummary,
                                                ItemGraphMaterializationResult.Stats
                                                        ::droppedPunctuationOnlyCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::droppedReservedSpecialCollisionCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::droppedTemporalCount)
                                        .containsExactly(2, 2, 2, 1, "未分类标签=1", 1, 1, 1))
                .verifyComplete();
    }

    @Test
    void materializeShouldReportTemporalStatsSeparatelyFromStructuredLinks() {
        var graphOps = new InMemoryGraphOperations();
        var items =
                List.of(
                        newItem(101L, "vector-101", "Cause item", Map.of()),
                        newItem(102L, "vector-102", "Effect item", Map.of()));
        var entries =
                List.of(entryWithSharedEntities(), entryWithSharedEntitiesAndCausalReference());
        var materializer =
                new DefaultItemGraphMaterializer(
                        new GraphHintNormalizer(),
                        new ExactCanonicalEntityResolutionStrategy(),
                        new EntityAliasIndexPlanner(),
                        new StructuredGraphPersister(graphOps),
                        new TemporalItemLinker(
                                new InMemoryItemOperations(),
                                graphOps,
                                new TemporalRelationClassifier(),
                                ItemGraphOptions.defaults().withEnabled(true)) {
                            @Override
                            public Mono<TemporalLinkingStats> link(
                                    MemoryId memoryId, List<MemoryItem> batchItems) {
                                return Mono.just(
                                        new TemporalLinkingStats(
                                                2, 1, 3, 1, 1, 1, 4L, 3L, 2L, false));
                            }
                        },
                        new SemanticItemLinker(
                                new InMemoryItemOperations(),
                                graphOps,
                                new StubMemoryVector(),
                                ItemGraphOptions.defaults().withEnabled(true)) {
                            @Override
                            public Mono<SemanticLinkingStats> link(
                                    MemoryId memoryId, List<MemoryItem> batchItems) {
                                return Mono.just(
                                        new SemanticLinkingStats(
                                                1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 2L, 1L, 1L,
                                                0L, false));
                            }
                        },
                        ItemGraphOptions.defaults().withEnabled(true));

        StepVerifier.create(materializer.materialize(MEMORY_ID, items, entries))
                .assertNext(
                        result -> {
                            assertThat(result.stats().structuredItemLinkCount()).isEqualTo(1);
                            assertThat(result.stats().temporalCreatedLinkCount()).isEqualTo(1);
                            assertThat(result.stats().semanticLinkCount()).isEqualTo(1);
                        })
                .verifyComplete();
    }

    @Test
    void temporalLinkerFailureStillReturnsStructuredStatsAndMarksTemporalDegraded() {
        var graphOps = new InMemoryGraphOperations();
        var temporalItemLinker =
                new TemporalItemLinker(
                        new InMemoryItemOperations(),
                        graphOps,
                        new TemporalRelationClassifier(),
                        ItemGraphOptions.defaults().withEnabled(true)) {
                    @Override
                    public Mono<TemporalLinkingStats> link(
                            MemoryId memoryId, List<MemoryItem> items) {
                        return Mono.error(
                                new IllegalStateException("simulated temporal linker failure"));
                    }
                };
        var semanticItemLinker =
                new SemanticItemLinker(
                        new InMemoryItemOperations(),
                        graphOps,
                        new StubMemoryVector(),
                        ItemGraphOptions.defaults().withEnabled(true)) {
                    @Override
                    public Mono<SemanticLinkingStats> link(
                            MemoryId memoryId, List<MemoryItem> items) {
                        return Mono.just(
                                new SemanticLinkingStats(
                                        1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 2L, 1L, 1L, 0L,
                                        false));
                    }
                };

        var materializer =
                new DefaultItemGraphMaterializer(
                        new GraphHintNormalizer(),
                        new ExactCanonicalEntityResolutionStrategy(),
                        new EntityAliasIndexPlanner(),
                        new StructuredGraphPersister(graphOps),
                        temporalItemLinker,
                        semanticItemLinker,
                        ItemGraphOptions.defaults().withEnabled(true));

        var items =
                List.of(
                        newItem(101L, "vector-101", "Cause item", Map.of()),
                        newItem(102L, "vector-102", "Effect item", Map.of()));
        var entries =
                List.of(entryWithSharedEntities(), entryWithSharedEntitiesAndCausalReference());

        StepVerifier.create(materializer.materialize(MEMORY_ID, items, entries))
                .assertNext(
                        result -> {
                            assertThat(result.stats().entityCount()).isEqualTo(2);
                            assertThat(result.stats().mentionCount()).isEqualTo(4);
                            assertThat(result.stats().structuredItemLinkCount()).isEqualTo(1);
                            assertThat(result.stats().temporalCreatedLinkCount()).isZero();
                            assertThat(result.stats().temporalDegraded()).isTrue();
                            assertThat(result.stats().semanticLinkCount()).isEqualTo(1);
                        })
                .verifyComplete();
    }

    @Test
    void materializeShouldExposeConservativeResolutionCounters() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntities(
                MEMORY_ID,
                List.of(
                        entity("organization:openai", "OpenAI"),
                        entity("organization:acme corp", "Acme Corp")));
        var materializer =
                new DefaultItemGraphMaterializer(
                        new GraphHintNormalizer(),
                        conservativeResolutionStrategy(graphOps),
                        new EntityAliasIndexPlanner(),
                        new StructuredGraphPersister(graphOps),
                        new TemporalItemLinker(
                                new InMemoryItemOperations(),
                                graphOps,
                                new TemporalRelationClassifier(),
                                ItemGraphOptions.defaults()
                                        .withEnabled(true)
                                        .withResolutionMode(EntityResolutionMode.CONSERVATIVE)),
                        new SemanticItemLinker(
                                new InMemoryItemOperations(),
                                graphOps,
                                new StubMemoryVector(),
                                ItemGraphOptions.defaults()
                                        .withEnabled(true)
                                        .withResolutionMode(EntityResolutionMode.CONSERVATIVE)),
                        ItemGraphOptions.defaults()
                                .withEnabled(true)
                                .withResolutionMode(EntityResolutionMode.CONSERVATIVE));

        var items =
                List.of(
                        newItem(101L, "vector-101", "User discussed OPENAI", Map.of()),
                        newItem(102L, "vector-102", "Acme Corporation closed the deal", Map.of()));
        var entries =
                List.of(
                        entry(
                                "User discussed OPENAI",
                                List.of(
                                        new ExtractedGraphHints.ExtractedEntityHint(
                                                "OPENAI", "organization", 0.90f)),
                                List.of()),
                        entry(
                                "Acme Corporation closed the deal",
                                List.of(
                                        new ExtractedGraphHints.ExtractedEntityHint(
                                                "Acme Corporation", "organization", 0.88f)),
                                List.of()));

        StepVerifier.create(materializer.materialize(MEMORY_ID, items, entries))
                .assertNext(
                        result -> {
                            assertThat(result.stats().entityCount()).isEqualTo(2);
                            assertThat(result.stats().mentionCount()).isEqualTo(2);
                            assertThat(result.stats().resolutionMergeAcceptedCount()).isEqualTo(2);
                            assertThat(result.stats().resolutionCreateNewCount()).isZero();
                            assertThat(result.stats().resolutionCandidateSourceSummary())
                                    .contains("exact_canonical_hit=1")
                                    .contains("safe_variant_hit=1");
                        })
                .verifyComplete();
    }

    @Test
    void materializeShouldPersistExplicitAliasAndUserDictionaryResolutionMetadata() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntities(MEMORY_ID, List.of(entity("organization:openai", "OpenAI")));
        var options =
                ItemGraphOptions.defaults()
                        .withEnabled(true)
                        .withResolutionMode(EntityResolutionMode.CONSERVATIVE)
                        .withUserAliasDictionary(
                                new UserAliasDictionary(
                                        true,
                                        Map.of(
                                                "organization|openai china",
                                                "organization:openai")));
        var materializer =
                new DefaultItemGraphMaterializer(
                        new GraphHintNormalizer(),
                        conservativeResolutionStrategy(graphOps),
                        new EntityAliasIndexPlanner(),
                        new StructuredGraphPersister(graphOps),
                        new TemporalItemLinker(
                                new InMemoryItemOperations(),
                                graphOps,
                                new TemporalRelationClassifier(),
                                options),
                        new SemanticItemLinker(
                                new InMemoryItemOperations(),
                                graphOps,
                                new StubMemoryVector(),
                                options),
                        options);

        var items =
                List.of(
                        newItem(101L, "vector-101", "开放人工智能（OpenAI）发布更新", Map.of()),
                        newItem(102L, "vector-102", "OpenAI China signed the deal", Map.of()));
        var entries =
                List.of(
                        entry(
                                "开放人工智能（OpenAI）发布更新",
                                List.of(
                                        new ExtractedGraphHints.ExtractedEntityHint(
                                                "开放人工智能",
                                                "organization",
                                                0.93f,
                                                List.of(
                                                        new EntityAliasObservation(
                                                                "OpenAI",
                                                                EntityAliasClass
                                                                        .EXPLICIT_PARENTHETICAL,
                                                                "entity_inline",
                                                                0.93f)))),
                                List.of()),
                        entry(
                                "OpenAI China signed the deal",
                                List.of(
                                        new ExtractedGraphHints.ExtractedEntityHint(
                                                "OpenAI China", "organization", 0.91f)),
                                List.of()));

        StepVerifier.create(materializer.materialize(MEMORY_ID, items, entries))
                .assertNext(
                        result -> {
                            assertThat(result.stats().entityCount()).isEqualTo(1);
                            assertThat(result.stats().mentionCount()).isEqualTo(2);
                            assertThat(result.stats().resolutionMergeAcceptedCount()).isEqualTo(2);
                            assertThat(result.stats().resolutionCreateNewCount()).isZero();
                            assertThat(result.stats().resolutionCandidateSourceSummary())
                                    .contains("explicit_alias_evidence_hit=1")
                                    .contains("user_dictionary_hit=1");
                        })
                .verifyComplete();

        assertThat(graphOps.listItemEntityMentions(MEMORY_ID))
                .filteredOn(mention -> mention.itemId() == 101L)
                .singleElement()
                .satisfies(
                        mention -> {
                            assertThat(mention.entityKey()).isEqualTo("organization:openai");
                            assertThat(mention.metadata())
                                    .containsEntry(
                                            "resolutionSource", "explicit_alias_evidence_hit")
                                    .containsEntry(
                                            "resolvedViaAliasClass", "explicit_parenthetical");
                        });
        assertThat(graphOps.listItemEntityMentions(MEMORY_ID))
                .filteredOn(mention -> mention.itemId() == 102L)
                .singleElement()
                .satisfies(
                        mention -> {
                            assertThat(mention.entityKey()).isEqualTo("organization:openai");
                            assertThat(mention.metadata())
                                    .containsEntry("resolutionSource", "user_dictionary_hit")
                                    .containsEntry("resolvedViaAliasClass", "user_dictionary");
                        });
        assertThat(graphOps.listEntities(MEMORY_ID))
                .singleElement()
                .satisfies(
                        entity -> {
                            assertThat(
                                            ((Number) entity.metadata().get("aliasEvidenceCount"))
                                                    .intValue())
                                    .isEqualTo(2);
                            assertThat((List<String>) entity.metadata().get("aliasClasses"))
                                    .containsExactly("explicit_parenthetical", "user_dictionary");
                            assertThat((List<String>) entity.metadata().get("topAliases"))
                                    .containsExactly("OpenAI", "OpenAI China");
                        });
    }

    @Test
    void materializeShouldPersistHistoricalAliasesAndReuseThemInLaterBatches() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntities(MEMORY_ID, List.of(entity("organization:google", "Google")));
        var options =
                ItemGraphOptions.defaults()
                        .withEnabled(true)
                        .withResolutionMode(EntityResolutionMode.CONSERVATIVE);
        var materializer =
                new DefaultItemGraphMaterializer(
                        new GraphHintNormalizer(),
                        conservativeResolutionStrategy(graphOps),
                        new EntityAliasIndexPlanner(),
                        new StructuredGraphPersister(graphOps),
                        new TemporalItemLinker(
                                new InMemoryItemOperations(),
                                graphOps,
                                new TemporalRelationClassifier(),
                                options),
                        new SemanticItemLinker(
                                new InMemoryItemOperations(),
                                graphOps,
                                new StubMemoryVector(),
                                options),
                        options);

        StepVerifier.create(
                        materializer.materialize(
                                MEMORY_ID,
                                List.of(newItem(101L, "vector-101", "谷歌（Google）发布更新", Map.of())),
                                List.of(
                                        entry(
                                                "谷歌（Google）发布更新",
                                                List.of(
                                                        new ExtractedGraphHints.ExtractedEntityHint(
                                                                "谷歌",
                                                                "organization",
                                                                0.93f,
                                                                List.of(
                                                                        new EntityAliasObservation(
                                                                                "Google",
                                                                                EntityAliasClass
                                                                                        .EXPLICIT_PARENTHETICAL,
                                                                                "entity_inline",
                                                                                0.93f)))),
                                                List.of()))))
                .assertNext(
                        result ->
                                assertThat(result.stats().resolutionCandidateSourceSummary())
                                        .contains("explicit_alias_evidence_hit=1"))
                .verifyComplete();

        assertThat(
                        graphOps.listEntityAliasesByNormalizedAlias(
                                MEMORY_ID, GraphEntityType.ORGANIZATION, "谷歌"))
                .singleElement()
                .extracting(GraphEntityAlias::entityKey, GraphEntityAlias::evidenceCount)
                .containsExactly("organization:google", 1);

        StepVerifier.create(
                        materializer.materialize(
                                MEMORY_ID,
                                List.of(newItem(102L, "vector-102", "谷歌签了新合同", Map.of())),
                                List.of(
                                        entry(
                                                "谷歌签了新合同",
                                                List.of(
                                                        new ExtractedGraphHints.ExtractedEntityHint(
                                                                "谷歌", "organization", 0.91f)),
                                                List.of()))))
                .assertNext(
                        result ->
                                assertThat(result.stats().resolutionCandidateSourceSummary())
                                        .contains("historical_alias_hit=1"))
                .verifyComplete();

        assertThat(graphOps.listItemEntityMentions(MEMORY_ID))
                .filteredOn(mention -> mention.itemId() == 102L)
                .singleElement()
                .satisfies(
                        mention ->
                                assertThat(mention.metadata())
                                        .containsEntry("resolutionSource", "historical_alias_hit"));
    }

    @Test
    void repeatedMaterializeShouldNotDuplicateMentionsLinksOrInflateCooccurrences() {
        var graphOps = new InMemoryGraphOperations();
        var materializer = materializer(graphOps);
        var items =
                List.of(
                        newItem(101L, "vector-101", "Cause item", Map.of()),
                        newItem(102L, "vector-102", "Effect item", Map.of()));
        var entries =
                List.of(entryWithSharedEntities(), entryWithSharedEntitiesAndCausalReference());

        StepVerifier.create(materializer.materialize(MEMORY_ID, items, entries))
                .assertNext(
                        result ->
                                assertThat(result.stats())
                                        .extracting(
                                                ItemGraphMaterializationResult.Stats::entityCount,
                                                ItemGraphMaterializationResult.Stats::mentionCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::structuredItemLinkCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::semanticSearchHitCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::semanticLinkCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::semanticSameBatchHitCount)
                                        .containsExactly(2, 4, 1, 0, 0, 0))
                .verifyComplete();
        StepVerifier.create(materializer.materialize(MEMORY_ID, items, entries))
                .assertNext(
                        result ->
                                assertThat(result.stats())
                                        .extracting(
                                                ItemGraphMaterializationResult.Stats::entityCount,
                                                ItemGraphMaterializationResult.Stats::mentionCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::structuredItemLinkCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::semanticSearchHitCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::semanticLinkCount,
                                                ItemGraphMaterializationResult.Stats
                                                        ::semanticSameBatchHitCount)
                                        .containsExactly(2, 4, 1, 0, 0, 0))
                .verifyComplete();

        assertThat(graphOps.listItemEntityMentions(MEMORY_ID)).hasSize(4);
        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .filteredOn(link -> link.linkType() != ItemLinkType.SEMANTIC)
                .hasSize(2);
        assertThat(graphOps.listEntityCooccurrences(MEMORY_ID))
                .singleElement()
                .extracting(EntityCooccurrence::cooccurrenceCount)
                .isEqualTo(2);
    }

    @Test
    void semanticDegradationDoesNotBreakStructuredGraphPersistence() {
        var graphOps = new SelectiveSemanticFailureGraphOperations();
        var itemOps = new InMemoryItemOperations();
        itemOps.insertItems(
                MEMORY_ID,
                List.of(
                        newItem(101L, "vector-101", "Cause item", Map.of()),
                        newItem(102L, "vector-102", "Effect item", Map.of()),
                        newItem(201L, "vector-201", "Existing semantic neighbor", Map.of())));

        var vector = new StubMemoryVector();
        vector.register(
                "Cause item",
                new VectorSearchResult(
                        "vector-201", "Existing semantic neighbor", 0.93f, Map.of()));

        var materializer =
                new DefaultItemGraphMaterializer(
                        new GraphHintNormalizer(),
                        new ExactCanonicalEntityResolutionStrategy(),
                        new EntityAliasIndexPlanner(),
                        new StructuredGraphPersister(graphOps),
                        new TemporalItemLinker(
                                itemOps,
                                graphOps,
                                new TemporalRelationClassifier(),
                                ItemGraphOptions.defaults()
                                        .withEnabled(true)
                                        .withSemanticSourceWindowSize(1)),
                        new SemanticItemLinker(
                                itemOps,
                                graphOps,
                                vector,
                                ItemGraphOptions.defaults()
                                        .withEnabled(true)
                                        .withSemanticSourceWindowSize(1)),
                        ItemGraphOptions.defaults()
                                .withEnabled(true)
                                .withSemanticSourceWindowSize(1));

        StepVerifier.create(
                        materializer.materialize(
                                MEMORY_ID,
                                List.of(
                                        newItem(101L, "vector-101", "Cause item", Map.of()),
                                        newItem(102L, "vector-102", "Effect item", Map.of())),
                                List.of(
                                        entryWithSharedEntities(),
                                        entryWithSharedEntitiesAndCausalReference())))
                .assertNext(
                        result -> {
                            assertThat(result.stats().entityCount()).isEqualTo(2);
                            assertThat(result.stats().mentionCount()).isEqualTo(4);
                            assertThat(result.stats().structuredItemLinkCount()).isEqualTo(1);
                            assertThat(result.stats().semanticSearchRequestCount()).isEqualTo(2);
                            assertThat(result.stats().semanticLinkCount()).isEqualTo(1);
                            assertThat(result.stats().semanticFailedUpsertBatchCount())
                                    .isEqualTo(1);
                            assertThat(result.stats().semanticDegraded()).isTrue();
                        })
                .verifyComplete();

        assertThat(graphOps.listItemEntityMentions(MEMORY_ID)).hasSize(4);
        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .filteredOn(link -> link.linkType() == ItemLinkType.CAUSAL)
                .hasSize(1);
    }

    @Test
    void semanticLinkerFailureStillReturnsStructuredStatsAndMarksSemanticDegraded() {
        var graphOps = new InMemoryGraphOperations();
        var semanticItemLinker =
                new SemanticItemLinker(
                        new InMemoryItemOperations(),
                        graphOps,
                        new StubMemoryVector(),
                        ItemGraphOptions.defaults()
                                .withEnabled(true)
                                .withSemanticSourceWindowSize(1)) {
                    @Override
                    public Mono<SemanticLinkingStats> link(
                            MemoryId memoryId, List<MemoryItem> items) {
                        return Mono.error(
                                new IllegalStateException("simulated semantic linker failure"));
                    }
                };

        var materializer =
                new DefaultItemGraphMaterializer(
                        new GraphHintNormalizer(),
                        new ExactCanonicalEntityResolutionStrategy(),
                        new EntityAliasIndexPlanner(),
                        new StructuredGraphPersister(graphOps),
                        new TemporalItemLinker(
                                new InMemoryItemOperations(),
                                graphOps,
                                new TemporalRelationClassifier(),
                                ItemGraphOptions.defaults()
                                        .withEnabled(true)
                                        .withSemanticSourceWindowSize(1)),
                        semanticItemLinker,
                        ItemGraphOptions.defaults()
                                .withEnabled(true)
                                .withSemanticSourceWindowSize(1));

        var items =
                List.of(
                        newItem(101L, "vector-101", "Cause item", Map.of()),
                        newItem(102L, "vector-102", "Effect item", Map.of()));
        var entries =
                List.of(entryWithSharedEntities(), entryWithSharedEntitiesAndCausalReference());

        StepVerifier.create(materializer.materialize(MEMORY_ID, items, entries))
                .assertNext(
                        result -> {
                            assertThat(result.stats().entityCount()).isEqualTo(2);
                            assertThat(result.stats().mentionCount()).isEqualTo(4);
                            assertThat(result.stats().structuredItemLinkCount()).isEqualTo(1);
                            assertThat(result.stats().semanticSearchRequestCount()).isZero();
                            assertThat(result.stats().semanticLinkCount()).isZero();
                            assertThat(result.stats().semanticDegraded()).isTrue();
                        })
                .verifyComplete();

        assertThat(graphOps.listItemEntityMentions(MEMORY_ID)).hasSize(4);
        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .filteredOn(link -> link.linkType() != ItemLinkType.SEMANTIC)
                .hasSize(2);
    }

    private static DefaultItemGraphMaterializer materializer(InMemoryGraphOperations graphOps) {
        return new DefaultItemGraphMaterializer(
                new GraphHintNormalizer(),
                new ExactCanonicalEntityResolutionStrategy(),
                new EntityAliasIndexPlanner(),
                new StructuredGraphPersister(graphOps),
                new TemporalItemLinker(
                        new InMemoryItemOperations(),
                        graphOps,
                        new TemporalRelationClassifier(),
                        ItemGraphOptions.defaults().withEnabled(true)),
                new SemanticItemLinker(
                        new InMemoryItemOperations(),
                        graphOps,
                        new StubMemoryVector(),
                        ItemGraphOptions.defaults().withEnabled(true)),
                ItemGraphOptions.defaults().withEnabled(true));
    }

    private static ConservativeHeuristicEntityResolutionStrategy conservativeResolutionStrategy(
            InMemoryGraphOperations graphOps) {
        return new ConservativeHeuristicEntityResolutionStrategy(
                graphOps,
                new DefaultEntityCandidateRetriever(
                        graphOps, new EntityVariantKeyGenerator(), true));
    }

    private static com.openmemind.ai.memory.core.store.graph.GraphEntity entity(
            String entityKey, String displayName) {
        return new com.openmemind.ai.memory.core.store.graph.GraphEntity(
                entityKey,
                MEMORY_ID.toIdentifier(),
                displayName,
                com.openmemind.ai.memory.core.store.graph.GraphEntityType.ORGANIZATION,
                Map.of(),
                CREATED_AT,
                CREATED_AT);
    }

    private static MemoryItem existingItem(Long id, String vectorId, String content) {
        return newItem(id, vectorId, content, Map.of());
    }

    private static MemoryItem newItem(
            Long id, String vectorId, String content, Map<String, Object> metadata) {
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
                metadata,
                CREATED_AT,
                MemoryItemType.FACT);
    }

    private static ExtractedMemoryEntry newEntry() {
        return new ExtractedMemoryEntry(
                "User discussed OpenAI deployment",
                1.0f,
                null,
                null,
                null,
                null,
                CREATED_AT,
                "raw-1",
                null,
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event",
                ExtractedGraphHints.empty());
    }

    private static ExtractedMemoryEntry entry(
            String content,
            List<ExtractedGraphHints.ExtractedEntityHint> entityHints,
            List<ExtractedGraphHints.ExtractedCausalRelationHint> causalHints) {
        return new ExtractedMemoryEntry(
                content,
                1.0f,
                null,
                null,
                null,
                null,
                CREATED_AT,
                "raw-" + Math.abs(content.hashCode()),
                null,
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event",
                new ExtractedGraphHints(entityHints, causalHints));
    }

    private static ExtractedMemoryEntry entryWithSharedEntities() {
        return new ExtractedMemoryEntry(
                "Cause item",
                1.0f,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-16T09:00:00Z"),
                "raw-1",
                null,
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event",
                new ExtractedGraphHints(
                        List.of(
                                new ExtractedGraphHints.ExtractedEntityHint(
                                        "OpenAI", "organization", 0.95f),
                                new ExtractedGraphHints.ExtractedEntityHint(
                                        "Sam Altman", "person", 0.88f)),
                        List.of()));
    }

    private static ExtractedMemoryEntry entryWithSharedEntitiesAndCausalReference() {
        return new ExtractedMemoryEntry(
                "Effect item",
                1.0f,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-16T10:00:00Z"),
                "raw-2",
                null,
                List.of(),
                Map.of(),
                MemoryItemType.FACT,
                "event",
                new ExtractedGraphHints(
                        List.of(
                                new ExtractedGraphHints.ExtractedEntityHint(
                                        "OpenAI", "organization", 0.95f),
                                new ExtractedGraphHints.ExtractedEntityHint(
                                        "Sam Altman", "person", 0.88f)),
                        List.of(
                                new ExtractedGraphHints.ExtractedCausalRelationHint(
                                        0, "caused_by", 0.91f))));
    }

    private static final class StubMemoryVector implements MemoryVector {

        private final Map<String, List<VectorSearchResult>> results = new HashMap<>();

        private void register(String query, VectorSearchResult result) {
            results.put(query, List.of(result));
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.fromIterable(results.getOrDefault(query, List.of()));
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

    private static final class SelectiveSemanticFailureGraphOperations
            extends InMemoryGraphOperations {

        @Override
        public void upsertItemLinks(MemoryId memoryId, List<ItemLink> links) {
            if (links.stream().anyMatch(link -> link.linkType() == ItemLinkType.SEMANTIC)) {
                throw new IllegalStateException("simulated semantic persistence failure");
            }
            super.upsertItemLinks(memoryId, links);
        }
    }
}
