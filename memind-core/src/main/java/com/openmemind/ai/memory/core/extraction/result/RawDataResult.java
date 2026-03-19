package com.openmemind.ai.memory.core.extraction.result;

import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import java.util.List;

/**
 * RawData processing result
 *
 * @param rawDataList Persisted RawData list
 * @param segments Parsed segment list
 * @param existed Whether it exists (idempotent hit)
 */
public record RawDataResult(
        List<MemoryRawData> rawDataList, List<ParsedSegment> segments, boolean existed) {

    /**
     * Create an empty result
     */
    public static RawDataResult empty() {
        return new RawDataResult(List.of(), List.of(), false);
    }

    /**
     * Create an existing result (idempotent hit)
     */
    public static RawDataResult existing(MemoryRawData rawData) {
        return new RawDataResult(List.of(rawData), List.of(), true);
    }

    /**
     * Whether it is empty
     */
    public boolean isEmpty() {
        return segments.isEmpty();
    }
}
