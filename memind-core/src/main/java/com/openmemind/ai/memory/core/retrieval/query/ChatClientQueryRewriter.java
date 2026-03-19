package com.openmemind.ai.memory.core.retrieval.query;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.prompt.retrieval.QueryRewritePrompts;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Query rewriter based on ChatClient
 *
 * <p>Call LLM to parse pronouns, supplement context, and generate query text more suitable for vector search
 *
 */
public class ChatClientQueryRewriter implements QueryRewriter {

    private final ChatClient chatClient;

    public ChatClientQueryRewriter(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    @Override
    public Mono<String> rewrite(MemoryId memoryId, String query, List<String> conversationHistory) {

        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return Mono.just(query);
        }

        var result = QueryRewritePrompts.build(query, conversationHistory).render("English");

        return Mono.fromCallable(
                        () -> {
                            var response =
                                    chatClient
                                            .prompt()
                                            .user(result.userPrompt())
                                            .call()
                                            .entity(RewriteResponse.class);
                            if (response != null
                                    && response.rewrittenQuery() != null
                                    && !response.rewrittenQuery().isBlank()) {
                                return response.rewrittenQuery();
                            }
                            return query;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(query);
    }

    private record RewriteResponse(String rewrittenQuery) {}
}
