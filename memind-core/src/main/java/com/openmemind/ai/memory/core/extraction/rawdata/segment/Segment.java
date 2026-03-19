package com.openmemind.ai.memory.core.extraction.rawdata.segment;

import java.util.Map;

/**
 * Segment
 *
 * <p>RawData output unit after chunking, containing content, summary, and boundary information.
 *
 * @param content Segmented content (textual)
 * @param caption Summary (can be null, filled by CaptionGenerator)
 * @param boundary Type-safe boundary
 * @param metadata Metadata
 */
public record Segment(
        String content, String caption, SegmentBoundary boundary, Map<String, Object> metadata) {

    /**
     * Create a Segment with a new summary
     *
     * @param caption Summary
     * @return New Segment
     */
    public Segment withCaption(String caption) {
        return new Segment(content, caption, boundary, metadata);
    }

    /**
     * Create a single segment (for content that does not need chunking)
     *
     * @param content Content
     * @return Single segment
     */
    public static Segment single(String content) {
        return new Segment(content, null, new CharBoundary(0, content.length()), Map.of());
    }
}
