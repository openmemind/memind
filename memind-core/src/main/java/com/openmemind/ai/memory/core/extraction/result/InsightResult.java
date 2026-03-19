package com.openmemind.ai.memory.core.extraction.result;

import com.openmemind.ai.memory.core.data.MemoryInsight;
import java.util.List;

/**
 * Insight generation result
 *
 * @param insights generated insights list
 */
public record InsightResult(List<MemoryInsight> insights) {

    /**
     * Create empty result
     */
    public static InsightResult empty() {
        return new InsightResult(List.of());
    }

    /**
     * Is empty
     */
    public boolean isEmpty() {
        return insights.isEmpty();
    }

    /**
     * Number of insights
     */
    public int totalCount() {
        return insights.size();
    }
}
