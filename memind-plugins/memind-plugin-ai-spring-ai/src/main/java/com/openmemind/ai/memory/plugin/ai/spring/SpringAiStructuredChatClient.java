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
package com.openmemind.ai.memory.plugin.ai.spring;

import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Mono;

/**
 * StructuredChatClient implementation backed by Spring AI ChatClient.
 */
public class SpringAiStructuredChatClient implements StructuredChatClient {

    private final ChatClient chatClient;

    public SpringAiStructuredChatClient(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    @Override
    public Mono<String> call(List<ChatMessage> messages) {
        return Mono.fromCallable(() -> applyMessages(messages).call().content());
    }

    @Override
    public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
        return Mono.fromCallable(() -> applyMessages(messages).call().entity(responseType));
    }

    private ChatClient.ChatClientRequestSpec applyMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        var mapped = messages.stream().map(this::toChatMessage).toList();
        return chatClient.prompt().messages(mapped);
    }

    private Message toChatMessage(ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }
}
