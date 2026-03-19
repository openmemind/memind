package com.openmemind.ai.memory.core.retrieval.sufficiency;

import com.openmemind.ai.memory.core.prompt.retrieval.SufficiencyGatePrompts;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Sufficiency gating based on ChatClient (temporal awareness version)
 *
 */
public class ChatClientSufficiencyGate implements SufficiencyGate {

    private static final Logger log = LoggerFactory.getLogger(ChatClientSufficiencyGate.class);

    private final ChatClient chatClient;

    public ChatClientSufficiencyGate(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    @Override
    public Mono<SufficiencyResult> check(QueryContext context, List<ScoredResult> results) {
        if (results.isEmpty()) {
            return Mono.just(SufficiencyResult.fallbackInsufficient());
        }

        var promptResult = SufficiencyGatePrompts.build(context, results).render("English");

        return Mono.fromCallable(
                        () -> {
                            var response =
                                    chatClient
                                            .prompt()
                                            .user(promptResult.userPrompt())
                                            .call()
                                            .entity(SufficiencyResponse.class);
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
