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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.chat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public final class MultiChatModel implements ChatModel {

    private final String defaultChatModelId;

    private final ChatModel defaultChatModel;

    private final Map<String, ChatModel> chatModels;

    public MultiChatModel(String defaultChatModelId, Map<String, ChatModel> chatModels) {
        this.defaultChatModelId = requireModelId(defaultChatModelId);
        this.chatModels = copyChatModels(chatModels);
        this.defaultChatModel = this.chatModels.get(this.defaultChatModelId);
        if (this.defaultChatModel == null) {
            throw missingChatModel(this.defaultChatModelId);
        }
    }

    public String getDefaultChatModelId() {
        return defaultChatModelId;
    }

    public ChatModel getDefaultChatModel() {
        return defaultChatModel;
    }

    public ChatModel getChatModel(String modelId) {
        String requiredModelId = requireModelId(modelId);
        ChatModel chatModel = chatModels.get(requiredModelId);
        if (chatModel == null) {
            throw missingChatModel(requiredModelId);
        }
        return chatModel;
    }

    public Map<String, ChatModel> getChatModels() {
        return chatModels;
    }

    @Override
    public @NonNull ChatResponse call(@NonNull Prompt prompt) {
        return defaultChatModel.call(prompt);
    }

    @Override
    public @NonNull Flux<ChatResponse> stream(@NonNull Prompt prompt) {
        return defaultChatModel.stream(prompt);
    }

    @Override
    public @NonNull ChatOptions getOptions() {
        return defaultChatModel.getOptions();
    }

    private static Map<String, ChatModel> copyChatModels(Map<String, ChatModel> chatModels) {
        if (chatModels == null || chatModels.isEmpty()) {
            throw new IllegalArgumentException("chatModels must not be empty");
        }
        Map<String, ChatModel> copiedChatModels = new LinkedHashMap<>();
        chatModels.forEach(
                (modelId, chatModel) ->
                        copiedChatModels.put(
                                requireModelId(modelId),
                                Objects.requireNonNull(
                                        chatModel,
                                        "chatModel must not be null for model id '"
                                                + modelId
                                                + "'")));
        return Collections.unmodifiableMap(copiedChatModels);
    }

    private static String requireModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("chat model id must not be blank");
        }
        return modelId;
    }

    private static IllegalArgumentException missingChatModel(String modelId) {
        return new IllegalArgumentException("No chat model configured with id '" + modelId + "'");
    }
}
