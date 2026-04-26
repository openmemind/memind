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

import java.time.Duration;

/**
 * Optional write-side memory-thread enrichment controls.
 */
public record MemoryThreadEnrichmentOptions(
        boolean enabled,
        int minimumEventCountForFirstEnrichment,
        int minimumMeaningfulEventDeltaForReenrichment,
        Duration minimumWallClockGapBetweenRuns,
        Duration timeout) {

    public MemoryThreadEnrichmentOptions {
        minimumWallClockGapBetweenRuns =
                minimumWallClockGapBetweenRuns != null
                        ? minimumWallClockGapBetweenRuns
                        : defaults().minimumWallClockGapBetweenRuns();
        timeout = timeout != null ? timeout : defaults().timeout();
        if (minimumEventCountForFirstEnrichment < 0) {
            throw new IllegalArgumentException(
                    "minimumEventCountForFirstEnrichment must be non-negative");
        }
        if (minimumMeaningfulEventDeltaForReenrichment < 0) {
            throw new IllegalArgumentException(
                    "minimumMeaningfulEventDeltaForReenrichment must be non-negative");
        }
        if (minimumWallClockGapBetweenRuns.isNegative()) {
            throw new IllegalArgumentException(
                    "minimumWallClockGapBetweenRuns must be non-negative");
        }
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }
    }

    public static MemoryThreadEnrichmentOptions defaults() {
        return new MemoryThreadEnrichmentOptions(
                false, 2, 3, Duration.ofMinutes(15), Duration.ofSeconds(10));
    }

    public MemoryThreadEnrichmentOptions withEnabled(boolean enabled) {
        return new MemoryThreadEnrichmentOptions(
                enabled,
                minimumEventCountForFirstEnrichment,
                minimumMeaningfulEventDeltaForReenrichment,
                minimumWallClockGapBetweenRuns,
                timeout);
    }
}
