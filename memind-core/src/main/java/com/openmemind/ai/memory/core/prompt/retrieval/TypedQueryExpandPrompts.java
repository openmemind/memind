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
package com.openmemind.ai.memory.core.prompt.retrieval;

import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Typed query expansion prompt builder.
 *
 * <p>Generates typed alternative search queries (lex/vec/hyde) to find
 * information missed by the initial retrieval round. Used by
 * {@link com.openmemind.ai.memory.core.retrieval.deep.LlmTypedQueryExpander}.
 */
public final class TypedQueryExpandPrompts {

    private static final String TASK_WITH_GAPS =
            """
            You are a memory retrieval query expander. The initial search found partial
            results but missed specific information. Generate typed search queries to
            fill the identified gaps.

            # Query Types

            Each query must have a "type" field:
            - "lex": Keyword query. Extract key entities and relation words, space-separated.
              Remove function words (articles, prepositions, conjunctions). Optimized for
              BM25 keyword matching.
            - "vec": Semantic query. Rephrase the intent in natural language from a different
              angle or perspective. Optimized for vector similarity search.
            - "hyde": Hypothetical document. Write a short sentence (15-30 words) that would
              appear in the ANSWER to the query, as if it were already stored in memory.
              NOT a question — a declarative statement. Optimized for matching against stored
              memory items.

            # Strategy

            1. Read the identified gaps carefully — each gap describes specific missing info.
            2. For each gap, generate 1-2 queries using the most appropriate type:
               - Gap about specific entities/names/dates → prefer "lex"
               - Gap about intent/meaning/context → prefer "vec"
               - Gap about facts that should exist in memory → prefer "hyde"
            3. Diversify: do NOT generate multiple queries of the same type for the same gap.
            4. Total queries MUST NOT exceed {{max_expansions}}.

            # Temporal Strategy

            When the query involves time (when, how long, before/after, recently):
            - "lex" query: include temporal keywords (date, month, year, "last week", etc.)
            - "vec" query: rephrase with explicit time anchors
            - "hyde" query: write the hypothetical answer WITH a specific date/time

            # Examples

            Original query: "What programming languages does the user know?"
            Gaps: ["No information about programming language proficiency levels"]

            Good expansions:
            {
              "queries": [
                {"type": "lex", "text": "programming language proficiency level expert"},
                {"type": "hyde", "text": "User is proficient in Python and has intermediate Java skills"}
              ]
            }

            Original query: "When did the user start their current job?"
            Gaps: ["Missing job start date"]

            Good expansions:
            {
              "queries": [
                {"type": "lex", "text": "job start date joined company"},
                {"type": "hyde", "text": "User started working at the company in March 2024"}
              ]
            }

            Bad expansions (DO NOT do this):
            - {"type": "vec", "text": "programming languages"} → too vague, same as original
            - {"type": "hyde", "text": "What languages does the user know?"} → hyde must be
              declarative, not a question
            - Generating 5 "vec" queries that are minor rephrasings of each other

            {{temporal_strategy}}
            """;

    private static final String TASK_GENERIC =
            """
            You are a memory retrieval query expander. Generate typed alternative search
            queries to find more relevant information from a different angle.

            # Query Types

            Each query must have a "type" field:
            - "lex": Keyword query. Extract key entities and relation words, space-separated.
              Remove function words. Optimized for BM25 keyword matching.
            - "vec": Semantic query. Rephrase the intent in natural language from a different
              angle. Optimized for vector similarity search.
            - "hyde": Hypothetical document. Write a short sentence (15-30 words) that would
              appear in the ANSWER, as if already stored in memory. Must be declarative,
              NOT a question.

            # Strategy

            1. Analyze the original query's intent and identify alternative angles.
            2. Generate queries that explore DIFFERENT aspects, not minor rephrasings.
            3. Mix query types for diversity. At least 2 different types.
            4. Total queries MUST NOT exceed {{max_expansions}}.

            # Temporal Strategy

            When the query involves time:
            - "lex": include temporal keywords
            - "vec": rephrase with explicit time anchors
            - "hyde": include specific date/time in the hypothetical answer

            # Examples

            Original query: "What does the user do for exercise?"

            Good expansions:
            {
              "queries": [
                {"type": "lex", "text": "exercise workout gym running fitness"},
                {"type": "vec", "text": "user's physical activity habits and sports routine"},
                {"type": "hyde", "text": "User goes running three times a week in the morning"}
              ]
            }

            Bad expansions:
            - {"type": "vec", "text": "user exercise"} → too short, same angle as original
            - Three "vec" queries that all ask about exercise differently

            {{temporal_strategy}}
            """;

