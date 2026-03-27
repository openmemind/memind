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
package com.openmemind.ai.memory.core.prompt.extraction.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InsightGroupPromptsTest {

    @Test
    @DisplayName("Rendered prompt should prioritize stable namespace reuse and explicit validation")
    void shouldPrioritizeStableNamespaceReuseAndExplicitValidation() {
        var insightType = createInsightType();
        var items =
                List.of(
                        new MemoryItem(
                                1L,
                                "m1",
                                "The team wants a concise way to redirect a meeting back to the"
                                        + " agenda.",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));

        var result =
                InsightGroupPrompts.build(
                                insightType, items, List.of("Meeting Coordination"), "English")
                        .render("English");

        assertThat(result.systemPrompt())
                .contains("namespace stability, not novelty")
                .contains("Treat existing groups as the default namespace.")
                .contains("Each item MUST be assigned to exactly ONE group.")
                .contains(
                        "Test: Can you describe what ALL items in this group have in common using"
                                + " one specific phrase?")
                .contains("`reason`: CRITICAL. Brief explanation of the enduring shared theme.")
                .contains(
                        "This validates your grouping logic. For reasoning only, will NOT be"
                                + " stored.");
    }

    @Test
    @DisplayName("Rendered prompt should use open source safe examples with natural language names")
    void shouldUseOpenSourceSafeExamplesWithNaturalLanguageNames() {
        var result =
                InsightGroupPrompts.build(
                                createInsightType(),
                                List.of(
                                        new MemoryItem(
                                                1L,
                                                "m1",
                                                "Delayed review responses are slowing down"
                                                        + " delivery.",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)),
                                List.of("Meeting Coordination", "Data Privacy"),
                                "English")
                        .render("English");

        assertThat(result.systemPrompt())
                .contains("Meeting Coordination")
                .contains("Data Privacy")
                .contains("Documentation Style")
                .contains("Incident Communication")
                .contains("Review Turnaround")
                .contains("Requested output language: Spanish")
                .contains("[same theme written in Spanish]")
                .doesNotContain("career_background")
                .doesNotContain("boundary_setting")
                .doesNotContain("开发工具偏好")
                .doesNotContain("自我抚慰与动物园心安")
                .doesNotContain("2026-03-27 会话记录")
                .doesNotContain("Coordinacion de reuniones");
    }

    @Test
    @DisplayName(
            "Rendered prompt should keep prompt text in English while scoping new names to the"
                    + " requested language")
    void shouldScopeOnlyNewGroupNamesToRequestedLanguage() {
        var result =
                InsightGroupPrompts.build(
                                createInsightType(),
                                List.of(
                                        new MemoryItem(
                                                1L,
                                                "m1",
                                                "Review responses are often delayed until the next"
                                                        + " sprint.",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)),
                                List.of("Meeting Coordination"),
                                "Spanish")
                        .render("Spanish");

        assertThat(result.userPrompt())
                .contains("Requested Output Language for NEW group names: Spanish")
                .contains("Existing group names must be copied exactly as provided.")
                .contains("Do NOT translate reused group names.");
    }

    private static MemoryInsightType createInsightType() {
        return new MemoryInsightType(
                1L,
                "collaboration",
                "Reusable patterns related to teamwork, communication, coordination,"
                        + " documentation, and execution.",
                null,
                List.of("procedural"),
                400,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
