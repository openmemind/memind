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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    public Mono<SemanticLinkingStats> link(MemoryId memoryId, List<MemoryItem> items) {
        if (!options.enabled() || items == null || items.isEmpty()) {
            return Mono.just(SemanticLinkingStats.empty());
        }

        int concurrency = Math.max(1, options.semanticLinkConcurrency());
        var batchVectorIds =
                items.stream()
                        .map(MemoryItem::vectorId)
                        .filter(vectorId -> vectorId != null && !vectorId.isBlank())
                        .collect(Collectors.toSet());

        return Flux.fromIterable(items)
                .flatMapSequential(
                        item ->
                                linkSingleItem(memoryId, item, batchVectorIds)
                                        .onErrorReturn(SemanticLinkingStats.empty()),
                        concurrency,
                        1)
                .reduce(SemanticLinkingStats.empty(), SemanticLinkingStats::plus)
                .defaultIfEmpty(SemanticLinkingStats.empty());
    }

    private Mono<SemanticLinkingStats> linkSingleItem(
            MemoryId memoryId, MemoryItem item, Set<String> batchVectorIds) {
        String query = ItemEmbeddingTextResolver.resolve(item);
        if (query.isBlank()) {
            return Mono.just(SemanticLinkingStats.empty());
        }

        int requestedTopK =
                options.maxSemanticLinksPerItem() + options.semanticSearchHeadroom() + 1;

        return vector.search(memoryId, query, requestedTopK, options.semanticMinScore(), null)
                .collectList()
                .map(results -> normalize(memoryId, item, batchVectorIds, results))
                .map(
                        outcome -> {
                            if (!outcome.links().isEmpty()) {
                                graphOperations.upsertItemLinks(memoryId, outcome.links());
                            }
                            return outcome.stats();
                        });
    }

    private NormalizedSemanticLinks normalize(
            MemoryId memoryId,
            MemoryItem sourceItem,
            Set<String> batchVectorIds,
            List<VectorSearchResult> searchResults) {
        Map<String, Float> bestScoreByVectorId =
                searchResults.stream()
                        .filter(result -> result.vectorId() != null && !result.vectorId().isBlank())
                        .collect(
                                Collectors.toMap(
                                        VectorSearchResult::vectorId,
                                        VectorSearchResult::score,
                                        Float::max,
                                        LinkedHashMap::new));

        var candidateByVectorId =
                itemOperations.getItemsByVectorIds(memoryId, bestScoreByVectorId.keySet()).stream()
                        .filter(
                                candidate ->
                                        candidate.vectorId() != null
                                                && !candidate.vectorId().isBlank())
                        .collect(
                                Collectors.toMap(
                                        MemoryItem::vectorId,
                                        Function.identity(),
                                        (left, right) -> left,
                                        LinkedHashMap::new));

        Instant createdAt = resolveLinkTimestamp(sourceItem);
        var normalizedCandidates =
                bestScoreByVectorId.entrySet().stream()
                        .map(
                                entry -> {
                                    var candidate = candidateByVectorId.get(entry.getKey());
                                    if (candidate == null
                                            || Objects.equals(candidate.id(), sourceItem.id())) {
                                        return null;
                                    }
                                    return new CandidateHit(
                                            candidate, entry.getKey(), entry.getValue());
                                })
                        .filter(Objects::nonNull)
                        .sorted(
                                Comparator.comparingDouble(CandidateHit::score)
                                        .reversed()
                                        .thenComparing(CandidateHit::vectorId)
                                        .thenComparing(hit -> hit.item().id()))
                        .toList();

        int sameBatchHitCount =
                (int)
                        normalizedCandidates.stream()
                                .map(CandidateHit::vectorId)
                                .filter(batchVectorIds::contains)
                                .count();

        var rankedLinks =
                normalizedCandidates.stream()
                        .limit(options.maxSemanticLinksPerItem())
                        .map(
                                hit ->
                                        new ItemLink(
                                                memoryId.toIdentifier(),
                                                sourceItem.id(),
                                                hit.item().id(),
                                                ItemLinkType.SEMANTIC,
                                                Double.valueOf(hit.score()),
                                                Map.of("source", "vector_search"),
                                                createdAt))
                        .toList();

        return new NormalizedSemanticLinks(
                rankedLinks,
                new SemanticLinkingStats(
                        searchResults.size(), rankedLinks.size(), sameBatchHitCount));
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

    public record SemanticLinkingStats(
            int searchHitCount, int createdLinkCount, int sameBatchHitCount) {

        public static SemanticLinkingStats empty() {
            return new SemanticLinkingStats(0, 0, 0);
        }

        public SemanticLinkingStats plus(SemanticLinkingStats other) {
            return new SemanticLinkingStats(
                    searchHitCount + other.searchHitCount,
                    createdLinkCount + other.createdLinkCount,
                    sameBatchHitCount + other.sameBatchHitCount);
        }
    }

    private record CandidateHit(MemoryItem item, String vectorId, float score) {}

    private record NormalizedSemanticLinks(List<ItemLink> links, SemanticLinkingStats stats) {}
}
