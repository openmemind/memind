package com.openmemind.ai.memory.core.extraction.item.dedup;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.utils.HashUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * Hash-based deduplication implementation
 *
 * <p>Use the first 16 bits of SHA-256(normalize(content)) for exact matching
 *
 */
public class HashBasedDeduplicator implements MemoryItemDeduplicator {

    private final MemoryStore store;

    public HashBasedDeduplicator(MemoryStore store) {
        this.store = store;
    }

    @Override
    public Mono<DeduplicationResult> deduplicate(
            MemoryId memoryId, List<ExtractedMemoryEntry> entries) {
        return Mono.fromCallable(
                () -> {
                    if (entries.isEmpty()) {
                        return new DeduplicationResult(List.of(), List.of());
                    }

                    // 1. Calculate hash for all entries
                    List<ExtractedMemoryEntry> entriesWithHash =
                            entries.stream()
                                    .map(e -> e.withContentHash(HashUtils.contentHash(e.content())))
                                    .toList();

                    // 2. Collect all hashes
                    List<String> hashes =
                            entriesWithHash.stream()
                                    .map(ExtractedMemoryEntry::contentHash)
                                    .toList();

                    // 3. Batch query existing items
                    Map<String, MemoryItem> existingMap =
                            store.getItemsByContentHashes(memoryId, hashes).stream()
                                    .collect(
                                            Collectors.toMap(
                                                    MemoryItem::contentHash,
                                                    Function.identity(),
                                                    (a, b) ->
                                                            a)); // Deduplication: the same hash may
                    // have multiple items, take the
                    // first

                    // 4. Classification: new entries vs existing entries (also deduplicate within
                    // the batch)
                    var newEntries = new ArrayList<ExtractedMemoryEntry>();
                    var matchedItems = new ArrayList<DeduplicationResult.MatchedItem>();
                    var seenHashes = new java.util.HashSet<String>();

                    for (var entry : entriesWithHash) {
                        MemoryItem existing = existingMap.get(entry.contentHash());
                        if (existing != null) {
                            matchedItems.add(new DeduplicationResult.MatchedItem(existing, 1.0));
                        } else if (seenHashes.add(entry.contentHash())) {
                            newEntries.add(entry);
                        }
                    }

                    return new DeduplicationResult(
                            List.copyOf(newEntries), List.copyOf(matchedItems));
                });
    }

    @Override
    public String spanName() {
        return MemorySpanNames.EXTRACTION_ITEM_DEDUP;
    }
}
