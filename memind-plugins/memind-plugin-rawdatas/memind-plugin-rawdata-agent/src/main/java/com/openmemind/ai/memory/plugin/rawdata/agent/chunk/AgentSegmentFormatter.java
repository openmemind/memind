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
import com.openmemind.ai.memory.plugin.rawdata.agent.content.AgentTimelineContent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentCommand;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEpisode;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventStatus;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentFileReference;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentProject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Formats agent episodes into deterministic segment text and metadata.
 */
public final class AgentSegmentFormatter {

    public FormattedSegment format(AgentTimelineContent timeline, AgentEpisode episode) {
        String content = formatContent(timeline, episode);
        return new FormattedSegment(content, metadata(timeline, episode));
    }

    private String formatContent(AgentTimelineContent timeline, AgentEpisode episode) {
        var lines = new ArrayList<String>();
        appendSentence(lines, "Goal", episode.goal());
        lines.add("Outcome: " + episode.outcome().wireValue());
        if (timeline.project() != null && hasText(timeline.project().name())) {
            lines.add("Project: " + timeline.project().name());
        }
        if (!episode.files().isEmpty()) {
            lines.add("Files: " + String.join(", ", episode.files()));
        }
        if (!episode.commands().isEmpty()) {
            lines.add("Commands:");
            episode.commandEvents().stream()
                    .filter(command -> hasText(command.command()))
                    .forEach(command -> lines.add("- " + formatCommand(command)));
        }
        List<String> actions = actions(episode);
        if (!actions.isEmpty()) {
            lines.add("Actions:");
            actions.forEach(action -> lines.add("- " + action));
        }
        lines.add("Evidence:");
        episode.events()
                .forEach(event -> lines.add("- " + event.eventId() + ": " + evidence(event)));
        return String.join("\n", lines);
    }

    private Map<String, Object> metadata(AgentTimelineContent timeline, AgentEpisode episode) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("segmentType", "agent_episode");
        metadata.put("sourceClient", timeline.sourceClient());
        metadata.put("sessionId", timeline.sessionId());
        metadata.put("agentTurnId", timeline.agentTurnId());
        metadata.put("timelineId", timeline.timelineId());
        metadata.put("episodeId", episode.id());
        metadata.put("phase", episode.phase());
        metadata.put("goal", episode.goal());
        metadata.put("outcome", episode.outcome().wireValue());
        AgentProject project = timeline.project();
        if (project != null) {
            if (hasText(project.name())) {
                metadata.put("projectName", project.name());
            }
            String projectSlug = string(project.metadata().get("projectSlug"));
            if (hasText(projectSlug)) {
                metadata.put("projectSlug", projectSlug);
                metadata.put("projectId", projectSlug);
            }
            if (hasText(project.rootPath())) {
                metadata.put(
                        "projectRootHash", "sha256:" + HashUtils.sampledSha256(project.rootPath()));
            }
            if (project.git() != null && hasText(project.git().branch())) {
                metadata.put("gitBranch", project.git().branch());
            }
        }
        metadata.put("files", episode.files());
        metadata.put("commands", episode.commands());
        metadata.put("toolNames", episode.toolNames());
        metadata.put("failureSignals", episode.failureSignals());
        metadata.put("eventIds", episode.eventIds());
        metadata.put("commandEvents", commandEventMetadata(episode.commandEvents()));
        metadata.put("fileEvents", fileEventMetadata(episode.fileReferences()));
        metadata.putAll(AgentToolTelemetry.metadata(episode.events()));
        if (episode.startTime() != null) {
            metadata.put("windowStart", episode.startTime());
        }
        if (episode.endTime() != null) {
            metadata.put("windowEnd", episode.endTime());
        }
        metadata.putAll(episode.metadata());
        return Map.copyOf(metadata);
    }

    private static List<Map<String, Object>> commandEventMetadata(List<AgentCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        return commands.stream().map(AgentSegmentFormatter::commandEventMetadata).toList();
    }

    private static Map<String, Object> commandEventMetadata(AgentCommand command) {
        var metadata = new LinkedHashMap<String, Object>();
        putIfHasText(metadata, "eventId", command.eventId());
        putIfNotNull(metadata, "seq", command.seq());
        putIfHasText(metadata, "command", command.command());
        if (command.status() != null) {
            metadata.put("status", command.status().wireValue());
        }
        putIfHasText(metadata, "output", command.output());
        putIfNotNull(metadata, "exitCode", command.exitCode());
        return Map.copyOf(metadata);
    }

    private static List<Map<String, Object>> fileEventMetadata(List<AgentFileReference> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream().map(AgentSegmentFormatter::fileEventMetadata).toList();
    }

    private static Map<String, Object> fileEventMetadata(AgentFileReference file) {
        var metadata = new LinkedHashMap<String, Object>();
        putIfHasText(metadata, "eventId", file.eventId());
        putIfNotNull(metadata, "seq", file.seq());
        putIfHasText(metadata, "path", file.path());
        putIfHasText(metadata, "operation", file.operation());
        return Map.copyOf(metadata);
    }

    private static void putIfHasText(Map<String, Object> metadata, String key, String value) {
        if (hasText(value)) {
            metadata.put(key, value);
        }
    }

    private static void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private static String formatCommand(AgentCommand command) {
        String status =
                command.status() == null
                        ? AgentEventStatus.UNKNOWN.wireValue()
                        : command.status().wireValue();
        if (command.status() == AgentEventStatus.FAILED && hasText(command.output())) {
            return command.command() + " -> " + status + ": " + concise(command.output());
        }
        return command.command() + " -> " + status;
    }

    private static List<String> actions(AgentEpisode episode) {
        var actions = new ArrayList<String>();
        episode.fileReferences().stream()
                .filter(file -> hasText(file.path()))
                .forEach(file -> actions.add(operationLabel(file.operation()) + " " + file.path()));
        return List.copyOf(actions);
    }

    private static String evidence(AgentEvent event) {
        var parts = new ArrayList<String>();
        if (event.kind() != null) {
            parts.add(event.kind().wireValue());
        }
        if (hasText(event.command())) {
            parts.add(event.command());
        }
        if (hasText(event.path())) {
            parts.add(event.path());
        }
        if (event.status() != null) {
            parts.add(event.status().wireValue());
        }
        if (hasText(event.input())) {
            parts.add(concise(event.input()));
        }
        if (hasText(event.output())) {
            parts.add(concise(event.output()));
        } else if (hasText(event.text())) {
            parts.add(concise(event.text()));
        }
        return String.join(" ", parts);
    }

    private static String operationLabel(String operation) {
        if (!hasText(operation)) {
            return "Touched";
        }
        return switch (operation.toLowerCase(java.util.Locale.ROOT)) {
            case "read" -> "Read";
            case "edit", "write", "patch", "modified" -> "Modified";
            default -> Character.toUpperCase(operation.charAt(0)) + operation.substring(1);
        };
    }

    private static void appendSentence(List<String> lines, String label, String value) {
        if (!hasText(value)) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.endsWith(".") && !trimmed.endsWith("?") && !trimmed.endsWith("!")) {
            trimmed += ".";
        }
        lines.add(label + ": " + trimmed);
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public record FormattedSegment(String content, Map<String, Object> metadata) {

        public FormattedSegment {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
