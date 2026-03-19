package com.openmemind.ai.memory.core.extraction.item.dedup;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Memory item deduplication strategy interface
 *
 */
public interface MemoryItemDeduplicator {

    /**
     * Deduplicate the extracted memory entries
     *
     * @param memoryId Memory identifier
     * @param entries Entries to be deduplicated
     * @return Deduplication result
     */
    Mono<DeduplicationResult> deduplicate(MemoryId memoryId, List<ExtractedMemoryEntry> entries);

    /**
     * The span name corresponding to this deduplicator
     */
    String spanName();
}
