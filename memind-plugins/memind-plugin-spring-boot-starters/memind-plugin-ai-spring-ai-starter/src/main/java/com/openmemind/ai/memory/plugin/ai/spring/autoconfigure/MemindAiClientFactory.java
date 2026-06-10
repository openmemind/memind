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
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
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
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

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
    private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;
    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ResponseErrorHandler responseErrorHandler;
    private final Binder binder;
    private final Environment environment;

    public MemindAiClientFactory(
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry,
            ToolCallingManager toolCallingManager,
            ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ResponseErrorHandler responseErrorHandler,
            Environment environment) {
        this.retryTemplate = Objects.requireNonNull(retryTemplate, "retryTemplate");
        this.observationRegistry =
                Objects.requireNonNull(observationRegistry, "observationRegistry");
        this.toolCallingManager = Objects.requireNonNull(toolCallingManager, "toolCallingManager");
        this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
        this.restClientBuilder = Objects.requireNonNull(restClientBuilder, "restClientBuilder");
        this.webClientBuilder = Objects.requireNonNull(webClientBuilder, "webClientBuilder");
        this.responseErrorHandler =
                Objects.requireNonNull(responseErrorHandler, "responseErrorHandler");
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
        OpenAiConnectionProperties connectionProperties =
                bind("spring.ai.openai", OpenAiConnectionProperties.class)
                        .orElseGet(OpenAiConnectionProperties::new);
        OpenAiChatProperties chatProperties =
                bind("spring.ai.openai.chat", OpenAiChatProperties.class)
                        .orElseGet(OpenAiChatProperties::new);
        String baseUrl =
                firstText(
                        properties.getBaseUrl(),
                        chatProperties.getBaseUrl(),
                        connectionProperties.getBaseUrl());
        String apiKey =
                firstText(
                        properties.getApiKey(),
                        chatProperties.getApiKey(),
                        connectionProperties.getApiKey());
        OpenAiChatOptions options = OpenAiChatOptions.fromOptions(chatProperties.getOptions());
        options.setModel(
                requireText(
                        firstText(properties.getModel(), options.getModel()),
                        propertyPath + ".model"));
        applyCommonChatOverrides(properties, options);
        OpenAiApi api =
                OpenAiApi.builder()
                        .baseUrl(requireText(baseUrl, propertyPath + ".base-url"))
                        .apiKey(requireText(apiKey, propertyPath + ".api-key"))
                        .headers(
                                openAiHeaders(
                                        chatProperties.getProjectId(),
                                        chatProperties.getOrganizationId(),
                                        connectionProperties))
                        .completionsPath(chatProperties.getCompletionsPath())
                        .restClientBuilder(restClientBuilder)
                        .webClientBuilder(webClientBuilder)
                        .responseErrorHandler(responseErrorHandler)
                        .build();
        OpenAiChatModel.Builder builder =
                OpenAiChatModel.builder()
                        .openAiApi(api)
                        .defaultOptions(options)
                        .toolCallingManager(toolCallingManager)
                        .retryTemplate(retryTemplate)
                        .observationRegistry(observationRegistry);
        if (toolExecutionEligibilityPredicate != null) {
            builder.toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate);
        }
        return builder.build();
    }

    private EmbeddingModel openAiEmbeddingModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OpenAiConnectionProperties connectionProperties =
                bind("spring.ai.openai", OpenAiConnectionProperties.class)
                        .orElseGet(OpenAiConnectionProperties::new);
        OpenAiEmbeddingProperties embeddingProperties =
                bind("spring.ai.openai.embedding", OpenAiEmbeddingProperties.class)
                        .orElseGet(OpenAiEmbeddingProperties::new);
        String baseUrl =
                firstText(
                        properties.getBaseUrl(),
                        embeddingProperties.getBaseUrl(),
                        connectionProperties.getBaseUrl());
        String apiKey =
                firstText(
                        properties.getApiKey(),
                        embeddingProperties.getApiKey(),
                        connectionProperties.getApiKey());
        OpenAiEmbeddingOptions options =
                copyOpenAiEmbeddingOptions(embeddingProperties.getOptions());
        options.setModel(
                requireText(
                        firstText(properties.getModel(), options.getModel()),
                        propertyPath + ".model"));
        if (properties.getDimensions() != null) {
            options.setDimensions(properties.getDimensions());
        }
        OpenAiApi api =
                OpenAiApi.builder()
                        .baseUrl(requireText(baseUrl, propertyPath + ".base-url"))
                        .apiKey(requireText(apiKey, propertyPath + ".api-key"))
                        .headers(
                                openAiHeaders(
                                        embeddingProperties.getProjectId(),
                                        embeddingProperties.getOrganizationId(),
                                        connectionProperties))
                        .embeddingsPath(embeddingProperties.getEmbeddingsPath())
                        .restClientBuilder(restClientBuilder)
                        .webClientBuilder(webClientBuilder)
                        .responseErrorHandler(responseErrorHandler)
                        .build();
        return new OpenAiEmbeddingModel(
                api,
                embeddingProperties.getMetadataMode() == null
                        ? MetadataMode.EMBED
                        : embeddingProperties.getMetadataMode(),
                options,
                retryTemplate,
                observationRegistry);
    }

    private ChatModel anthropicChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        AnthropicChatOptions options =
                bind("spring.ai.anthropic.chat.options", AnthropicChatOptions.class)
                        .orElseGet(AnthropicChatOptions::new);
        options.setModel(
                requireText(
                        firstText(properties.getModel(), options.getModel()),
                        propertyPath + ".model"));
        applyCommonChatOverrides(properties, options);
        AnthropicApi.Builder api =
                AnthropicApi.builder()
                        .apiKey(
                                requireText(
                                        firstText(
                                                properties.getApiKey(),
                                                property("spring.ai.anthropic.api-key")),
                                        propertyPath + ".api-key"))
                        .restClientBuilder(restClientBuilder)
                        .webClientBuilder(webClientBuilder)
                        .responseErrorHandler(responseErrorHandler);
        String baseUrl =
                firstText(properties.getBaseUrl(), property("spring.ai.anthropic.base-url"));
        if (StringUtils.hasText(baseUrl)) {
            api.baseUrl(baseUrl);
        }
        String completionsPath = property("spring.ai.anthropic.completions-path");
        if (StringUtils.hasText(completionsPath)) {
            api.completionsPath(completionsPath);
        }
        String version = property("spring.ai.anthropic.version");
        if (StringUtils.hasText(version)) {
            api.anthropicVersion(version);
        }
        String betaVersion = property("spring.ai.anthropic.beta-version");
        if (StringUtils.hasText(betaVersion)) {
            api.anthropicBetaFeatures(betaVersion);
        }
        AnthropicChatModel.Builder builder =
                AnthropicChatModel.builder()
                        .anthropicApi(api.build())
                        .defaultOptions(options)
                        .toolCallingManager(toolCallingManager)
                        .retryTemplate(retryTemplate)
                        .observationRegistry(observationRegistry);
        if (toolExecutionEligibilityPredicate != null) {
            builder.toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate);
        }
        return builder.build();
    }

    private ChatModel googleGenAiChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        GoogleGenAiChatOptions options =
                bind("spring.ai.google.genai.chat.options", GoogleGenAiChatOptions.class)
                        .orElseGet(GoogleGenAiChatOptions::new);
        options.setModel(
                requireText(
                        firstText(properties.getModel(), options.getModel()),
                        propertyPath + ".model"));
        applyCommonChatOverrides(properties, options);
        GoogleGenAiChatModel.Builder builder =
                GoogleGenAiChatModel.builder()
                        .genAiClient(
                                googleClient(propertyPath, properties, "spring.ai.google.genai"))
                        .defaultOptions(options)
                        .toolCallingManager(toolCallingManager)
                        .retryTemplate(retryTemplate)
                        .observationRegistry(observationRegistry);
        if (toolExecutionEligibilityPredicate != null) {
            builder.toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate);
        }
        return builder.build();
    }

    private EmbeddingModel googleGenAiEmbeddingModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        GoogleGenAiTextEmbeddingOptions options =
                bind(
                                "spring.ai.google.genai.embedding.options",
                                GoogleGenAiTextEmbeddingOptions.class)
                        .orElseGet(GoogleGenAiTextEmbeddingOptions::new);
        options.setModel(
                requireText(
                        firstText(properties.getModel(), options.getModel()),
                        propertyPath + ".model"));
        if (properties.getDimensions() != null) {
            options.setDimensions(properties.getDimensions());
        }
        GoogleGenAiEmbeddingConnectionDetails.Builder connectionDetailsBuilder =
                GoogleGenAiEmbeddingConnectionDetails.builder()
                        .apiKey(
                                requireText(
                                        firstText(
                                                properties.getApiKey(),
                                                property("spring.ai.google.genai.api-key")),
                                        propertyPath + ".api-key"))
                        .genAiClient(
                                googleClient(propertyPath, properties, "spring.ai.google.genai"));
        String projectId =
                firstText(properties.getProjectId(), property("spring.ai.google.genai.project-id"));
        if (StringUtils.hasText(projectId)) {
            connectionDetailsBuilder.projectId(projectId);
        }
        String location =
                firstText(properties.getLocation(), property("spring.ai.google.genai.location"));
        if (StringUtils.hasText(location)) {
            connectionDetailsBuilder.location(location);
        }
        GoogleGenAiEmbeddingConnectionDetails connectionDetails = connectionDetailsBuilder.build();
        return new GoogleGenAiTextEmbeddingModel(
                connectionDetails, options, retryTemplate, observationRegistry);
    }

    private ChatModel ollamaChatModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OllamaChatOptions options =
                bind("spring.ai.ollama.chat.options", OllamaChatOptions.class)
                        .orElseGet(OllamaChatOptions::new);
        options.setModel(
                requireText(
                        firstText(properties.getModel(), options.getModel()),
                        propertyPath + ".model"));
        applyCommonChatOverrides(properties, options);
        OllamaChatModel.Builder builder =
                OllamaChatModel.builder()
                        .ollamaApi(ollamaApi(propertyPath, properties))
                        .defaultOptions(options)
                        .toolCallingManager(toolCallingManager)
                        .retryTemplate(retryTemplate)
                        .observationRegistry(observationRegistry)
                        .modelManagementOptions(ModelManagementOptions.defaults());
        if (toolExecutionEligibilityPredicate != null) {
            builder.toolExecutionEligibilityPredicate(toolExecutionEligibilityPredicate);
        }
        return builder.build();
    }

    private EmbeddingModel ollamaEmbeddingModel(
            String propertyPath, MemindAiProperties.ClientProperties properties) {
        OllamaEmbeddingOptions options =
                bind("spring.ai.ollama.embedding.options", OllamaEmbeddingOptions.class)
                        .orElseGet(OllamaEmbeddingOptions::new);
        options.setModel(
                requireText(
                        firstText(properties.getModel(), options.getModel()),
                        propertyPath + ".model"));
        if (properties.getDimensions() != null) {
            options.setDimensions(properties.getDimensions());
        }
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi(propertyPath, properties))
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
    }

    private Client googleClient(
            String propertyPath, MemindAiProperties.ClientProperties properties, String prefix) {
        Client.Builder builder =
                Client.builder()
                        .apiKey(
                                requireText(
                                        firstText(
                                                properties.getApiKey(),
                                                property(prefix + ".api-key")),
                                        propertyPath + ".api-key"));
        String baseUrl = firstText(properties.getBaseUrl(), property(prefix + ".base-url"));
        if (StringUtils.hasText(baseUrl)) {
            builder.httpOptions(HttpOptions.builder().baseUrl(baseUrl).build());
        }
        String projectId = firstText(properties.getProjectId(), property(prefix + ".project-id"));
        if (StringUtils.hasText(projectId)) {
            builder.project(projectId);
        }
        String location = firstText(properties.getLocation(), property(prefix + ".location"));
        if (StringUtils.hasText(location)) {
            builder.location(location);
        }
        return builder.build();
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

    static RestClient.Builder defaultRestClientBuilder() {
        return RestClient.builder();
    }

    static WebClient.Builder defaultWebClientBuilder() {
        return WebClient.builder();
    }

    static ResponseErrorHandler defaultResponseErrorHandler() {
        return RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;
    }

    private <T> BindResult<T> bind(String name, Class<T> type) {
        return binder.bind(name, Bindable.of(type));
    }

    private String property(String name) {
        return environment.getProperty(name);
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static HttpHeaders openAiHeaders(
            String modelProjectId,
            String modelOrganizationId,
            OpenAiConnectionProperties connectionProperties) {
        String projectId = firstText(modelProjectId, connectionProperties.getProjectId());
        String organizationId =
                firstText(modelOrganizationId, connectionProperties.getOrganizationId());
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(projectId)) {
            headers.add("OpenAI-Project", projectId);
        }
        if (StringUtils.hasText(organizationId)) {
            headers.add("OpenAI-Organization", organizationId);
        }
        return headers;
    }

    private static OpenAiEmbeddingOptions copyOpenAiEmbeddingOptions(
            OpenAiEmbeddingOptions source) {
        OpenAiEmbeddingOptions target = new OpenAiEmbeddingOptions();
        target.setModel(source.getModel());
        target.setEncodingFormat(source.getEncodingFormat());
        target.setDimensions(source.getDimensions());
        target.setUser(source.getUser());
        return target;
    }

    private static void applyCommonChatOverrides(
            MemindAiProperties.ClientProperties properties, OpenAiChatOptions options) {
        if (properties.getTemperature() != null) {
            options.setTemperature(properties.getTemperature());
        }
        if (properties.getTopP() != null) {
            options.setTopP(properties.getTopP());
        }
        if (properties.getMaxTokens() != null) {
            options.setMaxTokens(properties.getMaxTokens());
        }
        if (properties.getMaxCompletionTokens() != null) {
            options.setMaxCompletionTokens(properties.getMaxCompletionTokens());
        }
    }

    private static void applyCommonChatOverrides(
            MemindAiProperties.ClientProperties properties, AnthropicChatOptions options) {
        if (properties.getTemperature() != null) {
            options.setTemperature(properties.getTemperature());
        }
        if (properties.getTopP() != null) {
            options.setTopP(properties.getTopP());
        }
        if (properties.getTopK() != null) {
            options.setTopK(properties.getTopK());
        }
        if (properties.getMaxTokens() != null) {
            options.setMaxTokens(properties.getMaxTokens());
        }
    }

    private static void applyCommonChatOverrides(
            MemindAiProperties.ClientProperties properties, GoogleGenAiChatOptions options) {
        if (properties.getTemperature() != null) {
            options.setTemperature(properties.getTemperature());
        }
        if (properties.getTopP() != null) {
            options.setTopP(properties.getTopP());
        }
        if (properties.getTopK() != null) {
            options.setTopK(properties.getTopK());
        }
        if (properties.getMaxTokens() != null) {
            options.setMaxOutputTokens(properties.getMaxTokens());
        }
    }

    private static void applyCommonChatOverrides(
            MemindAiProperties.ClientProperties properties, OllamaChatOptions options) {
        if (properties.getTemperature() != null) {
            options.setTemperature(properties.getTemperature());
        }
        if (properties.getTopP() != null) {
            options.setTopP(properties.getTopP());
        }
        if (properties.getTopK() != null) {
            options.setTopK(properties.getTopK());
        }
        if (properties.getMaxTokens() != null) {
            options.setNumPredict(properties.getMaxTokens());
        }
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
