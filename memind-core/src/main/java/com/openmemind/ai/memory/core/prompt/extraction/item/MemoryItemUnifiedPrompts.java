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

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Unified mode memory item extraction prompt builder.
 *
 * <p>Extracts atomic facts from conversation segments across all MemoryCategory types. Optimized
 * with Chain-of-Thought (category_reason) and Contrastive Examples for small models.
 */
public final class MemoryItemUnifiedPrompts {

    static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE)", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    static final Pattern MESSAGE_TIMESTAMP_PATTERN =
            Pattern.compile("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}]");

    static final String RESOLVE_DATES_INSTRUCTION =
            """
            Resolve relative expressions ("yesterday", "next month") to absolute dates. \
            Embed the resolved date in the content and populate `occurredAt` with the \
            ISO-8601 UTC timestamp.\
            """;

    // ── System & User Prompt Templates ───────────────────────────────────────

    private static final String SYSTEM_PROMPT_TEMPLATE =
            """
            You are an expert information extraction analyst. Your task is to analyze the \
            conversation and extract atomic memory facts optimized for retrieval.

            Extract self-contained facts, events, preferences, and behavioral patterns. \
            Assign the correct category to each item. Return an empty list if no valid \
            information exists.

            # Core Principles
            1. Atomicity: Each item must express EXACTLY ONE coherent unit of meaning. \
            If a message contains multiple distinct ideas, split them into separate items. \
            Count the distinct factual claims in each message and ensure each one is captured. \
            Do NOT merge unrelated facts into one item, even if they appear in the same message. \
            "User is a Java engineer who is building a Spring Boot service" -> split into TWO items: \
            "User is a Java engineer" (profile) and "User is building a Spring Boot service" (event).
            2. Independence: Independently retrievable without context from other items.
            3. Content Preservation: NEVER drop specific details (names, numbers, parameter names, \
            version numbers, technical terms, config values, frequencies, places, brands). \
            Remove ONLY filler words.
            4. Explicit Attribution: Always state WHO said or did what. Resolve pronouns to \
            specific names.
            5. Explicit Only: Extract ONLY facts directly stated/confirmed. No guesses.

            # Extraction Scope & What NOT to Extract
            - Extract from BOTH user AND assistant messages. User messages reveal personal facts, \
            preferences, and context. Assistant messages often contain technical solutions, \
            configuration recipes, and diagnostic conclusions — these are equally valuable as \
            procedural items.
            - When the user asks a question and the assistant provides a solution, extract the \
            combined problem+solution as a procedural item, NOT just "user asked about X".
            - Do NOT extract: Greetings, small talk, praise toward the assistant.
            - Do NOT extract: Vague platitudes without user context.

            # Extraction Bias
            When uncertain whether something is worth extracting, extract it. \
            The downstream deduplication system handles redundancy. \
            Missing a valuable memory is worse than creating a slightly redundant one.

            {{CATEGORY_CONTEXT}}
            {{IDENTITY_CONTEXT}}
            {{TEMPORAL_CONTEXT}}

            # Scoring Guidelines

            ## confidence
            0.0 to 1.0 indicating extraction certainty:
            - 0.95-1.0: Explicit direct statement
            - 0.85-0.94: Strong implication
            - 0.70-0.84: Reasonable inference
            - < 0.70: Do not extract

            ## occurredAt
            Temporal Content Embedding rules for time-specific memories:
            - Time-specific memories: embed the resolved absolute date in the content AND \
            populate `occurredAt` with the ISO-8601 UTC timestamp (e.g., "2025-02-07T00:00:00Z").
            - Non-temporal items (stable facts, preferences): set `occurredAt` to null.

            <OutputFormat>
            Return a JSON object ONLY. No extra text.
            {
              "items":[
                {
                  "content": "Single, complete, self-contained sentence preserving ALL details",
                  "confidence": 0.95,
                  "occurredAt": "2023-10-14T00:00:00Z",
                  "insightTypes": ["Choose ONLY from the Available insightTypes listed under the assigned category"],
                  "category_reason": "CRITICAL: Briefly explain WHY this category was chosen based on the rules. (Chain of Thought)",
                  "category": "<matched_category_from_list>"
                }
              ]
            }
            (Return `{"items":[]}` if nothing qualifies)
            </OutputFormat>
            """;

    private static final String USER_PROMPT_TEMPLATE =
            """
            Please extract unified memory items from the following conversation:

            <Conversation>
            {{CONVERSATION}}
            </Conversation>
            """;

    // ── Decision Logic & Common Confusions ────────────────────────────────────

    static final String DECISION_LOGIC =
            """
            # Category Classification

            ## Decision Logic
            For each extracted fact, ask yourself — what is this information mainly about?

            | Ask yourself                              | Answer points to         | Category    |
            |-------------------------------------------|--------------------------|-------------|
            | Is the user telling the AGENT how to act? | Behavioral directive     | procedural  |
            | How was a problem solved?                 | Problem + solution pair  | procedural  |
            | What steps should be followed?            | Reusable instructions    | procedural  |
            | How was a specific tool used/configured?  | Tool usage insight       | tool        |
            | How was a multi-step workflow executed?    | Skill execution strategy | skill       |
            | Does the user do this repeatedly?         | Recurring pattern        | behavior    |
            | What is happening / what happened?        | Time-bound situation     | event       |
            | Who is the user as a person?              | Stable identity/trait    | profile     |

            Match top-to-bottom. Profile is the LAST resort, not the default.

            ## Common Confusions
            - "Plan to do X" -> event (time-bound action, not profile)
            - "Project X status: in progress" -> event (project context, not profile)
            - "Encountered problem A, solved with B" -> procedural (not event)
            - "General process for handling X" -> procedural (not event)
            - "We use Redis/Kafka/X for purpose Y" -> event (current team setup, not profile)
            - "Currently learning/reading/migrating X" -> event (ongoing activity, not profile)
            - "Bug: parameter X misconfigured caused issue Y" -> procedural (problem + cause)
            - "User does X every morning, Y every evening" -> behavior (recurring routine, not event)
            - "Teammate Zhang is responsible for backend services" -> event (team context, not profile)
            - "Don't add comments to my code" -> procedural (agent directive, not profile)
            - "User follows a fixed process/SOP for X (e.g., stop bleeding → root cause → postmortem)" -> procedural if it is a reusable method that others could follow; behavior if it is described as the user's personal recurring habit
            - General industry or technology facts not specific to the user ("Rust has a steep learning curve") -> Do NOT extract unless they directly describe the user's own experience, decision, or outcome
            """;

    // ── Category Definition Template ─────────────────────────────────────────

    static final String CATEGORY_DEF_TEMPLATE =
            """
            **{{CATEGORY_NAME}}**
            {{PROMPT_DEFINITION}}
            Available insightTypes: {{INSIGHT_TYPES}}
            """;

    // ── Per-Category Examples ─────────────────────────────────────────────────

    static final String CATEGORY_EXAMPLES =
            """

            # Examples by Category

            ## profile

            Good:
            {
              "items": [
                {
                  "content": "User is a backend engineer with 5 years of Python experience, focused on distributed systems",
                  "confidence": 1.0,
                  "occurredAt": null,
                  "insightTypes": ["identity"],
                  "category_reason": "Stable professional identity, true regardless of current project.",
                  "category": "profile"
                },
                {
                  "content": "User dislikes type annotations in code, considers them too verbose",
                  "confidence": 0.95,
                  "occurredAt": null,
                  "insightTypes": ["preferences"],
                  "category_reason": "Enduring code style preference, not tied to any specific project.",
                  "category": "profile"
                }
              ]
            }

            Bad:
            {
              "items": [
                {
                  "content": "User is building a Spring Boot 3 service on Java 21",
                  "category": "profile"
                }
              ]
            }
            -> Wrong: this is event. Current project context that becomes outdated when the project ends.


            ## behavior

            Good:
            {
              "items": [
                {
                  "content": "User goes for a 5K run every Tuesday and Thursday morning along the Charles River path",
                  "confidence": 1.0,
                  "occurredAt": null,
                  "insightTypes": ["behavior"],
                  "category_reason": "Recurring pattern with explicit frequency: every Tuesday and Thursday.",
                  "category": "behavior"
                },
                {
                  "content": "User always reviews pull requests before morning standup",
                  "confidence": 0.95,
                  "occurredAt": null,
                  "insightTypes": ["behavior"],
                  "category_reason": "Habitual work routine indicated by 'always'.",
                  "category": "behavior"
                }
              ]
            }

            Bad:
            {
              "items": [
                {
                  "content": "User went for a run yesterday",
                  "category": "behavior"
                }
              ]
            }
            -> Wrong: this is event. Single occurrence with no evidence of recurrence.


            ## event

            Good:
            {
              "items": [
                {
                  "content": "User's team uses Redis for caching with 10-minute TTL",
                  "confidence": 0.95,
                  "occurredAt": null,
                  "insightTypes": ["experiences"],
                  "category_reason": "Current team infrastructure setup, no specific time anchor.",
                  "category": "event"
                },
                {
                  "content": "User started migrating their payment service from Java 17 to Java 21 on 2025-03-10",
                  "confidence": 0.95,
                  "occurredAt": "2025-03-10T00:00:00Z",
                  "insightTypes": ["experiences"],
                  "category_reason": "Ongoing project activity anchored to a specific start date.",
                  "category": "event"
                }
              ]
            }

            Bad:
            {
              "items": [
                {
                  "content": "User is a Java developer",
                  "category": "event"
                }
              ]
            }
            -> Wrong: this is profile. Stable professional identity, not a time-bound situation.


            ## procedural

            Good:
            {
              "items": [
                {
                  "content": "Enable virtual threads in Spring Boot 3.2+ by setting spring.threads.virtual.enabled=true; Tomcat and @Async will use virtual threads automatically",
                  "confidence": 0.90,
                  "occurredAt": null,
                  "insightTypes": ["procedural"],
                  "category_reason": "Reusable configuration recipe, applicable to any Spring Boot 3.2+ project.",
                  "category": "procedural"
                },
                {
                  "content": "Virtual threads caused HikariCP connection pool exhaustion; solved by setting maximumPoolSize to 10-20",
                  "confidence": 0.95,
                  "occurredAt": "2025-03-15T00:00:00Z",
                  "insightTypes": ["procedural"],
                  "category_reason": "Specific problem (pool exhaustion) with concrete solution (pool size 10-20).",
                  "category": "procedural"
                },
                {
                  "content": "User wants technical answers kept under 2 paragraphs",
                  "confidence": 1.0,
                  "occurredAt": null,
                  "insightTypes": ["procedural"],
                  "category_reason": "Direct directive to the agent about response length.",
                  "category": "procedural"
                }
              ]
            }

            Bad:
            {
              "items": [
                {
                  "content": "User enabled virtual threads for their service",
                  "category": "procedural"
                }
              ]
            }
            -> Wrong: this is event. Describes what the user DID, not HOW to do it.

            Bad:
            {
              "items": [
                {
                  "content": "User had connection pool issues with HikariCP",
                  "category": "procedural"
                }
              ]
            }
            -> Useless: missing cause (virtual threads) and solution (pool size 10-20). A problem without resolution has no value.

            Bad:
            {
              "items": [
                {
                  "content": "User dislikes verbose code comments",
                  "category": "procedural"
                }
              ]
            }
            -> Wrong: this is profile. Personal code style preference, not a directive to the agent.
            """;

    // ── Constructor ──────────────────────────────────────────────────────────

    private MemoryItemUnifiedPrompts() {}

    // ── Public API ───────────────────────────────────────────────────────────

    public static PromptTemplate build(
            List<MemoryInsightType> insightTypes,
            String segmentText,
            Instant referenceTime,
            String userName,
            Set<MemoryCategory> categories) {

        return PromptTemplate.builder("memory-item-unified")
                .section("system", SYSTEM_PROMPT_TEMPLATE)
                .userPrompt(USER_PROMPT_TEMPLATE)
                .variable("CATEGORY_CONTEXT", buildCategoryContext(categories, insightTypes))
                .variable("IDENTITY_CONTEXT", buildIdentityContext(userName))
                .variable("TEMPORAL_CONTEXT", buildTimeContext(segmentText, referenceTime))
                .variable("CONVERSATION", segmentText != null ? segmentText : "")
                .build();
    }

    // ── Category Context ─────────────────────────────────────────────────────

    static String buildCategoryContext(
            Set<MemoryCategory> categories, List<MemoryInsightType> insightTypes) {
        Set<MemoryCategory> effectiveCategories =
                categories != null ? categories : Set.of(MemoryCategory.values());

        String userDefs =
                renderCategoryDefinitions(effectiveCategories, MemoryScope.USER, insightTypes);
        String agentDefs =
                renderCategoryDefinitions(effectiveCategories, MemoryScope.AGENT, insightTypes);

        String defs =
                (userDefs.isEmpty() ? "" : "### [USER Scope]\n\n" + userDefs)
                        + (agentDefs.isEmpty() ? "" : "### [AGENT Scope]\n\n" + agentDefs);

        return DECISION_LOGIC + "\n## Category Definitions\n\n" + defs + CATEGORY_EXAMPLES;
    }

    private static String renderCategoryDefinitions(
            Set<MemoryCategory> categories,
            MemoryScope scope,
            List<MemoryInsightType> insightTypes) {
        return categories.stream()
                .filter(cat -> cat.scope() == scope)
                .map(cat -> renderSingleCategory(cat, insightTypes))
                .collect(Collectors.joining());
    }

    private static String renderSingleCategory(
            MemoryCategory cat, List<MemoryInsightType> insightTypes) {
        String matchedTypes =
                insightTypes == null
                        ? "none"
                        : insightTypes.stream()
                                .filter(
                                        it ->
                                                it.categories() != null
                                                        && it.categories()
                                                                .contains(cat.categoryName()))
                                .map(MemoryInsightType::name)
                                .collect(Collectors.joining(", "));

        return CATEGORY_DEF_TEMPLATE
                .replace("{{CATEGORY_NAME}}", cat.categoryName())
                .replace("{{PROMPT_DEFINITION}}", cat.promptDefinition())
                .replace("{{INSIGHT_TYPES}}", matchedTypes.isEmpty() ? "none" : matchedTypes);
    }

    // ── Identity Context ─────────────────────────────────────────────────────

    static final String IDENTITY_WITH_NAME =
            """
            # Identity
            CRITICAL: The user's real name is **{{USER_NAME}}**. In ALL extracted memory items, \
            use "{{USER_NAME}}" instead of "User" or "用户" as the subject.
            (e.g., "{{USER_NAME}} likes..." NOT "User likes...")
            """;

    static final String IDENTITY_DEFAULT =
            """
            # Identity
            Use "User" to refer to the user consistently in all extracted items.
            """;

    static String buildIdentityContext(String userName) {
        if (userName == null || userName.isBlank()) {
            return IDENTITY_DEFAULT;
        }
        return IDENTITY_WITH_NAME.replace("{{USER_NAME}}", userName);
    }

    // ── Temporal Context ─────────────────────────────────────────────────────

    static String buildTimeContext(String segmentText, Instant referenceTime) {
        boolean hasTimestamps =
                segmentText != null && MESSAGE_TIMESTAMP_PATTERN.matcher(segmentText).find();

        if (hasTimestamps) {
            String fallback =
                    referenceTime != null
                            ? "\nFallback Reference Date: " + DATE_FMT.format(referenceTime)
                            : "";
            return "# Temporal Resolution\n"
                    + "Messages contain timestamps (e.g., [2023-05-25 13:17]). Use EACH"
                    + " message's timestamp as the precise anchor for resolving relative time"
                    + " within that message."
                    + fallback
                    + "\n\n"
                    + RESOLVE_DATES_INSTRUCTION
                    + "\n";
        } else if (referenceTime != null) {
            return "# Temporal Resolution\n"
                    + "Today's date: "
                    + DATE_FMT.format(referenceTime)
                    + ". Use this to resolve relative temporal references.\n\n"
                    + RESOLVE_DATES_INSTRUCTION
                    + "\n";
        } else {
            return """
            # Temporal Resolution
            No absolute dates available. Do not resolve relative dates.
            """;
        }
    }
}
