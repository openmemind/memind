/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
