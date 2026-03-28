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
package com.openmemind.ai.memory.autoconfigure;

import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for multi-LLM routing via {@link ChatClientRegistry}.
 */
@AutoConfiguration
@AutoConfigureAfter(
        name =
                "com.openmemind.ai.memory.plugin.ai.spring.autoconfigure.SpringAiLlmAutoConfiguration")
@EnableConfigurationProperties(MemoryLlmProperties.class)
public class MemoryLlmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StructuredChatClient.class)
    public ChatClientRegistry chatClientRegistry(
            Map<String, StructuredChatClient> allClients,
            MemoryLlmProperties properties,
            ConfigurableListableBeanFactory beanFactory) {

        StructuredChatClient defaultClient = resolveDefaultClient(allClients, beanFactory);
        Map<ChatClientSlot, StructuredChatClient> slotClients = new EnumMap<>(ChatClientSlot.class);

        for (var entry : properties.getSlots().entrySet()) {
            ChatClientSlot slot = parseSlot(entry.getKey());
            StructuredChatClient client = allClients.get(entry.getValue());
            if (client == null) {
                throw new IllegalArgumentException(
                        "No StructuredChatClient bean named '"
                                + entry.getValue()
                                + "' found for slot "
                                + slot
                                + ". Available beans: "
                                + allClients.keySet());
            }
            slotClients.put(slot, client);
        }

        return new ChatClientRegistry(defaultClient, slotClients);
    }

    private static ChatClientSlot parseSlot(String rawSlotName) {
        String slotName = rawSlotName.toUpperCase().replace("-", "_");
        try {
            return ChatClientSlot.valueOf(slotName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown ChatClientSlot: '"
                            + rawSlotName
                            + "'. Valid slots: "
                            + Arrays.toString(ChatClientSlot.values()),
                    e);
        }
    }

    private static StructuredChatClient resolveDefaultClient(
            Map<String, StructuredChatClient> allClients,
            ConfigurableListableBeanFactory beanFactory) {
        String primaryBeanName = null;
        for (String beanName : allClients.keySet()) {
            if (beanFactory.containsBeanDefinition(beanName)
                    && beanFactory.getBeanDefinition(beanName).isPrimary()) {
                if (primaryBeanName != null) {
                    throw new IllegalStateException(
                            "Multiple @Primary StructuredChatClient beans found: "
                                    + allClients.keySet());
                }
                primaryBeanName = beanName;
            }
        }
        if (primaryBeanName != null) {
            return allClients.get(primaryBeanName);
        }
        if (allClients.containsKey("structuredChatClient")) {
            return allClients.get("structuredChatClient");
        }
        if (allClients.containsKey("structuredLlmClient")) {
            return allClients.get("structuredLlmClient");
        }
        if (allClients.size() == 1) {
            return allClients.values().iterator().next();
        }
        throw new IllegalStateException(
                "Unable to determine the default StructuredChatClient bean. Mark one bean"
                        + " as @Primary or expose a bean named 'structuredChatClient'."
                        + " Available beans: "
                        + allClients.keySet());
    }
}
