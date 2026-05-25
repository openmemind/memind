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
package com.openmemind.ai.memory.plugin.rawdata.agent.config;

import java.time.Duration;

/**
 * Chunking controls for agent timelines.
 */
public record AgentChunkingOptions(
        int targetEpisodeTokens, int hardMaxTokens, int maxEventsPerEpisode, Duration maxEventGap) {

    public AgentChunkingOptions {
        if (targetEpisodeTokens <= 0 || hardMaxTokens < targetEpisodeTokens) {
            throw new IllegalArgumentException("invalid agent chunking token limits");
        }
        if (maxEventsPerEpisode <= 0) {
            throw new IllegalArgumentException("maxEventsPerEpisode must be positive");
        }
        if (maxEventGap == null || maxEventGap.isZero() || maxEventGap.isNegative()) {
            throw new IllegalArgumentException("maxEventGap must be positive");
        }
    }

    public static AgentChunkingOptions defaults() {
        return new AgentChunkingOptions(2_000, 4_000, 80, Duration.ofMinutes(30));
    }
}
