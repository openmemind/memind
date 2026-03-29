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
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
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
            Only populate `occurredAt` when the text itself provides semantic temporal evidence \
            for the memory. Do NOT infer `occurredAt` from the reference date or message \
            timestamp alone.\
            """;

    // ── System & User Prompt Templates ───────────────────────────────────────

    private static final String OBJECTIVE =
            """
            You are an expert information extraction analyst. Analyze the conversation and \
            extract atomic memory items optimized for retrieval.

            Extract only self-contained facts with durable retrieval value. Assign the \
            correct category to each item. Return an empty list if no valid information exists.
            """;

    private static final String PRINCIPLES =
            """
            # Core Principles
            1. Atomicity: Each item must express EXACTLY ONE coherent unit of meaning. If a \
            message contains multiple distinct ideas, split them into separate items. Count \
            the distinct factual claims and capture each one separately. Do NOT merge \
            unrelated facts into one item.
            2. Independence: Independently retrievable without context from other items.
            3. Content Preservation: NEVER drop specific details such as names, numbers, \
            parameter names, version numbers, technical terms, config values, frequencies, \
            places, or brands. Remove ONLY filler words.
            4. Explicit Attribution: Always state WHO said or did what. Resolve pronouns to \
            specific names.
            5. Explicit Only: Extract ONLY facts directly stated/confirmed. No guesses.
            """;

    private static final String EXTRACTION_SCOPE =
            """
            # Extraction Scope & What NOT to Extract
            - Extract from BOTH user AND assistant messages.
            - User messages mainly reveal profile, behavior, and event memories.
            - Keep assistant content ONLY when it contains durable agent instructions, \
            reusable task workflows, resolved problem knowledge, or concrete tool guidance \
            with future reuse value.
            - Do NOT extract: greetings, small talk, praise toward the assistant, or vague \
            platitudes without user-specific context.
            - Do NOT extract assistant emotional support, encouragement, validation, reflective \
            coaching questions, or therapeutic phrasing unless the user explicitly adopts them \
            as a lasting routine, preference, or instruction.
            - For directive, playbook, and resolution items, normalize to reusable knowledge or \
            the durable rule itself.
            - Avoid conversational framing like "Assistant suggested:", "Assistant advised the \
            user to", or "At 2026-03-27 the assistant said...".
            - One-off control messages, transient execution commands, and session-management \
            turns are not durable agent memory.
            """;

    private static final String EXTRACTION_BIAS =
            """
            # Extraction Bias
            - For profile, behavior, and event, extract clearly stated facts even if they \
            seem minor.
            - For directive, playbook, and resolution, use strict precision.
            - If there is no clear evidence, do not extract.
            - if uncertain between agent memory and nothing, prefer nothing
            """;

    private static final String CATEGORY_CONTEXT_SECTION = "{{CATEGORY_CONTEXT}}";

    private static final String IDENTITY_CONTEXT_SECTION = "{{IDENTITY_CONTEXT}}";

    private static final String SUBJECT_CONTEXT_SECTION = "{{SUBJECT_CONTEXT}}";

    private static final String TEMPORAL_CONTEXT_SECTION = "{{TEMPORAL_CONTEXT}}";

    private static final String SCORING =
            """
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
            Return a JSON object ONLY. No extra text.
            {
              "items":[
                {
                  "content": "Single, complete, self-contained sentence preserving ALL details",
                  "confidence": 0.95,
                  "occurredAt": "2023-10-14T00:00:00Z",
                  "insightTypes": ["Choose ONLY from the Available insightTypes listed under the assigned category"],
                  "category_reason": "CRITICAL: Briefly explain WHY this category was chosen based on the rules. This field is for reasoning only and will NOT be stored.",
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
            For each extracted fact, ask what kind of memory it is.

            | Ask yourself                                                              | Answer points to        | Category   |
            |---------------------------------------------------------------------------|-------------------------|------------|
            | Is this about who the user is or an enduring preference or trait?         | Stable identity         | profile    |
            | Does the user do this repeatedly?                                         | Recurring pattern       | behavior   |
            | Is this a time-bound situation, current activity, or single occurrence?   | Time-bound situation    | event      |
            | Is the user setting a durable rule for how the agent should behave later? | Future interaction rule | directive  |
            | Is this a reusable workflow for handling a class of tasks?                | Repeatable method       | playbook   |
            | Is a named problem clearly resolved with a usable fix or conclusion?      | Problem plus resolution | resolution |
            | Does this describe how a specific tool was used or configured?            | Tool usage insight      | tool       |

            Match the narrowest valid category. Profile is the LAST resort, not the default.

            ## Common Confusions
            - "Plan to do X" -> event (time-bound action, not profile)
            - "Project X status: in progress" -> event (project context, not profile)
            - "Show the plan before substantial edits" -> directive
            - "Reply in Chinese and keep it concise" -> directive
            - "continue" -> one-off control messages. Do NOT extract.
            - "commit this" -> transient execution command. Do NOT extract unless it is \
            clearly framed as a lasting collaboration rule.
            - "Encountered problem A, solved with B" -> resolution (not event)
            - "General process for handling X" -> playbook (not event)
            - "We use Redis/Kafka/X for purpose Y" -> event (current team setup, not profile)
            - "Currently learning/reading/migrating X" -> event (ongoing activity, not profile)
            - "Bug: parameter X misconfigured caused issue Y and was fixed by Z" -> resolution
            - "User does X every morning, Y every evening" -> behavior (recurring routine, not event)
            - "Teammate Zhang is responsible for backend services" -> event (team context, not profile)
            - "Don't add comments to my code" -> directive (agent rule, not profile)
            - "For repository comparisons, first align scope, then compare taxonomy, \
            extraction flow, and storage path" -> playbook
            - "User follows a fixed process or SOP for X" -> playbook if it is a reusable \
            method that can be reused later; behavior if it is only the user's personal \
            recurring habit
            - "Assistant says 'be gentle with yourself', offers reassurance, or asks a \
            reflective coaching question" -> Do NOT extract unless the user later adopts it \
            as their own recurring practice or instruction
            - "Assistant gives a concrete configuration fix or diagnostic conclusion" -> \
            resolution when both the problem and usable fix are clear
            - "Assistant gives a reusable sequence for handling a class of tasks" -> playbook
            - "User dislikes verbose code comments" -> profile, unless it is explicitly \
            framed as a rule for how the agent should respond
            - General industry or technology facts not specific to the user \
            ("Rust has a steep learning curve") -> Do NOT extract unless they directly \
            describe the user's own experience, decision, or outcome
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
                  "content": "User is a backend engineer with 5 years of Python experience",
                  "confidence": 1.0,
                  "occurredAt": null,
                  "insightTypes": ["identity"],
                  "category_reason": "Stable professional identity that remains true across projects.",
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
                  "content": "User reviews pull requests before morning standup every workday",
                  "confidence": 1.0,
                  "occurredAt": null,
                  "insightTypes": ["behavior"],
                  "category_reason": "Recurring routine with explicit frequency evidence.",
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
                  "content": "User's team uses Redis for caching with a 10-minute TTL",
                  "confidence": 0.95,
                  "occurredAt": null,
                  "insightTypes": ["experiences"],
                  "category_reason": "Current team infrastructure setup, no specific time anchor.",
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


            ## directive

            Good:
            {
              "items": [
                {
                  "content": "User requires the agent to show the plan before substantial code changes",
                  "confidence": 1.0,
                  "occurredAt": null,
                  "insightTypes": ["directives"],
                  "category_reason": "Durable collaboration rule for future interactions.",
                  "category": "directive"
                }
              ]
            }

            Bad:
            {
              "items": [
                {
                  "content": "continue",
                  "category": "directive"
                }
              ]
            }
            -> Wrong: one-off control messages are not durable instructions.

            ## playbook

            Good:
            {
              "items": [
                {
                  "content": "For repository comparisons, first align memory scope, then compare taxonomy, extraction flow, and storage path",
                  "confidence": 0.95,
                  "occurredAt": null,
                  "insightTypes": ["playbooks"],
                  "category_reason": "Reusable workflow for a recurring class of tasks.",
                  "category": "playbook"
                }
              ]
            }

            Bad:
            {
              "items": [
                {
                  "content": "User asked to compare two repositories",
                  "category": "playbook"
                }
              ]
            }
            -> Wrong: a single request title is not a reusable workflow.

            ## resolution

            Good:
            {
              "items": [
                {
                  "content": "Virtual threads caused HikariCP connection pool exhaustion because virtual thread count exceeded pool size; solved by setting maximumPoolSize to 10-20",
                  "confidence": 0.95,
                  "occurredAt": null,
                  "insightTypes": ["resolutions"],
                  "category_reason": "Named problem plus usable fix with future reuse value.",
                  "category": "resolution"
                }
              ]
            }

            Bad:
            {
              "items": [
                {
                  "content": "User had connection pool issues with HikariCP",
                  "category": "resolution"
                }
              ]
            }
            -> Wrong: a resolution must include both the problem and a usable fix or conclusion.

            Bad:
            {
              "items": [
                {
                  "content": "Assistant suggested that User gently remind themselves 'I am learning to come back, no need to rush'",
                  "category": "directive"
                }
              ]
            }
            -> Wrong: assistant emotional support or reflective guidance is not durable agent \
            memory unless the user later adopts it as their own routine or instruction.
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
        return build(
                PromptRegistry.EMPTY,
                insightTypes,
                segmentText,
                referenceTime,
                userName,
                categories);
    }

    public static PromptTemplate buildDefault() {
        return defaultBuilder().build();
    }

    public static PromptTemplate buildPreview() {
        return defaultBuilder()
                .variable("CATEGORY_CONTEXT", buildCategoryContext(null, DefaultInsightTypes.all()))
                .variable("IDENTITY_CONTEXT", buildIdentityContext("Ada"))
                .variable("SUBJECT_CONTEXT", buildSubjectClarityContext("Ada"))
                .variable(
                        "TEMPORAL_CONTEXT",
                        buildTimeContext(null, Instant.parse("2026-03-29T00:00:00Z")))
                .build();
    }

    public static PromptTemplate build(
            PromptRegistry registry,
            List<MemoryInsightType> insightTypes,
            String segmentText,
            Instant referenceTime,
            String userName,
            Set<MemoryCategory> categories) {

        PromptTemplate.Builder builder =
                registry.hasOverride(PromptType.MEMORY_ITEM_UNIFIED)
                        ? PromptTemplate.builder("memory-item-unified")
                                .section(
                                        "system",
                                        registry.getOverride(PromptType.MEMORY_ITEM_UNIFIED))
                        : defaultBuilder();

        return builder.userPrompt(USER_PROMPT_TEMPLATE)
                .variable("CATEGORY_CONTEXT", buildCategoryContext(categories, insightTypes))
                .variable("IDENTITY_CONTEXT", buildIdentityContext(userName))
                .variable("SUBJECT_CONTEXT", buildSubjectClarityContext(userName))
                .variable("TEMPORAL_CONTEXT", buildTimeContext(segmentText, referenceTime))
                .variable("CONVERSATION", segmentText != null ? segmentText : "")
                .build();
    }

    private static PromptTemplate.Builder defaultBuilder() {
        return PromptTemplate.builder("memory-item-unified")
                .section("objective", OBJECTIVE)
                .section("principles", PRINCIPLES)
                .section("extractionScope", EXTRACTION_SCOPE)
                .section("extractionBias", EXTRACTION_BIAS)
                .section("categoryContext", CATEGORY_CONTEXT_SECTION)
                .section("identityContext", IDENTITY_CONTEXT_SECTION)
                .section("subjectContext", SUBJECT_CONTEXT_SECTION)
                .section("temporalContext", TEMPORAL_CONTEXT_SECTION)
                .section("scoring", SCORING)
                .section("output", OUTPUT)
                .section("examples", CATEGORY_EXAMPLES);
    }

    // ── Category Context ─────────────────────────────────────────────────────

    static String buildCategoryContext(
            Set<MemoryCategory> categories, List<MemoryInsightType> insightTypes) {
        Set<MemoryCategory> effectiveCategories =
                categories == null
                        ? EnumSet.allOf(MemoryCategory.class)
                        : categories.isEmpty()
                                ? EnumSet.noneOf(MemoryCategory.class)
                                : EnumSet.copyOf(categories);

        String userDefs =
                renderCategoryDefinitions(effectiveCategories, MemoryScope.USER, insightTypes);
        String agentDefs =
                renderCategoryDefinitions(effectiveCategories, MemoryScope.AGENT, insightTypes);

        String defs =
                (userDefs.isEmpty() ? "" : "### [USER Scope]\n\n" + userDefs)
                        + (agentDefs.isEmpty() ? "" : "### [AGENT Scope]\n\n" + agentDefs);

        return DECISION_LOGIC + "\n## Category Definitions\n\n" + defs;
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

    static final String SUBJECT_CLARITY_TEMPLATE =
            """
            # Subject Clarity
            Every memory item must be understandable without the original conversation.
            "{{OWNER_LABEL}}" refers only to the memory owner.
            If the item is about someone other than {{OWNER_LABEL}}, explicitly name that subject \
            with a stable role phrase, such as "{{OWNER_LABEL}}的朋友", "朋友的继子", or \
            "{{OWNER_LABEL}}的同事们".
            Do NOT use bare pronouns like "他", "她", "他们", or "自己" when the referent is \
            not unmistakably clear from the same sentence.
            Prefer repeating explicit role phrases over ambiguous pronouns.
            If the subject cannot be made explicit from the source text, do not extract the item.
            """;

    static String buildIdentityContext(String userName) {
        if (userName == null || userName.isBlank()) {
            return IDENTITY_DEFAULT;
        }
        return IDENTITY_WITH_NAME.replace("{{USER_NAME}}", userName);
    }

    static String buildSubjectClarityContext(String userName) {
        String ownerLabel = (userName == null || userName.isBlank()) ? "User" : userName;
        return SUBJECT_CLARITY_TEMPLATE.replace("{{OWNER_LABEL}}", ownerLabel);
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
                    + "Messages contain timestamps (e.g., [2023-05-25 13:17]). Use each message's"
                    + " timestamp only as a reference anchor for resolving relative expressions"
                    + " within that message. Do NOT copy message timestamps into occurredAt unless"
                    + " the memory text itself makes that time semantically explicit."
                    + fallback
                    + "\n\n"
                    + RESOLVE_DATES_INSTRUCTION
                    + "\n";
        } else if (referenceTime != null) {
            return "# Temporal Resolution\n"
                    + "Today's date: "
                    + DATE_FMT.format(referenceTime)
                    + ". Use this only to resolve relative temporal references. Do NOT treat it"
                    + " as a default occurredAt.\n\n"
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
