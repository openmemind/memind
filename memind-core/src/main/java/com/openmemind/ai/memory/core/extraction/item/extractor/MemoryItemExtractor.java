package com.openmemind.ai.memory.core.extraction.item.extractor;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Memory Item Extractor Interface
 *
 */
public interface MemoryItemExtractor {

    /**
     * Extract memory items from the list of segments
     *
     * @param segments List of segments (each segment carries the corresponding rawDataId)
     * @param insightTypes List of insight types (used for item-level insightType routing)
     * @param config Item extraction layer configuration
     * @return List of extracted memory entries
     */
    Mono<List<ExtractedMemoryEntry>> extract(
            List<ParsedSegment> segments,
            List<MemoryInsightType> insightTypes,
            ItemExtractionConfig config);
}
