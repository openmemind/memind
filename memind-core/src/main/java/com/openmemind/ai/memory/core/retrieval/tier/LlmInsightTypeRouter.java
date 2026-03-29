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
package com.openmemind.ai.memory.core.retrieval.tier;

import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.retrieval.InsightTypeRoutingPrompts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Insight type router based on StructuredChatClient
 *
 * <p>Calls LLM to select types related to the user query from the available BRANCH types.
 *
 */
public class LlmInsightTypeRouter implements InsightTypeRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmInsightTypeRouter.class);

    private final StructuredChatClient structuredChatClient;
    private final PromptRegistry promptRegistry;

    public LlmInsightTypeRouter(StructuredChatClient structuredChatClient) {
        this(structuredChatClient, PromptRegistry.EMPTY);
    }

    public LlmInsightTypeRouter(
            StructuredChatClient structuredChatClient, PromptRegistry promptRegistry) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
        this.promptRegistry =
                Objects.requireNonNull(promptRegistry, "promptRegistry must not be null");
    }

    @Override
    public Mono<List<String>> route(
            String query, List<String> conversationHistory, Map<String, String> availableTypes) {
        var typeNames = new ArrayList<>(availableTypes.keySet());
        return Mono.defer(
                        () -> {
                            var promptResult =
                                    InsightTypeRoutingPrompts.build(
                                                    promptRegistry,
                                                    query,
                                                    typeNames,
                                                    availableTypes,
                                                    conversationHistory)
                                            .render("English");
                            var messages =
                                    ChatMessages.systemUser(
                                            promptResult.systemPrompt(), promptResult.userPrompt());
                            return structuredChatClient
                                    .call(messages, RoutingResponse.class)
                                    .map(response -> sanitize(response, typeNames))
                                    .switchIfEmpty(Mono.just(List.of()));
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .retry(1)
                .onErrorResume(
                        e -> {
                            log.warn("Insight type routing failed, fallback returns all types", e);
                            return Mono.just(typeNames);
                        });
    }

    private List<String> sanitize(RoutingResponse response, List<String> available) {
        if (response == null || response.types() == null) {
            return List.of();
        }
        return response.types().stream().filter(available::contains).toList();
    }

    record RoutingResponse(List<String> types) {}
}
