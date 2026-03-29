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
package com.openmemind.ai.memory.core.retrieval.sufficiency;

import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.retrieval.SufficiencyGatePrompts;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Sufficiency gating based on StructuredChatClient (temporal awareness version)
 *
 */
public class LlmSufficiencyGate implements SufficiencyGate {

    private static final Logger log = LoggerFactory.getLogger(LlmSufficiencyGate.class);

    private final StructuredChatClient structuredChatClient;
    private final PromptRegistry promptRegistry;

    public LlmSufficiencyGate(StructuredChatClient structuredChatClient) {
        this(structuredChatClient, PromptRegistry.EMPTY);
    }

    public LlmSufficiencyGate(
            StructuredChatClient structuredChatClient, PromptRegistry promptRegistry) {
        this.structuredChatClient =
                Objects.requireNonNull(
                        structuredChatClient, "structuredChatClient must not be null");
        this.promptRegistry =
                Objects.requireNonNull(promptRegistry, "promptRegistry must not be null");
    }

    @Override
    public Mono<SufficiencyResult> check(QueryContext context, List<ScoredResult> results) {
        if (results.isEmpty()) {
            return Mono.just(SufficiencyResult.fallbackInsufficient());
        }

        var promptResult =
                SufficiencyGatePrompts.build(promptRegistry, context, results).render("English");
        var messages =
                ChatMessages.systemUser(promptResult.systemPrompt(), promptResult.userPrompt());

        return structuredChatClient
                .call(messages, SufficiencyResponse.class)
                .map(
                        response -> {
                            if (response == null) {
                                return SufficiencyResult.fallbackInsufficient();
                            }
                            return new SufficiencyResult(
                                    response.sufficient(),
                                    response.reasoning() != null ? response.reasoning() : "",
                                    response.evidences() != null ? response.evidences() : List.of(),
                                    response.gaps() != null ? response.gaps() : List.of(),
                                    response.keyInformation() != null
                                            ? response.keyInformation()
                                            : List.of());
                        })
                .switchIfEmpty(Mono.fromSupplier(SufficiencyResult::fallbackInsufficient))
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(2)).maxBackoff(Duration.ofSeconds(10)))
                .doOnError(
                        e ->
                                log.warn(
                                        "[SufficiencyGate] retries exhausted, falling back to"
                                                + " insufficient: {}",
                                        e.getMessage()))
                .onErrorReturn(SufficiencyResult.fallbackInsufficient());
    }

    private record SufficiencyResponse(
            boolean sufficient,
            String reasoning,
            List<String> evidences,
            List<String> gaps,
            List<String> keyInformation) {}
}
