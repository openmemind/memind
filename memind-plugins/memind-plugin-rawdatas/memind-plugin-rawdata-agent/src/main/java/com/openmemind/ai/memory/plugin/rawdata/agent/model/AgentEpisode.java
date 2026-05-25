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
import java.util.List;
import java.util.Map;

/**
 * Deterministic evidence segment derived from an agent timeline.
 */
public record AgentEpisode(
        String id,
        String goal,
        AgentOutcome outcome,
        String phase,
        List<AgentEvent> events,
        List<String> eventIds,
        List<String> files,
        List<AgentFileReference> fileReferences,
        List<String> commands,
        List<AgentCommand> commandEvents,
        List<String> toolNames,
        List<AgentToolCall> toolCalls,
        List<String> failureSignals,
        Instant startTime,
        Instant endTime,
        Map<String, Object> metadata) {

    public AgentEpisode {
        phase = phase == null || phase.isBlank() ? "full" : phase;
        events = events == null ? List.of() : List.copyOf(events);
        eventIds = eventIds == null ? List.of() : List.copyOf(eventIds);
        files = files == null ? List.of() : List.copyOf(files);
        fileReferences = fileReferences == null ? List.of() : List.copyOf(fileReferences);
        commands = commands == null ? List.of() : List.copyOf(commands);
        commandEvents = commandEvents == null ? List.of() : List.copyOf(commandEvents);
        toolNames = toolNames == null ? List.of() : List.copyOf(toolNames);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        failureSignals = failureSignals == null ? List.of() : List.copyOf(failureSignals);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
