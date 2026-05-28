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
package com.openmemind.ai.memory.plugin.rawdata.agent.model;

import java.time.Instant;
import java.util.Map;

/**
 * One normalized event from an agent session timeline.
 */
public record AgentEvent(
        String eventId,
        Integer seq,
        AgentEventKind kind,
        Instant occurredAt,
        String text,
        String toolName,
        String input,
        String output,
        AgentEventStatus status,
        Long durationMs,
        Integer inputTokens,
        Integer outputTokens,
        String contentHash,
        String path,
        String operation,
        String command,
        Integer exitCode,
        Map<String, Object> metadata) {

    public AgentEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
