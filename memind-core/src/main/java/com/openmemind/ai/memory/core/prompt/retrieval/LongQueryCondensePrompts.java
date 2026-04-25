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
import java.util.stream.Collectors;

public final class LongQueryCondensePrompts {

    private static final String SYSTEM_PROMPT =
            """
            You rewrite oversized retrieval queries into concise, retrieval-focused queries.
            Do not answer the query. Preserve entities, time constraints, topic constraints,
            exclusions, and user intent. Remove copied documents, logs, markup, repeated
            context, and irrelevant background. Return structured JSON only.
            """;

    private static final String USER_TEMPLATE =
            """
            # Target Max Tokens
            {{target_max_tokens}}

            {{conversation_section}}
            # Oversized Query
            {{query}}

            Return JSON with field condensedQuery.
            """;

    private LongQueryCondensePrompts() {}

    public static PromptTemplate buildDefault() {
        return PromptBuilderSupport.builder(
                        "long-query-condense",
                        PromptBuilderSupport.section("system", SYSTEM_PROMPT))
                .build();
    }

    public static PromptTemplate build(
            PromptRegistry registry,
            String query,
            List<String> conversationHistory,
            int targetMaxTokens) {
        String instruction =
                registry.hasOverride(PromptType.LONG_QUERY_CONDENSE)
                        ? registry.getOverride(PromptType.LONG_QUERY_CONDENSE)
                        : SYSTEM_PROMPT;
        return PromptBuilderSupport.builder(
                        "long-query-condense", PromptBuilderSupport.section("system", instruction))
                .userPrompt(USER_TEMPLATE)
                .variable("target_max_tokens", String.valueOf(targetMaxTokens))
                .variable("conversation_section", buildConversationSection(conversationHistory))
                .variable("query", query)
                .build();
    }

    private static String buildConversationSection(List<String> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        return "# Recent Conversation\n"
                + history.stream().map(message -> "- " + message).collect(Collectors.joining("\n"))
                + "\n";
    }
}
