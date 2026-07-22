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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.google;

import com.google.genai.Client;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.MultiAiModelConfigurationException;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiModelProviderContext;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatProperties;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.retry.RetryTemplate;

public final class GoogleGenAiChatModelFactory implements MultiAiChatModelFactory {

    @Override
    public MultiAiChatModelProviderType providerType() {
        return MultiAiChatModelProviderType.GOOGLE;
    }

    @Override
    public ChatModel createChatModel(
            String modelId, String providerPrefix, MultiAiModelProviderContext context) {
        GoogleGenAiConnectionProperties connectionProperties =
                context.bind(providerPrefix, GoogleGenAiConnectionProperties.class);
        GoogleGenAiChatProperties chatProperties =
                context.bind(providerPrefix, GoogleGenAiChatProperties.class);
        GoogleGenAiChatAutoConfiguration autoConfiguration = new GoogleGenAiChatAutoConfiguration();
        Client client;
        try {
            client = autoConfiguration.googleGenAiClient(connectionProperties);
        } catch (IOException exception) {
            throw new MultiAiModelConfigurationException(
                    "Failed to create Google GenAI chat model '" + modelId + "'", exception);
        }
        ObservationRegistry observationRegistry = context.observationRegistry();
        return autoConfiguration.googleGenAiChatModel(
                client,
                chatProperties,
                context.toolCallingManager(observationRegistry),
                context.provider(ApplicationContext.class).getIfAvailable(),
                context.provider(RetryTemplate.class),
                context.provider(ObservationRegistry.class),
                context.provider(ChatModelObservationConvention.class));
    }
}
