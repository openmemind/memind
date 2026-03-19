package com.openmemind.ai.memory.core.extraction.streaming;

/**
 * Boundary detection decision result
 *
 * @param shouldSeal Whether to seal the current buffer
 * @param confidence Confidence (0.0-1.0)
 * @param reason Sealing reason (for logging/debugging)
 */
public record BoundaryDecision(boolean shouldSeal, double confidence, String reason) {

    /**
     * Create sealing decision
     *
     * @param confidence Confidence
     * @param reason Sealing reason
     * @return Sealing decision
     */
    public static BoundaryDecision seal(double confidence, String reason) {
        return new BoundaryDecision(true, confidence, reason);
    }

    /**
     * Create holding (not sealing) decision
     *
     * @return Holding decision
     */
    public static BoundaryDecision hold() {
        return new BoundaryDecision(false, 0.0, "hold");
    }
}
