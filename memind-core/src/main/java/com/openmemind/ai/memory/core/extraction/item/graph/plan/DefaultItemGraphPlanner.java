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
package com.openmemind.ai.memory.core.extraction.item.graph.plan;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.alias.EntityAliasIndexPlanner;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.EntityDropReason;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionStrategy;
import com.openmemind.ai.memory.core.extraction.item.graph.link.semantic.SemanticItemLinker;
import com.openmemind.ai.memory.core.extraction.item.graph.link.temporal.TemporalItemLinker;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.GraphHintNormalizer;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.NormalizedGraphBatch;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.ResolvedGraphBatch;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.causal.CausalItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.causal.CausalRelationCode;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticEvidenceSource;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.temporal.TemporalItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.temporal.TemporalRelationCode;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Pure precompute planner for a single item-graph extraction batch.
 */
public final class DefaultItemGraphPlanner {

    private final GraphHintNormalizer normalizer;
    private final EntityResolutionStrategy resolutionStrategy;
    private final EntityAliasIndexPlanner aliasPlanner;
    private final TemporalItemLinker temporalItemLinker;
    private final SemanticItemLinker semanticItemLinker;
    private final ItemGraphOptions options;

    public DefaultItemGraphPlanner(
            GraphHintNormalizer normalizer,
            EntityResolutionStrategy resolutionStrategy,
            EntityAliasIndexPlanner aliasPlanner,
            TemporalItemLinker temporalItemLinker,
            SemanticItemLinker semanticItemLinker,
            ItemGraphOptions options) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.resolutionStrategy = Objects.requireNonNull(resolutionStrategy, "resolutionStrategy");
        this.aliasPlanner = Objects.requireNonNull(aliasPlanner, "aliasPlanner");
        this.temporalItemLinker = Objects.requireNonNull(temporalItemLinker, "temporalItemLinker");
        this.semanticItemLinker = Objects.requireNonNull(semanticItemLinker, "semanticItemLinker");
        this.options = Objects.requireNonNull(options, "options");
    }

    public Mono<PlanningResult> plan(
            MemoryId memoryId, List<MemoryItem> items, List<ExtractedMemoryEntry> sourceEntries) {
        if (!options.enabled() || items == null || items.isEmpty()) {
            return Mono.just(PlanningResult.empty());
        }

        List<MemoryItem> batchItems = List.copyOf(items);
        List<ExtractedMemoryEntry> batchEntries =
                sourceEntries == null ? List.of() : List.copyOf(sourceEntries);
        ResolvedGraphBatch resolvedBatch =
                resolveStructuredBatch(memoryId, batchItems, batchEntries);

        return temporalItemLinker
                .plan(memoryId, batchItems)
                .onErrorReturn(TemporalItemLinker.TemporalLinkingPlan.stageFailure())
                .flatMap(
                        temporalPlan ->
                                semanticItemLinker
                                        .plan(memoryId, batchItems)
                                        .onErrorReturn(
                                                SemanticItemLinker.SemanticLinkingPlan
                                                        .stageFailure())
                                        .map(
                                                semanticPlan ->
                                                        assemblePlanningResult(
                                                                resolvedBatch,
                                                                temporalPlan,
                                                                semanticPlan)));
    }

    private ResolvedGraphBatch resolveStructuredBatch(
            MemoryId memoryId,
            List<MemoryItem> batchItems,
            List<ExtractedMemoryEntry> batchEntries) {
        try {
            NormalizedGraphBatch normalized =
                    normalizer.normalize(memoryId, batchItems, batchEntries, options);
            ResolvedGraphBatch resolved = resolutionStrategy.resolve(memoryId, normalized, options);
            return resolved.withEntityAliases(aliasPlanner.plan(memoryId, resolved));
        } catch (RuntimeException error) {
            return ResolvedGraphBatch.empty();
        }
    }

    private PlanningResult assemblePlanningResult(
            ResolvedGraphBatch resolvedBatch,
            TemporalItemLinker.TemporalLinkingPlan temporalPlan,
            SemanticItemLinker.SemanticLinkingPlan semanticPlan) {
        var diagnostics = resolvedBatch.diagnostics();
        var droppedEntityCounts = diagnostics.droppedEntityCounts();

        var writePlan =
                ItemGraphWritePlan.builder()
                        .entities(resolvedBatch.entities())
                        .mentions(resolvedBatch.mentions())
                        .aliases(resolvedBatch.entityAliases())
                        .semanticRelations(structuredSemanticRelations(resolvedBatch))
                        .semanticRelations(toSemanticRelations(semanticPlan.links()))
                        .temporalRelations(structuredTemporalRelations(resolvedBatch))
                        .temporalRelations(toTemporalRelations(temporalPlan.links()))
                        .causalRelations(structuredCausalRelations(resolvedBatch))
                        .diagnostics(
                                Map.of(
                                        "resolutionDiagnostics",
                                        resolvedBatch.resolutionDiagnostics(),
                                        "normalizationDiagnostics",
                                        diagnostics))
                        .build();

        var stats =
                ItemGraphMaterializationResult.Stats.withTemporalAndSemantic(
                        resolvedBatch.entities().size(),
                        resolvedBatch.mentions().size(),
                        resolvedBatch.itemLinks().size(),
                        temporalPlan.stats(),
                        resolvedBatch.resolutionDiagnostics(),
                        semanticPlan.stats(),
                        diagnostics.typeFallbackToOtherCount(),
                        diagnostics.topUnresolvedTypeLabels().isEmpty()
                                ? ""
                                : diagnostics.topUnresolvedTypeLabels().entrySet().stream()
                                        .sorted(
                                                Map.Entry.<String, Integer>comparingByValue()
                                                        .reversed()
                                                        .thenComparing(Map.Entry::getKey))
                                        .limit(5)
                                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                                        .reduce((left, right) -> left + ", " + right)
                                        .orElse(""),
                        droppedEntityCounts.getOrDefault(EntityDropReason.BLANK, 0),
                        droppedEntityCounts.getOrDefault(EntityDropReason.PUNCTUATION_ONLY, 0),
                        droppedEntityCounts.getOrDefault(EntityDropReason.PRONOUN_LIKE, 0),
                        droppedEntityCounts.getOrDefault(EntityDropReason.TEMPORAL, 0),
                        droppedEntityCounts.getOrDefault(EntityDropReason.DATE_LIKE, 0),
                        droppedEntityCounts.getOrDefault(
                                EntityDropReason.RESERVED_SPECIAL_COLLISION, 0));
        return new PlanningResult(writePlan, stats);
    }

    private static List<SemanticItemRelation> structuredSemanticRelations(
            ResolvedGraphBatch batch) {
        return batch.itemLinks().stream()
                .filter(link -> link.linkType() == ItemLinkType.SEMANTIC)
                .map(DefaultItemGraphPlanner::toSemanticRelation)
                .toList();
    }

    private static List<TemporalItemRelation> structuredTemporalRelations(
            ResolvedGraphBatch batch) {
        return batch.itemLinks().stream()
                .filter(link -> link.linkType() == ItemLinkType.TEMPORAL)
                .map(DefaultItemGraphPlanner::toTemporalRelation)
                .toList();
    }

    private static List<CausalItemRelation> structuredCausalRelations(ResolvedGraphBatch batch) {
        return batch.itemLinks().stream()
                .filter(link -> link.linkType() == ItemLinkType.CAUSAL)
                .map(DefaultItemGraphPlanner::toCausalRelation)
                .toList();
    }

    private static List<SemanticItemRelation> toSemanticRelations(List<ItemLink> links) {
        return links.stream().map(DefaultItemGraphPlanner::toSemanticRelation).toList();
    }

    private static List<TemporalItemRelation> toTemporalRelations(List<ItemLink> links) {
        return links.stream().map(DefaultItemGraphPlanner::toTemporalRelation).toList();
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

    public record PlanningResult(
            ItemGraphWritePlan writePlan, ItemGraphMaterializationResult.Stats stats) {

        public PlanningResult {
            writePlan = writePlan == null ? ItemGraphWritePlan.builder().build() : writePlan;
            stats = stats == null ? ItemGraphMaterializationResult.Stats.empty() : stats;
        }

        public static PlanningResult empty() {
            return new PlanningResult(
                    ItemGraphWritePlan.builder().build(),
                    ItemGraphMaterializationResult.Stats.empty());
        }
    }
}
