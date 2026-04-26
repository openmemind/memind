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
package com.openmemind.ai.memory.core.retrieval.temporal;

import java.time.Duration;
import java.util.Objects;

public record TemporalItemChannelSettings(
        boolean enabled, int maxWindowCandidates, double channelWeight, Duration timeout) {

    public TemporalItemChannelSettings {
        Objects.requireNonNull(timeout, "timeout");
        if (maxWindowCandidates <= 0) {
            throw new IllegalArgumentException("maxWindowCandidates must be positive");
        }
        if (channelWeight < 0.0d) {
            throw new IllegalArgumentException("channelWeight must be non-negative");
        }
    }

    public static TemporalItemChannelSettings defaults() {
        return new TemporalItemChannelSettings(true, 100, 1.10d, Duration.ofMillis(200));
    }
}
