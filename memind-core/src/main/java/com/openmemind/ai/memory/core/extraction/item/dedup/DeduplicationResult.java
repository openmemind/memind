package com.openmemind.ai.memory.core.extraction.item.dedup;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import java.util.List;

/**
 * Deduplication result
 *
 * @param newEntries Confirmed new entries
 * @param matchedItems Hit existing memory items (carrying matching similarity)
 */
public record DeduplicationResult(
        List<ExtractedMemoryEntry> newEntries, List<MatchedItem> matchedItems) {

    /**
     * Deduplication hit existing memory items + matching similarity
     *
     * @param item Original store item (not enhanced)
     * @param similarity Matching similarity (0.0-1.0), hash exact match is 1.0
     */
    public record MatchedItem(MemoryItem item, double similarity) {}
}
