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
package com.openmemind.ai.memory.core.extraction.item.dedup;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Semantic deduplication implementation based on vector similarity
 *
 */
public class SemanticDeduplicator implements MemoryItemDeduplicator {

    private static final int SEARCH_CONCURRENCY = 4;

    private final MemoryStore store;
    private final MemoryVector vector;
    private final double threshold;

    public SemanticDeduplicator(MemoryStore store, MemoryVector vector, double threshold) {
        this.store = store;
        this.vector = vector;
        this.threshold = threshold;
    }

    @Override
    public Mono<DeduplicationResult> deduplicate(
            MemoryId memoryId, List<ExtractedMemoryEntry> entries) {
        if (threshold <= 0 || entries.isEmpty() || !store.itemOperations().hasItems(memoryId)) {
            return Mono.just(new DeduplicationResult(entries, List.of()));
        }

        return Flux.fromIterable(entries)
                .flatMapSequential(
                        entry ->
                                vector.search(memoryId, entry.content(), 3, threshold, Map.of())
                                        .next()
                                        .map(vr -> new EntryMatch(entry, vr.vectorId(), vr.score()))
                                        .defaultIfEmpty(new EntryMatch(entry, null, 0.0f)),
                        SEARCH_CONCURRENCY)
                .collectList()
                .map(
                        matches -> {
                            var matchedVectorIds =
                                    matches.stream()
                                            .map(EntryMatch::vectorId)
                                            .filter(Objects::nonNull)
                                            .distinct()
                                            .toList();

                            Map<String, MemoryItem> vectorIdToItem =
                                    matchedVectorIds.isEmpty()
                                            ? Map.of()
                                            : store
                                                    .itemOperations()
                                                    .getItemsByVectorIds(memoryId, matchedVectorIds)
                                                    .stream()
                                                    .filter(item -> item.vectorId() != null)
                                                    .collect(
                                                            Collectors.toMap(
                                                                    MemoryItem::vectorId,
                                                                    item -> item,
                                                                    (first, ignored) -> first));

                            var keepNew = new ArrayList<ExtractedMemoryEntry>();
                            var reinforced = new ArrayList<DeduplicationResult.MatchedItem>();
                            for (EntryMatch match : matches) {
                                if (match.vectorId() == null) {
                                    keepNew.add(match.entry());
                                    continue;
                                }

                                var item = vectorIdToItem.get(match.vectorId());
                                if (item == null) {
                                    keepNew.add(match.entry());
                                    continue;
                                }

                                reinforced.add(
                                        new DeduplicationResult.MatchedItem(item, match.score()));
                            }
                            return new DeduplicationResult(
                                    List.copyOf(keepNew), List.copyOf(reinforced));
                        });
    }

    @Override
    public String spanName() {
        return MemorySpanNames.EXTRACTION_ITEM_SEMANTIC_DEDUP;
    }

    private record EntryMatch(ExtractedMemoryEntry entry, String vectorId, float score) {}
}
