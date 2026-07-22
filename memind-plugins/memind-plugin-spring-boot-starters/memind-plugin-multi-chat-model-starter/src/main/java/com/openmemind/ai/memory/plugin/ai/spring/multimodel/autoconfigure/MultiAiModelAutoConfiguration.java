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

import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.ChatModelProperties;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.properties.EmbeddingModelProperties;
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
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class MultiAiModelAutoConfiguration {

    private static final String CHAT_MODELS_PREFIX = "spring.ai.chat-models";
    private static final String EMBEDDING_MODELS_PREFIX = "spring.ai.embedding-models";
    private static final String CHAT_PRIMARY_MODEL_PROPERTY = "spring.ai.model.chat";
    private static final String EMBEDDING_PRIMARY_MODEL_PROPERTY = "spring.ai.model.embedding";
    private static final String DEFAULT_PRIMARY_MODEL_ID = "default";

    @Bean
    @Primary
    @Conditional(MultiAiChatModelsConfiguredCondition.class)
    static MultiChatModel multiChatModel(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory,
            ObjectProvider<MultiAiChatModelFactory> chatModelFactories) {
        Map<String, ChatModelProperties> chatModels =
                emptyIfNull(MultiAiModelPropertiesBinder.bind(environment).getChatModels());
        return new MultiChatModel(
                primaryModelId(environment, CHAT_PRIMARY_MODEL_PROPERTY),
                createChatModels(environment, beanFactory, chatModelFactories, chatModels));
    }

    @Bean
    @Primary
    @Conditional(MultiAiEmbeddingModelsConfiguredCondition.class)
    static MultiEmbeddingModel multiEmbeddingModel(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory,
            ObjectProvider<MultiAiEmbeddingModelFactory> embeddingModelFactories) {
        Map<String, EmbeddingModelProperties> embeddingModels =
                emptyIfNull(MultiAiModelPropertiesBinder.bind(environment).getEmbeddingModels());
        return new MultiEmbeddingModel(
                primaryModelId(environment, EMBEDDING_PRIMARY_MODEL_PROPERTY),
                createEmbeddingModels(
                        environment, beanFactory, embeddingModelFactories, embeddingModels));
    }

    @Bean
    @ConditionalOnClass(
            name = {
                "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
                "org.springframework.ai.openai.OpenAiChatModel"
            })
    MultiAiChatModelFactory openAiChatModelFactory() {
        return new OpenAiChatModelFactory();
    }

    @Bean
    @ConditionalOnClass(
            name = {
                "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
                "org.springframework.ai.openai.OpenAiEmbeddingModel"
            })
    MultiAiEmbeddingModelFactory openAiEmbeddingModelFactory() {
        return new OpenAiEmbeddingModelFactory();
    }

    @Bean
    @ConditionalOnClass(
            name = {
                "org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration",
                "org.springframework.ai.anthropic.AnthropicChatModel"
            })
    MultiAiChatModelFactory anthropicChatModelFactory() {
        return new AnthropicChatModelFactory();
    }

    @Bean
    @ConditionalOnClass(
            name = {
                "com.google.genai.Client",
                "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration",
                "org.springframework.ai.google.genai.GoogleGenAiChatModel"
            })
    MultiAiChatModelFactory googleGenAiChatModelFactory() {
        return new GoogleGenAiChatModelFactory();
    }

    @Bean
    @ConditionalOnClass(
            name = {
                "org.springframework.ai.model.google.genai.autoconfigure.embedding."
                        + "GoogleGenAiTextEmbeddingAutoConfiguration",
                "org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel"
            })
    MultiAiEmbeddingModelFactory googleGenAiEmbeddingModelFactory() {
        return new GoogleGenAiEmbeddingModelFactory();
    }

    private static Map<String, ChatModel> createChatModels(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory,
            ObjectProvider<MultiAiChatModelFactory> chatModelFactories,
            Map<String, ChatModelProperties> models) {
        Map<String, ChatModel> chatModels = new LinkedHashMap<>();
        MultiAiModelProviderContext providerContext =
                new MultiAiModelProviderContext(environment, beanFactory);
        models.forEach(
                (modelId, properties) -> {
                    MultiAiChatModelProviderType provider =
                            requiredProviderType(properties.getType(), CHAT_MODELS_PREFIX, modelId);
                    String prefix =
                            modelProviderPrefix(
                                    CHAT_MODELS_PREFIX, modelId, provider.propertyValue());
                    chatModels.put(
                            modelId,
                            chatModelFactory(chatModelFactories, provider, modelId)
                                    .createChatModel(modelId, prefix, providerContext));
                });
        return chatModels;
    }

    private static MultiAiChatModelFactory chatModelFactory(
            ObjectProvider<MultiAiChatModelFactory> factories,
            MultiAiChatModelProviderType provider,
            String modelId) {
        return factories
                .orderedStream()
                .filter(factory -> factory.providerType() == provider)
                .findFirst()
                .orElseThrow(() -> missingChatFactoryException(provider, modelId));
    }

    private static Map<String, EmbeddingModel> createEmbeddingModels(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory,
            ObjectProvider<MultiAiEmbeddingModelFactory> embeddingModelFactories,
            Map<String, EmbeddingModelProperties> models) {
        Map<String, EmbeddingModel> embeddingModels = new LinkedHashMap<>();
        MultiAiModelProviderContext providerContext =
                new MultiAiModelProviderContext(environment, beanFactory);
        models.forEach(
                (modelId, properties) -> {
                    MultiAiEmbeddingModelProviderType provider =
                            requiredProviderType(
                                    properties.getType(), EMBEDDING_MODELS_PREFIX, modelId);
                    String prefix =
                            modelProviderPrefix(
                                    EMBEDDING_MODELS_PREFIX, modelId, provider.propertyValue());
                    embeddingModels.put(
                            modelId,
                            embeddingModelFactory(embeddingModelFactories, provider, modelId)
                                    .createEmbeddingModel(modelId, prefix, providerContext));
                });
        return embeddingModels;
    }

    private static MultiAiEmbeddingModelFactory embeddingModelFactory(
            ObjectProvider<MultiAiEmbeddingModelFactory> factories,
            MultiAiEmbeddingModelProviderType provider,
            String modelId) {
        return factories
                .orderedStream()
                .filter(factory -> factory.providerType() == provider)
                .findFirst()
                .orElseThrow(() -> missingEmbeddingFactoryException(provider, modelId));
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

    private static <T> T requiredProviderType(T provider, String collectionPrefix, String modelId) {
        String propertyPath = collectionPrefix + "." + modelId + ".type";
        if (provider == null) {
            throw new MultiAiModelConfigurationException(propertyPath + " must not be blank");
        }
        return provider;
    }

    private static String primaryModelId(Environment environment, String propertyName) {
        String configuredModelId = environment.getProperty(propertyName);
        if (configuredModelId == null || configuredModelId.isBlank()) {
            return DEFAULT_PRIMARY_MODEL_ID;
        }
        return configuredModelId.trim();
    }

    private static String modelProviderPrefix(
            String collectionPrefix, String modelId, String providerValue) {
        String dottedPrefix = collectionPrefix + "." + modelId + "." + providerValue;
        if (ConfigurationPropertyName.isValid(dottedPrefix)) {
            return dottedPrefix;
        }
        String canonicalModelId = modelId.toLowerCase(Locale.ROOT);
        dottedPrefix = collectionPrefix + "." + canonicalModelId + "." + providerValue;
        if (ConfigurationPropertyName.isValid(dottedPrefix)) {
            return dottedPrefix;
        }
        return collectionPrefix + "[" + modelId + "]." + providerValue;
    }
}
