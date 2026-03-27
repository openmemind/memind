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
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelfVerificationPromptsTest {

    @Test
    @DisplayName(
            "Rendered prompt should contain category context, identity, temporal resolution, and"
                    + " examples")
    void shouldContainAllSections() {
        var insightTypes =
                List.of(
                        createInsightType("identity", List.of("profile")),
                        createInsightType("experiences", List.of("event")),
                        createInsightType("procedural", List.of("procedural")));
        var existingEntries = List.of(createEntry("User is a backend engineer", "profile"));
        var template =
                SelfVerificationPrompts.build(
                        "user: I am a backend engineer working on Spring Boot",
                        existingEntries,
                        Instant.parse("2026-03-18T00:00:00Z"),
                        insightTypes,
                        "Alice",
                        Set.of(
                                MemoryCategory.PROFILE,
                                MemoryCategory.EVENT,
                                MemoryCategory.PROCEDURAL));
        var result = template.render("English");

        // System prompt structure
        assertThat(result.systemPrompt()).contains("memory extraction reviewer");
        assertThat(result.systemPrompt()).contains("# Core Principles");
        assertThat(result.systemPrompt()).contains("Non-overlap");
        assertThat(result.systemPrompt()).contains("# Common Miss Patterns");
        assertThat(result.systemPrompt()).contains("# Extraction Bias");
        assertThat(result.systemPrompt()).contains("## Decision Logic");
        assertThat(result.systemPrompt()).contains("## Category Definitions");
        assertThat(result.systemPrompt()).contains("Alice");
        assertThat(result.systemPrompt()).contains("# Temporal Resolution");
        assertThat(result.systemPrompt()).contains("category_reason");

        // Examples
        assertThat(result.systemPrompt()).contains("# Examples");
        assertThat(result.systemPrompt()).contains("Good Example");
        assertThat(result.systemPrompt()).contains("Bad Example");

        // User prompt structure
        assertThat(result.userPrompt()).contains("# AlreadyExtracted");
        assertThat(result.userPrompt()).contains("[profile] User is a backend engineer");
        assertThat(result.userPrompt()).contains("# Conversation");
    }

    @Test
    @DisplayName("Should use default identity when userName is null")
    void shouldUseDefaultIdentity() {
        var template =
                SelfVerificationPrompts.build(
                        "user: hello", List.of(), null, List.of(), null, null);
        var result = template.render(null);
        assertThat(result.systemPrompt()).contains("Use \"User\" to refer to the user");
        assertThat(result.systemPrompt()).doesNotContain("CRITICAL: The user's real name");
    }

    @Test
    @DisplayName("AlreadyExtracted should show category prefix when available")
    void shouldShowCategoryInExistingEntries() {
        var entries =
                List.of(
                        createEntry("User is a Java developer", "profile"),
                        createEntry("User is migrating to Java 21", "event"),
                        createEntry("Some fact", null));
        var template =
                SelfVerificationPrompts.build(
                        "conversation text", entries, null, List.of(), null, null);
        var result = template.render(null);
        assertThat(result.userPrompt()).contains("[profile] User is a Java developer");
        assertThat(result.userPrompt()).contains("[event] User is migrating to Java 21");
        assertThat(result.userPrompt()).contains("[unknown] Some fact");
    }

    @Test
    @DisplayName("Should show first-pass message when no existing entries")
    void shouldShowFirstPassMessage() {
        var template =
                SelfVerificationPrompts.build(
                        "conversation text", List.of(), null, List.of(), null, null);
        var result = template.render(null);
        assertThat(result.userPrompt()).contains("(none -- this is the first extraction pass)");
    }

    @Test
    @DisplayName(
            "Review prompt should not treat supportive assistant language as missed procedural")
    void shouldExcludeSupportiveAssistantSuggestionsFromReview() {
        var template =
                SelfVerificationPrompts.build(
                        """
                        [2026-03-27 01:54] user: 我总是觉得自己输了。
                        [2026-03-27 01:55] assistant: 当你感到“我输了”时，先不要评判，只问自己“除了输，我还感受到什么？”
                        """,
                        List.of(),
                        Instant.parse("2026-03-27T00:00:00Z"),
                        List.of(createInsightType("procedural", List.of("procedural"))),
                        null,
                        Set.of(MemoryCategory.PROCEDURAL));
        var result = template.render("English");

        assertThat(result.systemPrompt())
                .contains("Do NOT extract assistant emotional support")
                .contains(
                        "Do NOT treat supportive or therapeutic assistant language as a missed "
                                + "procedural memory")
                .contains("NOT conversational framing like \"assistant suggested...\"");
    }

    @Test
    @DisplayName("Review prompt should enforce strict occurredAt semantics")
    void shouldEnforceStrictOccurredAtSemantics() {
        var template =
                SelfVerificationPrompts.build(
                        """
                        [2026-03-27 02:18] user: 当对方是我信任的人时，我总会先找自己的问题。
                        """,
                        List.of(),
                        Instant.parse("2026-03-27T02:18:00Z"),
                        List.of(
                                createInsightType("profile", List.of("profile")),
                                createInsightType("behavior", List.of("behavior")),
                                createInsightType("procedural", List.of("procedural"))),
                        null,
                        Set.of(
                                MemoryCategory.PROFILE,
                                MemoryCategory.BEHAVIOR,
                                MemoryCategory.EVENT,
                                MemoryCategory.PROCEDURAL));
        var result = template.render("English");

        assertThat(result.systemPrompt())
                .contains(
                        "Profile, behavior, procedural, tool, and skill items should normally set")
                .contains("Event items should populate `occurredAt` only when the text itself")
                .contains(
                        "Do NOT use message timestamps or conversation timestamps as `occurredAt`")
                .contains("reference anchor for resolving relative expressions");
    }

    @Test
    @DisplayName("Review prompt should reject ambiguous third-party pronouns")
    void shouldRejectAmbiguousThirdPartyPronouns() {
        var template =
                SelfVerificationPrompts.build(
                        """
                        [2026-03-27 02:18] user: 我朋友最近越来越觉得自己不想再照顾继子了。
                        """,
                        List.of(),
                        Instant.parse("2026-03-27T02:18:00Z"),
                        List.of(createInsightType("relationships", List.of("profile"))),
                        null,
                        Set.of(MemoryCategory.PROFILE, MemoryCategory.EVENT));
        var result = template.render("English");

        assertThat(result.systemPrompt())
                .contains("# Subject Clarity")
                .contains("\"User\" refers only to the memory owner")
                .contains("Do NOT use bare pronouns like \"他\", \"她\", \"他们\", or \"自己\"")
                .contains(
                        "Reject any item if a reader cannot identify who each pronoun refers to");
    }

    private static MemoryInsightType createInsightType(String name, List<String> categories) {
        return new MemoryInsightType(
                null,
                name,
                null,
                null,
                categories,
                100,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static ExtractedMemoryEntry createEntry(String content, String category) {
        return new ExtractedMemoryEntry(
                content,
                0.9f,
                null,
                null,
                "raw-001",
                null,
                List.of(),
                null,
                MemoryItemType.FACT,
                category);
    }
}