    private static final String TEMPORAL_STRATEGY =
            """

            # Time-Aware Expansion

            If the original query or gaps involve temporal aspects:
            - Expand time references: "recently" → try both "last week" and "last month"
            - Include date-format variants: "2024-03", "March 2024", "last March"
            - For duration queries: search for both start and end events separately
            """;

    private static final String OUTPUT_FORMAT =
            """

            # Output Format

            Return a JSON object with a "queries" array.
            Each query: {"type": "<lex|vec|hyde>", "text": "<query text>"}
            Maximum {{max_expansions}} queries. No duplicates. No explanations outside JSON.
            """;

    private static final String USER_TEMPLATE =
            """
            # Original Query
            {{query}}

            {{gaps_section}}

            {{key_info_section}}

            {{conversation_section}}
            """;

    private TypedQueryExpandPrompts() {}

    public static PromptTemplate buildDefault() {
        String fullSystem = TASK_WITH_GAPS.replace("{{temporal_strategy}}", TEMPORAL_STRATEGY);
        return PromptTemplate.builder("typed-query-expand")
                .section("system", fullSystem)
                .section("output", OUTPUT_FORMAT)
                .build();
    }

    public static PromptTemplate buildPreview() {
        return buildDefault().withVariable("max_expansions", "5");
    }

    public static PromptTemplate build(
            String query,
            List<String> gaps,
            List<String> keyInformation,
            List<String> conversationHistory,
            int maxExpansions) {
        return build(
                PromptRegistry.EMPTY,
                query,
                gaps,
                keyInformation,
                conversationHistory,
                maxExpansions);
    }

    public static PromptTemplate build(
            PromptRegistry registry,
            String query,
            List<String> gaps,
            List<String> keyInformation,
            List<String> conversationHistory,
            int maxExpansions) {
        boolean hasGaps = gaps != null && !gaps.isEmpty();
        String taskSection = hasGaps ? TASK_WITH_GAPS : TASK_GENERIC;

        String gapsSection = "";
        if (hasGaps) {
            gapsSection =
                    "# Identified Gaps\n"
                            + gaps.stream().map(g -> "- " + g).collect(Collectors.joining("\n"))
                            + "\n";
        }

        String keyInfoSection = "";
        if (keyInformation != null && !keyInformation.isEmpty()) {
            keyInfoSection =
                    "# Already Found (do NOT search for these again)\n"
                            + keyInformation.stream()
                                    .map(k -> "- " + k)
                                    .collect(Collectors.joining("\n"))
                            + "\n";
        }

        String conversationSection = "";
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            conversationSection = buildConversationSection(conversationHistory);
        }

        boolean hasTemporalHint =
                query.matches(
                        "(?i).*\\b(when|how"
                            + " long|before|after|recently|last|ago|since|date|time|year|month)\\b.*");
        String temporalStrategy = hasTemporalHint ? TEMPORAL_STRATEGY : "";
        String systemPrompt = taskSection.replace("{{temporal_strategy}}", temporalStrategy);
        String instruction =
                registry.hasOverride(PromptType.TYPED_QUERY_EXPAND)
                        ? registry.getOverride(PromptType.TYPED_QUERY_EXPAND)
                        : systemPrompt;

        return PromptTemplate.builder("typed-query-expand")
                .section("system", instruction)
                .section("output", OUTPUT_FORMAT)
                .userPrompt(USER_TEMPLATE)
                .variable("query", query)
                .variable("gaps_section", gapsSection)
                .variable("key_info_section", keyInfoSection)
                .variable("conversation_section", conversationSection)
                .variable("max_expansions", String.valueOf(maxExpansions))
                .build();
    }

    private static String buildConversationSection(List<String> history) {
        return "# Recent Conversation\n"
                + history.stream().map(msg -> "- " + msg).collect(Collectors.joining("\n"))
                + "\n\n";
    }
}
