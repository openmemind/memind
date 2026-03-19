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

/**
 * Assembled prompt result containing a system prompt and a user prompt.
 *
 * <p>Produced by {@link PromptTemplate#render(String)}. Use the factory methods
 * {@link #of(String, String, String)} or {@link #userOnly(String)} to create instances:
 *
 * <ul>
 *   <li>{@link #of} — injects a language rule at the beginning of the system prompt,
 *       ensuring the LLM outputs in the specified language.
 *   <li>{@link #userOnly} — user prompt only, no system prompt, no language rule.
 * </ul>
 *
 * @param systemPrompt system prompt (may be {@code null} for user-only prompts)
 * @param userPrompt user prompt (never {@code null})
 */
public record PromptResult(String systemPrompt, String userPrompt) {

    /**
     * Default language for output: English.
     */
    public static final String DEFAULT_LANGUAGE = "English";

    /**
     * Build the output language rule for a specific language.
     *
     * @param language the target output language (e.g. "English", "Chinese", "Japanese")
     * @return the language rule prompt text
     */
    static String languageRule(String language) {
        return """
        # ABSOLUTE REQUIREMENT: Output Language = %s
        You MUST write every string value in your JSON response in %s.
        This applies to ALL fields: "content", "whenToUse", "evidence", "caption", \
        and any other text field.

        The task instructions and format examples below are written in English solely \
        for structural clarity. They are NOT a signal to output in English.

        WRONG (English output when %s is required):
          "content": "User uses HikariCP in their Spring Boot 3 service."
        CORRECT (%s output):
          "content": "[equivalent sentence written in %s]"

        Outputting in any language other than %s is an error. No exceptions.\
        """
                .formatted(language, language, language, language, language, language);
    }

    /**
     * Standard factory method: automatically injects language rule (default English) at the very
     * beginning of the system prompt.
     *
     * <p>All prompt builders should use this method instead of {@code new PromptResult(system, user)}.
     */
    public static PromptResult of(String systemPrompt, String userPrompt) {
        return of(systemPrompt, userPrompt, DEFAULT_LANGUAGE);
    }

    /**
     * Factory method with explicit language: injects language rule for the specified language.
     *
     * @param systemPrompt system prompt content
     * @param userPrompt   user prompt content
     * @param language     target output language (e.g. "Chinese", "English")
     */
    public static PromptResult of(String systemPrompt, String userPrompt, String language) {
        var lang = (language == null || language.isBlank()) ? DEFAULT_LANGUAGE : language;
        return new PromptResult(languageRule(lang) + "\n\n" + systemPrompt, userPrompt);
    }

    /** Single prompt scenario (only user prompt, does not inject language rule) */
    public static PromptResult userOnly(String userPrompt) {
        return new PromptResult(null, userPrompt);
    }

    /** Whether there is a system prompt */
    public boolean hasSystemPrompt() {
        return systemPrompt != null && !systemPrompt.isBlank();
    }
}
