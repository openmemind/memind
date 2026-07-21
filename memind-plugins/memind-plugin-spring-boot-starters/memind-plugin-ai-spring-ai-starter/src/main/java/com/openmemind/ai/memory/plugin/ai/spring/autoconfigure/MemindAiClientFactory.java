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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public final class MemindAiClientFactory {

    private final ConfigurableListableBeanFactory beanFactory;

    public MemindAiClientFactory(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory");
    }

    public MemindChatClients createChatClients(MemindAiProperties.ChatProperties properties) {
        String defaultBeanName = requireText(properties.getDefault(), "memind.ai.chat.default");

        Map<ChatClientSlot, String> slotClientIds = new EnumMap<>(ChatClientSlot.class);
        Map<String, StructuredChatClient> clients = new LinkedHashMap<>();
        clients.put(
                defaultBeanName, structuredChatClient(defaultBeanName, "memind.ai.chat.default"));

        emptySafe(properties.getSlots())
                .forEach(
                        (slot, beanName) -> {
                            String resolvedBeanName =
                                    requireText(
                                            beanName,
                                            "memind.ai.chat.slots." + slot + " must not be blank");
                            slotClientIds.put(slot, resolvedBeanName);
                            clients.computeIfAbsent(
                                    resolvedBeanName,
                                    ignored ->
                                            structuredChatClient(
                                                    resolvedBeanName,
                                                    "memind.ai.chat.slots." + slot));
                        });

        StructuredChatClient defaultClient = clients.get(defaultBeanName);
        Map<ChatClientSlot, StructuredChatClient> slotClients = new EnumMap<>(ChatClientSlot.class);
        slotClientIds.forEach((slot, beanName) -> slotClients.put(slot, clients.get(beanName)));

        return new MemindChatClients(
                defaultBeanName, defaultClient, clients, slotClientIds, slotClients);
    }

    public EmbeddingModel createEmbeddingModel(MemindAiProperties.EmbeddingProperties properties) {
        String beanName = requireText(properties.getDefault(), "memind.ai.embedding.default");
        Object bean = bean(beanName, "memind.ai.embedding.default");
        if (bean instanceof EmbeddingModel embeddingModel) {
            return embeddingModel;
        }
        throw new MemindAiConfigurationException(
                "memind.ai.embedding.default references bean '"
                        + beanName
                        + "' that is not a Spring AI EmbeddingModel");
    }

    private StructuredChatClient structuredChatClient(String beanName, String propertyPath) {
        Object bean = bean(beanName, propertyPath);
        if (bean instanceof StructuredChatClient structuredChatClient) {
            return structuredChatClient;
        }
        if (bean instanceof ChatClient chatClient) {
            return new SpringAiStructuredChatClient(chatClient);
        }
        if (bean instanceof ChatModel chatModel) {
            return new SpringAiStructuredChatClient(ChatClient.create(chatModel));
        }
        throw new MemindAiConfigurationException(
                propertyPath
                        + " references bean '"
                        + beanName
                        + "' that is not a Spring AI ChatClient or ChatModel");
    }

    private Object bean(String beanName, String propertyPath) {
        try {
            return beanFactory.getBean(beanName);
        } catch (NoSuchBeanDefinitionException e) {
            throw missingBeanException(beanName, propertyPath, e);
        }
    }

    private MemindAiConfigurationException missingBeanException(
            String beanName, String propertyPath, NoSuchBeanDefinitionException cause) {
        if (propertyPath.startsWith("memind.ai.embedding")) {
            return new MemindAiConfigurationException(
                    propertyPath
                            + " references missing Spring AI EmbeddingModel bean '"
                            + beanName
                            + "'",
                    cause);
        }
        return new MemindAiConfigurationException(
                propertyPath
                        + " references missing Spring AI ChatClient or ChatModel bean '"
                        + beanName
                        + "'",
                cause);
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
}
