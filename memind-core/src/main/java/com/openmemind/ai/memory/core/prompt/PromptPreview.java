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

import java.util.Objects;

/** Utility for rendering prompt previews without runtime variables. */
public final class PromptPreview {

    private PromptPreview() {}

    public static String preview(PromptRegistry registry, PromptType type, String language) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(type, "type");
        var lang = normalizeLanguage(language);
        if (registry.hasOverride(type)) {
            return PromptResult.languageRule(lang) + "\n\n" + registry.getOverride(type);
        }
        return PromptDefaults.build(type).previewSystemPrompt(lang);
    }

    public static String previewExpanded(
            PromptRegistry registry, PromptType type, String language) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(type, "type");
        var lang = normalizeLanguage(language);
        if (registry.hasOverride(type)) {
            return PromptResult.languageRule(lang) + "\n\n" + registry.getOverride(type);
        }
        return PromptDefaults.buildPreview(type).previewResolvedSystemPrompt(lang);
    }

    private static String normalizeLanguage(String language) {
        return (language == null || language.isBlank()) ? PromptResult.DEFAULT_LANGUAGE : language;
    }
}
