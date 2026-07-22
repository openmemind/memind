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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.openai;

import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiModelProviderContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;

public final class OpenAiChatModelFactory implements MultiAiChatModelFactory {

    @Override
    public MultiAiChatModelProviderType providerType() {
        return MultiAiChatModelProviderType.OPENAI;
    }

    @Override
    public ChatModel createChatModel(
            String modelId, String providerPrefix, MultiAiModelProviderContext context) {
        OpenAiCommonProperties commonProperties =
                context.bind(providerPrefix, OpenAiCommonProperties.class);
        OpenAiChatProperties chatProperties =
                context.bind(providerPrefix, OpenAiChatProperties.class);
        ObservationRegistry observationRegistry = context.observationRegistry();
        return new OpenAiChatAutoConfiguration()
                .openAiChatModel(
                        commonProperties,
                        chatProperties,
                        context.toolCallingManager(observationRegistry),
                        context.provider(ObservationRegistry.class),
                        context.provider(MeterRegistry.class),
                        context.provider(ChatModelObservationConvention.class),
                        context.provider(OpenAiHttpClientBuilderCustomizer.class));
    }
}
