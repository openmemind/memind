package com.openmemind.ai.memory.core.extraction.rawdata.segment;

/**
 * Message boundary
 *
 * @param startMessage Start message index (inclusive)
 * @param endMessage End message index (exclusive)
 */
public record MessageBoundary(int startMessage, int endMessage) implements SegmentBoundary {}
