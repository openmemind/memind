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

import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.MultiAiOpenAiChatProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiModelProviderContext;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.ProviderPropertyMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat.Type;
import org.springframework.ai.openai.OpenAiChatOptions.AudioParameters;
import org.springframework.ai.openai.OpenAiChatOptions.AudioParameters.AudioResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions.AudioParameters.Voice;
import org.springframework.ai.openai.OpenAiChatOptions.StreamOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;

public final class OpenAiChatModelFactory
        implements MultiAiChatModelFactory<MultiAiOpenAiChatProperties> {

    @Override
    public MultiAiChatModelProviderType providerType() {
        return MultiAiChatModelProviderType.OPENAI;
    }

    @Override
    public ChatModel createChatModel(
            String modelId,
            MultiAiOpenAiChatProperties properties,
            MultiAiModelProviderContext context) {
        OpenAiCommonProperties commonProperties = commonProperties(properties);
        OpenAiChatProperties chatProperties = chatProperties(properties);
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

    private static OpenAiCommonProperties commonProperties(MultiAiOpenAiChatProperties properties) {
        OpenAiCommonProperties target = new OpenAiCommonProperties();
        ProviderPropertyMapper.copyProperties(properties, target);
        return target;
    }

    private static OpenAiChatProperties chatProperties(MultiAiOpenAiChatProperties properties) {
        OpenAiChatProperties target = new OpenAiChatProperties();
        ProviderPropertyMapper.copyProperties(
                properties, target, "outputAudio", "responseFormat", "streamOptions");
        target.setOutputAudio(outputAudio(properties.getOutputAudio()));
        target.setResponseFormat(responseFormat(properties.getResponseFormat()));
        target.setStreamOptions(streamOptions(properties.getStreamOptions()));
        return target;
    }

    private static AudioParameters outputAudio(MultiAiOpenAiChatProperties.AudioParameters source) {
        if (source == null) {
            return null;
        }
        return new AudioParameters(
                ProviderPropertyMapper.convert(source.getVoice(), Voice.class),
                ProviderPropertyMapper.convert(source.getFormat(), AudioResponseFormat.class));
    }

    private static ResponseFormat responseFormat(
            MultiAiOpenAiChatProperties.ResponseFormat source) {
        if (source == null) {
            return null;
        }
        ResponseFormat target = new ResponseFormat();
        target.setType(ProviderPropertyMapper.convert(source.getType(), Type.class));
        target.setJsonSchema(source.getJsonSchema());
        return target;
    }

    private static StreamOptions streamOptions(MultiAiOpenAiChatProperties.StreamOptions source) {
        if (source == null) {
            return null;
        }
        return new StreamOptions(
                source.getIncludeObfuscation(),
                source.getIncludeUsage(),
                source.getAdditionalProperties());
    }
}
