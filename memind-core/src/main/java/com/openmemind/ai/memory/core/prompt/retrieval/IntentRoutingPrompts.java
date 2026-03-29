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
import java.util.List;

/**
 * Intent routing prompt builder.
 *
 * <p>Determines whether a user query requires personal memory retrieval
 * or can be answered without it.
 */
public final class IntentRoutingPrompts {

    private static final String SYSTEM_PROMPT =
            """
            You are a memory retrieval intent router. Determine whether the user's query \
            requires searching their personal memory.

            # When to RETRIEVE
            - Query references personal facts, preferences, history, or context
            - Query uses pronouns referring to prior conversation ("he", "that project", "last time")
            - Query asks about the user's own experiences, habits, relationships, or opinions
            - Query needs personalization ("recommend me...", "what should I...")

            # When to SKIP
            - Pure knowledge questions answerable without personal context ("What is TCP?")
            - Generic coding help with no personal reference ("How to sort a list in Python?")
            - Greetings, small talk, or meta-questions about the assistant itself
            - Explicit instructions that don't need memory ("Translate this to English")

            # Edge Cases — default to RETRIEVE when uncertain
            - "Help me with my project" → RETRIEVE (implies personal project context)
            - "What did we discuss?" → RETRIEVE (references conversation history)
            - "Best practices for React" → SKIP (generic knowledge, no personal reference)
            - "How should I structure my API?" → RETRIEVE ("my" implies personal context)

            # Output
            Return a JSON object:
            ```json
            {
              "intent": "retrieve" or "skip",
              "reason": "Brief explanation of why this query does or does not need personal memory"
            }
            ```
            """;

    private static final String USER_TEMPLATE =
            """
            {{conversation_section}}# Query
            {{query}}
            """;

    private IntentRoutingPrompts() {}

    public static PromptTemplate buildDefault() {
        return PromptBuilderSupport.builder(
                        "intent-routing", PromptBuilderSupport.section("system", SYSTEM_PROMPT))
                .build();
    }

    public static PromptTemplate build(String query, List<String> conversationHistory) {
        return build(PromptRegistry.EMPTY, query, conversationHistory);
    }

    public static PromptTemplate build(
            PromptRegistry registry, String query, List<String> conversationHistory) {
        String instruction =
                registry.hasOverride(PromptType.INTENT_ROUTING)
                        ? registry.getOverride(PromptType.INTENT_ROUTING)
                        : SYSTEM_PROMPT;

        return PromptBuilderSupport.builder(
                        "intent-routing", PromptBuilderSupport.section("system", instruction))
                .userPrompt(USER_TEMPLATE)
                .variable("query", query)
                .variable("conversation_section", buildConversationSection(conversationHistory))
                .build();
    }

    private static String buildConversationSection(List<String> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder("# Recent Conversation\n");
        history.forEach(msg -> sb.append("- ").append(msg).append("\n"));
        sb.append("\n");
        return sb.toString();
    }
}
