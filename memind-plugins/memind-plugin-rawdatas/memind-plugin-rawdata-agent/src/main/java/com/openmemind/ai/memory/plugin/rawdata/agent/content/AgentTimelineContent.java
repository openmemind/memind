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
package com.openmemind.ai.memory.plugin.rawdata.agent.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.utils.HashUtils;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventKind;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentProject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Raw content for a coding-agent timeline.
 */
public final class AgentTimelineContent extends RawContent {

    public static final String TYPE = "AGENT_TIMELINE";

    private static final int MAX_EVENT_FIELD_CHARS = 500;

    private final String sourceClient;
    private final String sourceVersion;
    private final String sessionId;
    private final String agentTurnId;
    private final String timelineId;
    private final AgentProject project;
    private final List<AgentEvent> events;
    private final Map<String, Object> metadata;

    public AgentTimelineContent(
            String sourceClient,
            String sourceVersion,
            String sessionId,
            String agentTurnId,
            String timelineId,
            AgentProject project,
            List<AgentEvent> events) {
        this(
                sourceClient,
                sourceVersion,
                sessionId,
                agentTurnId,
                timelineId,
                project,
                events,
                Map.of());
    }

    @JsonCreator
    public AgentTimelineContent(
            @JsonProperty("sourceClient") String sourceClient,
            @JsonProperty("sourceVersion") String sourceVersion,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("agentTurnId") String agentTurnId,
            @JsonProperty("timelineId") String timelineId,
            @JsonProperty("project") AgentProject project,
            @JsonProperty("events") List<AgentEvent> events,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.sourceClient = sourceClient;
        this.sourceVersion = sourceVersion;
        this.sessionId = sessionId;
        this.agentTurnId = agentTurnId;
        this.timelineId = timelineId;
        this.project = project;
        this.events = sortedEvents(events);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public String contentType() {
        return TYPE;
    }

    @Override
    public String toContentString() {
        var lines = new ArrayList<String>();
        lines.add("Agent Timeline");
        append(lines, "Source", sourceClientWithVersion());
        append(lines, "Session", sessionId);
        append(lines, "Agent Turn", agentTurnId);
        append(lines, "Timeline", timelineId);
        if (project != null) {
            append(lines, "Project", project.toDisplayString());
        }
        firstGoal().ifPresent(goal -> lines.add("Goal: " + goal));
        if (!events.isEmpty()) {
            lines.add("Events:");
            events.forEach(event -> lines.add("- " + formatEvent(event)));
        }
        return String.join("\n", lines);
    }

    @Override
    public String getContentId() {
        String eventIds = events.stream().map(AgentEvent::eventId).collect(Collectors.joining(","));
        String eventHash =
                HashUtils.sampledSha256(
                        events.stream().map(this::canonicalEvent).collect(Collectors.joining("|")));
        return HashUtils.sampledSha256(
                String.join(
                        "|",
                        normalized(sourceClient),
                        normalized(sessionId),
                        normalized(agentTurnId),
                        normalized(timelineId),
                        eventIds,
                        normalized(eventHash)));
    }

    @Override
    public Map<String, Object> contentMetadata() {
        return metadata;
    }

    @Override
    public RawContent withMetadata(Map<String, Object> metadata) {
        return new AgentTimelineContent(
                sourceClient,
                sourceVersion,
                sessionId,
                agentTurnId,
                timelineId,
                project,
                events,
                metadata);
    }

    @JsonProperty("sourceClient")
    public String sourceClient() {
        return sourceClient;
    }

    @JsonProperty("sourceVersion")
    public String sourceVersion() {
        return sourceVersion;
    }

    @JsonProperty("sessionId")
    public String sessionId() {
        return sessionId;
    }

    @JsonProperty("agentTurnId")
    public String agentTurnId() {
        return agentTurnId;
    }

    @JsonProperty("timelineId")
    public String timelineId() {
        return timelineId;
    }

    @JsonProperty("project")
    public AgentProject project() {
        return project;
    }

    @JsonProperty("events")
    public List<AgentEvent> events() {
        return events;
    }

    @JsonProperty("metadata")
    public Map<String, Object> metadata() {
        return metadata;
    }

    private java.util.Optional<String> firstGoal() {
        return events.stream()
                .filter(event -> event.kind() == AgentEventKind.USER_PROMPT)
                .map(AgentEvent::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst();
    }

    private String sourceClientWithVersion() {
        if (sourceVersion == null || sourceVersion.isBlank()) {
            return sourceClient;
        }
        if (sourceClient == null || sourceClient.isBlank()) {
            return sourceVersion;
        }
        return sourceClient + " " + sourceVersion;
    }

    private static List<AgentEvent> sortedEvents(List<AgentEvent> events) {
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

    private static void append(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(label + ": " + value);
        }
    }

    private static String formatEvent(AgentEvent event) {
        var parts = new ArrayList<String>();
        if (event.seq() != null) {
            parts.add("#" + event.seq());
        }
        if (event.kind() != null) {
            parts.add(event.kind().wireValue());
        }
        if (event.eventId() != null && !event.eventId().isBlank()) {
            parts.add(event.eventId());
        }
        if (event.status() != null) {
            parts.add("[" + event.status().wireValue() + "]");
        }
        appendPart(parts, "tool", event.toolName());
        appendPart(parts, "command", event.command());
        appendPart(parts, "path", event.path());
        appendPart(parts, "operation", event.operation());
        appendText(parts, event.text());
        appendText(parts, event.output());
        return String.join(" ", parts);
    }

    private static void appendPart(List<String> parts, String label, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(label + "=" + clamp(value));
        }
    }

    private static void appendText(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(clamp(value));
        }
    }

    private String canonicalEvent(AgentEvent event) {
        return String.join(
                "\u001f",
                normalized(event.eventId()),
                normalized(event.seq()),
                event.kind() == null ? "" : event.kind().wireValue(),
                normalized(event.occurredAt()),
                normalized(event.text()),
                normalized(event.toolName()),
                normalized(event.input()),
                normalized(event.output()),
                event.status() == null ? "" : event.status().wireValue(),
                normalized(event.durationMs()),
                normalized(event.inputTokens()),
                normalized(event.outputTokens()),
                normalized(event.contentHash()),
                normalized(event.path()),
                normalized(event.operation()),
                normalized(event.command()),
                normalized(event.exitCode()),
                canonicalMap(event.metadata()));
    }

    private static String canonicalMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return new TreeMap<>(map)
                .entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + normalized(entry.getValue()))
                        .collect(Collectors.joining(","));
    }

    private static String normalized(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String clamp(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_EVENT_FIELD_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_EVENT_FIELD_CHARS) + "...";
    }
}
