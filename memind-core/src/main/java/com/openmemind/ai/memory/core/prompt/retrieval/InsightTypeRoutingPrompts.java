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

import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Insight type routing prompt builder.
 *
 * <p>Selects which BRANCH insight types are relevant to a user query.
 * Used by Tier1 retrieval to narrow down the search space before
 * vector similarity matching.
 */
public final class InsightTypeRoutingPrompts {

    private static final String TASK =
            """
            You are an insight type router. Given a user query, select which insight
            types are likely to contain relevant information. You may select multiple
            types, or none if the query is too vague.

            # Principles
            1. Conservative Selection: Only select types where you have clear reason
               to believe they contain relevant information. Do NOT select a type just
               because it "might" be related.
            2. Query Decomposition: Break the query into key entities and intent before
               matching. "What does my wife think about my job change?" needs both
               relationships (wife's opinion) and experiences (job change).
            3. Prefer Fewer Types: Each selected type triggers a search. Selecting too
               many wastes resources and dilutes results. When in doubt, leave it out.

            # Type Semantics Guide
            Common insight types and when to select them:
            - identity: Query asks about WHO the user is — background, skills, traits.
              e.g., "What's my tech stack?" "What do I do for work?"
            - preferences: Query asks about LIKES/DISLIKES/VALUES — opinions, tastes.
              e.g., "Do I prefer tabs or spaces?" "What kind of food do I like?"
            - relationships: Query asks about OTHER PEOPLE — family, colleagues, dynamics.
              e.g., "Who is on my team?" "What does my wife do?"
            - experiences: Query asks about EVENTS/PROJECTS/GOALS — time-bound situations.
              e.g., "What am I working on?" "When did I start this project?"
            - behavior: Query asks about HABITS/ROUTINES — recurring patterns.
              e.g., "What's my morning routine?" "How often do I exercise?"
            - procedural: Query asks about HOW-TO — procedures, solutions, agent directives.
              e.g., "How did I fix that HikariCP issue?" "What did I tell you about code style?"

            # Boundary Cases
            - "What programming languages do I know?" → identity (stable skill), NOT experiences
            - "What programming language am I learning?" → experiences (time-bound activity)
            - "I like using Kotlin" → preferences (opinion), NOT identity
            - "My colleague Zhang handles the backend" → relationships (person) + experiences (team context)

            {{conversation_section}}

            # Available Insight Types
            {{available_types}}

            # Query
            {{query}}

            # Output
            Return a JSON array of selected type names. Empty array if none are relevant.
            Example: ["identity", "experiences"]
            """;

    private InsightTypeRoutingPrompts() {}

    public static PromptTemplate build(
            String query, List<String> insightTypeNames, Map<String, String> typeDescriptions) {
        return build(query, insightTypeNames, typeDescriptions, List.of());
    }

    public static PromptTemplate build(
            String query,
            List<String> insightTypeNames,
            Map<String, String> typeDescriptions,
            List<String> conversationHistory) {

        String availableTypes =
                insightTypeNames.stream()
                        .map(
                                name -> {
                                    String desc = typeDescriptions.getOrDefault(name, "");
                                    return "- " + name + ": " + desc;
                                })
                        .collect(Collectors.joining("\n"));

        String conversationSection = "";
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            conversationSection =
                    "# Recent Conversation\n"
                            + conversationHistory.stream()
                                    .map(msg -> "- " + msg)
                                    .collect(Collectors.joining("\n"))
                            + "\n";
        }

        return PromptTemplate.builder("insight-type-routing")
                .userPrompt(TASK)
                .variable("query", query)
                .variable("available_types", availableTypes)
                .variable("conversation_section", conversationSection)
                .build();
    }
}
