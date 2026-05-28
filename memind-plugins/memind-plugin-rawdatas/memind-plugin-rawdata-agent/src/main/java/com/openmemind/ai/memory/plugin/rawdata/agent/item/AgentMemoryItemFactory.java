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
package com.openmemind.ai.memory.plugin.rawdata.agent.item;

import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Builds deterministic memory items from agent episode metadata.
 */
public final class AgentMemoryItemFactory {

    public List<ExtractedMemoryEntry> deterministicEntries(ParsedSegment segment) {
        if (!isAgentEpisode(segment)) {
            return List.of();
        }
        EpisodeMetadata metadata = EpisodeMetadata.from(segment);
        var entries = new java.util.ArrayList<ExtractedMemoryEntry>();
        buildTool(segment, metadata).ifPresent(entries::add);
        buildResolution(segment, metadata).ifPresent(entries::add);
        return List.copyOf(entries);
    }

    private java.util.Optional<ExtractedMemoryEntry> buildTool(
            ParsedSegment segment, EpisodeMetadata episode) {
        if (episode.commands().isEmpty() && episode.toolNames().isEmpty()) {
            return java.util.Optional.empty();
        }

        String command = episode.commands().isEmpty() ? null : episode.commands().getFirst();
        String toolName = episode.toolNames().isEmpty() ? null : episode.toolNames().getFirst();
        int successCount = episode.successCount(command);
        int failCount = episode.failCount(command);
        String content = toolContent(episode, toolName, command, successCount, failCount);

        var metadata = baseMetadata(episode);
        if (toolName != null) {
            metadata.put("toolName", toolName);
        }
        if (command != null) {
            metadata.put("command", command);
        }
        metadata.put("successCount", successCount);
        metadata.put("failCount", failCount);
        metadata.put("evidenceEventIds", episode.eventIds());

        return java.util.Optional.of(
                entry(
                        content,
                        segment,
                        List.of("tools"),
                        metadata,
                        MemoryCategory.TOOL.categoryName(),
                        graphHints(episode, true, false)));
    }

    private java.util.Optional<ExtractedMemoryEntry> buildResolution(
            ParsedSegment segment, EpisodeMetadata episode) {
        if (episode.failureSignals().isEmpty() || !episode.resolvedOutcome()) {
            return java.util.Optional.empty();
        }
        Validation validation = episode.validation();
        if (validation == null) {
            return java.util.Optional.empty();
        }

        String failure = episode.failureSignals().getFirst();
        String files =
                episode.files().isEmpty()
                        ? "the touched files"
                        : String.join(", ", episode.files());
        String commands =
                validation.command() == null
                        ? String.join(", ", episode.commands())
                        : validation.command();
        String content =
                "%s was resolved in %s and validated with %s.".formatted(failure, files, commands);

        var metadata = baseMetadata(episode);
        metadata.put("problem", failure);
        metadata.put("outcome", episode.outcome());
        metadata.put("validatedBy", commands);
        metadata.put("evidenceEventIds", validation.evidenceEventIds());

        return java.util.Optional.of(
                entry(
                        content,
                        segment,
                        List.of("resolutions"),
                        metadata,
                        MemoryCategory.RESOLUTION.categoryName(),
                        graphHints(episode, true, true)));
    }

    private ExtractedMemoryEntry entry(
            String content,
            ParsedSegment segment,
            List<String> insightTypes,
            Map<String, Object> metadata,
            String category,
            ExtractedGraphHints graphHints) {
        return new ExtractedMemoryEntry(
                content,
                1.0f,
                null,
                null,
                null,
                null,
                observedAt(segment),
                segment.rawDataId(),
                null,
                insightTypes,
                Map.copyOf(metadata),
                MemoryItemType.FACT,
                category,
                graphHints);
    }

    private Map<String, Object> baseMetadata(EpisodeMetadata episode) {
        var metadata = new LinkedHashMap<String, Object>();
        copy(episode.raw(), metadata, "episodeId");
        copy(episode.raw(), metadata, "sessionId");
        copy(episode.raw(), metadata, "timelineId");
        copy(episode.raw(), metadata, "sourceClient");
        copy(episode.raw(), metadata, "projectId");
        copy(episode.raw(), metadata, "projectSlug");
        copy(episode.raw(), metadata, "projectName");
        copy(episode.raw(), metadata, "projectRootHash");
        copy(episode.raw(), metadata, "gitBranch");
        copy(episode.raw(), metadata, "toolStats");
        copy(episode.raw(), metadata, "toolRecords");
        copy(episode.raw(), metadata, "toolGroups");
        metadata.put("files", episode.files());
        metadata.put("commands", episode.commands());
        metadata.put("toolNames", episode.toolNames());
        metadata.put("failureSignals", episode.failureSignals());
        return metadata;
    }

    private ExtractedGraphHints graphHints(
            EpisodeMetadata episode, boolean includeTools, boolean includeFailureSignals) {
        var entities = new java.util.ArrayList<ExtractedGraphHints.ExtractedEntityHint>();
        episode.files().forEach(file -> entities.add(entity(file, "object", 0.9f)));
        episode.commands().forEach(command -> entities.add(entity(command, "object", 0.8f)));
        if (includeTools) {
            episode.toolNames().forEach(tool -> entities.add(entity(tool, "object", 0.7f)));
        }
        if (includeFailureSignals) {
            episode.failureSignals()
                    .forEach(signal -> entities.add(entity(signal, "concept", 0.8f)));
        }
        return new ExtractedGraphHints(entities, List.of());
    }

    private static ExtractedGraphHints.ExtractedEntityHint entity(
            String name, String entityType, Float salience) {
        return new ExtractedGraphHints.ExtractedEntityHint(name, entityType, salience);
    }

