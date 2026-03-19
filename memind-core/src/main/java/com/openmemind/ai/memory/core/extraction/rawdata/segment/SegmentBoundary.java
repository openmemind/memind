package com.openmemind.ai.memory.core.extraction.rawdata.segment;

/**
 * Segment boundary
 *
 * <p>Use sealed interface to ensure type safety and support pattern matching.
 *
 */
public sealed interface SegmentBoundary permits MessageBoundary, CharBoundary {}
