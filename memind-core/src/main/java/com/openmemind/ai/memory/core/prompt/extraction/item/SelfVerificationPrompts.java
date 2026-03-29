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

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Self-verification prompt builder.
 *
 * <p>Builds a prompt that asks the LLM to find memory items missed during the initial extraction
 * pass. Reuses category classification, identity context, and temporal resolution logic from
 * {@link MemoryItemUnifiedPrompts} for consistency.
 *
 * <p>Returns a {@link PromptTemplate} so the caller can defer language injection to {@code
 * render(language)}.
 */
public final class SelfVerificationPrompts {

    private static final String OBJECTIVE =
            """
            You are a memory extraction reviewer. Upstream extractors have already made a \
            first pass over the conversation. Your task is to find atomic facts that were \
            MISSED — items clearly present in the text but absent from the already-extracted \
            list. Return ONLY new, non-overlapping items.

            Return an empty list if nothing was missed. Do NOT fabricate or hallucinate items.
            """;

    private static final String PRINCIPLES =
            """
            # Core Principles
            1. Atomicity: Each item must express EXACTLY ONE coherent unit of meaning. If a \
            message contains multiple distinct ideas, split them into separate items. Do NOT \
            merge unrelated facts into one item.
            2. Independence: Independently retrievable without context from other items.
            3. Content Preservation: NEVER drop specific details such as names, numbers, \
            parameter names, version numbers, technical terms, config values, frequencies, \
            places, or brands. Remove ONLY filler words.
            4. Explicit Attribution: Always state WHO said or did what. Resolve pronouns to \
            specific names.
            5. Explicit Only: Extract ONLY facts directly stated/confirmed. No guesses.
            6. Non-overlap: Each item MUST cover information NOT already present in the \
            AlreadyExtracted list. Rephrasing an existing item in different words does NOT \
            count as a new item.
            """;

    private static final String EXTRACTION_SCOPE =
            """
            # Extraction Scope
            - Extract from BOTH user AND assistant messages.
            - Keep assistant content ONLY when it contains durable agent instructions, \
            reusable task workflows, resolved problem knowledge, or concrete tool guidance \
            with future reuse value.
            - When the user asks a question and the assistant provides a stable fix or \
            reusable workflow, extract the reusable resolution or playbook, NOT just \
            "user asked about X", and NOT conversational framing like "assistant suggested...".
            - Do NOT extract: greetings, small talk, praise toward the assistant, or vague \
            platitudes without user-specific context.
            - Do NOT extract assistant emotional support, encouragement, validation, reflective \
            coaching questions, or therapeutic phrasing unless the user explicitly adopts them \
            as a lasting routine, preference, or instruction.
            - One-off control messages, transient execution commands, and session-management \
            turns are not durable agent memory.
            """;

    private static final String MISS_PATTERNS =
            """
            # Common Miss Patterns
            Focus on these frequently missed patterns during review:
            1. **Technical assistant solutions**: Technical solutions, configuration advice, and \
            diagnostic conclusions from assistant messages are often missed by the first pass. \
            These usually become resolution items.
            2. **Problem + usable fix pairs**: The first pass may capture the problem but miss \
            the cause and fix. Combine them into a resolution item.
            3. **Reusable workflows**: The first pass may capture the request but miss the \
            reusable method for handling that class of tasks. These become playbook items.
            4. **Durable agent directives**: User instructions about how the agent should \
            respond in future interactions are often overlooked. These become directive items.
            5. **Specific details swallowed by summarization**: Version numbers, parameter \
            values, config keys, brand names, and exact numbers that the first pass merged \
            into a generic description.
            6. **Multi-fact messages split failure**: A single message containing 2-3 distinct \
            facts where the first pass only captured one.
            7. **Team or project context**: Team member roles, infrastructure details, and \
            project context that the first pass treated as background noise.
            Do NOT treat supportive or therapeutic assistant language as a missed agent memory.
            """;

