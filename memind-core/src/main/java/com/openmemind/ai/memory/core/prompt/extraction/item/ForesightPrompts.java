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

import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Foresight extraction prompt builder.
 *
 * <p>Generates associative predictions about the user's future behaviors, needs, and likely actions
 * based on conversation content. Returns a {@link PromptTemplate} so the caller can defer language
 * injection to {@code render(language)}.
 */
public final class ForesightPrompts {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private static final Pattern MESSAGE_TIMESTAMP_PATTERN =
            Pattern.compile("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}]");

    private static final String OBJECTIVE =
            """
            You are a predictive analyst. Your task is to analyze a conversation and generate \
            forward-looking predictions about the user's future behaviors, needs, and likely \
            actions.

            Upstream extractors have already captured explicit facts from this conversation \
            (stated plans, preferences, events). Your job is DIFFERENT — you must predict \
            what FOLLOWS from those facts. Generate associative predictions about behavioral \
            changes, emerging needs, and likely next actions that the user did NOT explicitly \
            state.
            """;

    private static final String GUIDELINES =
            """
            # Core Principles

            1. **Predict, don't restate**: Every prediction must go BEYOND what the user \
            explicitly said. If the user said "I have an interview Friday", the fact \
            extractor already captured that. You should predict what behavioral changes \
            this triggers (e.g., will shift practice focus, may need mock interview help).
            2. **Evidence-based**: Every prediction MUST be grounded in explicit signals from \
            the conversation (stated plans, described situations, repeated patterns, \
            unresolved issues). No speculation without textual support.
            3. **Specific and actionable**: Each prediction should be concrete enough that a \
            system could act on it (e.g., proactively offer help, surface relevant \
            information). NOT vague like "user might need help in the future".
            4. **Time-bounded**: Every prediction must include a validity period. Stale \
            predictions are noise.
            5. **Scene-appropriate language**: Match the tone to the scenario — use everyday \
            language for life situations (health, family, hobbies), professional language \
            for work situations (projects, career, skills).

            # What to Predict

            - **Behavioral changes**: Actions the user will likely take as a consequence of \
            what was discussed (e.g., dietary changes after a medical procedure, practice \
            focus shift before an exam).
            - **Emerging needs**: Help or resources the user will likely need soon, based on \
            their described situation (e.g., mock interview practice, deployment debugging \
            help after setting up a new service).
            - **Follow-up actions**: Likely next steps the user hasn't stated but that \
            logically follow from their current situation (e.g., after learning a new \
            framework → will try to apply it in their project).
            - **Recurring patterns**: If the conversation reveals a pattern, predict its \
            continuation (e.g., user runs every Tuesday → will likely run next Tuesday).

            # What NOT to Predict

            - **Restated facts**: "User has an interview next Friday" — this is a fact, not \
            a prediction. Already captured upstream.
            - **Restated plans in different wording**: "User will attend the interview" — just \
            rewording what the user said. Not a prediction.
            - **User-stated future commitments**: "User said they will finish X by date" — \
            this is a plan/commitment already captured as a FACT. Predict what FOLLOWS from \
            it, not the commitment itself.
            - **Vague speculation**: "User might need help someday" — not actionable.
            - **Emotional/personality inferences**: "User seems anxious about the interview" \
            — not a behavioral prediction.
            - **Predictions beyond 90 days**: Unless explicitly supported by the conversation.

            # Time Estimation

            - Specific deadline mentioned in conversation → `validUntil` = that exact date.
            - "next week" / "soon" / short-term tasks → `durationDays` = 7 to 14.
            - Recurring habits / skill application → `durationDays` = 30.
            - Long-term goals / career plans → `durationDays` = 60 to 90.
            - If uncertain → default `durationDays` = 30.

            Prefer extracting explicit time references from the conversation. Only estimate \
            when no time reference is available.
            """;

