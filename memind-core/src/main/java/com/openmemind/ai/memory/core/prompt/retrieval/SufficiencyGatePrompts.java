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

import com.openmemind.ai.memory.core.prompt.PromptBuilderSupport;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import com.openmemind.ai.memory.core.prompt.PromptType;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Sufficiency Gate Prompt Builder
 *
 * <p>Evaluates whether retrieved content fully answers the user's query.
 * When insufficient, identifies specific gaps to drive the next retrieval round.
 */
public final class SufficiencyGatePrompts {

    private static final String OBJECTIVE =
            """
            You are a retrieval sufficiency evaluator. Judge whether the retrieved
            content fully answers the user's query. Your judgment directly controls
            whether the system performs an expensive second retrieval round — be
            accurate.

            Core principle: It is BETTER to judge "insufficient" (triggering a
            cheap retry) than to judge "sufficient" when key information is missing
            (returning an incomplete answer to the user).
            """;

    private static final String CONTENT_TYPES =
            """
            # Content Types

            Retrieved results come in three types:
            - INSIGHT: High-level synthesized summaries across multiple memory items.
              Rich in patterns and conclusions, but may lack specific details.
            - ITEM: Atomic facts extracted from conversations. Specific and precise,
              but narrow in scope.
            - RAW_DATA: Original conversation context. Provides full narrative but
              may contain noise.

            When evaluating, consider that INSIGHT alone may "mention" a topic
            without providing the specific detail the query asks for. Always check
            whether the ACTUAL answer (not just the topic) is present.
            """;

    private static final String WORKFLOW =
            """
            # Workflow

            ## Step 1 — Decompose the Query
            Identify what the query requires:
            - Key entities: who, what, where
            - Specific details: numbers, dates, names, versions, config values
            - For time/date queries: start_time, end_time, temporal_relation
            - For comparison queries: both sides of the comparison
            - For "how" queries: actionable steps, not just topic mention

            ## Step 2 — Check Each Requirement Against Results
            For EACH requirement identified in Step 1:
            - Is there a result that DIRECTLY answers it (not just mentions the topic)?
            - Is the answer specific enough? ("User works with databases" does NOT
              answer "What database does the user use?")
            - For time queries: Are actual dates/times present, or only vague
              references like "recently"?

            ## Step 3 — Decide
            - ALL requirements answered with specific details → sufficient
            - ANY requirement missing or only vaguely covered → insufficient
            - When in doubt → insufficient

            ## Step 4 — Extract Outputs
            - gaps: List up to 3 specific missing pieces (only when insufficient).
              Each gap should be a concrete, searchable description, NOT a vague
              category. Bad: "time information". Good: "the specific date when
              user started learning Python".
            - keyInformation: Extract 1-3 key facts from results that ARE relevant
              to the query, even if insufficient overall. These help the next round
              avoid redundant results.
            - evidences: Copy the exact sentences from results that support your
              judgment. Include result index.
            """;

    private static final String OUTPUT =
            """
            # Output Format

            Return a JSON object:
            ```json
            {
              "sufficient": true/false,
              "reasoning": "Step-by-step: Query requires X, Y, Z.
              Result #1 provides X. Result #3 provides Y. Z is not found in any result. → insufficient.",
              "gaps": ["specific missing piece 1", "specific missing piece 2"],
              "keyInformation": ["key fact extracted from results"],
              "evidences": ["[#1] exact sentence from result"]
            }
            ```
            """;

