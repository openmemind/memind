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
import com.openmemind.ai.memory.core.extraction.item.support.ItemEmbeddingTextResolver;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Builds bounded semantic item links from vector similarity search.
 */
public final class SemanticItemLinker {

    private final ItemOperations itemOperations;
    private final GraphOperations graphOperations;
    private final MemoryVector vector;
    private final ItemGraphOptions options;

    public SemanticItemLinker(
            ItemOperations itemOperations,
            GraphOperations graphOperations,
            MemoryVector vector,
            ItemGraphOptions options) {
        this.itemOperations = Objects.requireNonNull(itemOperations, "itemOperations");
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
        this.vector = Objects.requireNonNull(vector, "vector");
        this.options = Objects.requireNonNull(options, "options");
    }

    public Mono<Void> link(MemoryId memoryId, List<MemoryItem> items) {
        if (!options.enabled() || items == null || items.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(items)
                .concatMap(
                        item ->
                                linkSingleItem(memoryId, item)
                                        .onErrorResume(ignored -> Mono.empty()))
                .then();
    }

    private Mono<Void> linkSingleItem(MemoryId memoryId, MemoryItem item) {
        String query = ItemEmbeddingTextResolver.resolve(item);
        if (query.isBlank()) {
            return Mono.empty();
        }

        return vector.search(
                        memoryId,
                        query,
                        options.maxSemanticLinksPerItem() + 1,
                        options.semanticMinScore(),
                        null)
                .filter(result -> result.vectorId() != null && !result.vectorId().isBlank())
                .collectList()
                .filter(results -> !results.isEmpty())
                .map(results -> upsertSemanticLinks(memoryId, item, results))
                .then();
    }

    private List<ItemLink> upsertSemanticLinks(
            MemoryId memoryId, MemoryItem sourceItem, List<VectorSearchResult> searchResults) {
        LinkedHashMap<String, Float> scoreByVectorId = new LinkedHashMap<>();
        searchResults.forEach(
                result -> scoreByVectorId.putIfAbsent(result.vectorId(), result.score()));

        if (scoreByVectorId.isEmpty()) {
            return List.of();
        }

        List<MemoryItem> candidates =
                itemOperations.getItemsByVectorIds(memoryId, scoreByVectorId.keySet());
        Map<String, MemoryItem> candidateByVectorId = new LinkedHashMap<>();
        candidates.forEach(
                candidate -> {
                    if (candidate.vectorId() != null && !candidate.vectorId().isBlank()) {
                        candidateByVectorId.put(candidate.vectorId(), candidate);
                    }
                });

        LinkedHashMap<LinkIdentity, ItemLink> retained = new LinkedHashMap<>();
        Instant createdAt = resolveLinkTimestamp(sourceItem);
        for (Map.Entry<String, Float> entry : scoreByVectorId.entrySet()) {
            MemoryItem candidate = candidateByVectorId.get(entry.getKey());
            if (candidate == null || Objects.equals(candidate.id(), sourceItem.id())) {
                continue;
            }

            ItemLink link =
                    new ItemLink(
                            memoryId.toIdentifier(),
                            sourceItem.id(),
                            candidate.id(),
                            ItemLinkType.SEMANTIC,
                            Double.valueOf(entry.getValue()),
                            Map.of("source", "vector_search"),
                            createdAt);
            retained.putIfAbsent(new LinkIdentity(link), link);
            if (retained.size() >= options.maxSemanticLinksPerItem()) {
                break;
            }
        }

        List<ItemLink> links = List.copyOf(retained.values());
        if (!links.isEmpty()) {
            graphOperations.upsertItemLinks(memoryId, links);
        }
        return links;
    }

    private static Instant resolveLinkTimestamp(MemoryItem item) {
        if (item.createdAt() != null) {
            return item.createdAt();
        }
        if (item.observedAt() != null) {
            return item.observedAt();
        }
        if (item.occurredAt() != null) {
            return item.occurredAt();
        }
        if (item.occurredStart() != null) {
            return item.occurredStart();
        }
        return Instant.EPOCH;
    }

    private record LinkIdentity(Long sourceItemId, Long targetItemId, ItemLinkType linkType) {

        private LinkIdentity(ItemLink link) {
            this(link.sourceItemId(), link.targetItemId(), link.linkType());
        }
    }
}
