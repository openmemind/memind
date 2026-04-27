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

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

class MemoryItemUnifiedPromptsTest {

    @Test
    @DisplayName("Rendered prompt should preserve user-scope category grouping")
    void shouldPreserveUserScopeCategoryGrouping() {
        var categoryDefinitions =
                categoryDefinitionsSection(renderSystemPrompt(MemoryCategory.userCategories()));

        assertThat(categoryDefinitions)
                .contains("### [USER Scope]")
                .contains("**profile**")
                .contains("**behavior**")
                .contains("**event**")
                .doesNotContain("### [AGENT Scope]")
                .doesNotContain("**tool**")
                .doesNotContain("**directive**")
                .doesNotContain("**playbook**")
                .doesNotContain("**resolution**");
    }

    @Test
    @DisplayName("Rendered prompt should preserve agent-scope categories including tool")
    void shouldPreserveAgentScopeCategoriesIncludingTool() {
        var categoryDefinitions =
                categoryDefinitionsSection(renderSystemPrompt(MemoryCategory.agentCategories()));

        assertThat(categoryDefinitions)
                .contains("### [AGENT Scope]")
                .contains("**tool**")
                .contains("**directive**")
                .contains("**playbook**")
                .contains("**resolution**")
                .doesNotContain("### [USER Scope]")
                .doesNotContain("**profile**")
                .doesNotContain("**behavior**")
                .doesNotContain("**event**");
    }

    @Test
    @DisplayName("Rendered prompt should preserve all categories and scope grouping")
    void shouldPreserveAllCategoriesAndScopeGrouping() {
        var categoryDefinitions =
                categoryDefinitionsSection(renderSystemPrompt(Set.of(MemoryCategory.values())));

        assertThat(categoryDefinitions)
                .contains("### [USER Scope]")
                .contains("### [AGENT Scope]")
                .contains("**profile**")
                .contains("**behavior**")
                .contains("**event**")
                .contains("**tool**")
                .contains("**directive**")
                .contains("**playbook**")
                .contains("**resolution**");
    }

    @Test
    @DisplayName("Rendered examples should not recommend disallowed categories")
    void shouldRenderExamplesOnlyForAllowedCategories() {
        var userOnlyExamples = examplesSection(renderSystemPrompt(MemoryCategory.userCategories()));
        var agentOnlyExamples =
                examplesSection(renderSystemPrompt(MemoryCategory.agentCategories()));
        var allExamples = examplesSection(renderSystemPrompt(Set.of(MemoryCategory.values())));

        assertThat(userOnlyExamples)
                .contains("## profile")
                .contains("## behavior")
                .contains("## event")
                .doesNotContain("## tool")
                .doesNotContain("## directive")
                .doesNotContain("## playbook")
                .doesNotContain("## resolution");
        assertThat(agentOnlyExamples)
                .contains("## tool")
                .doesNotContain("## profile")
                .doesNotContain("## behavior")
                .doesNotContain("## event");
        assertThat(allExamples)
                .contains("## profile")
                .contains("## behavior")
                .contains("## event")
                .contains("## tool")
                .contains("## directive")
                .contains("## playbook")
                .contains("## resolution");
    }

    @Test
    @DisplayName("Rendered prompt should require durable value and filter low-value chatter")
    void shouldRequireDurableValueAndFilterLowValueChatter() {
        var systemPrompt = renderSystemPrompt(Set.of(MemoryCategory.values()));

        assertThat(systemPrompt)
                .contains("likely to remain useful beyond the current turn")
                .contains("future days, weeks, or months")
                .contains("Do not create a memory item just because a sentence is factual")
                .contains("generic greetings")
                .contains("pure acknowledgements")
                .contains("process chatter")
                .contains("temporary status updates")
                .contains("one-off control messages")
                .contains("vague summaries without actionable or retrievable detail");
    }

    @Test
    @DisplayName("Rendered prompt should preserve user permissive and agent strict extraction bias")
    void shouldPreserveExtractionBiasGradient() {
        var systemPrompt = renderSystemPrompt(Set.of(MemoryCategory.values()));

        assertThat(systemPrompt)
                .contains("For USER-scope categories")
                .contains("profile")
                .contains("behavior")
                .contains("event")
                .contains("extract clearly stated facts even if they seem minor")
                .contains("For AGENT-scope categories")
                .contains("tool")
                .contains("directive")
                .contains("playbook")
                .contains("resolution")
                .contains("use strict precision")
                .contains("If uncertain between AGENT memory and nothing, prefer nothing");
    }

