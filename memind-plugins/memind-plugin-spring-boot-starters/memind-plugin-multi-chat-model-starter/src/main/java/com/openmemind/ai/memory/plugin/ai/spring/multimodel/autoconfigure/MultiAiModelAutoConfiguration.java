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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure;

import com.google.genai.Client;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.ChatModelProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.EmbeddingModelProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.MultiAiAnthropicChatProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.MultiAiGoogleGenAiChatProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.MultiAiGoogleGenAiEmbeddingProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.MultiAiModelProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.MultiAiOpenAiChatProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.MultiAiOpenAiEmbeddingProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiChatModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiEmbeddingModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiEmbeddingModelProviderType;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.MultiAiModelProviderContext;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.anthropic.AnthropicChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.google.GoogleGenAiChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.google.GoogleGenAiEmbeddingModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.openai.OpenAiChatModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider.openai.OpenAiEmbeddingModelFactory;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.chat.MultiChatModel;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.embedding.MultiEmbeddingModel;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
@EnableConfigurationProperties(MultiAiModelProperties.class)
public class MultiAiModelAutoConfiguration {

    private static final String CHAT_MODELS_PREFIX = "spring.ai.chat-models";
    private static final String EMBEDDING_MODELS_PREFIX = "spring.ai.embedding-models";

    @Bean
    @Primary
    @Conditional(MultiAiChatModelsConfiguredCondition.class)
    static MultiChatModel multiChatModel(
            MultiAiModelProperties properties,
            ConfigurableListableBeanFactory beanFactory,
            ObjectProvider<MultiAiChatModelFactory<?>> chatModelFactories) {
        return new MultiChatModel(
                properties.getModel().getChat(),
                createChatModels(
                        beanFactory, chatModelFactories, emptyIfNull(properties.getChatModels())));
    }

    @Bean
    @Primary
    @Conditional(MultiAiEmbeddingModelsConfiguredCondition.class)
    static MultiEmbeddingModel multiEmbeddingModel(
            MultiAiModelProperties properties,
            ConfigurableListableBeanFactory beanFactory,
            ObjectProvider<MultiAiEmbeddingModelFactory<?>> embeddingModelFactories) {
        return new MultiEmbeddingModel(
                properties.getModel().getEmbedding(),
                createEmbeddingModels(
                        beanFactory,
                        embeddingModelFactories,
                        emptyIfNull(properties.getEmbeddingModels())));
    }

    @Configuration
    @ConditionalOnClass({OpenAiChatAutoConfiguration.class, OpenAiChatModel.class})
    public static class OpenAiChatModelFactoryConfiguration {
        @Bean
        MultiAiChatModelFactory<MultiAiOpenAiChatProperties> openAiChatModelFactory() {
            return new OpenAiChatModelFactory();
        }
    }

    @Configuration
    @ConditionalOnClass({OpenAiEmbeddingAutoConfiguration.class, OpenAiEmbeddingModel.class})
    public static class OpenAiEmbeddingModelFactoryConfiguration {
        @Bean
        MultiAiEmbeddingModelFactory<MultiAiOpenAiEmbeddingProperties>
                openAiEmbeddingModelFactory() {
            return new OpenAiEmbeddingModelFactory();
        }
    }

    @Configuration
    @ConditionalOnClass({AnthropicChatAutoConfiguration.class, AnthropicChatModel.class})
    public static class AnthropicChatModelFactoryConfiguration {
        @Bean
        MultiAiChatModelFactory<MultiAiAnthropicChatProperties> anthropicChatModelFactory() {
            return new AnthropicChatModelFactory();
        }
    }

    @Configuration
    @ConditionalOnClass({
        Client.class,
        GoogleGenAiChatAutoConfiguration.class,
        GoogleGenAiChatModel.class
    })
    public static class GoogleGenAiChatModelFactoryConfiguration {

        @Bean
        MultiAiChatModelFactory<MultiAiGoogleGenAiChatProperties> googleGenAiChatModelFactory() {
            return new GoogleGenAiChatModelFactory();
        }
    }

    @ConditionalOnClass({
        Client.class,
        GoogleGenAiTextEmbeddingAutoConfiguration.class,
        GoogleGenAiTextEmbeddingModel.class
    })
    @Configuration
    public static class GoogleGenAiEmbeddingModelFactoryConfiguration {
        @Bean
        MultiAiEmbeddingModelFactory<MultiAiGoogleGenAiEmbeddingProperties>
                googleGenAiEmbeddingModelFactory() {
            return new GoogleGenAiEmbeddingModelFactory();
        }
    }

