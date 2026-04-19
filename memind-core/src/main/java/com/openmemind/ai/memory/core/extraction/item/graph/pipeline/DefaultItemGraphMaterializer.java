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

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializationResult;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.alias.EntityAliasIndexPlanner;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionStrategy;
import com.openmemind.ai.memory.core.extraction.item.graph.link.semantic.SemanticItemLinker;
import com.openmemind.ai.memory.core.extraction.item.graph.link.temporal.TemporalItemLinker;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.NormalizedGraphBatch;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.ResolvedGraphBatch;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Best-effort graph materializer that persists structured hints after item persistence.
 */
public final class DefaultItemGraphMaterializer implements ItemGraphMaterializer {

    private final GraphHintNormalizer normalizer;
    private final EntityResolutionStrategy resolutionStrategy;
    private final EntityAliasIndexPlanner aliasPlanner;
    private final StructuredGraphPersister structuredGraphPersister;
    private final TemporalItemLinker temporalItemLinker;
    private final SemanticItemLinker semanticItemLinker;
    private final ItemGraphOptions options;

    public DefaultItemGraphMaterializer(
            GraphHintNormalizer normalizer,
            EntityResolutionStrategy resolutionStrategy,
            EntityAliasIndexPlanner aliasPlanner,
            StructuredGraphPersister structuredGraphPersister,
            TemporalItemLinker temporalItemLinker,
            SemanticItemLinker semanticItemLinker,
            ItemGraphOptions options) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.resolutionStrategy = Objects.requireNonNull(resolutionStrategy, "resolutionStrategy");
        this.aliasPlanner = Objects.requireNonNull(aliasPlanner, "aliasPlanner");
        this.structuredGraphPersister =
                Objects.requireNonNull(structuredGraphPersister, "structuredGraphPersister");
        this.temporalItemLinker = Objects.requireNonNull(temporalItemLinker, "temporalItemLinker");
        this.semanticItemLinker = Objects.requireNonNull(semanticItemLinker, "semanticItemLinker");
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public Mono<ItemGraphMaterializationResult> materialize(
            MemoryId memoryId, List<MemoryItem> items, List<ExtractedMemoryEntry> sourceEntries) {
        if (!options.enabled() || items == null || items.isEmpty()) {
            return Mono.just(ItemGraphMaterializationResult.empty());
        }

        List<MemoryItem> batchItems = List.copyOf(items);
        List<ExtractedMemoryEntry> batchEntries =
                sourceEntries != null ? List.copyOf(sourceEntries) : List.of();
        return Mono.fromSupplier(
                        () -> normalizer.normalize(memoryId, batchItems, batchEntries, options))
                .onErrorReturn(NormalizedGraphBatch.empty())
                .map(batch -> resolutionStrategy.resolve(memoryId, batch, options))
                .onErrorReturn(ResolvedGraphBatch.empty())
                .map(batch -> batch.withEntityAliases(aliasPlanner.plan(memoryId, batch)))
                .map(batch -> structuredGraphPersister.persistStructuredGraph(memoryId, batch))
                .onErrorReturn(StructuredGraphPersister.StructuredGraphStats.empty())
                .flatMap(structuredStats -> temporalStage(memoryId, batchItems, structuredStats));
    }

    private Mono<ItemGraphMaterializationResult> temporalStage(
            MemoryId memoryId,
            List<MemoryItem> items,
            StructuredGraphPersister.StructuredGraphStats structuredStats) {
        return temporalItemLinker
                .link(memoryId, items)
                .onErrorReturn(TemporalItemLinker.TemporalLinkingStats.stageFailure())
                .flatMap(
                        temporalStats ->
                                semanticStage(memoryId, items, structuredStats, temporalStats));
    }

    private Mono<ItemGraphMaterializationResult> semanticStage(
            MemoryId memoryId,
            List<MemoryItem> items,
            StructuredGraphPersister.StructuredGraphStats structuredStats,
            TemporalItemLinker.TemporalLinkingStats temporalStats) {
        return semanticItemLinker
                .link(memoryId, items)
                .onErrorReturn(SemanticItemLinker.SemanticLinkingStats.stageFailure())
                .map(
                        semanticStats ->
                                new ItemGraphMaterializationResult(
                                        graphStats(structuredStats, temporalStats, semanticStats)));
    }

    private static ItemGraphMaterializationResult.Stats graphStats(
            StructuredGraphPersister.StructuredGraphStats structuredStats,
            TemporalItemLinker.TemporalLinkingStats temporalStats,
            SemanticItemLinker.SemanticLinkingStats semanticStats) {
        return ItemGraphMaterializationResult.Stats.withTemporalAndSemantic(
                structuredStats.entityCount(),
                structuredStats.mentionCount(),
                structuredStats.structuredItemLinkCount(),
                temporalStats,
                structuredStats.resolutionDiagnostics(),
                semanticStats,
                structuredStats.typeFallbackToOtherCount(),
                structuredStats.topUnresolvedTypeLabelsSummary(),
                structuredStats.droppedBlankCount(),
                structuredStats.droppedPunctuationOnlyCount(),
                structuredStats.droppedPronounLikeCount(),
                structuredStats.droppedTemporalCount(),
                structuredStats.droppedDateLikeCount(),
                structuredStats.droppedReservedSpecialCollisionCount());
    }
}