    private static final String OUTPUT =
            """
            # Output Format

            Return ONLY a JSON object. No extra text, no markdown fences.
            Return valid JSON ONLY. No markdown fences, no surrounding text.
            Generate 2-6 predictions. Return empty list if no predictable signals exist.

            {
              "items": [
                {
                  "content": "Specific prediction about future behavior or need (one sentence)",
                  "evidence": "Brief quote or paraphrase from conversation supporting this prediction",
                  "validUntil": "2026-03-28",
                  "durationDays": 7
                }
              ]
            }

            (Return `{"items": []}` if nothing qualifies)
            """;

    private static final String EXAMPLES =
            """
            # Examples

            ## Good Example 1: Life scenario (medical)

            Conversation:
            [2026-03-15 14:05] user: Just got my wisdom tooth extracted. Still a bit sore.
            [2026-03-15 14:06] assistant: Make sure to keep the area clean and avoid hard foods.
            [2026-03-15 14:07] user: The dentist said to come back if the swelling gets worse.

            Output:
            {
              "items": [
                {
                  "content": "User will avoid spicy and hot foods for the next week",
                  "evidence": "Wisdom tooth extraction; dentist advised keeping area clean",
                  "validUntil": "2026-03-22",
                  "durationDays": 7
                },
                {
                  "content": "User will prefer soft foods and reduce chewing force for several days",
                  "evidence": "User reported soreness after extraction",
                  "validUntil": "2026-03-19",
                  "durationDays": 4
                },
                {
                  "content": "User may need a follow-up dental visit if swelling worsens",
                  "evidence": "Dentist instructed to return if swelling gets worse",
                  "validUntil": "2026-03-29",
                  "durationDays": 14
                }
              ]
            }

            Why this is good:
            - None of these predictions are explicitly stated by the user
            - Each is a logical behavioral consequence of the tooth extraction
            - "Avoid spicy foods" and "prefer soft foods" are inferred from the medical \
            situation, not restated from the conversation
            - Time estimates are reasonable for post-extraction recovery

            ## Good Example 2: Work scenario (learning new technology)

            Conversation:
            [2026-03-15 10:00] user: I just finished the Spring Boot 3.2 virtual threads workshop
            [2026-03-15 10:02] user: The structured concurrency part was really eye-opening
            [2026-03-15 10:03] assistant: Virtual threads can dramatically simplify concurrent code...

            Output:
            {
              "items": [
                {
                  "content": "User will likely try enabling virtual threads in their current Spring Boot project",
                  "evidence": "Completed virtual threads workshop; found structured concurrency eye-opening",
                  "validUntil": "2026-04-14",
                  "durationDays": 30
                },
                {
                  "content": "User may need help migrating thread pool configurations when adopting virtual threads",
                  "evidence": "Learning virtual threads implies upcoming migration from platform threads",
                  "validUntil": "2026-04-14",
                  "durationDays": 30
                },
                {
                  "content": "User will likely explore structured concurrency APIs (StructuredTaskScope) in more depth",
                  "evidence": "User specifically highlighted structured concurrency as eye-opening",
                  "validUntil": "2026-04-14",
                  "durationDays": 30
                }
              ]
            }

            Why this is good:
            - Predicts what the user will DO with the knowledge, not just restating \
            "user learned about virtual threads"
            - "Need help migrating thread pool configs" is an inferred emerging need
            - "Explore StructuredTaskScope" follows from the expressed interest

            ## Bad Example 1: Restating facts (WRONG)

            Conversation: User mentions they have a Google interview next Friday.

            Output (WRONG):
            {
              "items": [
                {
                  "content": "User has a Google interview next Friday",
                  "evidence": "User stated they have a Google interview",
                  "validUntil": "2026-03-22",
                  "durationDays": 7
                }
              ]
            }

            -> Wrong: This is a fact, not a prediction. The upstream fact extractor already \
            captured "user has a Google interview next Friday". Foresight should predict \
            behavioral changes: "User will focus coding practice on algorithm problems this \
            week", "User may need mock behavioral interview practice before Friday".

            ## Bad Example 3: Restating user-committed plans (WRONG)

            Conversation:
            user: I need to finish the load test script by Wednesday.
            user: The test report must be submitted to the CTO by Friday.

            Output (WRONG):
            {
              "items": [
                {
                  "content": "User will complete the load test script by Wednesday",
                  "evidence": "User stated the script deadline is Wednesday",
                  "validUntil": "2026-03-19",
                  "durationDays": 4
                },
                {
                  "content": "User will submit the test report to the CTO by Friday",
                  "evidence": "User mentioned the Friday submission deadline",
                  "validUntil": "2026-03-21",
                  "durationDays": 6
                }
              ]
            }

            -> Wrong: These are explicit commitments the user stated — they are FACTs already \
            captured upstream. Foresight should predict what happens BECAUSE of these \
            deadlines, not the deadlines themselves. Good predictions here would be: \
            "User will be under time pressure and may need help optimizing Gatling scripts \
            quickly", "If load test results don't meet targets, user will need specific \
            bottleneck analysis and optimization strategies".

            ## Bad Example 2: Too vague (WRONG)
            {
              "items": [
                {
                  "content": "User might need help with their project",
                  "evidence": "User is working on a project",
                  "validUntil": "2026-04-15",
                  "durationDays": 30
                }
              ]
            }

            -> Wrong: "Might need help with their project" is not actionable. What kind of \
            help? What aspect of the project? A prediction must be specific enough that a \
            system could prepare relevant assistance.

            ## Example 3: No predictable signals

            Conversation:
            user: What's the weather like today?
            assistant: I can't check the weather, but you can try a weather app.

            Output:
            {"items": []}\
            """;

