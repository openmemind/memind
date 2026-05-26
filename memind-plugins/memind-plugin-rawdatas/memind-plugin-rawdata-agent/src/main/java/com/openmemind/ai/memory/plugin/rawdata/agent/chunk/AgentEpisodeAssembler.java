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
package com.openmemind.ai.memory.plugin.rawdata.agent.chunk;

import com.openmemind.ai.memory.core.utils.HashUtils;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentChunkingOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.content.AgentTimelineContent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentCommand;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEpisode;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventKind;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventStatus;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentFileReference;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentOutcome;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentToolCall;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds deterministic task episodes from normalized timeline events.
 */
public final class AgentEpisodeAssembler {

    private final AgentChunkingOptions options;

    public AgentEpisodeAssembler() {
        this(AgentChunkingOptions.defaults());
    }

    public AgentEpisodeAssembler(AgentChunkingOptions options) {
        this.options = options == null ? AgentChunkingOptions.defaults() : options;
    }

    public List<AgentEpisode> assemble(AgentTimelineContent timeline) {
        if (timeline == null || timeline.events().isEmpty()) {
            return List.of();
        }
        return buildBaseEpisodes(timeline, sorted(timeline.events()));
    }

    private List<AgentEpisode> buildBaseEpisodes(
            AgentTimelineContent timeline, List<AgentEvent> events) {
        List<AgentEpisode> episodes = new ArrayList<>();
        List<AgentEvent> current = new ArrayList<>();
        AgentEvent previous = null;
        String currentBoundaryKey = null;

        for (AgentEvent event : events) {
            boolean startsNewPrompt =
                    event.kind() == AgentEventKind.USER_PROMPT && !current.isEmpty();
            boolean crossesBoundary =
                    !current.isEmpty()
                            && (startsNewPrompt
                                    || fallbackBoundaryExceeded(
                                            currentBoundaryKey, previous, event, current)
                                    || boundaryKeyChanged(currentBoundaryKey, boundaryKey(event)));
            if (crossesBoundary) {
                episodes.add(buildEpisode(timeline, current, "full", Map.of()));
                current = new ArrayList<>();
                currentBoundaryKey = null;
            }

            current.add(event);
            if (currentBoundaryKey == null) {
                currentBoundaryKey = boundaryKey(event);
            }
            previous = event;

            if (isTerminal(event)) {
                episodes.add(buildEpisode(timeline, current, "full", Map.of()));
                current = new ArrayList<>();
                currentBoundaryKey = null;
                previous = null;
            }
        }

        if (!current.isEmpty()) {
            episodes.add(buildEpisode(timeline, current, "full", Map.of()));
        }
        return episodes;
    }

    private AgentEpisode buildEpisode(
            AgentTimelineContent timeline,
            List<AgentEvent> rawEvents,
            String phase,
            Map<String, Object> extraMetadata) {
        List<AgentEvent> events = sorted(rawEvents);
        List<String> eventIds =
                events.stream().map(AgentEvent::eventId).filter(this::hasText).toList();
        List<AgentCommand> commandEvents = commandEvents(events);
        List<AgentFileReference> fileReferences = fileReferences(events);
        List<AgentToolCall> toolCalls = toolCalls(events);
        List<String> commands =
                distinct(commandEvents.stream().map(AgentCommand::command).toList());
        List<String> files =
                distinct(fileReferences.stream().map(AgentFileReference::path).toList());
        List<String> toolNames = distinct(toolCalls.stream().map(AgentToolCall::toolName).toList());
        List<String> failureSignals = failureSignals(events);
        AgentOutcome outcome = outcome(events);
        Instant startTime = firstTime(events);
        Instant endTime = lastTime(events);
        String id = episodeId(timeline, eventIds);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.putAll(extraMetadata);
        return new AgentEpisode(
                id,
                goal(events),
                outcome,
                phase,
                events,
                eventIds,
                files,
                fileReferences,
                commands,
                commandEvents,
                toolNames,
                toolCalls,
                failureSignals,
                startTime,
                endTime,
                metadata);
    }

    private String episodeId(AgentTimelineContent timeline, List<String> eventIds) {
        String firstEventId = eventIds.isEmpty() ? "" : eventIds.getFirst();
        String lastEventId = eventIds.isEmpty() ? "" : eventIds.getLast();
        return HashUtils.sampledSha256(
                String.join(
                        "|",
                        normalized(timeline.sourceClient()),
                        normalized(timeline.sessionId()),
                        firstEventId,
                        lastEventId,
                        String.join(",", eventIds)));
    }

    private List<AgentCommand> commandEvents(List<AgentEvent> events) {
        return events.stream()
                .filter(event -> hasText(event.command()))
                .map(
                        event ->
                                new AgentCommand(
                                        event.command(),
                                        event.status(),
                                        event.output(),
                                        event.exitCode(),
                                        event.seq(),
                                        event.eventId()))
                .toList();
    }

    private List<AgentFileReference> fileReferences(List<AgentEvent> events) {
        return events.stream()
                .filter(event -> hasText(event.path()))
                .map(
                        event ->
                                new AgentFileReference(
                                        event.path(),
                                        event.operation(),
                                        event.seq(),
                                        event.eventId()))
                .toList();
    }

