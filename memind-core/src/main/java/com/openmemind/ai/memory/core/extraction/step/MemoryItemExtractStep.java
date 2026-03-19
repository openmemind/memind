package com.openmemind.ai.memory.core.extraction.step;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import reactor.core.publisher.Mono;

/**
 * MemoryItem extraction step
 *
 * <p>Responsible for extracting memory items from RawData segments, deduplication, and persistence
 *
 */
public interface MemoryItemExtractStep {

    /**
     * Extract MemoryItem
     *
     * @param memoryId Memory identifier
     * @param rawDataResult RawData processing result
     * @param config Item extraction layer configuration
     * @return Extraction result
     */
    Mono<MemoryItemResult> extract(
            MemoryId memoryId, RawDataResult rawDataResult, ItemExtractionConfig config);
}
