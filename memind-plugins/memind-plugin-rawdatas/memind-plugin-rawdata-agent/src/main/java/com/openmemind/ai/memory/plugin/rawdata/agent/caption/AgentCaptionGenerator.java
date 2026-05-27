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
package com.openmemind.ai.memory.plugin.rawdata.agent.caption;

import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * LLM-backed turn summary generator for agent episode segments, with deterministic fallback.
 */
public final class AgentCaptionGenerator implements CaptionGenerator {

    private static final int MAX_CAPTION_CHARS = 1200;

    private static final String SYSTEM_PROMPT =
            """
            You summarize one completed coding-agent turn for a memory system.

            The summary will be embedded and shown in retrieval results. It must be factual, \
            concise, and useful for continuing the project later.

            Rules:
            - Use only the provided episode text and metadata.
            - Do not invent files, tests, commands, decisions, outcomes, or next steps.
            - Preserve failed, partial, unknown, and unvalidated outcomes honestly.
            - Mention explicit validation only when command/test evidence is present.
            - Do not reveal or reconstruct redacted secrets.
            - Return a structured response with these fields:
              task: the user's concrete request or goal.
              outcome: success, failed, partial, cancelled, or unknown.
              summary: 1-2 factual sentences describing the result.
              keyActions: up to 3 important actions or decisions.
              evidence: up to 3 compact evidence lines for files, commands, validations, or \
            failures.
              next: explicit follow-up only; otherwise empty.
            """;

    private final StructuredChatClient chatClient;

    public AgentCaptionGenerator() {
        this(null);
    }

    public AgentCaptionGenerator(StructuredChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        return generate(content, metadata, null);
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata, String language) {
        String fallback = deterministicCaption(content, metadata);
        if (chatClient == null || content == null || content.isBlank()) {
            return Mono.just(fallback);
        }

        var messages =
                ChatMessages.systemUser(SYSTEM_PROMPT, userPrompt(content, metadata, language));
        return chatClient
                .call(messages, AgentCaptionResponse.class)
                .map(AgentCaptionGenerator::toCaption)
                .map(caption -> caption.isBlank() ? fallback : caption)
                .switchIfEmpty(Mono.just(fallback))
                .onErrorResume(ignored -> Mono.just(fallback));
    }

    private String deterministicCaption(String content, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return truncate(content, 160);
        }
        String goal = stringValue(metadata.get("goal"));
        String outcome = stringValue(metadata.get("outcome"));
        String summary = summary(metadata);
        String base =
                "Agent episode: "
                        + (goal.isBlank() ? "unknown goal" : goal)
                        + " -> "
                        + (outcome.isBlank() ? "unknown" : outcome);
        if (summary.isBlank()) {
            return base;
        }
        return base + " (" + summary + ")";
    }

    private static String userPrompt(
            String content, Map<String, Object> metadata, String language) {
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
        return """
        # Episode Metadata

        targetLanguage: %s
        goal: %s
        outcome: %s
        sourceClient: %s
        sessionId: %s
        timelineId: %s
        project: %s
        files: %s
        commands: %s
        toolNames: %s
        failureSignals: %s
        eventIds: %s

        # Episode Text

        %s
        """
                .formatted(
                        string(language),
                        string(safeMetadata.get("goal")),
                        string(safeMetadata.get("outcome")),
                        string(safeMetadata.get("sourceClient")),
                        string(safeMetadata.get("sessionId")),
                        string(safeMetadata.get("timelineId")),
                        string(firstPresent(safeMetadata, "projectName", "projectSlug")),
                        list(safeMetadata.get("files")),
                        list(safeMetadata.get("commands")),
                        list(safeMetadata.get("toolNames")),
                        list(safeMetadata.get("failureSignals")),
                        list(safeMetadata.get("eventIds")),
                        content == null ? "" : content);
    }

    private static String toCaption(AgentCaptionResponse response) {
        if (response == null) {
            return "";
        }
        var lines = new ArrayList<String>();
        appendLine(lines, "Task", response.task());
        String outcome = titleCase(response.outcome());
        String summary = clean(response.summary());
        if (!outcome.isBlank() || !summary.isBlank()) {
            lines.add("Outcome: " + joinSentence(outcome.isBlank() ? "Unknown" : outcome, summary));
        }
        appendList(lines, "Key actions", response.keyActions());
        appendList(lines, "Evidence", response.evidence());
        appendLine(lines, "Next", response.next());
        return truncate(String.join("\n\n", lines), MAX_CAPTION_CHARS);
    }

    private static void appendLine(List<String> lines, String label, String value) {
        String cleaned = clean(value);
        if (!cleaned.isBlank()) {
            lines.add(label + ": " + ensureSentence(cleaned));
        }
    }

    private static void appendList(List<String> lines, String label, List<String> values) {
        List<String> cleaned =
                values == null
                        ? List.of()
                        : values.stream()
                                .map(AgentCaptionGenerator::clean)
                                .filter(value -> !value.isBlank())
                                .limit(3)
                                .toList();
        if (cleaned.isEmpty()) {
            return;
        }
        lines.add(label + ":\n" + String.join("\n", cleaned.stream().map("- "::concat).toList()));
    }

    private static String joinSentence(String prefix, String suffix) {
        String first = ensureSentence(clean(prefix));
        String second = ensureSentence(clean(suffix));
        if (second.isBlank()) {
            return first;
        }
        return first + " " + second;
    }

    private static String ensureSentence(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()
                || cleaned.endsWith(".")
                || cleaned.endsWith("?")
                || cleaned.endsWith("!")) {
            return cleaned;
        }
        return cleaned + ".";
    }

    private static String titleCase(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            return "";
        }
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static Object firstPresent(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String list(Object value) {
        if (!(value instanceof List<?> list)) {
            return "[]";
        }
        return list.stream()
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .filter(item -> !item.isBlank())
                .toList()
                .toString();
    }

    private String summary(Map<String, Object> metadata) {
        var parts = new java.util.ArrayList<String>();
        String file = first(metadata.get("files"));
        String command = first(metadata.get("commands"));
        if (!file.isBlank() && !command.isBlank()) {
            parts.add(file + "; " + command);
        } else if (!file.isBlank()) {
            parts.add(file);
        } else if (!command.isBlank()) {
            parts.add(command);
        }
        String failureSignal = first(metadata.get("failureSignals"));
        if (!failureSignal.isBlank()) {
            parts.add(failureSignal);
        }
        if (contains(metadata.get("eventKinds"), "subagent_stop")) {
            parts.add("subagent");
        }
        if (contains(metadata.get("eventKinds"), "compact_boundary")) {
            parts.add("compact");
        } else if (contains(metadata.get("eventKinds"), "session_end")) {
            parts.add("session end");
        }
        return String.join("; ", parts);
    }

    private String first(Object value) {
        if (value instanceof List<?> list && !list.isEmpty() && list.getFirst() != null) {
            return list.getFirst().toString();
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean contains(Object value, String expected) {
        if (!(value instanceof List<?> list)) {
            return false;
        }
        return list.stream()
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .anyMatch(item -> expected.equalsIgnoreCase(item));
    }

    private static String truncate(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        return content.length() <= maxChars ? content : content.substring(0, maxChars);
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    public record AgentCaptionResponse(
            String task,
            String summary,
            String outcome,
            List<String> keyActions,
            List<String> evidence,
            String next) {}
}
