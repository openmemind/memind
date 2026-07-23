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
package com.openmemind.ai.memory.plugin.ai.spring.autoconfigure;

import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.chat.MultiChatModel;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

public final class MemindAiClientFactory {

    private static final String FALLBACK_DEFAULT_CLIENT_ID = "default";

    private final ObjectProvider<ChatModel> chatModelProvider;

    private final ObjectProvider<MultiChatModel> multiChatModelProvider;

    public MemindAiClientFactory(
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<MultiChatModel> multiChatModelProvider) {
        this.chatModelProvider = Objects.requireNonNull(chatModelProvider, "chatModelProvider");
        this.multiChatModelProvider =
                Objects.requireNonNull(multiChatModelProvider, "multiChatModelProvider");
    }

    public MemindChatClients createChatClients(MemindAiProperties.ChatProperties properties) {
        ChatModel defaultChatModel = requireDefaultChatModel();
        MultiChatModel multiChatModel = requireMultiChatModel();
        String defaultClientId = defaultClientId(defaultChatModel);
        StructuredChatClient defaultClient = structuredChatClient(defaultChatModel);
        Map<ChatClientSlot, String> slotClientIds = new EnumMap<>(ChatClientSlot.class);
        Map<String, StructuredChatClient> clients = new LinkedHashMap<>();
        clients.put(defaultClientId, defaultClient);

        emptySafe(properties.getSlots())
                .forEach(
                        (slot, modelId) -> {
                            String resolvedModelId =
                                    requireText(
                                            modelId,
                                            "memind.ai.chat.slots." + slot + " must not be blank");
                            slotClientIds.put(slot, resolvedModelId);
                            clients.put(
                                    resolvedModelId,
                                    structuredChatClient(
                                            multiChatModel,
                                            resolvedModelId,
                                            "memind.ai.chat.slots." + slot));
                        });

        Map<ChatClientSlot, StructuredChatClient> slotClients = new EnumMap<>(ChatClientSlot.class);
        slotClientIds.forEach((slot, modelId) -> slotClients.put(slot, clients.get(modelId)));

        return new MemindChatClients(
                defaultClientId, defaultClient, clients, slotClientIds, slotClients);
    }

    private StructuredChatClient structuredChatClient(
            MultiChatModel multiChatModel, String modelId, String propertyPath) {
        try {
            ChatModel chatModel = multiChatModel.getChatModel(modelId);
            return structuredChatClient(chatModel);
        } catch (IllegalArgumentException exception) {
            throw new MemindAiConfigurationException(
                    propertyPath + " references missing chat model id '" + modelId + "'",
                    exception);
        }
    }

    private StructuredChatClient structuredChatClient(ChatModel chatModel) {
        return new SpringAiStructuredChatClient(ChatClient.create(chatModel));
    }

    private ChatModel requireDefaultChatModel() {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new MemindAiConfigurationException(
                    "memind.ai.chat.slots requires a ChatModel bean");
        }
        return chatModel;
    }

    private MultiChatModel requireMultiChatModel() {
        MultiChatModel multiChatModel = multiChatModelProvider.getIfAvailable();
        if (multiChatModel == null) {
            throw new MemindAiConfigurationException(
                    "memind.ai.chat.slots requires a MultiChatModel bean");
        }
        return multiChatModel;
    }

    private static String requireText(String value, String propertyPath) {
        if (value == null || value.isBlank()) {
            throw new MemindAiConfigurationException(propertyPath + " must not be blank");
        }
        return value;
    }

    private static <K, V> Map<K, V> emptySafe(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }

    private static String defaultClientId(ChatModel chatModel) {
        if (chatModel instanceof MultiChatModel multiChatModel) {
            return multiChatModel.getDefaultChatModelId();
        }
        if (chatModel.getOptions() != null
                && StringUtils.hasText(chatModel.getOptions().getModel())) {
            return chatModel.getOptions().getModel();
        }
        return FALLBACK_DEFAULT_CLIENT_ID;
    }
}
