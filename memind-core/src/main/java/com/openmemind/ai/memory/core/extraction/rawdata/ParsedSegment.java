package com.openmemind.ai.memory.core.extraction.rawdata;

import java.util.Map;

/**
 * Parsed segment (including caption)
 *
 * @param text Segment text
 * @param caption Segment summary
 * @param startIndex Starting position in the original content
 * @param endIndex Ending position in the original content
 * @param rawDataId Persisted MemoryRawData business ID
 * @param metadata Segment metadata
 */
public record ParsedSegment(
        String text,
        String caption,
        int startIndex,
        int endIndex,
        String rawDataId,
        Map<String, Object> metadata) {}