    private static final String USER_PROMPT_TEMPLATE =
            """
            Please extract foresight predictions from the following conversation:

            # Conversation

            {{segment_text}}\
            """;

    private ForesightPrompts() {}

    /**
     * Builds a foresight extraction prompt template.
     *
     * @param segmentText conversation text to predict from
     * @param referenceTime reference time for calculating validUntil dates (null allowed)
     * @return prompt template; call {@code render(language)} to produce the final result
     */
    public static PromptTemplate build(String segmentText, Instant referenceTime) {
        return build(PromptRegistry.EMPTY, segmentText, referenceTime);
    }

    public static PromptTemplate buildDefault() {
        return defaultBuilder(buildTimeContext(null, null)).build();
    }

    public static PromptTemplate build(
            PromptRegistry registry, String segmentText, Instant referenceTime) {
        String timeCtx = buildTimeContext(segmentText, referenceTime);

        PromptTemplate.Builder builder =
                registry.hasOverride(PromptType.FORESIGHT)
                        ? PromptTemplate.builder("foresight-extraction")
                                .section("system", registry.getOverride(PromptType.FORESIGHT))
                        : defaultBuilder(timeCtx);

        return builder.userPrompt(USER_PROMPT_TEMPLATE)
                .variable("segment_text", segmentText != null ? segmentText : "")
                .build();
    }

    private static PromptTemplate.Builder defaultBuilder(String timeContext) {
        return PromptTemplate.builder("foresight-extraction")
                .section("objective", OBJECTIVE)
                .section("guidelines", GUIDELINES)
                .section("output", OUTPUT)
                .section("examples", EXAMPLES)
                .section("timeContext", timeContext);
    }

    private static String buildTimeContext(String segmentText, Instant referenceTime) {
        boolean hasTimestamps =
                segmentText != null && MESSAGE_TIMESTAMP_PATTERN.matcher(segmentText).find();

        if (hasTimestamps) {
            String fallback =
                    referenceTime != null
                            ? "\nFallback Reference Date (use only if a message lacks a"
                                    + " timestamp): "
                                    + DATE_FMT.format(referenceTime)
                            : "";
            return "# Time Context\n"
                    + "Messages contain timestamps (e.g., [2026-03-01 14:30]). Use the latest"
                    + " message's timestamp as the baseline to calculate `validUntil` dates."
                    + fallback;
        } else if (referenceTime != null) {
            return "# Time Context\n"
                    + "Today's Reference Date: "
                    + DATE_FMT.format(referenceTime)
                    + ". Use this as the baseline to calculate `validUntil` dates.";
        }

        return "# Time Context\n"
                + "No temporal anchors available. Estimate `validUntil` relatively if needed.";
    }
}