    private String toolContent(
            EpisodeMetadata episode,
            String toolName,
            String command,
            int successCount,
            int failCount) {
        if (command != null && !episode.files().isEmpty()) {
            String fileList = String.join(", ", episode.files());
            if (failCount > 0 || successCount > 0) {
                return "Use %s to validate changes touching %s; it failed %s and passed %s in this agent episode."
                        .formatted(
                                command, fileList, countWord(failCount), countWord(successCount));
            }
            return "Use %s to validate changes touching %s.".formatted(command, fileList);
        }
        if (command != null) {
            return "%s command %s failed %s and passed %s in episode %s."
                    .formatted(
                            toolName == null ? "Agent" : toolName,
                            command,
                            countWord(failCount),
                            countWord(successCount),
                            episode.episodeId());
        }
        return "Use %s during agent episodes."
                .formatted(toolName == null ? "agent tools" : toolName);
    }

    private static String countWord(int count) {
        return count == 1 ? "once" : count + " times";
    }

    private static Instant observedAt(ParsedSegment segment) {
        return segment.runtimeContext() == null ? null : segment.runtimeContext().observedAt();
    }

    private static boolean isAgentEpisode(ParsedSegment segment) {
        return segment != null
                && segment.metadata() != null
                && "agent_episode".equals(segment.metadata().get("segmentType"));
    }

    private static void copy(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    record EpisodeMetadata(Map<String, Object> raw) {

        static EpisodeMetadata from(ParsedSegment segment) {
            return new EpisodeMetadata(segment.metadata() == null ? Map.of() : segment.metadata());
        }

        String episodeId() {
            return string(raw.get("episodeId"));
        }

        String outcome() {
            return string(raw.get("outcome"));
        }

        List<String> files() {
            return stringList(raw.get("files"));
        }

        List<String> commands() {
            return stringList(raw.get("commands"));
        }

        List<String> toolNames() {
            return stringList(raw.get("toolNames"));
        }

        List<String> failureSignals() {
            return stringList(raw.get("failureSignals"));
        }

        List<String> eventIds() {
            return stringList(raw.get("eventIds"));
        }

        List<CommandEvent> commandEvents() {
            Object value = raw.get("commandEvents");
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(entry -> CommandEvent.from((Map<?, ?>) entry))
                    .toList();
        }

        List<FileEvent> fileEvents() {
            Object value = raw.get("fileEvents");
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(entry -> FileEvent.from((Map<?, ?>) entry))
                    .toList();
        }

        int successCount(String command) {
            return (int)
                    commandEvents().stream()
                            .filter(event -> command == null || command.equals(event.command()))
                            .filter(CommandEvent::success)
                            .count();
        }

        int failCount(String command) {
            return (int)
                    commandEvents().stream()
                            .filter(event -> command == null || command.equals(event.command()))
                            .filter(CommandEvent::failed)
                            .count();
        }

        boolean resolvedOutcome() {
            return "success".equalsIgnoreCase(outcome())
                    || "partial_success".equalsIgnoreCase(outcome());
        }

        Validation validation() {
            for (CommandEvent failed : commandEvents()) {
                if (!failed.failed()) {
                    continue;
                }
                for (CommandEvent candidate : commandEvents()) {
                    if (candidate.seq() <= failed.seq() || !candidate.success()) {
                        continue;
                    }
                    if (sameCommandFamily(failed.command(), candidate.command())) {
                        var evidence = new LinkedHashSet<String>();
                        addIfPresent(evidence, failed.eventId());
                        fileEvents().stream()
                                .filter(
                                        file ->
                                                file.seq() > failed.seq()
                                                        && file.seq() < candidate.seq())
                                .map(FileEvent::eventId)
                                .forEach(id -> addIfPresent(evidence, id));
                        addIfPresent(evidence, candidate.eventId());
                        return new Validation(candidate.command(), List.copyOf(evidence));
                    }
                }
            }
            return null;
        }
    }

    record CommandEvent(String eventId, int seq, String command, String status, String output) {

        static CommandEvent from(Map<?, ?> map) {
            return new CommandEvent(
                    string(map.get("eventId")),
                    intValue(map.get("seq")),
                    string(map.get("command")),
                    string(map.get("status")),
                    string(map.get("output")));
        }

        boolean success() {
            return "success".equalsIgnoreCase(status);
        }

        boolean failed() {
            return "failed".equalsIgnoreCase(status);
        }
    }

    record FileEvent(String eventId, int seq, String path) {

        static FileEvent from(Map<?, ?> map) {
            return new FileEvent(
                    string(map.get("eventId")), intValue(map.get("seq")), string(map.get("path")));
        }
    }

    record Validation(String command, List<String> evidenceEventIds) {}

    private static boolean sameCommandFamily(String failedCommand, String successCommand) {
        String failedTarget = commandFamily(failedCommand);
        String successTarget = commandFamily(successCommand);
        return !failedTarget.isBlank() && failedTarget.equals(successTarget);
    }

    private static String commandFamily(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String normalized =
                command.replaceAll("\\s+", " ").trim().toLowerCase(java.util.Locale.ROOT);
        normalized = normalized.replace(" -- ", " ");
        var parts = new java.util.ArrayList<>(List.of(normalized.split(" ")));
        parts.removeIf(
                part ->
                        part.isBlank()
                                || part.startsWith("-")
                                || "npm".equals(part)
                                || "pnpm".equals(part)
                                || "yarn".equals(part)
                                || "run".equals(part));
        if (parts.isEmpty()) {
            return normalized;
        }
        if ("test".equals(parts.getFirst()) && parts.size() > 1) {
            return "test:" + parts.get(1);
        }
        return String.join(" ", parts);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(AgentMemoryItemFactory::string)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(string(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void addIfPresent(LinkedHashSet<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }
}
