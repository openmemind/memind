package com.openmemind.ai.memory.core.prompt.retrieval;

import com.openmemind.ai.memory.core.prompt.PromptTemplate;
import java.util.List;

/**
 * Intent routing prompt builder.
 *
 * <p>Determines whether a user query requires personal memory retrieval
 * or can be answered without it.
 */
public final class IntentRoutingPrompts {

    private static final String TASK =
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

    private static final String TEMPLATE =
            """
            {{task_section}}

            {{conversation_section}}

            Query: {{query}}
            """;

    private IntentRoutingPrompts() {}

    public static PromptTemplate build(String query, List<String> conversationHistory) {
        String conversationSection =
                (conversationHistory == null || conversationHistory.isEmpty())
                        ? ""
                        : buildConversationSection(conversationHistory);

        return PromptTemplate.builder("intent-routing")
                .userPrompt(TEMPLATE)
                .variable("task_section", TASK)
                .variable("query", query)
                .variable("conversation_section", conversationSection)
                .build();
    }

    private static String buildConversationSection(List<String> history) {
        var sb = new StringBuilder("# Recent Conversation\n");
        history.forEach(msg -> sb.append("- ").append(msg).append("\n"));
        return sb.toString();
    }
}
