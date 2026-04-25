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
import com.openmemind.ai.memory.core.prompt.retrieval.LongQueryCondensePrompts;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class LlmLongQueryCondenser implements LongQueryCondenser {

    private static final Logger log = LoggerFactory.getLogger(LlmLongQueryCondenser.class);
    private static final int DEFAULT_MAX_INPUT_CHARS = 12000;
    private static final int HALF_WINDOW = DEFAULT_MAX_INPUT_CHARS / 2;

    private final StructuredChatClient structuredChatClient;
    private final PromptRegistry promptRegistry;

    public LlmLongQueryCondenser(StructuredChatClient structuredChatClient) {
        this(structuredChatClient, PromptRegistry.EMPTY);
    }

    public LlmLongQueryCondenser(
            StructuredChatClient structuredChatClient, PromptRegistry promptRegistry) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
        this.promptRegistry =
                Objects.requireNonNull(promptRegistry, "promptRegistry must not be null");
    }

    @Override
    public Mono<String> condense(
            MemoryId memoryId,
            String query,
            List<String> conversationHistory,
            int targetMaxTokens) {
        if (query == null || query.isBlank()) {
            return Mono.empty();
        }
        String boundedQuery = boundedInput(query);
        var rendered =
                LongQueryCondensePrompts.build(
                                promptRegistry,
                                boundedQuery,
                                conversationHistory == null ? List.of() : conversationHistory,
                                targetMaxTokens)
                        .render("English");
        var messages = ChatMessages.systemUser(rendered.systemPrompt(), rendered.userPrompt());
        return structuredChatClient
                .call(messages, CondenseResponse.class)
                .flatMap(
                        response -> {
                            if (response == null
                                    || response.condensedQuery() == null
                                    || response.condensedQuery().isBlank()) {
                                return Mono.empty();
                            }
                            return Mono.just(response.condensedQuery().trim());
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(
                        error -> {
                            log.warn("Long query condensation failed", error);
                            return Mono.empty();
                        });
    }

    private static String boundedInput(String query) {
        if (query.length() <= DEFAULT_MAX_INPUT_CHARS) {
            return query;
        }
        return query.substring(0, HALF_WINDOW)
                + "\n\n...[middle omitted for long-query condensation]...\n\n"
                + query.substring(query.length() - HALF_WINDOW);
    }

    private record CondenseResponse(String condensedQuery) {}
}
