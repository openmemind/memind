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
package com.openmemind.ai.memory.core.retrieval.deep;

import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.retrieval.TypedQueryExpandPrompts;
import com.openmemind.ai.memory.core.retrieval.deep.ExpandedQuery.QueryType;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Type-annotated query expander based on StructuredChatClient
 *
 * <p>Calls LLM to generate type-annotated (LEX / VEC / HYDE) expanded queries based on information gaps,
 * which can be routed to BM25 or vector search channels downstream according to {@link QueryType}.
 *
 */
public class LlmTypedQueryExpander implements TypedQueryExpander {

    private static final Logger log = LoggerFactory.getLogger(LlmTypedQueryExpander.class);

    private final StructuredChatClient structuredChatClient;
    private final PromptRegistry promptRegistry;

    public LlmTypedQueryExpander(StructuredChatClient structuredChatClient) {
        this(structuredChatClient, PromptRegistry.EMPTY);
    }

    public LlmTypedQueryExpander(
            StructuredChatClient structuredChatClient, PromptRegistry promptRegistry) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
        this.promptRegistry =
                Objects.requireNonNull(promptRegistry, "promptRegistry must not be null");
    }

    @Override
    public Mono<List<ExpandedQuery>> expand(
            String query,
            List<String> gaps,
            List<String> keyInformation,
            List<String> conversationHistory,
            int maxExpansions) {
        return Mono.defer(
                        () -> {
                            var promptResult =
                                    TypedQueryExpandPrompts.build(
                                                    promptRegistry,
                                                    query,
                                                    gaps,
                                                    keyInformation,
                                                    conversationHistory,
                                                    maxExpansions)
                                            .render("English");
                            var messages =
                                    ChatMessages.systemUser(
                                            promptResult.systemPrompt(), promptResult.userPrompt());
                            return structuredChatClient
                                    .call(messages, TypedExpandResponse.class)
                                    .map(response -> toExpandedQueries(response, maxExpansions))
                                    .switchIfEmpty(Mono.just(List.of()));
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(2)).maxBackoff(Duration.ofSeconds(10)))
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "Type-annotated query expansion failed, returning empty list",
                                    e);
                            return Mono.just(List.of());
                        });
    }

    private List<ExpandedQuery> toExpandedQueries(TypedExpandResponse response, int maxExpansions) {
        if (response == null || response.queries() == null || response.queries().isEmpty()) {
            return List.of();
        }
        return response.queries().stream()
                .filter(tq -> tq.text() != null && !tq.text().isBlank())
                .map(tq -> new ExpandedQuery(mapQueryType(tq.type()), tq.text()))
                .limit(maxExpansions)
                .toList();
    }

    private QueryType mapQueryType(String type) {
        if (type == null) {
            return QueryType.VEC;
        }
        return switch (type.toLowerCase()) {
            case "lex" -> QueryType.LEX;
            case "vec" -> QueryType.VEC;
            case "hyde" -> QueryType.HYDE;
            default -> QueryType.VEC;
        };
    }

    record TypedExpandResponse(List<TypedQuery> queries) {}

    record TypedQuery(String type, String text) {}
}
