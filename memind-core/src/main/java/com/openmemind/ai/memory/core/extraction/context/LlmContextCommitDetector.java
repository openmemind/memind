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
import com.openmemind.ai.memory.core.prompt.extraction.rawdata.BoundaryDetectionPrompts;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.time.Duration;
import java.time.Instant;
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

    public LlmContextCommitDetector(CommitDetectorConfig config) {
        this(config, null);
    }

    public LlmContextCommitDetector(
            CommitDetectorConfig config, StructuredChatClient structuredChatClient) {
        this.config = Objects.requireNonNull(config, "config is required");
        this.structuredChatClient = structuredChatClient;
    }

    @Override
    public Mono<CommitDecision> shouldCommit(List<Message> buffer, CommitDetectionContext context) {
        if (buffer == null || buffer.isEmpty()) {
            return Mono.just(CommitDecision.hold());
        }

        if (buffer.size() >= config.maxMessages()) {
            log.debug(
                    "L1 hard limit triggered: buffer.size()={} >= maxMessages={}",
                    buffer.size(),
                    config.maxMessages());
            return Mono.just(CommitDecision.commit(1.0, "hard_limit"));
        }

        int tokens = TokenUtils.countTokens(buffer);
        if (tokens >= config.maxTokens()) {
            log.debug(
                    "L1.5 token limit triggered: tokens={} >= maxTokens={}",
                    tokens,
                    config.maxTokens());
            return Mono.just(CommitDecision.commit(1.0, "token_limit"));
        }

        boolean hasEnoughMessagesForLlm = buffer.size() >= config.minMessagesForLlm();
        if (!hasEnoughMessagesForLlm) {
            if (hasTimeGap(buffer, FALLBACK_TIME_GAP)) {
                log.debug(
                        "Time-gap fallback triggered before LLM threshold: gap between last two"
                                + " messages exceeds {}",
                        FALLBACK_TIME_GAP);
                return Mono.just(CommitDecision.commit(0.9, "time_gap"));
            }
            return Mono.just(CommitDecision.hold());
        }

        if (structuredChatClient != null) {
            log.debug("L3 LLM detection: buffer.size()={}", buffer.size());
            var enrichedContext = new CommitDetectionContext(computeTimeGap(buffer));
            return callLlm(buffer, enrichedContext)
                    .onErrorResume(
                            e -> {
                                log.warn(
                                        "LLM boundary detection failed, holding without sealing:"
                                                + " {}",
                                        e.getMessage());
                                return Mono.just(CommitDecision.hold());
                            });
        }

        if (hasTimeGap(buffer, FALLBACK_TIME_GAP)) {
            log.debug(
                    "Time-gap fallback triggered: gap between last two messages exceeds {}",
                    FALLBACK_TIME_GAP);
            return Mono.just(CommitDecision.commit(0.9, "time_gap"));
        }

        return Mono.just(CommitDecision.hold());
    }

    protected Mono<CommitDecision> callLlm(List<Message> buffer, CommitDetectionContext context) {
        var prompt = BoundaryDetectionPrompts.build(buffer, context).render(null);
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

    private Duration computeTimeGap(List<Message> buffer) {
        if (buffer.size() < 2) {
            return null;
        }
        Instant last = buffer.getLast().timestamp();
        Instant prev = buffer.get(buffer.size() - 2).timestamp();
        return (last == null || prev == null) ? null : Duration.between(prev, last);
    }

    private boolean hasTimeGap(List<Message> buffer, Duration threshold) {
        Duration gap = computeTimeGap(buffer);
        return gap != null && gap.compareTo(threshold) > 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static record LlmResponse(
            @JsonProperty("should_seal") boolean shouldSeal, double confidence, String reasoning) {}
}
