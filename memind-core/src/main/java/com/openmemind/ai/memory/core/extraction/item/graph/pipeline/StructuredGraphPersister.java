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
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
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