    @Test
    @DisplayName("Rendered prompt should preserve important semantic dimensions")
    void shouldRenderContentPreservationChecklist() {
        var systemPrompt = renderSystemPrompt(Set.of(MemoryCategory.values()));

        assertThat(systemPrompt)
                .contains("Preserve who")
                .contains("what")
                .contains("when")
                .contains("where")
                .contains("why")
                .contains("important relationships")
                .contains("comparisons")
                .contains("capabilities")
                .contains("attitudes")
                .contains("intent")
                .contains("Do not compress away the detail that makes the memory useful");
    }

    @Test
    @DisplayName("Rendered prompt should keep key common confusion examples")
    void shouldKeepKeyCommonConfusionExamples() {
        var systemPrompt = renderSystemPrompt(Set.of(MemoryCategory.values()));

        assertThat(systemPrompt)
                .contains("We use Redis/Kafka/X for purpose Y")
                .contains("Currently learning/reading/migrating X")
                .contains("Teammate Zhang is responsible for backend services")
                .contains("Assistant says 'be gentle with yourself'")
                .contains("Do NOT extract unless the user later adopts it")
                .contains("User follows a fixed process or SOP for X");
    }

    @Test
    @DisplayName("Graph-enabled prompt should include entity and coreference quality rules")
    void graphEnabledPromptShouldIncludeEntityCoreferenceQualityRules() {
        var systemPrompt = renderSystemPrompt(Set.of(MemoryCategory.EVENT), true);

        assertThat(systemPrompt)
                .contains("Extract entities only when they help link related memories")
                .contains("Prefer concrete named entities")
                .contains("specific durable domain concepts")
                .contains("Resolve role-only mentions to named entities")
                .contains("my roommate")
                .contains("Emily")
                .contains("Do not create separate entities for generic role mentions")
                .contains("Do not extract generic nouns such as")
                .contains("project")
                .contains("team")
                .contains("system")
                .doesNotContain("aliasClass: role")
                .doesNotContain("role_alias");
    }

    @Test
    @DisplayName("Graph-disabled prompt should omit graph rules but keep subject clarity")
    void graphDisabledPromptShouldOmitGraphRulesButKeepSubjectClarity() {
        var systemPrompt = renderSystemPrompt(Set.of(MemoryCategory.EVENT), false);

        assertThat(systemPrompt)
                .doesNotContain("Extract entities only when they help link related memories")
                .doesNotContain("Resolve role-only mentions to named entities")
                .doesNotContain("Do not extract generic nouns such as")
                .doesNotContain("\"entities\"")
                .doesNotContain("\"causalRelations\"")
                .contains("# Subject Clarity")
                .contains("Do NOT use bare pronouns like \"他\", \"她\", \"他们\", or \"自己\"")
                .contains("If the subject cannot be made explicit from the source text");
    }

    @Test
    @DisplayName("Directive example should not duplicate category_reason")
    void directiveExampleShouldNotDuplicateCategoryReason() {
        var systemPrompt = renderSystemPrompt(Set.of(MemoryCategory.values()));
        var directiveStart = systemPrompt.indexOf("## directive");
        var playbookStart = systemPrompt.indexOf("## playbook");

        assertThat(directiveStart).isGreaterThanOrEqualTo(0);
        assertThat(playbookStart).isGreaterThan(directiveStart);

        var directiveExample = systemPrompt.substring(directiveStart, playbookStart);
        assertThat(countOccurrences(directiveExample, "\"category_reason\"")).isEqualTo(1);
    }

