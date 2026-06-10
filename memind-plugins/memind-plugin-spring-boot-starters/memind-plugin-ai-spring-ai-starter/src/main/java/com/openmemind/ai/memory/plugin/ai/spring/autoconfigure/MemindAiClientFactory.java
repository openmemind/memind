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

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient;
import io.micrometer.observation.ObservationRegistry;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.StringUtils;

public final class MemindAiClientFactory {

    private static final String PROVIDER_OPENAI = "openai";
    private static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";
    private static final String PROVIDER_ANTHROPIC = "anthropic";
    private static final String PROVIDER_CLAUDE = "claude";
    private static final String PROVIDER_GEMINI = "gemini";
    private static final String PROVIDER_GOOGLE = "google";
    private static final String PROVIDER_GOOGLE_GENAI = "google-genai";
    private static final String PROVIDER_OLLAMA = "ollama";

    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;
    private final ToolCallingManager toolCallingManager;

    public MemindAiClientFactory(
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry,
            ToolCallingManager toolCallingManager) {
        this.retryTemplate = Objects.requireNonNull(retryTemplate, "retryTemplate");
        this.observationRegistry =
                Objects.requireNonNull(observationRegistry, "observationRegistry");
        this.toolCallingManager = Objects.requireNonNull(toolCallingManager, "toolCallingManager");
    }

    public MemindChatClients createChatClients(MemindAiProperties.ChatProperties properties) {
        Map<String, MemindAiProperties.ClientProperties> configuredClients =
                emptySafe(properties.getClients());
        if (configuredClients.isEmpty()) {
            throw new MemindAiConfigurationException("memind.ai.chat.clients must not be empty");
        }
        String defaultClientId =
                requireText(
                        properties.getDefaultClient(),
                        "memind.ai.chat.default-client must not be blank");

        Map<String, StructuredChatClient> clients = new LinkedHashMap<>();
        configuredClients.forEach(
                (clientId, clientProperties) ->
                        clients.put(
                                clientId,
                                new SpringAiStructuredChatClient(
                                        ChatClient.create(
                                                createChatModel(
                                                        "memind.ai.chat.clients." + clientId,
                                                        clientProperties)))));

        StructuredChatClient defaultClient = clients.get(defaultClientId);
        if (defaultClient == null) {
            throw new MemindAiConfigurationException(
                    "memind.ai.chat.default-client '"
                            + defaultClientId
                            + "' does not match any configured client");
        }

        Map<ChatClientSlot, String> slotClientIds = new EnumMap<>(ChatClientSlot.class);
        Map<ChatClientSlot, StructuredChatClient> slotClients = new EnumMap<>(ChatClientSlot.class);
        emptySafe(properties.getSlots())
                .forEach(
                        (slot, clientId) -> {
                            String resolvedClientId =
                                    requireText(
                                            clientId,
                                            "memind.ai.chat.slots." + slot + " must not be blank");
                            StructuredChatClient client = clients.get(resolvedClientId);
                            if (client == null) {
                                throw new MemindAiConfigurationException(
                                        "memind.ai.chat.slots."
                                                + slot
                                                + " references unknown client '"
                                                + resolvedClientId
                                                + "'");
                            }
                            slotClientIds.put(slot, resolvedClientId);
                            slotClients.put(slot, client);
                        });

        return new MemindChatClients(
                defaultClientId, defaultClient, clients, slotClientIds, slotClients);
    }

    public EmbeddingModel createEmbeddingModel(MemindAiProperties.EmbeddingProperties properties) {
        Map<String, MemindAiProperties.ClientProperties> configuredClients =
                emptySafe(properties.getClients());
        if (configuredClients.isEmpty()) {
            throw new MemindAiConfigurationException(
                    "memind.ai.embedding.clients must not be empty");
        }
        String clientId =
                requireText(properties.getClient(), "memind.ai.embedding.client must not be blank");
        MemindAiProperties.ClientProperties clientProperties = configuredClients.get(clientId);
        if (clientProperties == null) {
            throw new MemindAiConfigurationException(
                    "memind.ai.embedding.client '"
                            + clientId
                            + "' does not match any configured client");
        }
        return createEmbeddingModel("memind.ai.embedding.clients." + clientId, clientProperties);
    }

