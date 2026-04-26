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
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.derived.GraphDerivedMaintainer;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.DefaultItemGraphPlanner;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.ItemGraphCommitOperations;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Commit-critical item graph materializer with explicit plan / commit / derived phases.
 */
public final class DefaultItemGraphMaterializer implements ItemGraphMaterializer {

    private final DefaultItemGraphPlanner planner;
    private final ItemGraphCommitOperations commitOperations;
    private final GraphDerivedMaintainer derivedMaintainer;
    private final ItemGraphOptions options;

    public DefaultItemGraphMaterializer(
            DefaultItemGraphPlanner planner,
            ItemGraphCommitOperations commitOperations,
            GraphDerivedMaintainer derivedMaintainer,
            ItemGraphOptions options) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.commitOperations = Objects.requireNonNull(commitOperations, "commitOperations");
        this.derivedMaintainer = Objects.requireNonNull(derivedMaintainer, "derivedMaintainer");
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
                sourceEntries == null ? List.of() : List.copyOf(sourceEntries);

        return planner.plan(memoryId, batchItems, batchEntries)
                .map(
                        planningResult -> {
                            var batchId = ExtractionBatchId.newId();
                            commitOperations.commit(
                                    memoryId, batchId, batchItems, planningResult.writePlan());
                            return planningResult;
                        })
                .flatMap(
                        planningResult ->
                                Mono.fromRunnable(
                                                () ->
                                                        derivedMaintainer.refresh(
                                                                memoryId,
                                                                planningResult
                                                                        .writePlan()
                                                                        .affectedEntityKeys()))
                                        .thenReturn(
                                                new ItemGraphMaterializationResult(
                                                        planningResult.stats()))
                                        .onErrorResume(
                                                error ->
                                                        Mono.just(
                                                                new ItemGraphMaterializationResult(
                                                                                planningResult
                                                                                        .stats())
                                                                        .withDerivedMaintenanceDegraded())));
    }
}