    @Test
    @DisplayName("Rendered prompt should include graph schema only when graph is enabled")
    void shouldIncludeGraphSchemaOnlyWhenGraphIsEnabled() {
        var enabled =
                MemoryItemUnifiedPrompts.build(
                                List.of(),
                                "user: 我昨天和 OpenAI 团队讨论了部署方案",
                                Instant.parse("2026-04-16T00:00:00Z"),
                                null,
                                Set.of(MemoryCategory.EVENT),
                                ItemGraphOptions.defaults().withEnabled(true))
                        .render("English");
        var disabled =
                MemoryItemUnifiedPrompts.build(
                                List.of(),
                                "user: 我昨天和 OpenAI 团队讨论了部署方案",
                                Instant.parse("2026-04-16T00:00:00Z"),
                                null,
                                Set.of(MemoryCategory.EVENT),
                                ItemGraphOptions.defaults().withEnabled(false))
                        .render("English");

        assertThat(enabled.systemPrompt())
                .contains("\"entities\"")
                .contains("\"causalRelations\"")
                .contains("causeIndex")
                .contains("effectIndex")
                .contains("Good entities")
                .contains("Bad entities")
                .contains("Good causal relation")
                .contains("Bad causal relation")
                .contains("not for topical similarity")
                .contains("not for ownership or dependency");
        assertThat(disabled.systemPrompt())
                .doesNotContain("\"entities\"")
                .doesNotContain("\"causalRelations\"");
    }

    @Test
    @DisplayName("Rendered prompt should describe allowed entity types and special anchors")
    void renderedPromptShouldDescribeAllowedEntityTypesAndSpecialAnchors() {
        var prompt =
                MemoryItemUnifiedPrompts.build(
                                List.of(),
                                "user: 我今天告诉助手联系张三",
                                Instant.parse("2026-04-18T00:00:00Z"),
                                "Alice",
                                Set.of(MemoryCategory.EVENT),
                                ItemGraphOptions.defaults().withEnabled(true))
                        .render("English");

        assertThat(prompt.systemPrompt())
                .contains("\"entityType\"")
                .contains("person")
                .contains("organization")
                .contains("special")
                .contains("Use \"special\" only for conversational role anchors");
    }

    @Test
    @DisplayName("Rendered prompt should describe alias observation schema when graph is enabled")
    void renderedPromptShouldDescribeAliasObservationSchemaWhenGraphIsEnabled() {
        var prompt =
                MemoryItemUnifiedPrompts.build(
                                List.of(),
                                "user: 我在 OpenAI（开放人工智能）团队工作",
                                Instant.parse("2026-04-18T00:00:00Z"),
                                null,
                                Set.of(MemoryCategory.EVENT),
                                ItemGraphOptions.defaults().withEnabled(true))
                        .render("English");

        assertThat(prompt.systemPrompt())
                .contains("\"aliasObservations\"")
                .contains("\"aliasSurface\"")
                .contains("\"aliasClass\"")
                .contains("explicit_parenthetical")
                .contains("explicit_slash_apposition");
    }

    @Test
    @DisplayName("Rendered prompt should require explicit causal strength when graph is enabled")
    void renderedPromptRequiresExplicitCausalStrengthWhenGraphHintsAreEnabled() {
        var prompt =
                MemoryItemUnifiedPrompts.build(
                                List.of(),
                                "user: the rollback happened because the deployment failed",
                                Instant.parse("2026-04-18T00:00:00Z"),
                                null,
                                Set.of(MemoryCategory.EVENT),
                                ItemGraphOptions.defaults().withEnabled(true))
                        .render("English");

        assertThat(prompt.systemPrompt())
                .contains("include an explicit strength in [0,1]")
                .contains("omit the relation")
                .contains("\"aliasObservations\"")
                .contains("Use \"special\" only for conversational role anchors");
    }

