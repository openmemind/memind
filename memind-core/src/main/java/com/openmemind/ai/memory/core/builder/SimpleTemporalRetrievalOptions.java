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
package com.openmemind.ai.memory.core.builder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.Objects;

public record SimpleTemporalRetrievalOptions(
        boolean enabled, int maxWindowCandidates, double channelWeight, Duration timeout) {

    public SimpleTemporalRetrievalOptions {
        validateTemporalShape(maxWindowCandidates, channelWeight, timeout);
    }

    public static SimpleTemporalRetrievalOptions defaults() {
        return new SimpleTemporalRetrievalOptions(true, 100, 1.10d, Duration.ofMillis(200));
    }

    @JsonCreator
    public static SimpleTemporalRetrievalOptions fromJson(
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("maxWindowCandidates") Integer maxWindowCandidates,
            @JsonProperty("channelWeight") Double channelWeight,
            @JsonProperty("timeout") Duration timeout) {
        var defaults = defaults();
        return new SimpleTemporalRetrievalOptions(
                enabled != null ? enabled : defaults.enabled(),
                maxWindowCandidates != null ? maxWindowCandidates : defaults.maxWindowCandidates(),
                channelWeight != null ? channelWeight : defaults.channelWeight(),
                timeout != null ? timeout : defaults.timeout());
    }

    public static void validateTemporalShape(
            int maxWindowCandidates, double channelWeight, Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (maxWindowCandidates <= 0) {
            throw new IllegalArgumentException("maxWindowCandidates must be positive");
        }
        if (channelWeight < 0.0d) {
            throw new IllegalArgumentException("channelWeight must be non-negative");
        }
    }
}
