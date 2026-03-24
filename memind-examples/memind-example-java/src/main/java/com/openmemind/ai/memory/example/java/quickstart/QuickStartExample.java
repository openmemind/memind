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
package com.openmemind.ai.memory.example.java.quickstart;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.example.java.support.ExampleDataLoader;
import com.openmemind.ai.memory.example.java.support.ExamplePrinter;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiFileVector;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient;
import com.openmemind.ai.memory.plugin.jdbc.JdbcMemoryAccess;
import com.openmemind.ai.memory.plugin.jdbc.JdbcStore;
import io.micrometer.observation.ObservationRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Quick start example for the pure Java integration path.
 */
public final class QuickStartExample {

    private static final Logger log = LoggerFactory.getLogger(QuickStartExample.class);

    private QuickStartExample() {}

    public static void main(String[] args) {
        run(createMemory("quickstart", MemoryBuildOptions.defaults()), new ExampleDataLoader());
    }

    private static void run(Memory memory, ExampleDataLoader loader) {
        var memoryId = DefaultMemoryId.of("user-quickstart", "memind");
        var messages = loader.loadMessages("quickstart/messages.json");

        ExamplePrinter.printSection("Step 1: Extract Memory — addMessages()");
        log.info("  loaded {} messages", messages.size());

        var result =
                memory.addMessages(
                                memoryId,
                                messages,
                                ExtractionConfig.defaults().withLanguage("Chinese"))
                        .block();
        ExamplePrinter.printExtractionResult(result);

        ExamplePrinter.printSection("Step 2: Retrieve Memory — retrieve()");
        var query = "这个用户的技术背景是什么？";
        log.info("  query: {}", query);

        long startedAt = System.currentTimeMillis();
        var retrieval = memory.retrieve(memoryId, query, RetrievalConfig.Strategy.SIMPLE).block();
        ExamplePrinter.printRetrievalResult(retrieval, System.currentTimeMillis() - startedAt);
    }

    private static Memory createMemory(String scenario, MemoryBuildOptions options) {
        Path runtimeDir = resolveRuntimeDir(scenario);
        String jdbcUrl = resolveJdbcUrl(runtimeDir);
        prepareRuntimeLayout(runtimeDir, jdbcUrl);
        StructuredChatClient chatClient = createChatClient();
        EmbeddingModel embeddingModel = createEmbeddingModel();
        JdbcMemoryAccess jdbc = createJdbcAccess(jdbcUrl);

        return Memory.builder()
                .chatClient(chatClient)
                .store(jdbc.store())
                .textSearch(jdbc.textSearch())
                .vector(
                        SpringAiFileVector.file(
                                runtimeDir.resolve("vector-store.json"), embeddingModel))
                .options(options)
                .build();
    }

    private static StructuredChatClient createChatClient() {
        OpenAiApi openAiApi = createOpenAiApi();
        OpenAiChatModel chatModel =
                OpenAiChatModel.builder()
                        .openAiApi(openAiApi)
                        .defaultOptions(
                                OpenAiChatOptions.builder()
                                        .model(
                                                resolveValue(
                                                        "memind.examples.openai.chat-model",
                                                        "OPENAI_CHAT_MODEL",
                                                        "gpt-4o-mini"))
                                        .build())
                        .observationRegistry(ObservationRegistry.NOOP)
                        .build();
        return new SpringAiStructuredChatClient(ChatClient.builder(chatModel).build());
    }

    private static EmbeddingModel createEmbeddingModel() {
        return new OpenAiEmbeddingModel(
                createOpenAiApi(),
                MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder()
                        .model(
                                resolveValue(
                                        "memind.examples.openai.embedding-model",
                                        "OPENAI_EMBEDDING_MODEL",
                                        "text-embedding-3-small"))
                        .build());
    }

    private static OpenAiApi createOpenAiApi() {
        return OpenAiApi.builder()
                .apiKey(
                        requireValue(
                                "memind.examples.openai.api-key",
                                "OPENAI_API_KEY",
                                "Missing OpenAI API key. Set system property "
                                        + "'memind.examples.openai.api-key' or environment "
                                        + "variable 'OPENAI_API_KEY'."))
                .baseUrl(
                        resolveValue(
                                "memind.examples.openai.base-url",
                                "OPENAI_BASE_URL",
                                "https://api.openai.com"))
                .build();
    }

