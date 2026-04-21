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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.EntityDropReason;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionDiagnostics;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.ResolvedGraphBatch;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.causal.CausalItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.causal.CausalRelationCode;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticEvidenceSource;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.temporal.TemporalItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.temporal.TemporalRelationCode;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Persists the structured entity graph stage and returns bounded stage statistics.
 */
public final class StructuredGraphPersister {

    private final GraphOperations graphOperations;

    public StructuredGraphPersister(GraphOperations graphOperations) {
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
    }

    ItemGraphWritePlan toWritePlan(ResolvedGraphBatch batch) {
        var resolved = batch == null ? ResolvedGraphBatch.empty() : batch;
        return ItemGraphWritePlan.builder()
                .entities(resolved.entities())
                .mentions(resolved.mentions())
                .aliases(resolved.entityAliases())
                .semanticRelations(
                        resolved.itemLinks().stream()
                                .filter(link -> link.linkType() == ItemLinkType.SEMANTIC)
                                .map(StructuredGraphPersister::toSemanticRelation)
                                .toList())
                .temporalRelations(
                        resolved.itemLinks().stream()
                                .filter(link -> link.linkType() == ItemLinkType.TEMPORAL)
                                .map(StructuredGraphPersister::toTemporalRelation)
                                .toList())
                .causalRelations(
                        resolved.itemLinks().stream()
                                .filter(link -> link.linkType() == ItemLinkType.CAUSAL)
                                .map(StructuredGraphPersister::toCausalRelation)
                                .toList())
                .diagnostics(
                        Map.of(
                                "resolutionDiagnostics",
                                resolved.resolutionDiagnostics(),
                                "normalizationDiagnostics",
                                resolved.diagnostics()))
                .build();
    }

    StructuredGraphStats persistStructuredGraph(MemoryId memoryId, ResolvedGraphBatch batch) {
        graphOperations.upsertEntities(memoryId, batch.entities());
        graphOperations.upsertItemEntityMentions(memoryId, batch.mentions());
        graphOperations.upsertEntityAliases(memoryId, batch.entityAliases());
        graphOperations.upsertItemLinks(memoryId, batch.itemLinks());

        List<String> affectedEntityKeys =
                batch.mentions().stream().map(ItemEntityMention::entityKey).distinct().toList();
        if (!affectedEntityKeys.isEmpty()) {
            graphOperations.rebuildEntityCooccurrences(memoryId, affectedEntityKeys);
        }

        Map<EntityDropReason, Integer> droppedEntityCounts =
                batch.diagnostics().droppedEntityCounts();
        return new StructuredGraphStats(
                batch.entities().size(),
                batch.mentions().size(),
                batch.itemLinks().size(),
                batch.resolutionDiagnostics(),
                batch.diagnostics().typeFallbackToOtherCount(),
                summarizeTopUnresolvedTypeLabels(batch.diagnostics().topUnresolvedTypeLabels(), 5),
                droppedEntityCounts.getOrDefault(EntityDropReason.BLANK, 0),
                droppedEntityCounts.getOrDefault(EntityDropReason.PUNCTUATION_ONLY, 0),
                droppedEntityCounts.getOrDefault(EntityDropReason.PRONOUN_LIKE, 0),
                droppedEntityCounts.getOrDefault(EntityDropReason.TEMPORAL, 0),
                droppedEntityCounts.getOrDefault(EntityDropReason.DATE_LIKE, 0),
                droppedEntityCounts.getOrDefault(EntityDropReason.RESERVED_SPECIAL_COLLISION, 0));
    }

    private static String summarizeTopUnresolvedTypeLabels(
            Map<String, Integer> topUnresolvedTypeLabels, int maxEntries) {
        Map<String, Integer> trackedLabels =
                topUnresolvedTypeLabels == null ? Map.of() : topUnresolvedTypeLabels;
        if (trackedLabels.isEmpty()) {
            return "";
        }
        return trackedLabels.entrySet().stream()
                .sorted(
                        Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                                .reversed()
                                .thenComparing(Map.Entry::getKey))
                .limit(maxEntries)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static SemanticItemRelation toSemanticRelation(ItemLink link) {
        return new SemanticItemRelation(
                link.sourceItemId(),
                link.targetItemId(),
                SemanticEvidenceSource.fromCode(
                        link.evidenceSource() == null ? "vector_search" : link.evidenceSource()),
                link.strength() == null ? 1.0d : link.strength());
    }

    private static TemporalItemRelation toTemporalRelation(ItemLink link) {
        return new TemporalItemRelation(
                link.sourceItemId(),
                link.targetItemId(),
                TemporalRelationCode.fromCode(
                        link.relationCode() == null ? "before" : link.relationCode()),
                link.strength() == null ? 1.0d : link.strength());
    }

    private static CausalItemRelation toCausalRelation(ItemLink link) {
        return new CausalItemRelation(
                link.sourceItemId(),
                link.targetItemId(),
                CausalRelationCode.fromCode(
                        link.relationCode() == null ? "caused_by" : link.relationCode()),
                link.strength() == null ? 1.0d : link.strength());
    }

    record StructuredGraphStats(
            int entityCount,
            int mentionCount,
            int structuredItemLinkCount,
            EntityResolutionDiagnostics resolutionDiagnostics,
            int typeFallbackToOtherCount,
            String topUnresolvedTypeLabelsSummary,
            int droppedBlankCount,
            int droppedPunctuationOnlyCount,
            int droppedPronounLikeCount,
            int droppedTemporalCount,
            int droppedDateLikeCount,
            int droppedReservedSpecialCollisionCount) {

        StructuredGraphStats {
            resolutionDiagnostics =
                    resolutionDiagnostics == null
                            ? EntityResolutionDiagnostics.empty()
                            : resolutionDiagnostics;
            topUnresolvedTypeLabelsSummary =
                    topUnresolvedTypeLabelsSummary == null ? "" : topUnresolvedTypeLabelsSummary;
        }

        static StructuredGraphStats empty() {
            return new StructuredGraphStats(
                    0, 0, 0, EntityResolutionDiagnostics.empty(), 0, "", 0, 0, 0, 0, 0, 0);
        }
    }
}
