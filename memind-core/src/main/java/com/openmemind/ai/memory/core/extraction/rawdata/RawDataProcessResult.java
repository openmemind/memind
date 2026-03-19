package com.openmemind.ai.memory.core.extraction.rawdata;

import com.openmemind.ai.memory.core.data.MemoryRawData;
import java.util.List;

/**
 * RawData layer processing result
 *
 * @param rawDataList Persisted RawData list (one record per segment)
 * @param segments Parsed segment list (for MemoryItem layer use)
 * @param existed Whether it exists (idempotent hit)
 */
public record RawDataProcessResult(
        List<MemoryRawData> rawDataList, List<ParsedSegment> segments, boolean existed) {

    /**
     * Create an existing result (idempotent hit)
     *
     * @param rawData Existing RawData
     * @return Processing result
     */
    public static RawDataProcessResult existing(MemoryRawData rawData) {
        return new RawDataProcessResult(List.of(rawData), List.of(), true);
    }
}
