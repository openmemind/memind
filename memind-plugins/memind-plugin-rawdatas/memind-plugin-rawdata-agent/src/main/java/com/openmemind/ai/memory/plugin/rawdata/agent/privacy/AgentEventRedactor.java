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
package com.openmemind.ai.memory.plugin.rawdata.agent.privacy;

import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentPrivacyOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventKind;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Applies privacy redaction to normalized agent events.
 */
public final class AgentEventRedactor {

    private static final String FILE_CONTENT_PLACEHOLDER = "[REDACTED:file_content]";
    private static final String TRUNCATED_PLACEHOLDER = "truncated";

    private final AgentPrivacyOptions options;
    private final SecretPatternRedactor secretRedactor;

    public AgentEventRedactor() {
        this(new AgentPrivacyOptions());
    }

    public AgentEventRedactor(AgentPrivacyOptions options) {
        this(options, new SecretPatternRedactor());
    }

    public AgentEventRedactor(AgentPrivacyOptions options, SecretPatternRedactor secretRedactor) {
        this.options = options == null ? new AgentPrivacyOptions() : options;
        this.secretRedactor = secretRedactor == null ? new SecretPatternRedactor() : secretRedactor;
    }

    public AgentEvent redact(AgentEvent event) {
        if (event == null) {
            return null;
        }

        var state = new RedactionState();
        boolean dropFileContent = shouldDropFileContent(event);
        String text = redactSecrets(event.text(), state);
        String input =
                dropFileContent
                        ? redactFileContent(event.input(), state)
                        : truncate(
                                redactSecrets(event.input(), state),
                                options.maxInputChars(),
                                state);
        String output =
                dropFileContent
                        ? redactFileContent(event.output(), state)
                        : truncate(
                                redactSecrets(event.output(), state),
                                options.maxOutputChars(),
                                state);
        String command = redactSecrets(event.command(), state);

        Map<String, Object> metadata = mergeMetadata(event.metadata(), state);
        return new AgentEvent(
                event.eventId(),
                event.seq(),
                event.kind(),
                event.occurredAt(),
                text,
                event.toolName(),
                input,
                output,
                event.status(),
                event.durationMs(),
                event.inputTokens(),
                event.outputTokens(),
                event.contentHash(),
                event.path(),
                event.operation(),
                command,
                event.exitCode(),
                metadata);
    }

    private String redactSecrets(String value, RedactionState state) {
        if (value == null || !options.redactSecrets()) {
            return value;
        }
        SecretPatternRedactor.RedactionResult result = secretRedactor.redact(value);
        if (result.redacted()) {
            state.redacted = true;
            result.redactionKinds().forEach(state.redactionKinds::add);
        }
        return result.text();
    }

    private static String truncate(String value, int maxChars, RedactionState state) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        state.redacted = true;
        state.truncated = true;
        state.redactionKinds.add(TRUNCATED_PLACEHOLDER);
        return value.substring(0, maxChars);
    }

    private static String redactFileContent(String value, RedactionState state) {
        if (value == null) {
            return null;
        }
        state.redacted = true;
        state.redactionKinds.add("file_content");
        return FILE_CONTENT_PLACEHOLDER;
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> existing, RedactionState state) {
        if (!state.redacted && !state.truncated) {
            return existing == null ? Map.of() : Map.copyOf(existing);
        }
        var metadata = new LinkedHashMap<String, Object>();
        if (existing != null) {
            metadata.putAll(existing);
        }
        metadata.put("redacted", true);
        if (state.truncated) {
            metadata.put("truncated", true);
        }
        if (!state.redactionKinds.isEmpty()) {
            metadata.put("redactionKinds", java.util.List.copyOf(state.redactionKinds));
        }
        return Map.copyOf(metadata);
    }

    private boolean shouldDropFileContent(AgentEvent event) {
        if (event.kind() != AgentEventKind.FILE_READ && event.kind() != AgentEventKind.FILE_EDIT) {
            return false;
        }
        if (matchesAny(options.allowPathPatterns(), event.path())) {
            return false;
        }
        if (!options.captureFileContent()) {
            return true;
        }
        return matchesAny(options.denyPathPatterns(), event.path());
    }

    private static boolean matchesAny(java.util.List<String> patterns, String path) {
        if (path == null || path.isBlank() || patterns == null || patterns.isEmpty()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> matchesPathPattern(pattern, path));
    }

    private static boolean matchesPathPattern(String pattern, String path) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        String normalizedPattern = normalizePath(pattern);
        String normalizedPath = normalizePath(path);
        String fileName = fileName(normalizedPath);
        if (!hasGlobSyntax(normalizedPattern)) {
            return normalizedPath.equals(normalizedPattern)
                    || normalizedPath.endsWith("/" + normalizedPattern)
                    || fileName.equals(normalizedPattern);
        }
        String regex = globToRegex(normalizedPattern);
        return normalizedPath.matches(regex) || fileName.matches(regex);
    }

    private static String normalizePath(String value) {
        return value.replace('\\', '/');
    }

    private static String fileName(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private static boolean hasGlobSyntax(String pattern) {
        return pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0;
    }

    private static String globToRegex(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < pattern.length(); index++) {
            char ch = pattern.charAt(index);
            if (ch == '*') {
                boolean doubleStar =
                        index + 1 < pattern.length() && pattern.charAt(index + 1) == '*';
                regex.append(doubleStar ? ".*" : "[^/]*");
                if (doubleStar) {
                    index++;
                }
            } else if (ch == '?') {
                regex.append("[^/]");
            } else {
                appendRegexLiteral(regex, ch);
            }
        }
        regex.append('$');
        return regex.toString();
    }

    private static void appendRegexLiteral(StringBuilder regex, char ch) {
        if ("\\.[]{}()+-^$|".indexOf(ch) >= 0) {
            regex.append('\\');
        }
        regex.append(String.valueOf(ch).toLowerCase(Locale.ROOT));
    }

    private static final class RedactionState {
        private final Set<String> redactionKinds = new LinkedHashSet<>();
        private boolean redacted;
        private boolean truncated;
    }
}
