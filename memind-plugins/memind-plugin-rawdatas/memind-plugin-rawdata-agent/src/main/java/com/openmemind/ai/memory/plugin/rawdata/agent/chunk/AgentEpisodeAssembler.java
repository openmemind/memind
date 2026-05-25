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
import com.openmemind.ai.memory.core.utils.TokenUtils;
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

    private static final List<String> PHASE_ORDER =
            List.of("investigation", "implementation", "validation", "handoff");

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
        List<AgentEpisode> baseEpisodes = buildBaseEpisodes(timeline, sorted(timeline.events()));
        List<AgentEpisode> result = new ArrayList<>();
        for (AgentEpisode episode : baseEpisodes) {
            if (TokenUtils.countTokens(episodeText(episode)) > options.targetEpisodeTokens()) {
                result.addAll(splitByPhase(timeline, episode));
            } else {
                result.add(episode);
            }
        }
        return List.copyOf(result);
    }

    private List<AgentEpisode> buildBaseEpisodes(
            AgentTimelineContent timeline, List<AgentEvent> events) {
        List<AgentEpisode> episodes = new ArrayList<>();
        List<AgentEvent> current = new ArrayList<>();
        AgentEvent previous = null;
        String currentTaskKey = null;

        for (AgentEvent event : events) {
            boolean startsNewPrompt =
                    event.kind() == AgentEventKind.USER_PROMPT && !current.isEmpty();
            boolean crossesBoundary =
                    !current.isEmpty()
                            && (startsNewPrompt
                                    || exceedsGap(previous, event)
                                    || exceedsEventLimit(current)
                                    || taskKeyChanged(currentTaskKey, taskKey(event)));
            if (crossesBoundary) {
                episodes.add(buildEpisode(timeline, current, "full", Map.of()));
                current = new ArrayList<>();
                currentTaskKey = null;
            }

            current.add(event);
            if (currentTaskKey == null) {
                currentTaskKey = taskKey(event);
            }
            previous = event;

            if (isTerminal(event)) {
                episodes.add(buildEpisode(timeline, current, "full", Map.of()));
                current = new ArrayList<>();
                currentTaskKey = null;
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
        List<String> eventIds = events.stream().map(AgentEvent::id).filter(this::hasText).toList();
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

    private List<AgentEpisode> splitByPhase(AgentTimelineContent timeline, AgentEpisode episode) {
        var byPhase = new LinkedHashMap<String, List<AgentEvent>>();
        PHASE_ORDER.forEach(phase -> byPhase.put(phase, new ArrayList<>()));
        for (AgentEvent event : episode.events()) {
            byPhase.get(phase(event)).add(event);
        }
        List<AgentEpisode> split = new ArrayList<>();
        for (String phase : PHASE_ORDER) {
            List<AgentEvent> phaseEvents = byPhase.get(phase);
            if (!phaseEvents.isEmpty()) {
                split.add(phaseEpisode(timeline, episode, phaseEvents, phase));
            }
        }
        return List.copyOf(split);
    }

    private AgentEpisode phaseEpisode(
            AgentTimelineContent timeline,
            AgentEpisode parent,
            List<AgentEvent> phaseEvents,
            String phase) {
        AgentEpisode local = buildEpisode(timeline, phaseEvents, phase, Map.of("phaseSplit", true));
        return new AgentEpisode(
                local.id(),
                parent.goal(),
                parent.outcome(),
                local.phase(),
                local.events(),
                local.eventIds(),
                local.files(),
                local.fileReferences(),
                local.commands(),
                local.commandEvents(),
                local.toolNames(),
                local.toolCalls(),
                local.failureSignals(),
                local.startTime(),
                local.endTime(),
                local.metadata());
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
                                        event.id()))
                .toList();
    }

    private List<AgentFileReference> fileReferences(List<AgentEvent> events) {
        return events.stream()
                .filter(event -> hasText(event.path()))
                .map(
                        event ->
                                new AgentFileReference(
                                        event.path(), event.operation(), event.seq(), event.id()))
                .toList();
    }

    private List<AgentToolCall> toolCalls(List<AgentEvent> events) {
        return events.stream()
                .filter(event -> hasText(event.toolName()))
                .map(
                        event ->
                                new AgentToolCall(
                                        event.toolName(), event.status(), event.seq(), event.id()))
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

    private String phase(AgentEvent event) {
        if (event.kind() == AgentEventKind.FILE_EDIT) {
            return "implementation";
        }
        if (event.kind() == AgentEventKind.STOP
                || event.kind() == AgentEventKind.SESSION_END
                || event.kind() == AgentEventKind.TASK_COMPLETED
                || event.kind() == AgentEventKind.ASSISTANT_MESSAGE) {
            return "handoff";
        }
        if ((event.kind() == AgentEventKind.COMMAND || event.kind() == AgentEventKind.TEST_RESULT)
                && event.status() == AgentEventStatus.SUCCESS) {
            return "validation";
        }
        return "investigation";
    }

    private String episodeText(AgentEpisode episode) {
        var lines = new ArrayList<String>();
        lines.add(episode.goal());
        lines.addAll(episode.failureSignals());
        lines.addAll(episode.files());
        lines.addAll(episode.commands());
        lines.addAll(
                episode.events().stream()
                        .map(
                                event ->
                                        String.join(
                                                " ",
                                                normalized(event.text()),
                                                normalized(event.output())))
                        .toList());
        return String.join("\n", lines);
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

    private boolean taskKeyChanged(String currentTaskKey, String nextTaskKey) {
        return hasText(currentTaskKey)
                && hasText(nextTaskKey)
                && !currentTaskKey.equals(nextTaskKey);
    }

    private String taskKey(AgentEvent event) {
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
                                        AgentEvent::id, Comparator.nullsLast(String::compareTo)))
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
