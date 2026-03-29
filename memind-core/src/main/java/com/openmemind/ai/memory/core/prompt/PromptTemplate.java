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
package com.openmemind.ai.memory.core.prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Structured representation of a prompt with ordered sections and {{variable}} placeholders.
 *
 * <p>Use {@link #builder(String)} to create instances. Call {@link #render(String)} to produce
 * the final {@link PromptResult}. Use {@link #withSection}, {@link #withoutSection},
 * {@link #withVariable} for immutable transformations (e.g., InsightType overrides).
 */
public final class PromptTemplate {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final String name;
    private final LinkedHashMap<String, String> sections;
    private final Map<String, String> variables;
    private final String userPromptTemplate;

    private PromptTemplate(
            String name,
            LinkedHashMap<String, String> sections,
            Map<String, String> variables,
            String userPromptTemplate) {
        this.name = name;
        this.sections = sections;
        this.variables = variables;
        this.userPromptTemplate = userPromptTemplate;
    }

    // ==================== Render ====================

    /**
     * Resolve variables, join sections, inject language rule, and return PromptResult.
     *
     * @param language target output language (e.g. "English", "Chinese")
     * @return assembled prompt result
     * @throws IllegalStateException if any {{variable}} placeholder remains unresolved
     */
    public PromptResult render(String language) {
        String system = renderSections();
        system = resolveVariables(system);
        String user = resolveVariables(userPromptTemplate);
        validateNoUnresolvedVariables(system, user);

        if (system.isBlank()) {
            return PromptResult.userOnly(user);
        }
        return PromptResult.of(system, user, language);
    }

    // ==================== Immutable transformations ====================

    public PromptTemplate withSection(String sectionName, String content) {
        var newSections = new LinkedHashMap<>(this.sections);
        newSections.put(sectionName, content);
        return new PromptTemplate(name, newSections, new HashMap<>(variables), userPromptTemplate);
    }

    public PromptTemplate withoutSection(String sectionName) {
        var newSections = new LinkedHashMap<>(this.sections);
        newSections.remove(sectionName);
        return new PromptTemplate(name, newSections, new HashMap<>(variables), userPromptTemplate);
    }

    public PromptTemplate withVariable(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        var newVars = new HashMap<>(this.variables);
        newVars.put(key, value);
        return new PromptTemplate(name, new LinkedHashMap<>(sections), newVars, userPromptTemplate);
    }

    // ==================== Debug ====================

    public String describeStructure() {
        var sb = new StringBuilder();
        sb.append("[").append(name).append("]\n");
        sb.append("Sections: ").append(String.join(", ", sections.keySet())).append("\n");
        sb.append("Variables: ")
                .append(
                        variables.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue())
                                .collect(Collectors.joining(", ")))
                .append("\n");
        return sb.toString();
    }

    public String preview(String language) {
        var result = render(language);
        return "=== SYSTEM ===\n"
                + (result.systemPrompt() != null ? result.systemPrompt() : "(none)")
                + "\n=== USER ===\n"
                + result.userPrompt();
    }

    /**
     * Preview the full system prompt without resolving or validating placeholders.
     *
     * @param language target output language
     * @return system prompt preview including the language rule
     */
    public String previewSystemPrompt(String language) {
        String system = renderSections();
        var lang =
                (language == null || language.isBlank()) ? PromptResult.DEFAULT_LANGUAGE : language;
        return PromptResult.languageRule(lang) + "\n\n" + system;
    }

    /**
     * Preview the full system prompt while resolving known variables but keeping unresolved
     * placeholders visible.
     *
     * @param language target output language
     * @return partially resolved system prompt preview including the language rule
     */
    public String previewResolvedSystemPrompt(String language) {
        String system = resolveVariables(renderSections());
        var lang =
                (language == null || language.isBlank()) ? PromptResult.DEFAULT_LANGUAGE : language;
        return PromptResult.languageRule(lang) + "\n\n" + system;
    }

    // ==================== Internal ====================

    private String renderSections() {
        return sections.values().stream()
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    private String resolveVariables(String text) {
        if (text == null) {
            return "";
        }
        var result = text;
        for (var entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private void validateNoUnresolvedVariables(String system, String user) {
        var unresolved = new ArrayList<String>();
        collectUnresolved(system, unresolved);
        collectUnresolved(user, unresolved);
        if (!unresolved.isEmpty()) {
            throw new IllegalStateException(
                    "Prompt [%s] has unresolved variables: %s".formatted(name, unresolved));
        }
    }

    private void collectUnresolved(String text, List<String> target) {
        var matcher = VARIABLE_PATTERN.matcher(text);
        while (matcher.find()) {
            target.add(matcher.group(1));
        }
    }

    // ==================== Builder ====================

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final LinkedHashMap<String, String> sections = new LinkedHashMap<>();
        private final Map<String, String> variables = new HashMap<>();
        private String userPromptTemplate = "";

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
        }

        public Builder section(String sectionName, String content) {
            sections.put(sectionName, content);
            return this;
        }

        public Builder variable(String key, String value) {
            variables.put(
                    Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /** Stores the value with JSON-unsafe characters escaped: \ " \n \r \t */
        public Builder jsonSafeVariable(String key, String value) {
            return variable(key, jsonEscape(value));
        }

        public Builder userPrompt(String template) {
            this.userPromptTemplate = template;
            return this;
        }

        public PromptTemplate build() {
            return new PromptTemplate(
                    name,
                    new LinkedHashMap<>(sections),
                    new HashMap<>(variables),
                    userPromptTemplate);
        }

        private static String jsonEscape(String value) {
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}