    private List<AgentToolCall> toolCalls(List<AgentEvent> events) {
        return events.stream()
                .filter(event -> hasText(event.toolName()))
                .map(
                        event ->
                                new AgentToolCall(
                                        event.toolName(),
                                        event.status(),
                                        event.seq(),
                                        event.eventId()))
                .toList();
    }

    private List<String> failureSignals(List<AgentEvent> events) {
        var signals = new LinkedHashSet<String>();
        for (AgentEvent event : events) {
            if (event.status() == AgentEventStatus.FAILED) {
                addSignal(signals, event.output());
                addSignal(signals, event.text());
            }
            Object failureSignal = event.metadata().get("failureSignal");
            if (failureSignal != null) {
                addSignal(signals, failureSignal.toString());
            }
        }
        return List.copyOf(signals);
    }

    private static void addSignal(LinkedHashSet<String> signals, String value) {
        String normalized = concise(value);
        if (!normalized.isBlank()) {
            signals.add(normalized);
        }
    }

    private AgentOutcome outcome(List<AgentEvent> events) {
        boolean success =
                events.stream()
                        .anyMatch(
                                event ->
                                        (isTerminal(event)
                                                        || event.kind() == AgentEventKind.COMMAND
                                                        || event.kind()
                                                                == AgentEventKind.TEST_RESULT)
                                                && event.status() == AgentEventStatus.SUCCESS);
        boolean failed =
                events.stream().anyMatch(event -> event.status() == AgentEventStatus.FAILED);
        boolean cancelled =
                events.stream().anyMatch(event -> event.status() == AgentEventStatus.CANCELLED);
        if (success && failed) {
            return AgentOutcome.SUCCESS;
        }
        if (success) {
            return AgentOutcome.SUCCESS;
        }
        if (cancelled) {
            return AgentOutcome.CANCELLED;
        }
        if (failed) {
            return AgentOutcome.FAILED;
        }
        return AgentOutcome.UNKNOWN;
    }

    private String goal(List<AgentEvent> events) {
        return events.stream()
                .filter(event -> event.kind() == AgentEventKind.USER_PROMPT)
                .map(AgentEvent::text)
                .filter(this::hasText)
                .findFirst()
                .orElse("");
    }

    private boolean exceedsGap(AgentEvent previous, AgentEvent event) {
        return previous != null
                && previous.occurredAt() != null
                && event.occurredAt() != null
                && Duration.between(previous.occurredAt(), event.occurredAt())
                                .compareTo(options.maxEventGap())
                        > 0;
    }

    private boolean exceedsEventLimit(List<AgentEvent> current) {
        return current.size() >= options.maxEventsPerEpisode();
    }

    private boolean fallbackBoundaryExceeded(
            String currentBoundaryKey,
            AgentEvent previous,
            AgentEvent event,
            List<AgentEvent> current) {
        return !hasText(currentBoundaryKey)
                && !hasOpenUserPrompt(current)
                && (exceedsGap(previous, event) || exceedsEventLimit(current));
    }

    private boolean hasOpenUserPrompt(List<AgentEvent> current) {
        return current.stream().anyMatch(event -> event.kind() == AgentEventKind.USER_PROMPT);
    }

    private boolean boundaryKeyChanged(String currentBoundaryKey, String nextBoundaryKey) {
        return hasText(currentBoundaryKey)
                && hasText(nextBoundaryKey)
                && !currentBoundaryKey.equals(nextBoundaryKey);
    }

    private String boundaryKey(AgentEvent event) {
        Object turnId = event.metadata().get("turnId");
        Object turnSeq = event.metadata().get("turnSeq");
        if (turnId != null || turnSeq != null) {
            return normalized(turnId) + "|" + normalized(turnSeq);
        }
        Object taskId = event.metadata().get("taskId");
        Object subtaskId = event.metadata().get("subtaskId");
        if (taskId == null && subtaskId == null) {
            return null;
        }
        return normalized(taskId) + "|" + normalized(subtaskId);
    }

    private static boolean isTerminal(AgentEvent event) {
        return event.kind() == AgentEventKind.STOP
                || event.kind() == AgentEventKind.SESSION_END
                || event.kind() == AgentEventKind.SYNTHETIC_BOUNDARY
                || event.kind() == AgentEventKind.TASK_COMPLETED;
    }

    private Instant firstTime(List<AgentEvent> events) {
        return events.stream()
                .map(AgentEvent::occurredAt)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Instant lastTime(List<AgentEvent> events) {
        Instant last = null;
        for (AgentEvent event : events) {
            if (event.occurredAt() != null) {
                last = event.occurredAt();
            }
        }
        return last;
    }

    private List<AgentEvent> sorted(List<AgentEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream()
                .filter(Objects::nonNull)
                .sorted(
                        Comparator.comparing(
                                        AgentEvent::seq, Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(
                                        AgentEvent::occurredAt,
                                        Comparator.nullsLast(Instant::compareTo))
                                .thenComparing(
                                        AgentEvent::eventId,
                                        Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<String> distinct(List<String> values) {
        var seen = new LinkedHashSet<String>();
        values.stream().filter(this::hasText).forEach(seen::add);
        return List.copyOf(seen);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalized(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String concise(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 180);
    }
}
