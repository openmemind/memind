package com.openmemind.ai.memory.core.extraction.item;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Strategy for extracting memory items from parsed segments.
 *
 * <p>Different content types may require different extraction approaches.
 * Implement this interface to provide custom extraction logic for a content type.
 */
@FunctionalInterface
public interface ItemExtractionStrategy {

    /**
     * Extract memory items from the given segments.
     *
     * @param segments parsed segments from rawdata layer
     * @param insightTypes available insight types for categorization
     * @param config extraction configuration
     * @return list of extracted memory entries
     */
    Mono<List<ExtractedMemoryEntry>> extract(
            List<ParsedSegment> segments,
            List<MemoryInsightType> insightTypes,
            ItemExtractionConfig config);
}
