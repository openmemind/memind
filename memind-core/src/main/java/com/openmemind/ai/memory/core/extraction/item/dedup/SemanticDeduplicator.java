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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Semantic deduplication implementation based on vector similarity
 *
 */
public class SemanticDeduplicator implements MemoryItemDeduplicator {

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
        if (threshold <= 0 || entries.isEmpty() || !store.hasItems(memoryId)) {
            return Mono.just(new DeduplicationResult(entries, List.of()));
        }

        return Flux.fromIterable(entries)
                .flatMapSequential(
                        entry ->
                                vector.search(memoryId, entry.content(), 3, threshold, Map.of())
                                        .next()
                                        .flatMap(
                                                vr ->
                                                        Mono.justOrEmpty(
                                                                        findItemByVectorId(
                                                                                memoryId,
                                                                                vr.vectorId()))
                                                                .map(
                                                                        item ->
                                                                                new DeduplicationResult
                                                                                        .MatchedItem(
                                                                                        item,
                                                                                        vr
                                                                                                .score())))
                                        .map(matched -> (Object) matched)
                                        .defaultIfEmpty(entry))
                .collectList()
                .map(
                        results -> {
                            var keepNew = new ArrayList<ExtractedMemoryEntry>();
                            var reinforced = new ArrayList<DeduplicationResult.MatchedItem>();
                            for (Object result : results) {
                                if (result instanceof ExtractedMemoryEntry e) {
                                    keepNew.add(e);
                                } else if (result
                                        instanceof DeduplicationResult.MatchedItem matched) {
                                    reinforced.add(matched);
                                }
                            }
                            return new DeduplicationResult(
                                    List.copyOf(keepNew), List.copyOf(reinforced));
                        });
    }

    @Override
    public String spanName() {
        return MemorySpanNames.EXTRACTION_ITEM_SEMANTIC_DEDUP;
    }

    private java.util.Optional<MemoryItem> findItemByVectorId(MemoryId memoryId, String vectorId) {
        return store.getAllItems(memoryId).stream()
                .filter(item -> vectorId.equals(item.vectorId()))
                .findFirst();
    }
}
