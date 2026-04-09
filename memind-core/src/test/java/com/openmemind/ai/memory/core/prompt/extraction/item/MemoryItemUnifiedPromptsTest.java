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
package com.openmemind.ai.memory.core.prompt.extraction.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemoryItemUnifiedPromptsTest {

    @Test
    @DisplayName("build default should keep runtime category placeholders raw")
    void buildDefaultKeepsDynamicCategoryPlaceholdersRaw() {
        String preview = MemoryItemUnifiedPrompts.buildDefault().previewSystemPrompt("English");

        assertThat(preview).contains("{{CATEGORY_CONTEXT}}");
        assertThat(preview).contains("{{IDENTITY_CONTEXT}}");
        assertThat(preview).contains("{{TEMPORAL_CONTEXT}}");
    }

    @Test
    @DisplayName("Rendered prompt should contain Decision Logic and Category Definitions")
    void shouldContainCategoryContext() {
        var insightTypes =
                List.of(
                        createInsightType("profile", List.of("profile")),
                        createInsightType("experiences", List.of("event")),
                        createInsightType("directives", List.of("directive")),
                        createInsightType("playbooks", List.of("playbook")),
                        createInsightType("resolutions", List.of("resolution")));
        var template =
                MemoryItemUnifiedPrompts.build(
                        insightTypes,
                        "user: hello",
                        Instant.parse("2026-03-18T00:00:00Z"),
                        "Alice",
                        Set.of(MemoryCategory.PROFILE, MemoryCategory.EVENT));
        var result = template.render("English");
        assertThat(template.describeStructure())
                .contains(
                        "Sections: objective, principles, extractionScope, extractionBias,"
                                + " categoryContext, identityContext, subjectContext,"
                                + " temporalContext, scoring, output, examples");
        assertThat(result.systemPrompt()).contains("## Decision Logic");
        assertThat(result.systemPrompt()).contains("## Category Definitions");
        assertThat(result.systemPrompt()).contains("**profile**");
        assertThat(result.systemPrompt()).contains("**event**");
        assertThat(result.systemPrompt()).contains("Alice");
        assertThat(result.systemPrompt()).contains("# Temporal Resolution");
    }

    @Test
    @DisplayName("Rendered prompt should describe new agent categories and strict gating")
    void shouldDescribeNewAgentCategoriesAndStrictGating() {
        var insightTypes =
                List.of(
                        createInsightType("directives", List.of("directive")),
                        createInsightType("playbooks", List.of("playbook")),
                        createInsightType("resolutions", List.of("resolution")));
        var template =
                MemoryItemUnifiedPrompts.build(
                        insightTypes,
                        "user: compare the repositories before proposing changes",
                        Instant.parse("2026-03-28T00:00:00Z"),
                        null,
                        Set.of(
                                MemoryCategory.DIRECTIVE,
                                MemoryCategory.PLAYBOOK,
                                MemoryCategory.RESOLUTION));

        var result = template.render("English");

        assertThat(result.systemPrompt())
                .contains("directive")
                .contains("playbook")
                .contains("resolution")
                .contains("if uncertain between agent memory and nothing, prefer nothing")
                .contains("one-off control messages");
    }

    @Test
    @DisplayName(
            "Rendered prompt should exclude supportive assistant suggestions from agent memory")
    void shouldExcludeSupportiveAssistantSuggestionsFromAgentMemory() {
        var insightTypes = List.of(createInsightType("directives", List.of("directive")));
        var template =
                MemoryItemUnifiedPrompts.build(
                        insightTypes,
                        """
                        [2026-03-27 01:54] user: 我总是觉得自己输了。
                        [2026-03-27 01:55] assistant: 当你感到“我输了”时，先不要评判，只问自己“除了输，我还感受到什么？”
                        """,
                        Instant.parse("2026-03-27T00:00:00Z"),
                        null,
                        Set.of(MemoryCategory.DIRECTIVE));

        var result = template.render("English");

        assertThat(result.systemPrompt())
                .contains("Do NOT extract assistant emotional support")
                .contains("reflective coaching questions")
                .contains("Avoid conversational framing like \"Assistant suggested:\"");
    }

    @Test
    @DisplayName("Rendered prompt should enforce strict occurredAt semantics")
    void shouldEnforceStrictOccurredAtSemantics() {
        var insightTypes =
                List.of(
                        createInsightType("profile", List.of("profile")),
                        createInsightType("experiences", List.of("event")),
                        createInsightType("directives", List.of("directive")));
        var template =
                MemoryItemUnifiedPrompts.build(
                        insightTypes,
                        """
                        [2026-03-27 02:18] user: 当对方是我信任的人时，我总会先找自己的问题。
                        """,
                        Instant.parse("2026-03-27T02:18:00Z"),
                        null,
                        Set.of(
                                MemoryCategory.PROFILE,
                                MemoryCategory.BEHAVIOR,
                                MemoryCategory.EVENT,
                                MemoryCategory.DIRECTIVE));

        var result = template.render("English");

        assertThat(result.systemPrompt())
                .contains(
                        "Profile, behavior, directive, playbook, resolution, and tool items should"
                                + " normally set")
                .contains("Event items should populate `occurredAt` only when the text itself")
                .contains(
                        "Do NOT use message timestamps or conversation timestamps as `occurredAt`")
                .contains("reference anchor for resolving relative expressions");
    }

    @Test
    @DisplayName("Rendered prompt should require explicit third-party subject references")
    void shouldRequireExplicitThirdPartySubjectReferences() {
        var insightTypes = List.of(createInsightType("relationships", List.of("profile")));
        var template =
                MemoryItemUnifiedPrompts.build(
                        insightTypes,
                        """
                        [2026-03-27 02:18] user: 我朋友最近越来越觉得自己不想再照顾继子了。
                        """,
                        Instant.parse("2026-03-27T02:18:00Z"),
                        null,
                        Set.of(MemoryCategory.PROFILE, MemoryCategory.EVENT));

        var result = template.render("English");

        assertThat(result.systemPrompt())
                .contains("# Subject Clarity")
                .contains("\"User\" refers only to the memory owner")
                .contains("explicitly name that subject with a stable role phrase")
                .contains("Do NOT use bare pronouns like \"他\", \"她\", \"他们\", or \"自己\"")
                .contains("If the subject cannot be made explicit from the source text");
        assertThat(result.userPrompt())
                .contains("<SourceText>")
                .doesNotContain("<Conversation>")
                .doesNotContain("following conversation")
                .doesNotContain("## Decision Logic")
                .doesNotContain("# Extraction Scope");
    }

    @Test
    @DisplayName("build with registry should replace unified extraction instructions")
    void buildWithRegistryUsesOverrideInstruction() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(
                                PromptType.MEMORY_ITEM_UNIFIED,
                                "Custom unified extraction instruction")
                        .build();
        var template =
                MemoryItemUnifiedPrompts.build(
                        registry,
                        List.of(),
                        "user: hello",
                        Instant.parse("2026-03-29T00:00:00Z"),
                        null,
                        Set.of());
        var result = template.render("English");

        assertThat(result.systemPrompt()).contains("Custom unified extraction instruction");
        assertThat(result.systemPrompt()).doesNotContain("# Core Principles");
    }

    private static MemoryInsightType createInsightType(String name, List<String> categories) {
        return new MemoryInsightType(
                null, name, null, null, categories, 100, null, null, null, null, null, null, null);
    }
}
