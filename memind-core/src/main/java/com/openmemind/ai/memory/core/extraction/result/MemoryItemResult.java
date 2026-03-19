package com.openmemind.ai.memory.core.extraction.result;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import java.util.List;

/**
 * MemoryItem extraction result
 *
 * @param newItems Newly created memory items
 * @param resolvedInsightTypes Resolved insight types (used to pass to InsightLayer)
 */
public record MemoryItemResult(
        List<MemoryItem> newItems, List<MemoryInsightType> resolvedInsightTypes) {

    /**
     * Create empty result
     */
    public static MemoryItemResult empty() {
        return new MemoryItemResult(List.of(), List.of());
    }

    /**
     * Is empty
     */
    public boolean isEmpty() {
        return newItems.isEmpty();
    }

    /**
     * New count
     */
    public int newCount() {
        return newItems.size();
    }
}
