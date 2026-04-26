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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasClass;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityResolutionMode;
import com.openmemind.ai.memory.core.extraction.item.graph.UserAliasDictionary;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.GraphEntityNormalizationDiagnostics;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.NormalizedEntityMentionCandidate;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.NormalizedGraphBatch;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConservativeHeuristicEntityResolutionStrategyTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-18T00:00:00Z");

    @Test
    void conservativeResolverShouldMergeExactAndSupportedSameScriptSafeVariants() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntities(
                MEMORY_ID,
                List.of(
                        entity("organization:openai", "OpenAI"),
                        entity("organization:acme corp", "Acme Corp")));

        var resolver =
                new ConservativeHeuristicEntityResolutionStrategy(
                        graphOps,
                        new DefaultEntityCandidateRetriever(
                                graphOps, new EntityVariantKeyGenerator(), true));
        var resolved =
                resolver.resolve(
                        MEMORY_ID,
                        normalizedBatch(
                                candidate(
                                        101L,
                                        "OPENAI",
                                        "organization",
                                        "openai",
                                        "OPENAI",
                                        GraphEntityType.ORGANIZATION,
                                        "organization:openai",
                                        0.90f),
                                candidate(
                                        102L,
                                        "Acme Corporation",
                                        "organization",
                                        "acme corporation",
                                        "Acme Corporation",
                                        GraphEntityType.ORGANIZATION,
                                        "organization:acme corporation",
                                        0.88f)),
                        conservativeOptions());

        assertThat(resolved.mentions())
                .extracting(ItemEntityMention::entityKey)
                .containsExactly("organization:openai", "organization:acme corp");
        assertThat(resolved.mentions().getFirst().metadata())
                .containsEntry("resolutionSource", "exact_canonical_hit");
        assertThat(resolved.mentions().get(1).metadata())
                .containsEntry("resolutionSource", "safe_variant_hit")
                .containsEntry("resolvedViaAliasClass", "org_suffix");
        assertThat(resolved.resolutionDiagnostics().mergeAcceptedCount()).isEqualTo(2);
        assertThat(resolved.resolutionDiagnostics().createNewCount()).isZero();
        assertThat(resolved.resolutionDiagnostics().candidateSourceCounts())
                .containsEntry(EntityResolutionSource.EXACT_CANONICAL_HIT, 1)
                .containsEntry(EntityResolutionSource.SAFE_VARIANT_HIT, 1);
    }

    @Test
    void conservativeResolverShouldStayDeterministicWhenScoresTie() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntities(
                MEMORY_ID,
                List.of(
                        entity("organization:open ai", "Open AI"),
                        entity("organization:openai", "OpenAI")));

        var resolver =
                new ConservativeHeuristicEntityResolutionStrategy(
                        graphOps,
                        new DefaultEntityCandidateRetriever(
                                graphOps, new EntityVariantKeyGenerator(), true));
        var resolved =
                resolver.resolve(
                        MEMORY_ID,
                        normalizedBatch(
                                candidate(
                                        101L,
                                        "Open-AI",
                                        "organization",
                                        "open-ai",
                                        "Open-AI",
                                        GraphEntityType.ORGANIZATION,
                                        "organization:open-ai",
                                        0.90f)),
                        conservativeOptions());

        assertThat(resolved.mentions().getFirst().entityKey()).isEqualTo("organization:open ai");
        assertThat(resolved.resolutionDiagnostics().candidateRejectCounts()).isEmpty();
        assertThat(resolved.resolutionDiagnostics().mergeAcceptedCount()).isEqualTo(1);
    }

    @Test
    void conservativeResolverShouldNotImplicitlyMergeSameBatchNewVariantsWithoutExistingEntity() {
        var graphOps = new InMemoryGraphOperations();
        var resolver =
                new ConservativeHeuristicEntityResolutionStrategy(
                        graphOps,
                        new DefaultEntityCandidateRetriever(
                                graphOps, new EntityVariantKeyGenerator(), true));

        var forward =
                resolver.resolve(
                        MEMORY_ID,
                        normalizedBatch(
                                candidate(
                                        101L,
                                        "Open AI",
                                        "organization",
                                        "open ai",
                                        "Open AI",
                                        GraphEntityType.ORGANIZATION,
                                        "organization:open ai",
                                        0.90f),
                                candidate(
                                        102L,
                                        "OpenAI",
                                        "organization",
                                        "openai",
                                        "OpenAI",
                                        GraphEntityType.ORGANIZATION,
                                        "organization:openai",
                                        0.91f)),
                        conservativeOptions());

        var reversed =
                resolver.resolve(
                        MEMORY_ID,
                        normalizedBatch(
                                candidate(
                                        102L,
                                        "OpenAI",
                                        "organization",
                                        "openai",
                                        "OpenAI",
                                        GraphEntityType.ORGANIZATION,
                                        "organization:openai",
                                        0.91f),
                                candidate(
                                        101L,
                                        "Open AI",
                                        "organization",
                                        "open ai",
                                        "Open AI",
                                        GraphEntityType.ORGANIZATION,
                                        "organization:open ai",
                                        0.90f)),
                        conservativeOptions());

        assertThat(forward.mentions())
                .extracting(ItemEntityMention::entityKey)
                .containsExactlyInAnyOrder("organization:open ai", "organization:openai");
        assertThat(reversed.mentions())
                .extracting(ItemEntityMention::entityKey)
                .containsExactlyInAnyOrder("organization:open ai", "organization:openai");
        assertThat(forward.resolutionDiagnostics().mergeAcceptedCount()).isZero();
        assertThat(reversed.resolutionDiagnostics().mergeAcceptedCount()).isZero();
        assertThat(forward.resolutionDiagnostics().createNewCount()).isEqualTo(2);
        assertThat(reversed.resolutionDiagnostics().createNewCount()).isEqualTo(2);
    }

    @Test
    void conservativeResolverShouldMergeExplicitParentheticalAliasEvidence() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntities(MEMORY_ID, List.of(entity("organization:openai", "OpenAI")));

        var resolved =
                conservativeResolver(graphOps)
                        .resolve(
                                MEMORY_ID,
                                normalizedBatch(
                                        candidate(
                                                101L,
                                                "开放人工智能",
                                                "organization",
                                                "开放人工智能",
                                                "开放人工智能",
                                                GraphEntityType.ORGANIZATION,
                                                "organization:开放人工智能",
                                                0.93f,
                                                alias(
                                                        "OpenAI",
                                                        EntityAliasClass.EXPLICIT_PARENTHETICAL))),
                                conservativeOptions());

        assertThat(resolved.mentions().getFirst().entityKey()).isEqualTo("organization:openai");
        assertThat(resolved.mentions().getFirst().metadata())
                .containsEntry("resolutionSource", "explicit_alias_evidence_hit")
                .containsEntry("resolvedViaAliasClass", "explicit_parenthetical");
        assertThat(resolved.resolutionDiagnostics().candidateSourceCounts())
                .containsEntry(EntityResolutionSource.EXPLICIT_ALIAS_EVIDENCE_HIT, 1);
    }

    @Test
    void conservativeResolverShouldUseUserDictionaryOnlyWhenEnabled() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntities(MEMORY_ID, List.of(entity("organization:openai", "OpenAI")));
        var candidate =
                candidate(
                        101L,
                        "OpenAI China",
                        "organization",
                        "openai china",
                        "OpenAI China",
                        GraphEntityType.ORGANIZATION,
                        "organization:openai china",
                        0.91f);

        var disabled =
                conservativeResolver(graphOps)
                        .resolve(MEMORY_ID, normalizedBatch(candidate), conservativeOptions());

        var enabled =
                conservativeResolver(graphOps)
                        .resolve(
                                MEMORY_ID,
                                normalizedBatch(candidate),
                                conservativeOptions()
                                        .withUserAliasDictionary(
                                                new UserAliasDictionary(
                                                        true,
                                                        Map.of(
                                                                "organization|openai china",
                                                                "organization:openai"))));

        assertThat(disabled.mentions().getFirst().entityKey())
                .isEqualTo("organization:openai china");
        assertThat(enabled.mentions().getFirst().entityKey()).isEqualTo("organization:openai");
        assertThat(enabled.mentions().getFirst().metadata())
                .containsEntry("resolutionSource", "user_dictionary_hit")
                .containsEntry("resolvedViaAliasClass", "user_dictionary");
        assertThat(enabled.resolutionDiagnostics().candidateSourceCounts())
                .containsEntry(EntityResolutionSource.USER_DICTIONARY_HIT, 1);
    }

    @Test
    void explicitAliasEvidenceMustNotCrossTypeProbeReservedSpecialEntities() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntities(
                MEMORY_ID, List.of(entity("special:user", "用户", GraphEntityType.SPECIAL)));

        var resolved =
                conservativeResolver(graphOps)
                        .resolve(
                                MEMORY_ID,
                                normalizedBatch(
                                        candidate(
                                                101L,
                                                "客户",
                                                "person",
                                                "客户",
                                                "客户",
                                                GraphEntityType.PERSON,
                                                "person:客户",
                                                0.92f,
                                                alias(
                                                        "用户",
                                                        EntityAliasClass.EXPLICIT_PARENTHETICAL))),
                                conservativeOptions());

        assertThat(resolved.mentions().getFirst().entityKey()).isEqualTo("person:客户");
        assertThat(resolved.resolutionDiagnostics().candidateRejectCounts()).isEmpty();
        assertThat(resolved.resolutionDiagnostics().candidateCount()).isZero();
    }

    @Test
    void conservativeResolverShouldPersistResolverSynthesizedAliasEvidenceForSafeVariantMerges() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntities(MEMORY_ID, List.of(entity("organization:openai", "OpenAI")));

        var resolved =
                conservativeResolver(graphOps)
                        .resolve(
                                MEMORY_ID,
                                normalizedBatch(
                                        candidate(
                                                101L,
                                                "Open AI",
                                                "organization",
                                                "open ai",
                                                "Open AI",
                                                GraphEntityType.ORGANIZATION,
                                                "organization:open ai",
                                                0.93f)),
                                conservativeOptions());

        assertThat(
                        ((Number)
                                        resolved.entities()
                                                .getFirst()
                                                .metadata()
                                                .get("aliasEvidenceCount"))
                                .intValue())
                .isEqualTo(1);
        assertThat((List<String>) resolved.entities().getFirst().metadata().get("aliasClasses"))
                .containsExactly("spacing");
        assertThat((List<String>) resolved.entities().getFirst().metadata().get("topAliases"))
                .containsExactly("Open AI");
    }

    @Test
    void conservativeResolverShouldMergeUniqueHistoricalAliasHits() {
        var graphOps = new InMemoryGraphOperations();
        graphOps.upsertEntities(MEMORY_ID, List.of(entity("organization:google", "Google")));
        graphOps.upsertEntityAliases(
                MEMORY_ID,
                List.of(
                        new GraphEntityAlias(
                                MEMORY_ID.toIdentifier(),
                                "organization:google",
                                GraphEntityType.ORGANIZATION,
                                "谷歌",
                                1,
                                Map.of(),
                                CREATED_AT,
                                CREATED_AT)));

        var resolver =
                new ConservativeHeuristicEntityResolutionStrategy(
                        graphOps,
                        new DefaultEntityCandidateRetriever(
                                graphOps, new EntityVariantKeyGenerator(), true));

        var resolved =
                resolver.resolve(
                        MEMORY_ID,
                        normalizedBatch(
                                candidate(
                                        101L,
                                        "谷歌",
                                        "organization",
                                        "谷歌",
                                        "谷歌",
                                        GraphEntityType.ORGANIZATION,
                                        "organization:谷歌",
                                        0.93f)),
                        conservativeOptions());

        assertThat(resolved.mentions().getFirst().entityKey()).isEqualTo("organization:google");
        assertThat(resolved.mentions().getFirst().metadata())
                .containsEntry("resolutionSource", "historical_alias_hit");
        assertThat(resolved.resolutionDiagnostics().candidateSourceCounts())
                .containsEntry(EntityResolutionSource.HISTORICAL_ALIAS_HIT, 1);
    }

    private static ItemGraphOptions conservativeOptions() {
        return ItemGraphOptions.defaults()
                .withEnabled(true)
                .withResolutionMode(EntityResolutionMode.CONSERVATIVE);
    }

    private static ConservativeHeuristicEntityResolutionStrategy conservativeResolver(
            InMemoryGraphOperations graphOps) {
        return new ConservativeHeuristicEntityResolutionStrategy(
                graphOps,
                new DefaultEntityCandidateRetriever(
                        graphOps, new EntityVariantKeyGenerator(), true));
    }

    private static NormalizedGraphBatch normalizedBatch(
            NormalizedEntityMentionCandidate... candidates) {
        return new NormalizedGraphBatch(
                List.of(candidates), List.of(), GraphEntityNormalizationDiagnostics.empty());
    }

    private static NormalizedEntityMentionCandidate candidate(
            long itemId,
            String rawName,
            String rawTypeLabel,
            String normalizedName,
            String displayName,
            GraphEntityType entityType,
            String preResolutionEntityKey,
            float salience) {
        return new NormalizedEntityMentionCandidate(
                itemId,
                MEMORY_ID.toIdentifier(),
                rawName,
                rawTypeLabel,
                normalizedName,
                displayName,
                entityType,
                preResolutionEntityKey,
                salience,
                List.of(),
                CREATED_AT);
    }

    private static NormalizedEntityMentionCandidate candidate(
            long itemId,
            String rawName,
            String rawTypeLabel,
            String normalizedName,
            String displayName,
            GraphEntityType entityType,
            String preResolutionEntityKey,
            float salience,
            EntityAliasObservation... aliasObservations) {
        return new NormalizedEntityMentionCandidate(
                itemId,
                MEMORY_ID.toIdentifier(),
                rawName,
                rawTypeLabel,
                normalizedName,
                displayName,
                entityType,
                preResolutionEntityKey,
                salience,
                List.of(aliasObservations),
                CREATED_AT);
    }

    private static EntityAliasObservation alias(String aliasSurface, EntityAliasClass aliasClass) {
        return new EntityAliasObservation(aliasSurface, aliasClass, "entity_inline", 0.93f);
    }

    private static GraphEntity entity(String entityKey, String displayName) {
        return entity(entityKey, displayName, GraphEntityType.ORGANIZATION);
    }

    private static GraphEntity entity(
            String entityKey, String displayName, GraphEntityType entityType) {
        return new GraphEntity(
                entityKey,
                MEMORY_ID.toIdentifier(),
                displayName,
                entityType,
                Map.of(),
                CREATED_AT,
                CREATED_AT);
    }
}
