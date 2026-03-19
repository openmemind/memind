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
package com.openmemind.ai.memory.core.retrieval.intent;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.prompt.retrieval.IntentRoutingPrompts;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Intent router based on ChatClient
 *
 * <p>Call LLM to determine whether the current query needs to retrieve memory
 *
 */
public class ChatClientIntentionRouter implements IntentionRouter {

    private final ChatClient chatClient;

    public ChatClientIntentionRouter(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    @Override
    public Mono<RetrievalIntent> route(
            MemoryId memoryId, String query, List<String> conversationHistory) {

        var result = IntentRoutingPrompts.build(query, conversationHistory).render("English");

        return Mono.fromCallable(
                        () -> {
                            var response =
                                    chatClient
                                            .prompt()
                                            .user(result.userPrompt())
                                            .call()
                                            .entity(IntentResponse.class);
                            if (response != null && "skip".equalsIgnoreCase(response.intent())) {
                                return RetrievalIntent.SKIP;
                            }
                            return RetrievalIntent.RETRIEVE;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(RetrievalIntent.RETRIEVE);
    }

    private record IntentResponse(String intent, String reason) {}
}
