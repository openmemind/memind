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
package com.openmemind.ai.memory.core.retrieval.intent;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.retrieval.IntentRoutingPrompts;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Intent router based on StructuredChatClient
 *
 * <p>Call LLM to determine whether the current query needs to retrieve memory
 *
 */
public class LlmIntentionRouter implements IntentionRouter {

    private final StructuredChatClient structuredChatClient;
    private final PromptRegistry promptRegistry;

    public LlmIntentionRouter(StructuredChatClient structuredChatClient) {
        this(structuredChatClient, PromptRegistry.EMPTY);
    }

    public LlmIntentionRouter(
            StructuredChatClient structuredChatClient, PromptRegistry promptRegistry) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
        this.promptRegistry =
                Objects.requireNonNull(promptRegistry, "promptRegistry must not be null");
    }

    @Override
    public Mono<RetrievalIntent> route(
            MemoryId memoryId, String query, List<String> conversationHistory) {

        var result =
                IntentRoutingPrompts.build(promptRegistry, query, conversationHistory)
                        .render("English");
        var messages = ChatMessages.systemUser(result.systemPrompt(), result.userPrompt());

        return structuredChatClient
                .call(messages, IntentResponse.class)
                .map(
                        response -> {
                            if (response != null && "skip".equalsIgnoreCase(response.intent())) {
                                return RetrievalIntent.SKIP;
                            }
                            return RetrievalIntent.RETRIEVE;
                        })
                .switchIfEmpty(Mono.just(RetrievalIntent.RETRIEVE))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(RetrievalIntent.RETRIEVE);
    }

    private record IntentResponse(String intent, String reason) {}
}