    private ChatModel createChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        String provider =
                normalizeProvider(
                        requireText(
                                properties.getProvider(), propertyPath + ".provider is required"));
        return switch (provider) {
            case PROVIDER_OPENAI, PROVIDER_OPENAI_COMPATIBLE ->
                    openAiChatModel(propertyPath, properties);
            case PROVIDER_ANTHROPIC, PROVIDER_CLAUDE ->
                    anthropicChatModel(propertyPath, properties);
            case PROVIDER_GEMINI, PROVIDER_GOOGLE, PROVIDER_GOOGLE_GENAI ->
                    googleGenAiChatModel(propertyPath, properties);
            case PROVIDER_OLLAMA -> ollamaChatModel(propertyPath, properties);
            default ->
                    throw new MemindAiConfigurationException(
                            propertyPath + ".provider '" + provider + "' is not supported");
        };
    }

    private EmbeddingModel createEmbeddingModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        String provider =
                normalizeProvider(
                        requireText(
                                properties.getProvider(), propertyPath + ".provider is required"));
        return switch (provider) {
            case PROVIDER_OPENAI, PROVIDER_OPENAI_COMPATIBLE ->
                    openAiEmbeddingModel(propertyPath, properties);
            case PROVIDER_GEMINI, PROVIDER_GOOGLE, PROVIDER_GOOGLE_GENAI ->
                    googleGenAiEmbeddingModel(propertyPath, properties);
            case PROVIDER_OLLAMA -> ollamaEmbeddingModel(propertyPath, properties);
            default ->
                    throw new MemindAiConfigurationException(
                            propertyPath
                                    + ".provider '"
                                    + provider
                                    + "' does not support embeddings");
        };
    }

    private ChatModel openAiChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OpenAiApi api =
                OpenAiApi.builder()
                        .baseUrl(requireText(properties.getBaseUrl(), propertyPath + ".base-url"))
                        .apiKey(requireText(properties.getApiKey(), propertyPath + ".api-key"))
                        .build();
        OpenAiChatOptions.Builder options =
                OpenAiChatOptions.builder()
                        .model(requireText(properties.getModel(), propertyPath + ".model"));
        if (properties.getTemperature() != null) {
            options.temperature(properties.getTemperature());
        }
        if (properties.getTopP() != null) {
            options.topP(properties.getTopP());
        }
        if (properties.getMaxTokens() != null) {
            options.maxTokens(properties.getMaxTokens());
        }
        if (properties.getMaxCompletionTokens() != null) {
            options.maxCompletionTokens(properties.getMaxCompletionTokens());
        }
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options.build())
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry)
                .build();
    }

    private EmbeddingModel openAiEmbeddingModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OpenAiApi api =
                OpenAiApi.builder()
                        .baseUrl(requireText(properties.getBaseUrl(), propertyPath + ".base-url"))
                        .apiKey(requireText(properties.getApiKey(), propertyPath + ".api-key"))
                        .build();
        OpenAiEmbeddingOptions.Builder options =
                OpenAiEmbeddingOptions.builder()
                        .model(requireText(properties.getModel(), propertyPath + ".model"));
        if (properties.getDimensions() != null) {
            options.dimensions(properties.getDimensions());
        }
        return new OpenAiEmbeddingModel(
                api, MetadataMode.EMBED, options.build(), retryTemplate, observationRegistry);
    }

    private ChatModel anthropicChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        AnthropicApi.Builder api =
                AnthropicApi.builder()
                        .apiKey(requireText(properties.getApiKey(), propertyPath + ".api-key"));
        if (StringUtils.hasText(properties.getBaseUrl())) {
            api.baseUrl(properties.getBaseUrl());
        }
        AnthropicChatOptions.Builder options =
                AnthropicChatOptions.builder()
                        .model(requireText(properties.getModel(), propertyPath + ".model"));
        if (properties.getTemperature() != null) {
            options.temperature(properties.getTemperature());
        }
        if (properties.getTopP() != null) {
            options.topP(properties.getTopP());
        }
        if (properties.getTopK() != null) {
            options.topK(properties.getTopK());
        }
        if (properties.getMaxTokens() != null) {
            options.maxTokens(properties.getMaxTokens());
        }
        return AnthropicChatModel.builder()
                .anthropicApi(api.build())
                .defaultOptions(options.build())
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry)
                .build();
    }

    private ChatModel googleGenAiChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        GoogleGenAiChatOptions.Builder options =
                GoogleGenAiChatOptions.builder()
                        .model(requireText(properties.getModel(), propertyPath + ".model"));
        if (properties.getTemperature() != null) {
            options.temperature(properties.getTemperature());
        }
        if (properties.getTopP() != null) {
            options.topP(properties.getTopP());
        }
        if (properties.getTopK() != null) {
            options.topK(properties.getTopK());
        }
        if (properties.getMaxTokens() != null) {
            options.maxOutputTokens(properties.getMaxTokens());
        }
        return GoogleGenAiChatModel.builder()
                .genAiClient(googleClient(propertyPath, properties))
                .defaultOptions(options.build())
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry)
                .build();
    }

    private EmbeddingModel googleGenAiEmbeddingModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        GoogleGenAiTextEmbeddingOptions.Builder options =
                GoogleGenAiTextEmbeddingOptions.builder()
                        .model(requireText(properties.getModel(), propertyPath + ".model"));
        if (properties.getDimensions() != null) {
            options.dimensions(properties.getDimensions());
        }
        GoogleGenAiEmbeddingConnectionDetails.Builder connectionDetailsBuilder =
                GoogleGenAiEmbeddingConnectionDetails.builder()
                        .apiKey(requireText(properties.getApiKey(), propertyPath + ".api-key"))
                        .genAiClient(googleClient(propertyPath, properties));
        if (StringUtils.hasText(properties.getProjectId())) {
            connectionDetailsBuilder.projectId(properties.getProjectId());
        }
        if (StringUtils.hasText(properties.getLocation())) {
            connectionDetailsBuilder.location(properties.getLocation());
        }
        GoogleGenAiEmbeddingConnectionDetails connectionDetails = connectionDetailsBuilder.build();
        return new GoogleGenAiTextEmbeddingModel(
                connectionDetails, options.build(), retryTemplate, observationRegistry);
    }

    private ChatModel ollamaChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OllamaChatOptions.Builder options =
                OllamaChatOptions.builder()
                        .model(requireText(properties.getModel(), propertyPath + ".model"));
        if (properties.getTemperature() != null) {
            options.temperature(properties.getTemperature());
        }
        if (properties.getTopP() != null) {
            options.topP(properties.getTopP());
        }
        if (properties.getTopK() != null) {
            options.topK(properties.getTopK());
        }
        if (properties.getMaxTokens() != null) {
            options.numPredict(properties.getMaxTokens());
        }
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi(propertyPath, properties))
                .defaultOptions(options.build())
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate)
                .observationRegistry(observationRegistry)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
    }

    private EmbeddingModel ollamaEmbeddingModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OllamaEmbeddingOptions.Builder options =
                OllamaEmbeddingOptions.builder()
                        .model(requireText(properties.getModel(), propertyPath + ".model"));
        if (properties.getDimensions() != null) {
            options.dimensions(properties.getDimensions());
        }
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi(propertyPath, properties))
                .defaultOptions(options.build())
                .observationRegistry(observationRegistry)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
    }

    private Client googleClient(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        Client.Builder builder =
                Client.builder()
                        .apiKey(requireText(properties.getApiKey(), propertyPath + ".api-key"));
        if (StringUtils.hasText(properties.getBaseUrl())) {
            builder.httpOptions(HttpOptions.builder().baseUrl(properties.getBaseUrl()).build());
        }
        if (StringUtils.hasText(properties.getProjectId())) {
            builder.project(properties.getProjectId());
        }
        if (StringUtils.hasText(properties.getLocation())) {
            builder.location(properties.getLocation());
        }
        return builder.build();
    }

    private OllamaApi ollamaApi(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OllamaApi.Builder builder = OllamaApi.builder();
        if (StringUtils.hasText(properties.getBaseUrl())) {
            builder.baseUrl(properties.getBaseUrl());
        } else {
            throw new MemindAiConfigurationException(propertyPath + ".base-url is required");
        }
        return builder.build();
    }

    static RetryTemplate defaultRetryTemplate() {
        return new RetryTemplate(RetryPolicy.withDefaults());
    }

    static ObservationRegistry defaultObservationRegistry() {
        return ObservationRegistry.NOOP;
    }

    static ToolCallingManager defaultToolCallingManager(ObservationRegistry observationRegistry) {
        return ToolCallingManager.builder().observationRegistry(observationRegistry).build();
    }

    private static String normalizeProvider(String provider) {
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String propertyPath) {
        if (!StringUtils.hasText(value)) {
            throw new MemindAiConfigurationException(propertyPath + " must not be blank");
        }
        return value;
    }

    private static <K, V> Map<K, V> emptySafe(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }
}
