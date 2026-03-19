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
package com.openmemind.ai.memory.core.retrieval.truncation;

/**
 * Adaptive truncation configuration
 *
 * @param enabled      Whether to enable adaptive truncation
 * @param minElbowGap  Minimum gap threshold for elbow detection (truncation is triggered when
 *                     the difference in adjacent scores >= this value)
 * @param dropRatio    Relative threshold ratio (truncation occurs when {@code score < topScore * dropRatio})
 */
public record TruncationConfig(boolean enabled, double minElbowGap, double dropRatio) {

    /** Default configuration: enabled, minElbowGap=0.15, dropRatio=0.65 */
    public static TruncationConfig defaults() {
        return new TruncationConfig(true, 0.15, 0.65);
    }

    /** Disable truncation */
    public static TruncationConfig disabled() {
        return new TruncationConfig(false, 0, 0);
    }

    /** Custom parameters */
    public static TruncationConfig of(double minElbowGap, double dropRatio) {
        return new TruncationConfig(true, minElbowGap, dropRatio);
    }
}
