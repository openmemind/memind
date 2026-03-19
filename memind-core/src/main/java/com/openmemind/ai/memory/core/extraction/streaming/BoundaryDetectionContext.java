package com.openmemind.ai.memory.core.extraction.streaming;

import java.time.Duration;

/**
 * Boundary detection context
 *
 * @param lastTimeGap The time interval between the last two messages
 */
public record BoundaryDetectionContext(Duration lastTimeGap) {

    public static BoundaryDetectionContext empty() {
        return new BoundaryDetectionContext(null);
    }
}
