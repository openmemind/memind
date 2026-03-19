package com.openmemind.ai.memory.core.extraction.rawdata.segment;

/**
 * Character boundary
 *
 * @param startChar Starting character index (inclusive)
 * @param endChar Ending character index (exclusive)
 */
public record CharBoundary(int startChar, int endChar) implements SegmentBoundary {}