    private static final String EXTRACTION_BIAS =
            """
            # Extraction Bias
            - Add only clearly missed items.
            - For directive, playbook, and resolution, use strict precision.
            - If there is no clear evidence, do not add the item.
            - if uncertain between agent memory and nothing, prefer nothing
            """;

    private static final String CATEGORY_CONTEXT_SECTION = "{{CATEGORY_CONTEXT}}";

    private static final String IDENTITY_CONTEXT_SECTION = "{{IDENTITY_CONTEXT}}";

    private static final String SUBJECT_CONTEXT_SECTION = "{{SUBJECT_CONTEXT}}";

    private static final String TEMPORAL_CONTEXT_SECTION = "{{TEMPORAL_CONTEXT}}";

    private static final String SCORING =
            """
            # Scoring Guidelines

            ## occurredAt
            - Time-specific memories: embed the resolved absolute date in the content AND \
            populate `occurredAt` with the ISO-8601 UTC timestamp only when the text itself \
            states or clearly implies that time.
            - Profile, behavior, directive, playbook, resolution, and tool items should \
            normally set `occurredAt` to null.
            - Event items should populate `occurredAt` only when the text itself contains \
            explicit temporal evidence such as a date, relative date phrase, or clear \
            start/end marker.
            - Do NOT use message timestamps or conversation timestamps as `occurredAt` by \
            default. They are for resolving relative expressions, not for persistence defaults.
            """;

    private static final String OUTPUT =
            """
            <OutputFormat>
            Return a JSON object ONLY. No extra text. No markdown fences.
            {
              "items": [
                {
                  "content": "Single, complete, self-contained sentence preserving ALL details",
                  "occurredAt": "2026-03-18T00:00:00Z",
                  "insightTypes": ["Choose ONLY from the Available insightTypes listed under the assigned category"],
                  "category_reason": "CRITICAL: Briefly explain WHY this category was chosen AND why this item was missed by the first pass. This field is for reasoning only and will NOT be stored.",
                  "category": "<matched_category_from_list>"
                }
              ]
            }
            (Return `{"items": []}` if nothing was missed)
            </OutputFormat>
            """;

