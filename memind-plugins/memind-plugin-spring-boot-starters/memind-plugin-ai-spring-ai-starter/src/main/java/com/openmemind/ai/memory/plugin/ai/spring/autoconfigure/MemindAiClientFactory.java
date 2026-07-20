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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient;
import com.openmemind.ai.memory.plugin.ai.spring.autoconfigure.MemindAiProperties.AiProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatProperties;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionProperties;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingProperties;
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
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.StringUtils;

public final class MemindAiClientFactory {

    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;
    private final ToolCallingManager toolCallingManager;
    private final MeterRegistry meterRegistry;
    private final ChatModelObservationConvention chatModelObservationConvention;
    private final EmbeddingModelObservationConvention embeddingModelObservationConvention;
    private final List<OpenAiHttpClientBuilderCustomizer> openAiHttpClientBuilderCustomizers;
    private final List<AnthropicHttpClientBuilderCustomizer> anthropicHttpClientBuilderCustomizers;
    private final Binder binder;
    private final Environment environment;

    public MemindAiClientFactory(
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry,
            ToolCallingManager toolCallingManager,
            MeterRegistry meterRegistry,
            ChatModelObservationConvention chatModelObservationConvention,
            EmbeddingModelObservationConvention embeddingModelObservationConvention,
            List<OpenAiHttpClientBuilderCustomizer> openAiHttpClientBuilderCustomizers,
            List<AnthropicHttpClientBuilderCustomizer> anthropicHttpClientBuilderCustomizers,
            Environment environment) {
        this.retryTemplate = Objects.requireNonNull(retryTemplate, "retryTemplate");
        this.observationRegistry =
                Objects.requireNonNull(observationRegistry, "observationRegistry");
        this.toolCallingManager = Objects.requireNonNull(toolCallingManager, "toolCallingManager");
        this.meterRegistry = meterRegistry;
        this.chatModelObservationConvention = chatModelObservationConvention;
        this.embeddingModelObservationConvention = embeddingModelObservationConvention;
        this.openAiHttpClientBuilderCustomizers =
                List.copyOf(
                        Objects.requireNonNull(
                                openAiHttpClientBuilderCustomizers,
                                "openAiHttpClientBuilderCustomizers"));
        this.anthropicHttpClientBuilderCustomizers =
                List.copyOf(
                        Objects.requireNonNull(
                                anthropicHttpClientBuilderCustomizers,
                                "anthropicHttpClientBuilderCustomizers"));
        this.environment = Objects.requireNonNull(environment, "environment");
        this.binder = Binder.get(environment);
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

        if (!configuredClients.containsKey(defaultClientId)) {
            throw new MemindAiConfigurationException(
                    "memind.ai.chat.default-client '"
                            + defaultClientId
                            + "' does not match any configured client");
        }

        Map<ChatClientSlot, String> slotClientIds = new EnumMap<>(ChatClientSlot.class);
        Map<String, MemindAiProperties.ClientProperties> referencedClients = new LinkedHashMap<>();
        referencedClients.put(defaultClientId, configuredClients.get(defaultClientId));
        emptySafe(properties.getSlots())
                .forEach(
                        (slot, clientId) -> {
                            String resolvedClientId =
                                    requireText(
                                            clientId,
                                            "memind.ai.chat.slots." + slot + " must not be blank");
                            MemindAiProperties.ClientProperties clientProperties =
                                    configuredClients.get(resolvedClientId);
                            if (clientProperties == null) {
                                throw new MemindAiConfigurationException(
                                        "memind.ai.chat.slots."
                                                + slot
                                                + " references unknown client '"
                                                + resolvedClientId
                                                + "'");
                            }
                            slotClientIds.put(slot, resolvedClientId);
                            referencedClients.putIfAbsent(resolvedClientId, clientProperties);
                        });

        Map<String, StructuredChatClient> clients = new LinkedHashMap<>();
        referencedClients.forEach(
                (clientId, clientProperties) ->
                        clients.put(
                                clientId,
                                new SpringAiStructuredChatClient(
                                        ChatClient.create(
                                                createChatModel(
                                                        "memind.ai.chat.clients." + clientId,
                                                        clientProperties)))));

        StructuredChatClient defaultClient = clients.get(defaultClientId);
        Map<ChatClientSlot, StructuredChatClient> slotClients = new EnumMap<>(ChatClientSlot.class);
        slotClientIds.forEach((slot, clientId) -> slotClients.put(slot, clients.get(clientId)));

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
        AiProvider provider = requireProvider(properties.getProvider(), propertyPath + ".provider");
        return switch (provider) {
            case OPENAI -> openAiChatModel(propertyPath, properties);
            case ANTHROPIC -> anthropicChatModel(propertyPath, properties);
            case GOOGLE -> googleGenAiChatModel(propertyPath, properties);
            case OLLAMA -> ollamaChatModel(propertyPath, properties);
        };
    }

