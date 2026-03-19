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
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Composite deduplicator, executes multiple {@link MemoryItemDeduplicator} in sequence.
 *
 * <p>Each sub deduplicator receives the newEntries from the previous stage, matchedItems accumulate across stages.
 * When newEntries is empty, it short-circuits and skips subsequent deduplicators.
 *
 */
public class CompositeDeduplicator implements MemoryItemDeduplicator {

    private final List<MemoryItemDeduplicator> deduplicators;

    public CompositeDeduplicator(List<MemoryItemDeduplicator> deduplicators) {
        this.deduplicators = List.copyOf(deduplicators);
    }

    @Override
    public Mono<DeduplicationResult> deduplicate(
            MemoryId memoryId, List<ExtractedMemoryEntry> entries) {
        var seed = Mono.just(new DeduplicationResult(entries, List.of()));

        return Flux.fromIterable(deduplicators)
                .reduce(
                        seed,
                        (accMono, dedup) ->
                                accMono.flatMap(
                                        acc -> {
                                            if (acc.newEntries().isEmpty()) {
                                                return Mono.just(acc);
                                            }
                                            return dedup.deduplicate(memoryId, acc.newEntries())
                                                    .map(result -> merge(acc, result));
                                        }))
                .flatMap(mono -> mono);
    }

    @Override
    public String spanName() {
        return "memind.extraction.item.dedup";
    }

    private DeduplicationResult merge(DeduplicationResult acc, DeduplicationResult stage) {
        var allMatched = new ArrayList<>(acc.matchedItems());
        allMatched.addAll(stage.matchedItems());
        return new DeduplicationResult(stage.newEntries(), List.copyOf(allMatched));
    }
}