    private static final String EXAMPLES =
            """
            # Examples

            ## Good Example 1: First pass merged multiple facts into one item

            AlreadyExtracted:
            - [event] User is migrating payment service from Java 17 to Java 21

            Conversation:
            [2026-03-18 10:01] user: I'm migrating our payment service from Java 17 to 21, \
            using Spring Boot 3.2. Zhang San handles the frontend on our team.

            Output:
            {
              "items": [
                {
                  "content": "User's payment service migration uses Spring Boot 3.2",
                  "occurredAt": null,
                  "insightTypes": ["experiences"],
                  "category_reason": "Current project tech stack detail. Missed because the first pass captured the migration but not the specific framework version.",
                  "category": "event"
                },
                {
                  "content": "Zhang San is responsible for frontend in User's team",
                  "occurredAt": null,
                  "insightTypes": ["experiences"],
                  "category_reason": "Team member role, current project context. Missed because the first pass focused on the migration fact and overlooked the team structure.",
                  "category": "event"
                }
              ]
            }

            Why good: The first pass only captured the migration. Spring Boot version and team member info were swallowed.

            ## Good Example 2: Assistant solution not captured

            AlreadyExtracted:
            - [event] User encountered HikariCP connection pool exhaustion after enabling virtual threads

            Conversation:
            [2026-03-18 10:04] user: After enabling virtual threads, my HikariCP connection pool keeps getting exhausted.
            [2026-03-18 10:05] assistant: That's because the number of virtual threads far exceeds \
            the pool size limit. Set maximumPoolSize to 10-20 to fix it.

            Output:
            {
              "items": [
                {
                  "content": "Virtual threads caused HikariCP connection pool exhaustion because virtual thread count far exceeds pool size limit; solved by setting maximumPoolSize to 10-20",
                  "occurredAt": null,
                  "insightTypes": ["resolutions"],
                  "category_reason": "Resolution item: named problem, cause, and usable fix. The first pass only captured the symptom and missed the fix from the assistant response.",
                  "category": "resolution"
                }
              ]
            }

            Why good: The first pass captured only the symptom. The root cause and solution from the assistant message were missed.

            ## Good Example 3: Durable agent directive missed

            AlreadyExtracted:
            - [profile] User is a backend engineer with 5 years of Java experience
            - [event] User is migrating payment service from Java 17 to Java 21

            Conversation:
            [2026-03-18 10:06] user: From now on, reply in Chinese and keep it concise.

            Output:
            {
              "items": [
                {
                  "content": "User instructed the agent to respond in Chinese and keep answers concise",
                  "occurredAt": null,
                  "insightTypes": ["directives"],
                  "category_reason": "Directive item: durable rule for future interaction behavior. Commonly missed because it appears as a brief aside rather than the main task content.",
                  "category": "directive"
                }
              ]
            }

            Why good: User directives to the agent are frequently overlooked during first-pass extraction.

            ## Good Example 4: Reusable workflow missed

            AlreadyExtracted:
            - [event] User wants a comparison between two repositories

            Conversation:
            [2026-03-18 10:08] user: Compare the repositories before proposing changes.
            [2026-03-18 10:09] assistant: First align memory scope, then compare taxonomy, extraction flow, and storage path.

            Output:
            {
              "items": [
                {
                  "content": "For repository comparisons, first align memory scope, then compare taxonomy, extraction flow, and storage path",
                  "occurredAt": null,
                  "insightTypes": ["playbooks"],
                  "category_reason": "Playbook item: reusable handling workflow for a recurring class of tasks. The first pass captured the request but missed the reusable method.",
                  "category": "playbook"
                }
              ]
            }

            Why good: The reusable method is the memory, not the one-off request title.

            ## Good Example 5: Nothing missed — return empty

            AlreadyExtracted:
            - [profile] User is a backend engineer with 5 years of Java experience
            - [event] User started migrating payment service from Java 17 to Java 21

            Conversation:
            [2026-03-18 10:00] user: I'm a backend engineer, been writing Java for 5 years. \
            Recently started migrating our payment service from 17 to 21.

            Output:
            {"items": []}

            Why good: All facts in the conversation are already covered. No duplicates generated.

            ## Bad Example 1: Supportive assistant language (WRONG)

            Conversation:
            [2026-03-18 10:07] assistant: When you feel like you lost, try asking yourself \
            what else you are feeling without judgment.

            Output (WRONG):
            {
              "items": [
                {
                  "content": "Assistant suggested that User ask what else they are feeling without judgment",
                  "category": "directive"
                }
              ]
            }

            -> Wrong: Supportive or therapeutic assistant language is not a missed agent memory \
            unless the user later adopts it as a lasting routine or instruction.

            ## Bad Example 2: One-off control message (WRONG)

            Conversation:
            [2026-03-18 10:11] user: continue

            Output (WRONG):
            {
              "items": [
                {
                  "content": "User told the agent to continue",
                  "category": "directive"
                }
              ]
            }

            -> Wrong: one-off control messages are not durable agent memory.

            ## Bad Example 3: Rephrasing an existing item (WRONG)

            AlreadyExtracted:
            - [profile] User is a backend engineer with 5 years of Java experience

            Output (WRONG):
            {
              "items": [
                {
                  "content": "User has been writing Java for 5 years",
                  "category": "profile"
                }
              ]
            }

            -> Wrong: This is just a rephrase of an already-extracted item, not a new fact.\
            """;

    private static final String USER_PROMPT_TEMPLATE =
            """
            Please identify any missed memory items from the following conversation.

            # AlreadyExtracted
            {{existing_entries}}

            # Conversation
            {{original_text}}\
            """;

    private SelfVerificationPrompts() {}

