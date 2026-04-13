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
package com.openmemind.ai.memory.plugin.rawdata.document.caption;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentCaptionPromptsTest {

    @Test
    void buildUsesRetrievalFocusedSystemPromptWithDocumentSpecificConstraints() {
        var prompt =
                DocumentCaptionPrompts.build(
                        "spring.datasource.hikari.maximum-pool-size=16", Map.of(), "English");

        assertThat(prompt.systemPrompt()).contains("document chunk");
        assertThat(prompt.systemPrompt()).contains("retrieval system");
        assertThat(prompt.systemPrompt()).contains("contextual anchor");
        assertThat(prompt.systemPrompt()).contains("Distill, Don't Transcribe");
        assertThat(prompt.systemPrompt())
                .contains("Summarize only the provided chunk, not the whole document");
        assertThat(prompt.systemPrompt())
                .contains(
                        "Preserve names, paths, URLs, versions, dates, numbers, and config keys"
                                + " exactly");
        assertThat(prompt.systemPrompt()).contains("Do NOT infer broader document intent");
        assertThat(prompt.systemPrompt()).contains("Return valid JSON ONLY");
        assertThat(prompt.systemPrompt()).contains("Good Example");
        assertThat(prompt.systemPrompt()).contains("Bad Example");
        assertThat(prompt.systemPrompt()).contains("Self-Check");
    }

    @Test
    void buildSwitchesToWholeDocumentPromptWhenChunkStrategyIsDocumentWhole() {
        var prompt =
                DocumentCaptionPrompts.build(
                        "# Intro\nhello\n\n## Usage\nworld",
                        Map.of("chunkStrategy", "document-whole"),
                        "English");

        assertThat(prompt.systemPrompt()).contains("whole document");
        assertThat(prompt.systemPrompt()).doesNotContain("Summarize only the provided chunk");
        assertThat(prompt.userPrompt()).contains("Summarize the following document.");
    }

    @Test
    void buildIncludesChunkStructureHintsInUserPrompt() {
        var prompt =
                DocumentCaptionPrompts.build(
                        "ALTER TABLE users ADD COLUMN bio TEXT;",
                        Map.of("headingTitle", "Migration plan", "pageStart", 3, "pageEnd", 4),
                        "English");

        assertThat(prompt.userPrompt()).contains("Known structure hints:");
        assertThat(prompt.userPrompt()).contains("Migration plan");
        assertThat(prompt.userPrompt()).contains("Pages 3-4");
        assertThat(prompt.userPrompt()).contains("ALTER TABLE users ADD COLUMN bio TEXT;");
    }

    @Test
    void buildPreservesLiteralTemplateMarkersInChunkText() {
        var prompt =
                DocumentCaptionPrompts.build(
                        "{{ user.name }} uses {{ .Values.image.tag }}",
                        Map.of("headingTitle", "Helm config"),
                        "English");

        assertThat(prompt.userPrompt()).contains("{{ user.name }}");
        assertThat(prompt.userPrompt()).contains("{{ .Values.image.tag }}");
    }
}
