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
package com.openmemind.ai.memory.core.extraction.context;

/**
 * Boundary detection decision result
 *
 * @param shouldSeal Whether to seal the current buffer
 * @param confidence Confidence (0.0-1.0)
 * @param reason Sealing reason (for logging/debugging)
 */
public record CommitDecision(boolean shouldSeal, double confidence, String reason) {

    /**
     * Create sealing decision
     *
     * @param confidence Confidence
     * @param reason Sealing reason
     * @return Sealing decision
     */
    public static CommitDecision commit(double confidence, String reason) {
        return new CommitDecision(true, confidence, reason);
    }

    /**
     * Create holding (not sealing) decision
     *
     * @return Holding decision
     */
    public static CommitDecision hold() {
        return new CommitDecision(false, 0.0, "hold");
    }
}
