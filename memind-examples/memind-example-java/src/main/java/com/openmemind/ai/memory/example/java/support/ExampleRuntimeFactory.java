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
package com.openmemind.ai.memory.example.java.support;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuilder;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.llm.rerank.LlmReranker;
import com.openmemind.ai.memory.core.llm.rerank.NoopReranker;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiFileVector;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient;
import com.openmemind.ai.memory.plugin.jdbc.JdbcMemoryAccess;
import com.openmemind.ai.memory.plugin.jdbc.JdbcStore;
import com.openmemind.ai.memory.plugin.rawdata.document.plugin.DocumentRawDataPlugin;
import com.openmemind.ai.memory.plugin.rawdata.toolcall.plugin.ToolCallRawDataPlugin;
import io.micrometer.observation.ObservationRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public final class ExampleRuntimeFactory {

    private ExampleRuntimeFactory() {}

    public static ExampleRuntime create(String scenario, MemoryBuildOptions options) {
        return create(scenario, options, ExampleSettings.load(scenario));
    }

    static ExampleRuntime create(
            String scenario, MemoryBuildOptions options, ExampleSettings settings) {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(settings, "settings");

        try {
            Path runtimeDir = prepareRuntimeLayout(scenario, settings);
            String jdbcDialect = detectDialect(settings.store().jdbcUrl());
            JdbcMemoryAccess jdbc = createJdbcAccess(settings.store());
            EmbeddingModel embeddingModel = createEmbeddingModel(settings.openAi());

            MemoryBuilder memoryBuilder =
                    Memory.builder()
                            .chatClient(createChatClient(settings.openAi()))
                            .store(jdbc.store())
                            .buffer(jdbc.buffer())
                            .textSearch(jdbc.textSearch())
                            .vector(
                                    SpringAiFileVector.file(
                                            runtimeDir.resolve(
                                                    settings.runtime().vectorStoreFileName()),
                                            embeddingModel))
                            .reranker(createReranker(settings.rerank()))
                            .options(options);
            memoryBuilder = applyScenarioPlugins(memoryBuilder, scenario);
            Memory memory = memoryBuilder.build();

            return new ExampleRuntime(
                    memory,
                    jdbc.store(),
                    new ExampleDataLoader(),
                    settings,
                    runtimeDir,
                    jdbcDialect);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to create Java example runtime for scenario: " + scenario, exception);
        }
    }

    static MemoryBuilder applyScenarioPlugins(MemoryBuilder memoryBuilder, String scenario) {
        Objects.requireNonNull(memoryBuilder, "memoryBuilder");
        Objects.requireNonNull(scenario, "scenario");

        return switch (scenario.toLowerCase(Locale.ROOT)) {
            case "tool" -> memoryBuilder.rawDataPlugin(new ToolCallRawDataPlugin());
            case "document" -> memoryBuilder.rawDataPlugin(new DocumentRawDataPlugin());
            default -> memoryBuilder;
        };
    }

    static Path prepareRuntimeLayout(String scenario, ExampleSettings settings) throws Exception {
        Path runtimeDir = settings.runtime().scenarioRuntimeDir(scenario);
        Files.createDirectories(runtimeDir);
        Files.createDirectories(
                runtimeDir.resolve(settings.runtime().vectorStoreFileName()).getParent());
        ensureSqliteParentDirectory(settings.store().jdbcUrl());
        return runtimeDir;
    }

    static String detectDialect(String jdbcUrl) {
        String normalized = jdbcUrl.toLowerCase(Locale.ROOT);
        if (normalized.contains(":sqlite:")) {
            return "sqlite";
        }
        if (normalized.contains(":mysql:")) {
            return "mysql";
        }
        if (normalized.contains(":postgresql:")) {
            return "postgresql";
        }
        throw new IllegalStateException("Unsupported JDBC URL for pure Java example: " + jdbcUrl);
    }

    private static StructuredChatClient createChatClient(ExampleSettings.OpenAiSettings settings) {
        OpenAiChatModel chatModel =
                OpenAiChatModel.builder()
                        .openAiApi(createOpenAiApi(settings))
                        .defaultOptions(
                                OpenAiChatOptions.builder().model(settings.chatModel()).build())
                        .observationRegistry(ObservationRegistry.NOOP)
                        .build();
        return new SpringAiStructuredChatClient(ChatClient.builder(chatModel).build());
    }

    private static EmbeddingModel createEmbeddingModel(ExampleSettings.OpenAiSettings settings) {
        return new OpenAiEmbeddingModel(
                createOpenAiApi(settings),
                MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder().model(settings.embeddingModel()).build());
    }

    private static OpenAiApi createOpenAiApi(ExampleSettings.OpenAiSettings settings) {
        return OpenAiApi.builder().apiKey(settings.apiKey()).baseUrl(settings.baseUrl()).build();
    }

    private static JdbcMemoryAccess createJdbcAccess(ExampleSettings.StoreSettings settings) {
        return switch (detectDialect(settings.jdbcUrl())) {
            case "sqlite" -> JdbcStore.sqlite(extractSqlitePath(settings.jdbcUrl()));
            case "mysql" ->
                    JdbcStore.mysql(settings.jdbcUrl(), settings.username(), settings.password());
            case "postgresql" ->
                    JdbcStore.postgresql(
                            settings.jdbcUrl(), settings.username(), settings.password());
            default ->
                    throw new IllegalStateException("Unsupported JDBC URL: " + settings.jdbcUrl());
        };
    }

    private static Reranker createReranker(ExampleSettings.RerankSettings settings) {
        if (!settings.enabled()) {
            return new NoopReranker();
        }
        if (settings.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Rerank is enabled but no API key was configured. Set system property "
                            + "'memind.examples.rerank.api-key' or environment variable "
                            + "'RERANK_API_KEY'.");
        }
        return new LlmReranker(settings.baseUrl(), settings.apiKey(), settings.model());
    }

    private static void ensureSqliteParentDirectory(String jdbcUrl) throws Exception {
        if (!"sqlite".equals(detectDialect(jdbcUrl))) {
            return;
        }

        String rawPath = extractSqlitePath(jdbcUrl);
        if (rawPath.isBlank() || ":memory:".equals(rawPath) || "file::memory:".equals(rawPath)) {
            return;
        }

        Path parent = Path.of(rawPath).toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String extractSqlitePath(String jdbcUrl) {
        String prefix = ":sqlite:";
        int marker = jdbcUrl.toLowerCase(Locale.ROOT).indexOf(prefix);
        if (marker < 0) {
            throw new IllegalStateException("Unsupported SQLite JDBC URL: " + jdbcUrl);
        }
        String rawPath = jdbcUrl.substring(marker + prefix.length());
        if (rawPath.isBlank()) {
            throw new IllegalStateException("SQLite JDBC URL must contain a database path");
        }
        return rawPath;
    }
}
