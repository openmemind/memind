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
package com.openmemind.ai.memory.core.extraction.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.extraction.rawdata.BoundaryDetectionPrompts;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * LLM-aware implementation of {@link ContextCommitDetector}.
 */
public class LlmContextCommitDetector implements ContextCommitDetector {

    private static final Logger log = LoggerFactory.getLogger(LlmContextCommitDetector.class);
    private static final Duration FALLBACK_TIME_GAP = Duration.ofMinutes(30);

    private final CommitDetectorConfig config;
    private final StructuredChatClient structuredChatClient;
    private final PromptRegistry promptRegistry;

    public LlmContextCommitDetector(CommitDetectorConfig config) {
        this(config, null, PromptRegistry.EMPTY);
    }

    public LlmContextCommitDetector(
            CommitDetectorConfig config, StructuredChatClient structuredChatClient) {
        this(config, structuredChatClient, PromptRegistry.EMPTY);
    }

    public LlmContextCommitDetector(
            CommitDetectorConfig config,
            StructuredChatClient structuredChatClient,
            PromptRegistry promptRegistry) {
        this.config = Objects.requireNonNull(config, "config is required");
        this.structuredChatClient = structuredChatClient;
        this.promptRegistry =
                Objects.requireNonNull(promptRegistry, "promptRegistry must not be null");
    }

    @Override
    public Mono<CommitDecision> shouldCommit(CommitDetectionInput input) {
        if (input == null || input.history().isEmpty() || input.incomingMessages().isEmpty()) {
            return Mono.just(CommitDecision.hold());
        }
        List<Message> candidateWindow = input.candidateWindow();

        if (candidateWindow.size() >= config.maxMessages()) {
            log.debug(
                    "L1 hard limit triggered: candidateWindow.size()={} >= maxMessages={}",
                    candidateWindow.size(),
                    config.maxMessages());
            return Mono.just(CommitDecision.commit(1.0, "hard_limit"));
        }

        int tokens = TokenUtils.countTokens(candidateWindow);
        if (tokens >= config.maxTokens()) {
            log.debug(
                    "L1.5 token limit triggered: tokens={} >= maxTokens={}",
                    tokens,
                    config.maxTokens());
            return Mono.just(CommitDecision.commit(1.0, "token_limit"));
        }

        Duration timeGap = input.timeGap();
        boolean hasEnoughMessagesForLlm = candidateWindow.size() >= config.minMessagesForLlm();
        if (!hasEnoughMessagesForLlm) {
            if (hasTimeGap(timeGap, FALLBACK_TIME_GAP)) {
                log.debug(
                        "Time-gap fallback triggered before LLM threshold: gap between history"
                                + " and incoming messages exceeds {}",
                        FALLBACK_TIME_GAP);
                return Mono.just(CommitDecision.commit(0.9, "time_gap"));
            }
            return Mono.just(CommitDecision.hold());
        }

        if (structuredChatClient != null) {
            log.debug(
                    "L3 LLM detection: history.size()={}, incoming.size()={}",
                    input.history().size(),
                    input.incomingMessages().size());
            var enrichedContext = new CommitDetectionContext(timeGap);
            return callLlm(input, enrichedContext)
                    .onErrorResume(
                            e -> {
                                log.warn(
                                        "LLM boundary detection failed, holding without sealing:"
                                                + " {}",
                                        e.getMessage());
                                return Mono.just(CommitDecision.hold());
                            });
        }

        if (hasTimeGap(timeGap, FALLBACK_TIME_GAP)) {
            log.debug(
                    "Time-gap fallback triggered: gap between history and incoming messages"
                            + " exceeds {}",
                    FALLBACK_TIME_GAP);
            return Mono.just(CommitDecision.commit(0.9, "time_gap"));
        }

        return Mono.just(CommitDecision.hold());
    }

    protected Mono<CommitDecision> callLlm(
            CommitDetectionInput input, CommitDetectionContext context) {
        var prompt =
                BoundaryDetectionPrompts.build(
                                promptRegistry, input.history(), input.incomingMessages(), context)
                        .render(null);
        var messages = ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt());
        return structuredChatClient
                .call(messages, LlmResponse.class)
                .map(this::toDecision)
                .switchIfEmpty(Mono.just(CommitDecision.hold()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private CommitDecision toDecision(LlmResponse response) {
        if (response == null) {
            return CommitDecision.hold();
        }
        return response.shouldSeal()
                ? CommitDecision.commit(response.confidence(), response.reasoning())
                : CommitDecision.hold();
    }

    private boolean hasTimeGap(Duration gap, Duration threshold) {
        return gap != null && gap.compareTo(threshold) > 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static record LlmResponse(
            @JsonProperty("should_seal") boolean shouldSeal, double confidence, String reasoning) {}
}
