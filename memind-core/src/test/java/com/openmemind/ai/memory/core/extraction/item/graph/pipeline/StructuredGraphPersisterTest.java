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

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.EntityDropReason;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.GraphEntityNormalizationDiagnostics;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionDiagnostics;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionRejectReason;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionSource;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.ResolvedGraphBatch;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.InMemoryGraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuredGraphPersisterTest {

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");
    private static final Instant CREATED_AT = Instant.parse("2026-04-16T00:00:00Z");

    @Test
    void persistShouldUpsertStructuredRecordsRebuildAffectedCooccurrencesAndSummarizeDiagnostics() {
        var graphOps = new RecordingGraphOperations();
        var batch =
                new ResolvedGraphBatch(
                        List.of(
                                entity(
                                        "organization:openai",
                                        "OpenAI",
                                        GraphEntityType.ORGANIZATION),
                                entity("person:sam altman", "Sam Altman", GraphEntityType.PERSON)),
                        List.of(
                                alias(
                                        "organization:openai",
                                        GraphEntityType.ORGANIZATION,
                                        "openai"),
                                alias(
                                        "organization:openai",
                                        GraphEntityType.ORGANIZATION,
                                        "openai china")),
                        List.of(
                                mention(101L, "organization:openai", "openai"),
                                mention(101L, "person:sam altman", "sam altman"),
                                mention(102L, "organization:openai", "openai")),
                        List.of(
                                new ItemLink(
                                        MEMORY_ID.toIdentifier(),
                                        101L,
                                        102L,
                                        ItemLinkType.CAUSAL,
                                        0.91d,
                                        Map.of("relationType", "caused_by"),
                                        CREATED_AT)),
                        new GraphEntityNormalizationDiagnostics(
                                3,
                                Map.of(
                                        EntityDropReason.TEMPORAL, 2,
                                        EntityDropReason.PUNCTUATION_ONLY, 1,
                                        EntityDropReason.RESERVED_SPECIAL_COLLISION, 1),
                                unresolvedTypeLabels("alpha", 1, "beta", 2)),
                        new EntityResolutionDiagnostics(
                                4,
                                Map.of(
                                        EntityResolutionSource.EXPLICIT_ALIAS_EVIDENCE_HIT, 2,
                                        EntityResolutionSource.USER_DICTIONARY_HIT, 1),
                                Map.of(EntityResolutionRejectReason.BELOW_THRESHOLD, 1),
                                2,
                                1,
                                0,
                                0,
                                0,
                                2,
                                2,
                                0,
                                "0.90-0.99=2"));

        var stats = new StructuredGraphPersister(graphOps).persistStructuredGraph(MEMORY_ID, batch);

        assertThat(graphOps.listEntities(MEMORY_ID))
                .extracting(GraphEntity::entityKey, GraphEntity::displayName)
                .containsExactly(
                        tuple("organization:openai", "OpenAI"),
                        tuple("person:sam altman", "Sam Altman"));
        assertThat(graphOps.listItemEntityMentions(MEMORY_ID))
                .extracting(ItemEntityMention::itemId, ItemEntityMention::entityKey)
                .containsExactly(
                        tuple(101L, "organization:openai"),
                        tuple(101L, "person:sam altman"),
                        tuple(102L, "organization:openai"));
        assertThat(graphOps.listEntityAliases(MEMORY_ID))
                .extracting(GraphEntityAlias::entityKey, GraphEntityAlias::normalizedAlias)
                .containsExactly(
                        tuple("organization:openai", "openai"),
                        tuple("organization:openai", "openai china"));
        assertThat(graphOps.listItemLinks(MEMORY_ID))
                .extracting(ItemLink::linkType, ItemLink::sourceItemId, ItemLink::targetItemId)
                .containsExactly(tuple(ItemLinkType.CAUSAL, 101L, 102L));
        assertThat(graphOps.listEntityCooccurrences(MEMORY_ID))
                .extracting(
                        cooccurrence -> cooccurrence.leftEntityKey(),
                        cooccurrence -> cooccurrence.rightEntityKey(),
                        cooccurrence -> cooccurrence.cooccurrenceCount())
                .containsExactly(tuple("organization:openai", "person:sam altman", 1));
        assertThat(graphOps.rebuildRequests)
                .singleElement()
                .satisfies(
                        keys ->
                                assertThat(keys)
                                        .containsExactly(
                                                "organization:openai", "person:sam altman"));

        assertThat(stats)
                .extracting(
                        StructuredGraphPersister.StructuredGraphStats::entityCount,
                        StructuredGraphPersister.StructuredGraphStats::mentionCount,
                        StructuredGraphPersister.StructuredGraphStats::structuredItemLinkCount,
                        StructuredGraphPersister.StructuredGraphStats::typeFallbackToOtherCount,
                        StructuredGraphPersister.StructuredGraphStats
                                ::topUnresolvedTypeLabelsSummary,
                        StructuredGraphPersister.StructuredGraphStats::droppedTemporalCount,
                        StructuredGraphPersister.StructuredGraphStats::droppedPunctuationOnlyCount,
                        StructuredGraphPersister.StructuredGraphStats
                                ::droppedReservedSpecialCollisionCount)
                .containsExactly(2, 3, 1, 3, "beta=2, alpha=1", 2, 1, 1);
        assertThat(stats.resolutionDiagnostics().candidateSourceCounts())
                .containsEntry(EntityResolutionSource.EXPLICIT_ALIAS_EVIDENCE_HIT, 2);
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

    private static GraphEntityAlias alias(
            String entityKey, GraphEntityType entityType, String normalizedAlias) {
        return new GraphEntityAlias(
                MEMORY_ID.toIdentifier(),
                entityKey,
                entityType,
                normalizedAlias,
                1,
                Map.of("source", "item_extraction"),
                CREATED_AT,
                CREATED_AT);
    }

    private static ItemEntityMention mention(Long itemId, String entityKey, String normalizedName) {
        return new ItemEntityMention(
                MEMORY_ID.toIdentifier(),
                itemId,
                entityKey,
                0.95f,
                Map.of(
                        "normalizedName",
                        normalizedName,
                        "resolutionSource",
                        EntityResolutionSource.EXPLICIT_ALIAS_EVIDENCE_HIT.name().toLowerCase()),
                CREATED_AT);
    }

    private static final class RecordingGraphOperations extends InMemoryGraphOperations {

        private final List<LinkedHashSet<String>> rebuildRequests = new ArrayList<>();

        @Override
        public void rebuildEntityCooccurrences(
                MemoryId memoryId, java.util.Collection<String> entityKeys) {
            rebuildRequests.add(new LinkedHashSet<>(entityKeys));
            super.rebuildEntityCooccurrences(memoryId, entityKeys);
        }
    }

    private static Map<String, Integer> unresolvedTypeLabels(
            String firstLabel, int firstCount, String secondLabel, int secondCount) {
        var labels = new LinkedHashMap<String, Integer>();
        labels.put(firstLabel, firstCount);
        labels.put(secondLabel, secondCount);
        return labels;
    }
}
