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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.anthropic;

import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiModelProviderContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;

public final class AnthropicChatModelFactory implements MultiAiChatModelFactory {

    @Override
    public MultiAiChatModelProviderType providerType() {
        return MultiAiChatModelProviderType.ANTHROPIC;
    }

    @Override
    public ChatModel createChatModel(
            String modelId, String providerPrefix, MultiAiModelProviderContext context) {
        AnthropicConnectionProperties connectionProperties =
                context.bind(providerPrefix, AnthropicConnectionProperties.class);
        AnthropicChatProperties chatProperties =
                context.bind(providerPrefix, AnthropicChatProperties.class);
        ObservationRegistry observationRegistry = context.observationRegistry();
        return new AnthropicChatAutoConfiguration()
                .anthropicChatModel(
                        connectionProperties,
                        chatProperties,
                        context.toolCallingManager(observationRegistry),
                        context.provider(ObservationRegistry.class),
                        context.provider(MeterRegistry.class),
                        context.provider(ChatModelObservationConvention.class),
                        context.provider(AnthropicHttpClientBuilderCustomizer.class));
    }
}