    private static JdbcMemoryAccess createJdbcAccess(String jdbcUrl) {
        return switch (detectDialect(jdbcUrl)) {
            case SQLITE -> JdbcStore.sqlite(extractSqlitePath(jdbcUrl));
            case MYSQL ->
                    JdbcStore.mysql(
                            jdbcUrl,
                            requireValue(
                                    "memind.examples.jdbc.username",
                                    "MEMIND_EXAMPLES_JDBC_USERNAME",
                                    "Missing JDBC username. Set system property "
                                            + "'memind.examples.jdbc.username' or environment "
                                            + "variable 'MEMIND_EXAMPLES_JDBC_USERNAME'."),
                            requireValue(
                                    "memind.examples.jdbc.password",
                                    "MEMIND_EXAMPLES_JDBC_PASSWORD",
                                    "Missing JDBC password. Set system property "
                                            + "'memind.examples.jdbc.password' or environment "
                                            + "variable 'MEMIND_EXAMPLES_JDBC_PASSWORD'."));
            case POSTGRESQL ->
                    JdbcStore.postgresql(
                            jdbcUrl,
                            requireValue(
                                    "memind.examples.jdbc.username",
                                    "MEMIND_EXAMPLES_JDBC_USERNAME",
                                    "Missing JDBC username. Set system property "
                                            + "'memind.examples.jdbc.username' or environment "
                                            + "variable 'MEMIND_EXAMPLES_JDBC_USERNAME'."),
                            requireValue(
                                    "memind.examples.jdbc.password",
                                    "MEMIND_EXAMPLES_JDBC_PASSWORD",
                                    "Missing JDBC password. Set system property "
                                            + "'memind.examples.jdbc.password' or environment "
                                            + "variable 'MEMIND_EXAMPLES_JDBC_PASSWORD'."));
        };
    }

    private static Path resolveRuntimeDir(String scenario) {
        return Path.of(
                        resolveValue(
                                "memind.examples.runtime-dir",
                                "MEMIND_EXAMPLES_RUNTIME_DIR",
                                Path.of("target", "example-runtime", scenario).toString()))
                .toAbsolutePath()
                .normalize();
    }

    private static String resolveJdbcUrl(Path runtimeDir) {
        return resolveValue(
                "memind.examples.jdbc.url",
                "MEMIND_EXAMPLES_JDBC_URL",
                "jdbc:sqlite:" + runtimeDir.resolve("memind.db"));
    }

    private static String requireValue(String propertyName, String envName, String message) {
        String value = resolveValue(propertyName, envName, "");
        if (value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private static String resolveValue(String propertyName, String envName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        String environmentValue = System.getenv(envName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        return defaultValue;
    }

    private static void prepareRuntimeLayout(Path runtimeDir, String jdbcUrl) {
        try {
            Files.createDirectories(runtimeDir);
            Files.createDirectories(runtimeDir.resolve("vector-store.json").getParent());
            ensureSqliteParentDirectory(jdbcUrl);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to prepare runtime directory: " + runtimeDir, e);
        }
    }

    private static void ensureSqliteParentDirectory(String jdbcUrl) throws Exception {
        if (detectDialect(jdbcUrl) != JdbcDialect.SQLITE) {
            return;
        }

        String rawPath = extractSqlitePath(jdbcUrl);
        if (rawPath.isBlank() || ":memory:".equals(rawPath) || "file::memory:".equals(rawPath)) {
            return;
        }

        Path dbPath = Path.of(rawPath).toAbsolutePath().normalize();
        Path parent = dbPath.getParent();
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

    private static JdbcDialect detectDialect(String jdbcUrl) {
        String normalized = jdbcUrl.toLowerCase(Locale.ROOT);
        if (normalized.contains(":sqlite:")) {
            return JdbcDialect.SQLITE;
        }
        if (normalized.contains(":mysql:")) {
            return JdbcDialect.MYSQL;
        }
        if (normalized.contains(":postgresql:")) {
            return JdbcDialect.POSTGRESQL;
        }
        throw new IllegalStateException("Unsupported JDBC URL for pure Java example: " + jdbcUrl);
    }

    private enum JdbcDialect {
        SQLITE,
        MYSQL,
        POSTGRESQL
    }
}