    private static final String EXAMPLES =
            """
            # Examples

            ## Example 1 — Sufficient

            Query: "What programming languages does the user know?"
            Results:
            - #1 [ITEM] User is a backend engineer with 5 years of Java experience
            - #2 [ITEM] User has been learning Python for data analysis
            - #3 [INSIGHT] User's technical identity centers on Java/Spring ecosystem with
              emerging Python skills

            Output:
            {
              "sufficient": true,
              "reasoning": "Query asks for programming languages. #1 provides Java (5 years).
              #2 provides Python (learning). #3 confirms both. All aspects covered with specifics.",
              "gaps": [],
              "keyInformation": ["User knows Java (5 years) and Python (learning)"],
              "evidences": ["[#1] User is a backend engineer with 5 years of Java experience",
              "[#2] User has been learning Python for data analysis"]
            }

            ## Example 2 — Insufficient (topic mentioned but detail missing)

            Query: "What database does the user use in their current project?"
            Results:
            - #1 [INSIGHT] User is working on a microservices migration project
            - #2 [ITEM] User asked about connection pool configuration

            Output:
            {
              "sufficient": false,
              "reasoning": "Query asks for specific database name in current project.
              #1 mentions the project but no database. #2 mentions connection pool but no specific database name.
              The topic is related but the actual answer is missing.",
              "gaps": ["the specific database (e.g., PostgreSQL, MySQL) used in the microservices project"],
              "keyInformation": ["User is working on a microservices migration project"],
              "evidences": []
            }

            ## Example 3 — Insufficient (time query without dates)

            Query: "When did the user start their current job?"
            Results:
            - #1 [ITEM] User works at TechCorp as a senior engineer
            - #2 [INSIGHT] User has been at their current company for a while and was recently promoted

            Output:
            {
              "sufficient": false,
              "reasoning": "Query asks for a specific start date/time. #1 confirms the company but no date.
               #2 says 'for a while' which is vague, not a specific date. Time queries require actual dates,
               not vague temporal references.",
              "gaps": ["the specific date or year when user started working at TechCorp"],
              "keyInformation": ["User works at TechCorp as a senior engineer"],
              "evidences": ["[#1] User works at TechCorp as a senior engineer"]
            }
            """;

    private static final String USER_TEMPLATE =
            """
            {{conversation_section}}
            # Query
            {{query}}

            # Retrieved Results
            {{results}}
            """;

    private SufficiencyGatePrompts() {}

    public static PromptTemplate buildDefault() {
        return PromptBuilderSupport.coreSections(
                        "sufficiency-gate", OBJECTIVE, CONTENT_TYPES, WORKFLOW, OUTPUT, EXAMPLES)
                .build();
    }

    public static PromptTemplate build(QueryContext context, List<ScoredResult> results) {
        return build(PromptRegistry.EMPTY, context, results);
    }

    public static PromptTemplate build(
            PromptRegistry registry, QueryContext context, List<ScoredResult> results) {
        String formattedResults =
                IntStream.range(0, results.size())
                        .mapToObj(
                                i -> {
                                    var r = results.get(i);
                                    return "#"
                                            + (i + 1)
                                            + " ["
                                            + r.sourceType()
                                            + "] (score: "
                                            + String.format("%.2f", r.finalScore())
                                            + ") "
                                            + r.text();
                                })
                        .collect(Collectors.joining("\n"));

        PromptTemplate.Builder builder;
        if (registry.hasOverride(PromptType.SUFFICIENCY_GATE)) {
            builder =
                    PromptBuilderSupport.builder(
                            "sufficiency-gate",
                            PromptBuilderSupport.section(
                                    "system", registry.getOverride(PromptType.SUFFICIENCY_GATE)));
        } else {
            builder =
                    PromptBuilderSupport.coreSections(
                            "sufficiency-gate",
                            OBJECTIVE,
                            CONTENT_TYPES,
                            WORKFLOW,
                            OUTPUT,
                            EXAMPLES);
        }

        return builder.userPrompt(USER_TEMPLATE)
                .variable(
                        "conversation_section",
                        buildConversationSection(context.conversationHistory()))
                .variable("query", context.searchQuery())
                .variable("results", formattedResults)
                .build();
    }

    private static String buildConversationSection(List<String> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        return "# Recent Conversation\n"
                + history.stream().map(msg -> "- " + msg).collect(Collectors.joining("\n"))
                + "\n";
    }
}
