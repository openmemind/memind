package com.openmemind.ai.memory.core.extraction.streaming;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.prompt.extraction.rawdata.BoundaryDetectionPrompts;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Default implementation of {@link BoundaryDetector}.
 *
 * <p>Evaluates whether the current message buffer should be sealed using a three-level cascade:
 *
 * <ul>
 *   <li><b>L1 — Hard limit:</b> seals immediately when message count reaches {@code maxMessages}.
 *   <li><b>L1.5 — Token limit:</b> seals when accumulated token count reaches {@code maxTokens},
 *       estimated via {@link TokenUtils}.
 *   <li><b>L3 — LLM topic shift:</b> calls the LLM to detect topic changes when buffer size
 *       reaches {@code minMessagesForLlm} (only when a {@link ChatClient} is provided). The time
 *       gap between the last two messages is included in the prompt for richer context.
 * </ul>
 *
 * <p>When no {@link ChatClient} is configured, a 30-minute time-gap fallback is used instead of
 * L3.
 */
public class DefaultBoundaryDetector implements BoundaryDetector {

    private static final Logger log = LoggerFactory.getLogger(DefaultBoundaryDetector.class);
    private static final Duration FALLBACK_TIME_GAP = Duration.ofMinutes(30);

    private final BoundaryDetectorConfig config;
    private final ChatClient chatClient;

    /** Creates a detector with hard-limit and time-gap fallback only (no LLM). */
    public DefaultBoundaryDetector(BoundaryDetectorConfig config) {
        this(config, null);
    }

    /** Creates a detector with LLM-based topic-shift detection (L3) enabled. */
    public DefaultBoundaryDetector(BoundaryDetectorConfig config, ChatClient chatClient) {
        this.config = Objects.requireNonNull(config, "config is required");
        this.chatClient = chatClient;
    }

    @Override
    public Mono<BoundaryDecision> shouldSeal(
            List<Message> buffer, BoundaryDetectionContext context) {
        if (buffer == null || buffer.isEmpty()) {
            return Mono.just(BoundaryDecision.hold());
        }

        // L1: Hard limit — message count
        if (buffer.size() >= config.maxMessages()) {
            log.debug(
                    "L1 hard limit triggered: buffer.size()={} >= maxMessages={}",
                    buffer.size(),
                    config.maxMessages());
            return Mono.just(BoundaryDecision.seal(1.0, "hard_limit"));
        }

        // L1.5: Token limit
        int tokens = TokenUtils.countTokens(buffer);
        if (tokens >= config.maxTokens()) {
            log.debug(
                    "L1.5 token limit triggered: tokens={} >= maxTokens={}",
                    tokens,
                    config.maxTokens());
            return Mono.just(BoundaryDecision.seal(1.0, "token_limit"));
        }

        // L3: LLM topic-shift detection
        if (chatClient != null && buffer.size() >= config.minMessagesForLlm()) {
            log.debug("L3 LLM detection: buffer.size()={}", buffer.size());
            var enrichedContext = new BoundaryDetectionContext(computeTimeGap(buffer));
            return callLlm(buffer, enrichedContext)
                    .onErrorResume(
                            e -> {
                                log.warn(
                                        "LLM boundary detection failed, holding without sealing:"
                                                + " {}",
                                        e.getMessage());
                                return Mono.just(BoundaryDecision.hold());
                            });
        }

        // Fallback (no LLM): seal on long time gap
        if (chatClient == null && hasTimeGap(buffer, FALLBACK_TIME_GAP)) {
            log.debug(
                    "Time-gap fallback triggered: gap between last two messages exceeds {}",
                    FALLBACK_TIME_GAP);
            return Mono.just(BoundaryDecision.seal(0.9, "time_gap"));
        }

        return Mono.just(BoundaryDecision.hold());
    }

    protected Mono<BoundaryDecision> callLlm(
            List<Message> buffer, BoundaryDetectionContext context) {
        var prompt = BoundaryDetectionPrompts.build(buffer, context).render(null);
        return Mono.fromCallable(
                        () ->
                                chatClient
                                        .prompt()
                                        .system(prompt.systemPrompt())
                                        .user(prompt.userPrompt())
                                        .call()
                                        .entity(LlmResponse.class))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toDecision);
    }

    private BoundaryDecision toDecision(LlmResponse response) {
        if (response == null) {
            return BoundaryDecision.hold();
        }
        return response.shouldSeal()
                ? BoundaryDecision.seal(response.confidence(), response.reasoning())
                : BoundaryDecision.hold();
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
    private record LlmResponse(
            @JsonProperty("should_seal") boolean shouldSeal, double confidence, String reasoning) {}
}
