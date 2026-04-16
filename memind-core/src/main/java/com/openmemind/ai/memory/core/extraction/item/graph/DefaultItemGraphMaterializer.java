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
package com.openmemind.ai.memory.core.extraction.item.graph;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Best-effort graph materializer that persists structured hints after item persistence.
 */
public final class DefaultItemGraphMaterializer implements ItemGraphMaterializer {

    private final GraphOperations graphOperations;
    private final GraphHintNormalizer normalizer;
    private final SemanticItemLinker semanticItemLinker;
    private final ItemGraphOptions options;

    public DefaultItemGraphMaterializer(
            GraphOperations graphOperations,
            GraphHintNormalizer normalizer,
            SemanticItemLinker semanticItemLinker,
            ItemGraphOptions options) {
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.semanticItemLinker = Objects.requireNonNull(semanticItemLinker, "semanticItemLinker");
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public Mono<Void> materialize(
            MemoryId memoryId, List<MemoryItem> items, List<ExtractedMemoryEntry> sourceEntries) {
        if (!options.enabled() || items == null || items.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromSupplier(
                        () ->
                                normalizer.normalize(
                                        memoryId,
                                        List.copyOf(items),
                                        sourceEntries != null
                                                ? List.copyOf(sourceEntries)
                                                : List.of(),
                                        options))
                .onErrorReturn(ResolvedGraphBatch.empty())
                .flatMap(batch -> persistStructuredGraph(memoryId, batch))
                .onErrorResume(ignored -> Mono.empty())
                .then(
                        semanticItemLinker
                                .link(memoryId, items)
                                .onErrorResume(ignored -> Mono.empty()));
    }

    private Mono<Void> persistStructuredGraph(MemoryId memoryId, ResolvedGraphBatch batch) {
        return Mono.fromRunnable(
                () -> {
                    graphOperations.upsertEntities(memoryId, batch.entities());
                    graphOperations.upsertItemEntityMentions(memoryId, batch.mentions());
                    graphOperations.upsertItemLinks(memoryId, batch.itemLinks());

                    List<String> affectedEntityKeys =
                            batch.mentions().stream()
                                    .map(ItemEntityMention::entityKey)
                                    .distinct()
                                    .toList();
                    if (!affectedEntityKeys.isEmpty()) {
                        graphOperations.rebuildEntityCooccurrences(memoryId, affectedEntityKeys);
                    }
                });
    }
}
