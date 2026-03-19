package com.openmemind.ai.memory.core.retrieval.tier;

import com.openmemind.ai.memory.core.prompt.retrieval.InsightTypeRoutingPrompts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Insight type router based on ChatClient
 *
 * <p>Calls LLM to select types related to the user query from the available BRANCH types.
 *
 */
public class ChatClientInsightTypeRouter implements InsightTypeRouter {

    private static final Logger log = LoggerFactory.getLogger(ChatClientInsightTypeRouter.class);

    private final ChatClient chatClient;

    public ChatClientInsightTypeRouter(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    @Override
    public Mono<List<String>> route(
            String query, List<String> conversationHistory, Map<String, String> availableTypes) {
        var typeNames = new ArrayList<>(availableTypes.keySet());
        return Mono.fromCallable(
                        () -> {
                            var promptResult =
                                    InsightTypeRoutingPrompts.build(
                                                    query, conversationHistory, availableTypes)
                                            .render("English");
                            var response =
                                    chatClient
                                            .prompt()
                                            .user(promptResult.userPrompt())
                                            .call()
                                            .entity(RoutingResponse.class);
                            return sanitize(response, typeNames);
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

    private record RoutingResponse(List<String> types) {}
}
