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
package com.openmemind.ai.memory.core.retrieval.query;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.retrieval.QueryRewritePrompts;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Query rewriter based on StructuredChatClient
 *
 * <p>Call LLM to parse pronouns, supplement context, and generate query text more suitable for vector search
 *
 */
public class LlmQueryRewriter implements QueryRewriter {

    private final StructuredChatClient structuredChatClient;
    private final PromptRegistry promptRegistry;

    public LlmQueryRewriter(StructuredChatClient structuredChatClient) {
        this(structuredChatClient, PromptRegistry.EMPTY);
    }

    public LlmQueryRewriter(
            StructuredChatClient structuredChatClient, PromptRegistry promptRegistry) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
        this.promptRegistry =
                Objects.requireNonNull(promptRegistry, "promptRegistry must not be null");
    }

    @Override
    public Mono<String> rewrite(MemoryId memoryId, String query, List<String> conversationHistory) {

        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return Mono.just(query);
        }

        var result =
                QueryRewritePrompts.build(promptRegistry, query, conversationHistory)
                        .render("English");
        var messages = ChatMessages.systemUser(result.systemPrompt(), result.userPrompt());

        return structuredChatClient
                .call(messages, RewriteResponse.class)
                .map(
                        response -> {
                            if (response != null
                                    && response.rewrittenQuery() != null
                                    && !response.rewrittenQuery().isBlank()) {
                                return response.rewrittenQuery();
                            }
                            return query;
                        })
                .switchIfEmpty(Mono.just(query))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(query);
    }

    private record RewriteResponse(String rewrittenQuery) {}
}
