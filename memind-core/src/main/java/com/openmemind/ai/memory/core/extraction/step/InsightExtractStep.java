package com.openmemind.ai.memory.core.extraction.step;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import reactor.core.publisher.Mono;

/**
 * Insight extraction step
 *
 * <p>Responsible for generating insights (such as classification summaries, user profiles, etc.) from MemoryItem
 *
 */
public interface InsightExtractStep {

    /**
     * Extract Insight
     *
     * @param memoryId Memory identifier
     * @param memoryItemResult MemoryItem extraction result
     * @return Generated result
     */
    Mono<InsightResult> extract(MemoryId memoryId, MemoryItemResult memoryItemResult);

    /**
     * Extract Insight (with language hint)
     *
     * @param memoryId Memory identifier
     * @param memoryItemResult MemoryItem extraction result
     * @param language Output language hint, can be null
     * @return Generated result
     */
    default Mono<InsightResult> extract(
            MemoryId memoryId, MemoryItemResult memoryItemResult, String language) {
        return extract(memoryId, memoryItemResult);
    }
}