    private static Map<String, ChatModel> createChatModels(
            ConfigurableListableBeanFactory beanFactory,
            ObjectProvider<MultiAiChatModelFactory<?>> chatModelFactories,
            Map<String, ChatModelProperties> models) {
        Map<String, ChatModel> chatModels = new LinkedHashMap<>();
        MultiAiModelProviderContext providerContext = new MultiAiModelProviderContext(beanFactory);
        models.forEach(
                (modelId, properties) -> {
                    MultiAiChatModelProviderType provider = properties.requiredType(modelId);
                    chatModels.put(
                            modelId,
                            createChatModel(
                                    chatModelFactory(chatModelFactories, provider, modelId),
                                    modelId,
                                    chatProviderProperties(properties, provider),
                                    providerContext));
                });
        return chatModels;
    }

    private static MultiAiChatModelFactory<?> chatModelFactory(
            ObjectProvider<MultiAiChatModelFactory<?>> factories,
            MultiAiChatModelProviderType provider,
            String modelId) {
        return factories
                .orderedStream()
                .filter(factory -> factory.providerType() == provider)
                .findFirst()
                .orElseThrow(() -> missingChatFactoryException(provider, modelId));
    }

    @SuppressWarnings("unchecked")
    private static <T> ChatModel createChatModel(
            MultiAiChatModelFactory<T> factory,
            String modelId,
            Object properties,
            MultiAiModelProviderContext context) {
        return factory.createChatModel(modelId, (T) properties, context);
    }

    private static Object chatProviderProperties(
            ChatModelProperties properties, MultiAiChatModelProviderType provider) {
        return switch (provider) {
            case OPENAI -> properties.getOpenai();
            case ANTHROPIC -> properties.getAnthropic();
            case GOOGLE -> properties.getGoogle();
        };
    }

    private static Map<String, EmbeddingModel> createEmbeddingModels(
            ConfigurableListableBeanFactory beanFactory,
            ObjectProvider<MultiAiEmbeddingModelFactory<?>> embeddingModelFactories,
            Map<String, EmbeddingModelProperties> models) {
        Map<String, EmbeddingModel> embeddingModels = new LinkedHashMap<>();
        MultiAiModelProviderContext providerContext = new MultiAiModelProviderContext(beanFactory);
        models.forEach(
                (modelId, properties) -> {
                    MultiAiEmbeddingModelProviderType provider = properties.requiredType(modelId);
                    embeddingModels.put(
                            modelId,
                            createEmbeddingModel(
                                    embeddingModelFactory(
                                            embeddingModelFactories, provider, modelId),
                                    modelId,
                                    embeddingProviderProperties(properties, provider),
                                    providerContext));
                });
        return embeddingModels;
    }

    private static MultiAiEmbeddingModelFactory<?> embeddingModelFactory(
            ObjectProvider<MultiAiEmbeddingModelFactory<?>> factories,
            MultiAiEmbeddingModelProviderType provider,
            String modelId) {
        return factories
                .orderedStream()
                .filter(factory -> factory.providerType() == provider)
                .findFirst()
                .orElseThrow(() -> missingEmbeddingFactoryException(provider, modelId));
    }

    @SuppressWarnings("unchecked")
    private static <T> EmbeddingModel createEmbeddingModel(
            MultiAiEmbeddingModelFactory<T> factory,
            String modelId,
            Object properties,
            MultiAiModelProviderContext context) {
        return factory.createEmbeddingModel(modelId, (T) properties, context);
    }

    private static Object embeddingProviderProperties(
            EmbeddingModelProperties properties, MultiAiEmbeddingModelProviderType provider) {
        return switch (provider) {
            case OPENAI -> properties.getOpenai();
            case GOOGLE -> properties.getGoogle();
        };
    }

    private static MultiAiModelConfigurationException missingChatFactoryException(
            MultiAiChatModelProviderType provider, String modelId) {
        return new MultiAiModelConfigurationException(
                CHAT_MODELS_PREFIX
                        + "."
                        + modelId
                        + ".type="
                        + provider.propertyValue()
                        + " requires Spring AI "
                        + provider.displayName()
                        + " chat dependencies");
    }

    private static MultiAiModelConfigurationException missingEmbeddingFactoryException(
            MultiAiEmbeddingModelProviderType provider, String modelId) {
        return new MultiAiModelConfigurationException(
                EMBEDDING_MODELS_PREFIX
                        + "."
                        + modelId
                        + ".type="
                        + provider.propertyValue()
                        + " requires Spring AI "
                        + provider.displayName()
                        + " embedding dependencies");
    }

    private static <T> Map<String, T> emptyIfNull(Map<String, T> models) {
        return models != null ? models : Map.of();
    }
}