    /**
     * Builds a self-verification prompt template.
     *
     * @param originalText original conversation text
     * @param existingEntries already-extracted memory entries
     * @param referenceTime reference time for resolving relative dates (null allowed)
     * @param insightTypes available insight types for categorization
     * @param userName user name (replaces "User" in prompt when not empty)
     * @param categories categories to include in the prompt (null = all)
     * @return prompt template; call {@code render(language)} to produce the final result
     */
    public static PromptTemplate build(
            String originalText,
            List<ExtractedMemoryEntry> existingEntries,
            Instant referenceTime,
            List<MemoryInsightType> insightTypes,
            String userName,
            Set<MemoryCategory> categories) {
        return build(
                PromptRegistry.EMPTY,
                originalText,
                existingEntries,
                referenceTime,
                insightTypes,
                userName,
                categories);
    }

    public static PromptTemplate buildDefault() {
        return defaultBuilder().build();
    }

    public static PromptTemplate buildPreview() {
        return defaultBuilder()
                .variable(
                        "CATEGORY_CONTEXT",
                        MemoryItemUnifiedPrompts.buildCategoryContext(
                                null, DefaultInsightTypes.all()))
                .variable("IDENTITY_CONTEXT", MemoryItemUnifiedPrompts.buildIdentityContext("Ada"))
                .variable(
                        "SUBJECT_CONTEXT",
                        MemoryItemUnifiedPrompts.buildSubjectClarityContext("Ada")
                                + "\nReject any item if a reader cannot identify who each pronoun"
                                + " refers to without reading the original conversation.")
                .variable(
                        "TEMPORAL_CONTEXT",
                        MemoryItemUnifiedPrompts.buildTimeContext(
                                null, Instant.parse("2026-03-29T00:00:00Z")))
                .build();
    }

    public static PromptTemplate build(
            PromptRegistry registry,
            String originalText,
            List<ExtractedMemoryEntry> existingEntries,
            Instant referenceTime,
            List<MemoryInsightType> insightTypes,
            String userName,
            Set<MemoryCategory> categories) {

        PromptTemplate.Builder builder =
                registry.hasOverride(PromptType.SELF_VERIFICATION)
                        ? PromptTemplate.builder("self-verification")
                                .section(
                                        "system",
                                        registry.getOverride(PromptType.SELF_VERIFICATION))
                        : defaultBuilder();

        return builder.userPrompt(USER_PROMPT_TEMPLATE)
                .variable(
                        "CATEGORY_CONTEXT",
                        MemoryItemUnifiedPrompts.buildCategoryContext(categories, insightTypes))
                .variable(
                        "IDENTITY_CONTEXT", MemoryItemUnifiedPrompts.buildIdentityContext(userName))
                .variable(
                        "SUBJECT_CONTEXT",
                        MemoryItemUnifiedPrompts.buildSubjectClarityContext(userName)
                                + "\nReject any item if a reader cannot identify who each pronoun"
                                + " refers to without reading the original conversation.")
                .variable(
                        "TEMPORAL_CONTEXT",
                        MemoryItemUnifiedPrompts.buildTimeContext(originalText, referenceTime))
                .variable("existing_entries", formatExistingEntries(existingEntries))
                .variable("original_text", originalText != null ? originalText : "")
                .build();
    }

    private static PromptTemplate.Builder defaultBuilder() {
        return PromptTemplate.builder("self-verification")
                .section("objective", OBJECTIVE)
                .section("principles", PRINCIPLES)
                .section("extractionScope", EXTRACTION_SCOPE)
                .section("missPatterns", MISS_PATTERNS)
                .section("extractionBias", EXTRACTION_BIAS)
                .section("categoryContext", CATEGORY_CONTEXT_SECTION)
                .section("identityContext", IDENTITY_CONTEXT_SECTION)
                .section("subjectContext", SUBJECT_CONTEXT_SECTION)
                .section("temporalContext", TEMPORAL_CONTEXT_SECTION)
                .section("scoring", SCORING)
                .section("output", OUTPUT)
                .section("examples", EXAMPLES);
    }

    private static String formatExistingEntries(List<ExtractedMemoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "(none -- this is the first extraction pass)";
        }
        return entries.stream()
                .map(
                        e ->
                                "- ["
                                        + (e.category() != null ? e.category() : "unknown")
                                        + "] "
                                        + e.content())
                .collect(Collectors.joining("\n"));
    }
}