    @Test
    @DisplayName(
            "Rendered prompt should describe alias observation schema when graph is enabled for"
                    + " Chinese")
    void renderedPromptShouldDescribeAliasObservationSchemaWhenGraphIsEnabledForChinese() {
        var prompt =
                MemoryItemUnifiedPrompts.build(
                                List.of(),
                                "user: 我在 OpenAI（开放人工智能）团队工作",
                                Instant.parse("2026-04-18T00:00:00Z"),
                                null,
                                Set.of(MemoryCategory.EVENT),
                                ItemGraphOptions.defaults().withEnabled(true))
                        .render("Chinese");

        assertThat(prompt.systemPrompt())
                .contains("\"aliasObservations\"")
                .contains("\"aliasSurface\"")
                .contains("\"aliasClass\"")
                .contains("explicit_parenthetical")
                .contains("explicit_slash_apposition");
    }

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
                                + " contentPreservation, categoryContext, identityContext,"
                                + " subjectContext, temporalContext, scoring, output, examples");
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
                .contains("If uncertain between AGENT memory and nothing, prefer nothing")
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
    @DisplayName("Rendered prompt should enforce structured temporal semantics")
    void shouldEnforceStructuredTemporalSemantics() {
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
                .contains("Event items should populate `time` only when the text itself")
                .contains(
                        "Do NOT use message timestamps or conversation timestamps as default"
                                + " temporal")
                .contains("canonical half-open bucket")
                .contains("reference anchor for resolving relative expressions");
    }

    @Test
    @DisplayName("Rendered prompt should expose structured time schema")
    void shouldRenderStructuredTimeSchemaInUnifiedPrompt() {
        var result =
                MemoryItemUnifiedPrompts.build(
                                List.of(),
                                "user: 我上周去了杭州",
                                Instant.parse("2026-04-16T00:00:00Z"),
                                null,
                                Set.of(MemoryCategory.EVENT))
                        .render("English");

        assertThat(result.systemPrompt())
                .contains("time.expression")
                .contains("time.start")
                .contains("time.end")
                .contains("time.granularity");
    }

    @Test
    @DisplayName("Rendered prompt should describe optional versioned thread semantics output")
    void shouldDescribeOptionalVersionedThreadSemanticsOutput() {
        var result =
                MemoryItemUnifiedPrompts.build(
                                List.of(),
                                "user: 项目 alpha 从规划阶段进入开发阶段",
                                Instant.parse("2026-04-16T00:00:00Z"),
                                null,
                                Set.of(MemoryCategory.EVENT))
                        .render("English");

        assertThat(result.systemPrompt())
                .contains("\"threadSemantics\"")
                .contains("\"version\": 1")
                .contains("\"markers\"")
                .contains("\"canonicalRefs\"")
                .contains("\"continuityLinks\"")
                .contains("If unsure, omit `threadSemantics` entirely");
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    @DisplayName("Rendered prompt should resolve temporal context in system zone")
    void shouldRenderTemporalContextUsingSystemZone() {
        var originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        try {
            var result =
                    MemoryItemUnifiedPrompts.build(
                                    List.of(),
                                    "user: 我昨天修好了线上 bug",
                                    Instant.parse("2026-04-15T17:00:00Z"),
                                    null,
                                    Set.of(MemoryCategory.EVENT))
                            .render("English");

            assertThat(result.systemPrompt())
                    .contains("System Time Zone: Asia/Shanghai")
                    .contains("Today's date: 2026-04-16");
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
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

    private static String renderSystemPrompt(Set<MemoryCategory> categories) {
        return MemoryItemUnifiedPrompts.build(
                        List.of(),
                        "user: Please remember the durable project preference",
                        Instant.parse("2026-04-16T00:00:00Z"),
                        "Ada",
                        categories,
                        ItemGraphOptions.defaults().withEnabled(true))
                .render("English")
                .systemPrompt();
    }

    private static String renderSystemPrompt(Set<MemoryCategory> categories, boolean graphEnabled) {
        return MemoryItemUnifiedPrompts.build(
                        List.of(),
                        "user: Please remember the durable project preference",
                        Instant.parse("2026-04-16T00:00:00Z"),
                        "Ada",
                        categories,
                        ItemGraphOptions.defaults().withEnabled(graphEnabled))
                .render("English")
                .systemPrompt();
    }

    private static String categoryDefinitionsSection(String systemPrompt) {
        var start = systemPrompt.indexOf("## Category Definitions");
        var end = systemPrompt.indexOf("# Identity", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return systemPrompt.substring(start, end);
    }

    private static String examplesSection(String systemPrompt) {
        var start = systemPrompt.indexOf("# Examples by Category");
        return start < 0 ? "" : systemPrompt.substring(start);
    }

    private static int countOccurrences(String text, String needle) {
        var count = 0;
        var index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static MemoryInsightType createInsightType(String name, List<String> categories) {
        return new MemoryInsightType(
                null, name, null, null, categories, 100, null, null, null, null, null, null);
    }
}