    private EmbeddingModel createEmbeddingModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        AiProvider provider = requireProvider(properties.getProvider(), propertyPath + ".provider");
        return switch (provider) {
            case OPENAI -> openAiEmbeddingModel(propertyPath, properties);
            case GOOGLE -> googleGenAiEmbeddingModel(propertyPath, properties);
            case OLLAMA -> ollamaEmbeddingModel(propertyPath, properties);
            case ANTHROPIC ->
                    throw new MemindAiConfigurationException(
                            propertyPath
                                    + ".provider '"
                                    + provider.propertyValue()
                                    + "' does not support embeddings");
        };
    }

    private ChatModel openAiChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OpenAiCommonProperties commonProperties =
                bind("spring.ai.openai", OpenAiCommonProperties.class)
                        .orElseGet(OpenAiCommonProperties::new);
        OpenAiChatProperties chatProperties =
                bind("spring.ai.openai.chat", OpenAiChatProperties.class)
                        .orElseGet(OpenAiChatProperties::new);
        OpenAiAutoConfigurationUtil.ResolvedConnectionProperties connectionProperties =
                OpenAiAutoConfigurationUtil.resolveCommonProperties(
                        commonProperties, chatProperties);
        OpenAiChatOptions springOptions = chatProperties.toOptions();
        OpenAiChatOptions.Builder optionsBuilder = springOptions.mutate();
        applyOpenAiConnectionOptions(
                optionsBuilder, connectionProperties, properties, propertyPath);
        optionsBuilder.model(
                requireText(
                        firstText(
                                properties.getModel(),
                                springOptions.getModel(),
                                connectionProperties.getModel()),
                        propertyPath + ".model"));
        applyCommonChatOverrides(properties, optionsBuilder);
        if (properties.getMaxCompletionTokens() != null) {
            optionsBuilder.maxCompletionTokens(properties.getMaxCompletionTokens());
        }
        OpenAiChatOptions options = optionsBuilder.build();
        MeterRegistry meterRegistryToUse =
                connectionProperties.isConnectionPoolMetricsEnabled() ? meterRegistry : null;
        OpenAiChatModel.Builder builder =
                OpenAiChatModel.builder()
                        .options(options)
                        .toolCallingManager(toolCallingManager)
                        .observationRegistry(observationRegistry)
                        .meterRegistry(meterRegistryToUse)
                        .httpClientBuilderCustomizers(openAiHttpClientBuilderCustomizers);
        OpenAiChatModel model = builder.build();
        if (chatModelObservationConvention != null) {
            model.setObservationConvention(chatModelObservationConvention);
        }
        return model;
    }

    private EmbeddingModel openAiEmbeddingModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OpenAiCommonProperties commonProperties =
                bind("spring.ai.openai", OpenAiCommonProperties.class)
                        .orElseGet(OpenAiCommonProperties::new);
        OpenAiEmbeddingProperties embeddingProperties =
                bind("spring.ai.openai.embedding", OpenAiEmbeddingProperties.class)
                        .orElseGet(OpenAiEmbeddingProperties::new);
        OpenAiAutoConfigurationUtil.ResolvedConnectionProperties connectionProperties =
                OpenAiAutoConfigurationUtil.resolveCommonProperties(
                        commonProperties, embeddingProperties);
        OpenAiEmbeddingOptions springOptions = embeddingProperties.toOptions();
        OpenAiEmbeddingOptions.Builder optionsBuilder =
                OpenAiEmbeddingOptions.builder().from(springOptions);
        applyOpenAiConnectionOptions(
                optionsBuilder, connectionProperties, properties, propertyPath);
        optionsBuilder.model(
                requireText(
                        firstText(
                                properties.getModel(),
                                springOptions.getModel(),
                                connectionProperties.getModel()),
                        propertyPath + ".model"));
        if (properties.getDimensions() != null) {
            optionsBuilder.dimensions(properties.getDimensions());
        }
        OpenAiEmbeddingOptions options = optionsBuilder.build();
        OpenAiEmbeddingModel model =
                OpenAiEmbeddingModel.builder()
                        .metadataMode(
                                embeddingProperties.getMetadataMode() == null
                                        ? MetadataMode.EMBED
                                        : embeddingProperties.getMetadataMode())
                        .options(options)
                        .observationRegistry(observationRegistry)
                        .httpClientBuilderCustomizers(openAiHttpClientBuilderCustomizers)
                        .build();
        if (embeddingModelObservationConvention != null) {
            model.setObservationConvention(embeddingModelObservationConvention);
        }
        return model;
    }

    private void applyOpenAiConnectionOptions(
            OpenAiChatOptions.Builder optionsBuilder,
            OpenAiAutoConfigurationUtil.ResolvedConnectionProperties connectionProperties,
            MemindAiProperties.ClientProperties properties,
            String propertyPath) {
        optionsBuilder
                .baseUrl(
                        requireText(
                                firstText(
                                        properties.getBaseUrl(), connectionProperties.getBaseUrl()),
                                propertyPath + ".base-url"))
                .apiKey(
                        requireText(
                                firstText(properties.getApiKey(), connectionProperties.getApiKey()),
                                propertyPath + ".api-key"))
                .credential(connectionProperties.getCredential())
                .deploymentName(connectionProperties.getDeploymentName())
                .microsoftFoundryServiceVersion(
                        connectionProperties.getMicrosoftFoundryServiceVersion())
                .organizationId(connectionProperties.getOrganizationId())
                .microsoftFoundry(connectionProperties.isMicrosoftFoundry())
                .gitHubModels(connectionProperties.isGitHubModels())
                .timeout(connectionProperties.getTimeout())
                .maxRetries(connectionProperties.getMaxRetries())
                .proxy(connectionProperties.getProxy())
                .customHeaders(connectionProperties.getCustomHeaders());
    }

    private void applyOpenAiConnectionOptions(
            OpenAiEmbeddingOptions.Builder optionsBuilder,
            OpenAiAutoConfigurationUtil.ResolvedConnectionProperties connectionProperties,
            MemindAiProperties.ClientProperties properties,
            String propertyPath) {
        optionsBuilder
                .baseUrl(
                        requireText(
                                firstText(
                                        properties.getBaseUrl(), connectionProperties.getBaseUrl()),
                                propertyPath + ".base-url"))
                .apiKey(
                        requireText(
                                firstText(properties.getApiKey(), connectionProperties.getApiKey()),
                                propertyPath + ".api-key"))
                .credential(connectionProperties.getCredential())
                .deploymentName(connectionProperties.getDeploymentName())
                .microsoftFoundryServiceVersion(
                        connectionProperties.getMicrosoftFoundryServiceVersion())
                .organizationId(connectionProperties.getOrganizationId())
                .microsoftFoundry(connectionProperties.isMicrosoftFoundry())
                .gitHubModels(connectionProperties.isGitHubModels())
                .timeout(connectionProperties.getTimeout())
                .maxRetries(connectionProperties.getMaxRetries())
                .proxy(connectionProperties.getProxy())
                .customHeaders(connectionProperties.getCustomHeaders());
    }

    private ChatModel anthropicChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        AnthropicConnectionProperties connectionProperties =
                bind("spring.ai.anthropic", AnthropicConnectionProperties.class)
                        .orElseGet(AnthropicConnectionProperties::new);
        AnthropicChatProperties chatProperties =
                bind("spring.ai.anthropic.chat", AnthropicChatProperties.class)
                        .orElseGet(AnthropicChatProperties::new);
        AnthropicChatOptions springOptions = chatProperties.toOptions();
        AnthropicChatOptions.Builder optionsBuilder = springOptions.mutate();
        applyAnthropicConnectionOptions(
                optionsBuilder, connectionProperties, properties, propertyPath);
        optionsBuilder.model(
                requireText(
                        firstText(properties.getModel(), springOptions.getModel()),
                        propertyPath + ".model"));
        applyCommonChatOverrides(properties, optionsBuilder);
        AnthropicChatOptions options = optionsBuilder.build();
        MeterRegistry meterRegistryToUse =
                chatProperties.isConnectionPoolMetricsEnabled() ? meterRegistry : null;
        AnthropicChatModel.Builder builder =
                AnthropicChatModel.builder()
                        .options(options)
                        .toolCallingManager(toolCallingManager)
                        .observationRegistry(observationRegistry)
                        .meterRegistry(meterRegistryToUse)
                        .httpClientBuilderCustomizers(anthropicHttpClientBuilderCustomizers);
        AnthropicChatModel model = builder.build();
        if (chatModelObservationConvention != null) {
            model.setObservationConvention(chatModelObservationConvention);
        }
        return model;
    }

    private ChatModel googleGenAiChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        GoogleGenAiConnectionProperties connectionProperties =
                bind("spring.ai.google.genai", GoogleGenAiConnectionProperties.class)
                        .orElseGet(GoogleGenAiConnectionProperties::new);
        GoogleGenAiChatProperties chatProperties =
                bind("spring.ai.google.genai.chat", GoogleGenAiChatProperties.class)
                        .orElseGet(GoogleGenAiChatProperties::new);
        GoogleGenAiChatOptions springOptions = chatProperties.toOptions();
        GoogleGenAiChatOptions.Builder optionsBuilder = springOptions.mutate();
        optionsBuilder.model(
                requireText(
                        firstText(properties.getModel(), springOptions.getModel()),
                        propertyPath + ".model"));
        applyCommonChatOverrides(properties, optionsBuilder);
        if (properties.getMaxTokens() != null) {
            optionsBuilder.maxOutputTokens(properties.getMaxTokens());
        }
        GoogleGenAiChatOptions options = optionsBuilder.build();
        GoogleGenAiChatModel.Builder builder =
                GoogleGenAiChatModel.builder()
                        .genAiClient(googleClient(propertyPath, properties, connectionProperties))
                        .options(options)
                        .toolCallingManager(toolCallingManager)
                        .retryTemplate(retryTemplate)
                        .observationRegistry(observationRegistry);
        GoogleGenAiChatModel model = builder.build();
        if (chatModelObservationConvention != null) {
            model.setObservationConvention(chatModelObservationConvention);
        }
        return model;
    }

    private EmbeddingModel googleGenAiEmbeddingModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        GoogleGenAiConnectionProperties commonConnectionProperties =
                bind("spring.ai.google.genai", GoogleGenAiConnectionProperties.class)
                        .orElseGet(GoogleGenAiConnectionProperties::new);
        GoogleGenAiEmbeddingConnectionProperties connectionProperties =
                bind(
                                "spring.ai.google.genai.embedding",
                                GoogleGenAiEmbeddingConnectionProperties.class)
                        .orElseGet(GoogleGenAiEmbeddingConnectionProperties::new);
        GoogleGenAiTextEmbeddingProperties embeddingProperties =
                bind(
                                "spring.ai.google.genai.embedding.text",
                                GoogleGenAiTextEmbeddingProperties.class)
                        .orElseGet(GoogleGenAiTextEmbeddingProperties::new);
        GoogleGenAiTextEmbeddingOptions springOptions = embeddingProperties.toOptions();
        GoogleGenAiTextEmbeddingOptions.Builder optionsBuilder =
                GoogleGenAiTextEmbeddingOptions.builder().from(springOptions);
        optionsBuilder.model(
                requireText(
                        firstText(properties.getModel(), springOptions.getModel()),
                        propertyPath + ".model"));
        if (properties.getDimensions() != null) {
            optionsBuilder.dimensions(properties.getDimensions());
        }
        GoogleGenAiTextEmbeddingOptions options = optionsBuilder.build();
        GoogleGenAiEmbeddingConnectionDetails connectionDetails =
                googleGenAiEmbeddingConnectionDetails(
                        propertyPath, properties, connectionProperties, commonConnectionProperties);
        GoogleGenAiTextEmbeddingModel model =
                new GoogleGenAiTextEmbeddingModel(
                        connectionDetails, options, retryTemplate, observationRegistry);
        if (embeddingModelObservationConvention != null) {
            model.setObservationConvention(embeddingModelObservationConvention);
        }
        return model;
    }

    private ChatModel ollamaChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OllamaChatOptions.Builder optionsBuilder = OllamaChatOptions.builder();
        applyCommonChatDefaults(
                optionsBuilder, "spring.ai.ollama.chat", "spring.ai.ollama.chat.options");
        applyOllamaChatDefaults(optionsBuilder);
        optionsBuilder.model(
                requireText(
                        firstText(
                                properties.getModel(),
                                property("spring.ai.ollama.chat.model"),
                                property("spring.ai.ollama.chat.options.model")),
                        propertyPath + ".model"));
        applyCommonChatOverrides(properties, optionsBuilder);
        if (properties.getMaxTokens() != null) {
            optionsBuilder.numPredict(properties.getMaxTokens());
        }
        OllamaChatOptions options = optionsBuilder.build();
        OllamaChatModel.Builder builder =
                OllamaChatModel.builder()
                        .ollamaApi(ollamaApi(propertyPath, properties))
                        .options(options)
                        .toolCallingManager(toolCallingManager)
                        .retryTemplate(retryTemplate)
                        .observationRegistry(observationRegistry)
                        .modelManagementOptions(ModelManagementOptions.defaults());
        return builder.build();
    }

    private EmbeddingModel ollamaEmbeddingModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OllamaEmbeddingOptions.Builder optionsBuilder = OllamaEmbeddingOptions.builder();
        optionsBuilder.model(
                requireText(
                        firstText(
                                properties.getModel(),
                                property("spring.ai.ollama.embedding.model"),
                                property("spring.ai.ollama.embedding.options.model")),
                        propertyPath + ".model"));
        Integer dimensions =
                firstNonNull(
                        properties.getDimensions(),
                        integerProperty("spring.ai.ollama.embedding.dimensions"),
                        integerProperty("spring.ai.ollama.embedding.options.dimensions"));
        if (dimensions != null) {
            optionsBuilder.dimensions(dimensions);
        }
        OllamaEmbeddingOptions options = optionsBuilder.build();
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi(propertyPath, properties))
                .options(options)
                .observationRegistry(observationRegistry)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
    }

    private Client googleClient(
            String propertyPath,
            MemindAiProperties.ClientProperties properties,
            GoogleGenAiConnectionProperties connectionProperties) {
        return googleClient(
                propertyPath,
                properties,
                connectionProperties.getApiKey(),
                connectionProperties.getProjectId(),
                connectionProperties.getLocation(),
                connectionProperties.getCredentialsUri(),
                connectionProperties.isVertexAi());
    }

    private GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(
            String propertyPath,
            MemindAiProperties.ClientProperties properties,
            GoogleGenAiEmbeddingConnectionProperties connectionProperties,
            GoogleGenAiConnectionProperties commonConnectionProperties) {
        GoogleConnectionSettings settings =
                googleConnectionSettings(
                        propertyPath,
                        properties,
                        firstText(
                                connectionProperties.getApiKey(),
                                commonConnectionProperties.getApiKey()),
                        firstText(
                                connectionProperties.getProjectId(),
                                commonConnectionProperties.getProjectId()),
                        firstText(
                                connectionProperties.getLocation(),
                                commonConnectionProperties.getLocation()),
                        firstNonNull(
                                connectionProperties.getCredentialsUri(),
                                commonConnectionProperties.getCredentialsUri()),
                        connectionProperties.isVertexAi()
                                || commonConnectionProperties.isVertexAi());

        GoogleGenAiEmbeddingConnectionDetails.Builder builder =
                GoogleGenAiEmbeddingConnectionDetails.builder().genAiClient(googleClient(settings));
        if (settings.useVertexAi()) {
            builder.projectId(settings.projectId()).location(settings.location());
        } else {
            builder.apiKey(settings.apiKey());
        }
        return builder.build();
    }

    private Client googleClient(
            String propertyPath,
            MemindAiProperties.ClientProperties properties,
            String defaultApiKey,
            String defaultProjectId,
            String defaultLocation,
            Resource defaultCredentialsUri,
            boolean defaultVertexAi) {
        return googleClient(
                googleConnectionSettings(
                        propertyPath,
                        properties,
                        defaultApiKey,
                        defaultProjectId,
                        defaultLocation,
                        defaultCredentialsUri,
                        defaultVertexAi));
    }

    private Client googleClient(GoogleConnectionSettings settings) {
        Client.Builder builder = Client.builder();
        if (settings.useVertexAi()) {
            configureGoogleVertexAi(builder, settings);
        } else {
            builder.apiKey(settings.apiKey());
        }
        String baseUrl = settings.baseUrl();
        if (StringUtils.hasText(baseUrl)) {
            builder.httpOptions(HttpOptions.builder().baseUrl(baseUrl).build());
        }
        return builder.build();
    }

    private GoogleConnectionSettings googleConnectionSettings(
            String propertyPath,
            MemindAiProperties.ClientProperties properties,
            String defaultApiKey,
            String defaultProjectId,
            String defaultLocation,
            Resource defaultCredentialsUri,
            boolean defaultVertexAi) {
        String apiKey = firstText(properties.getApiKey(), defaultApiKey);
        String projectId = firstText(properties.getProjectId(), defaultProjectId);
        String location = firstText(properties.getLocation(), defaultLocation);
        boolean hasApiKey = StringUtils.hasText(apiKey);
        boolean hasVertexConfig = StringUtils.hasText(projectId) && StringUtils.hasText(location);
        boolean useVertexAi = defaultVertexAi || (!hasApiKey && hasVertexConfig);
        if (useVertexAi) {
            projectId = requireText(projectId, propertyPath + ".project-id");
            location = requireText(location, propertyPath + ".location");
        } else if (!hasApiKey) {
            throw new MemindAiConfigurationException(
                    propertyPath + ".api-key or project-id/location must be configured");
        }
        return new GoogleConnectionSettings(
                apiKey,
                projectId,
                location,
                defaultCredentialsUri,
                useVertexAi,
                firstText(properties.getBaseUrl(), property("spring.ai.google.genai.base-url")));
    }

    private void configureGoogleVertexAi(
            Client.Builder builder, GoogleConnectionSettings settings) {
        builder.project(settings.projectId()).location(settings.location()).vertexAI(true);
        Resource credentialsUri = settings.credentialsUri();
        if (credentialsUri == null) {
            return;
        }
        try (InputStream inputStream = credentialsUri.getInputStream()) {
            builder.credentials(GoogleCredentials.fromStream(inputStream));
        } catch (IOException e) {
            throw new MemindAiConfigurationException(
                    "Failed to load Google GenAI credentials from " + credentialsUri, e);
        }
    }

    private OllamaApi ollamaApi(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OllamaApi.Builder builder = OllamaApi.builder();
        String baseUrl = firstText(properties.getBaseUrl(), property("spring.ai.ollama.base-url"));
        if (StringUtils.hasText(baseUrl)) {
            builder.baseUrl(baseUrl);
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

    private <T> BindResult<T> bind(String name, Class<T> type) {
        return binder.bind(name, Bindable.of(type));
    }

    private String property(String name) {
        return environment.getProperty(name);
    }

    private Double doubleProperty(String name) {
        return environment.getProperty(name, Double.class);
    }

    private Integer integerProperty(String name) {
        return environment.getProperty(name, Integer.class);
    }

    private void applyAnthropicConnectionOptions(
            AnthropicChatOptions.Builder optionsBuilder,
            AnthropicConnectionProperties connectionProperties,
            MemindAiProperties.ClientProperties properties,
            String propertyPath) {
        String apiKey = firstText(properties.getApiKey(), connectionProperties.getApiKey());
        optionsBuilder.apiKey(requireText(apiKey, propertyPath + ".api-key"));

        String baseUrl = firstText(properties.getBaseUrl(), connectionProperties.getBaseUrl());
        if (StringUtils.hasText(baseUrl)) {
            optionsBuilder.baseUrl(baseUrl);
        }
        if (connectionProperties.getTimeout() != null) {
            optionsBuilder.timeout(connectionProperties.getTimeout());
        }
        if (connectionProperties.getMaxRetries() != null) {
            optionsBuilder.maxRetries(connectionProperties.getMaxRetries());
        }
        if (connectionProperties.getProxy() != null) {
            optionsBuilder.proxy(connectionProperties.getProxy());
        }
        if (!connectionProperties.getCustomHeaders().isEmpty()) {
            optionsBuilder.customHeaders(connectionProperties.getCustomHeaders());
        }
    }

    private void applyCommonChatDefaults(
            ChatOptions.Builder<?> optionsBuilder, String prefix, String deprecatedPrefix) {
        Double temperature =
                firstNonNull(
                        doubleProperty(prefix + ".temperature"),
                        doubleProperty(deprecatedPrefix + ".temperature"));
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        Double topP =
                firstNonNull(
                        doubleProperty(prefix + ".top-p"),
                        doubleProperty(deprecatedPrefix + ".top-p"));
        if (topP != null) {
            optionsBuilder.topP(topP);
        }
        Integer topK =
                firstNonNull(
                        integerProperty(prefix + ".top-k"),
                        integerProperty(deprecatedPrefix + ".top-k"));
        if (topK != null) {
            optionsBuilder.topK(topK);
        }
        Integer maxTokens =
                firstNonNull(
                        integerProperty(prefix + ".max-tokens"),
                        integerProperty(deprecatedPrefix + ".max-tokens"));
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }
    }

    private void applyOllamaChatDefaults(OllamaChatOptions.Builder optionsBuilder) {
        Integer numPredict =
                firstNonNull(
                        integerProperty("spring.ai.ollama.chat.num-predict"),
                        integerProperty("spring.ai.ollama.chat.options.num-predict"));
        if (numPredict != null) {
            optionsBuilder.numPredict(numPredict);
        }
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static void applyCommonChatOverrides(
            MemindAiProperties.ClientProperties properties, ChatOptions.Builder<?> optionsBuilder) {
        if (properties.getTemperature() != null) {
            optionsBuilder.temperature(properties.getTemperature());
        }
        if (properties.getTopP() != null) {
            optionsBuilder.topP(properties.getTopP());
        }
        if (properties.getTopK() != null) {
            optionsBuilder.topK(properties.getTopK());
        }
        if (properties.getMaxTokens() != null) {
            optionsBuilder.maxTokens(properties.getMaxTokens());
        }
    }

    private static AiProvider requireProvider(AiProvider provider, String propertyPath) {
        if (provider == null) {
            throw new MemindAiConfigurationException(propertyPath + " is required");
        }
        return provider;
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

    private record GoogleConnectionSettings(
            String apiKey,
            String projectId,
            String location,
            Resource credentialsUri,
            boolean useVertexAi,
            String baseUrl) {}
}
