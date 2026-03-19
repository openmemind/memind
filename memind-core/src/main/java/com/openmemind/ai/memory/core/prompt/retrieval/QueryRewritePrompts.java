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

/**
 * Query rewrite prompt builder.
 *
 * <p>Resolves pronouns, coreferences, and implicit context from conversation
 * history to produce a self-contained search query optimized for vector retrieval.
 */
public final class QueryRewritePrompts {

    private static final String TASK =
            """
            You are a query rewriter for a memory retrieval system. Your task is to
            rewrite the user's latest query into a self-contained search query that
            can be understood WITHOUT any conversation context.

            # Core Principles
            1. Resolve ALL pronouns and references using conversation history
               ("he" → actual name, "that project" → actual project name).
            2. Preserve the original query intent — do NOT add, remove, or change
               what is being asked.
            3. Keep the rewritten query concise — suitable for vector similarity search.
            4. If the query is already self-contained (no pronouns, no implicit
               references), return it unchanged.

            # Workflow
            Step 1 — Identify references: Find pronouns (he/she/it/they/that/this),
            ellipsis ("what about..."), and implicit context that depends on prior turns.
            Step 2 — Resolve from history: Replace each reference with the concrete
            entity from conversation history.
            Step 3 — Simplify: Remove conversational filler (well, so, um, okay) and
            keep only the search-relevant content.

            # Examples

            History: ["User asked about Spring Boot configuration", "Assistant explained application.yml"]
            Query: "What about the logging part?"
            Rewritten: "Spring Boot logging configuration"

            History: ["User mentioned their colleague Zhang Wei is handling the database migration"]
            Query: "When did he start?"
            Rewritten: "When did Zhang Wei start the database migration"

            History: (none)
            Query: "What is my favorite programming language?"
            Rewritten: "What is my favorite programming language?"
            (Already self-contained, returned unchanged)
            """;

    private static final String TEMPLATE =
            """
            {{task_section}}

            {{conversation_section}}

            # Query
            {{query}}

            # Rewritten Query
            """;

    private QueryRewritePrompts() {}

    public static PromptTemplate build(String query, List<String> conversationHistory) {
        String conversationSection =
                (conversationHistory != null && !conversationHistory.isEmpty())
                        ? buildConversationSection(conversationHistory)
                        : "";

        return PromptTemplate.builder("query-rewrite")
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
