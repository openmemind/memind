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
package com.openmemind.ai.memory.example.java.agent;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.ExtractionCommonOptions;
import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.InsightExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.builder.RetrievalOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.llm.rerank.LlmReranker;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.store.MemoryStore;
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
 * Agent-scope memory example for the pure Java integration path.
 */
public final class AgentScopeMemoryExample {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeMemoryExample.class);
    private static final String LANGUAGE = "Chinese";

    private AgentScopeMemoryExample() {}

    public static void main(String[] args) {
        var runtime = createRuntime("agent", agentOptions());
        run(runtime.memory(), runtime.store(), new ExampleDataLoader());
    }

    private static void run(Memory memory, MemoryStore store, ExampleDataLoader loader) {
        var memoryId = DefaultMemoryId.of("user-agent-scope", "memind");
        var config = ExtractionConfig.agentOnly().withLanguage(LANGUAGE);
        var dataFiles = new String[] {"agent/messages-1.json", "agent/messages-2.json"};
        var labels = new String[] {"directives + playbooks", "resolutions"};

        for (int i = 0; i < dataFiles.length; i++) {
            ExamplePrinter.printSection(
                    "Step " + (i + 1) + ": Extract Agent Memory — " + labels[i]);
            var messages = loader.loadMessages(dataFiles[i]);
            log.info("  loaded {} messages", messages.size());

            var result = memory.addMessages(memoryId, messages, config).block();
            ExamplePrinter.printExtractionResult(result);
        }

        ExamplePrinter.printSection("Step 3: Flush Agent Insights — flushInsights()");
        log.info("  flushing agent insight tree...");
        long flushStartedAt = System.currentTimeMillis();
        memory.flushInsights(memoryId, LANGUAGE);
        log.info("  flush completed in {}ms", System.currentTimeMillis() - flushStartedAt);

        ExamplePrinter.printSection("Step 4: Agent Insight Tree — Full Structure");
        ExamplePrinter.printInsightTree(store, memoryId);

        retrieveAgentMemory(
                memory, memoryId, "Step 5: Retrieve Directive Memory", "开始修改生产配置前必须准备什么？");
        retrieveAgentMemory(
                memory, memoryId, "Step 6: Retrieve Playbook Memory", "收到 webhook 重试告警时应该怎么排查？");
        retrieveAgentMemory(
                memory, memoryId, "Step 7: Retrieve Resolution Memory", "staging 启动失败的问题后来怎么解决的？");
    }

    private static void retrieveAgentMemory(
            Memory memory, DefaultMemoryId memoryId, String title, String query) {
        ExamplePrinter.printSection(title);
        log.info("  query: {}", query);

        long startedAt = System.currentTimeMillis();
        var retrieval =
                memory.retrieve(
                                RetrievalRequest.agentMemory(
                                        memoryId, query, RetrievalConfig.Strategy.SIMPLE))
                        .block();
        ExamplePrinter.printRetrievalResult(retrieval, System.currentTimeMillis() - startedAt);
    }

    private static MemoryBuildOptions agentOptions() {
        InsightBuildConfig defaults = MemoryBuildOptions.defaults().extraction().insight().build();
        return MemoryBuildOptions.builder()
                .extraction(
                        new ExtractionOptions(
                                ExtractionCommonOptions.defaults(),
                                RawDataExtractionOptions.defaults(),
                                ItemExtractionOptions.defaults(),
                                new InsightExtractionOptions(
                                        true,
                                        new InsightBuildConfig(
                                                2,
                                                2,
                                                defaults.concurrency(),
                                                defaults.maxRetries()))))
                .retrieval(RetrievalOptions.defaults())
                .build();
    }

    private static AgentScopeRuntime createRuntime(String scenario, MemoryBuildOptions options) {
        Path runtimeDir = resolveRuntimeDir(scenario);
        String jdbcUrl = resolveJdbcUrl(runtimeDir);
        prepareRuntimeLayout(runtimeDir, jdbcUrl);
        StructuredChatClient chatClient = createChatClient();
        EmbeddingModel embeddingModel = createEmbeddingModel();
        JdbcMemoryAccess jdbc = createJdbcAccess(jdbcUrl);
        MemoryStore memoryStore = jdbc.store();

        Memory memory =
                Memory.builder()
                        .chatClient(chatClient)
                        .store(memoryStore)
                        .textSearch(jdbc.textSearch())
                        .vector(
                                SpringAiFileVector.file(
                                        runtimeDir.resolve("vector-store.json"), embeddingModel))
                        .reranker(createReranker())
                        .options(options)
                        .build();

        return new AgentScopeRuntime(memory, memoryStore);
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

    private static Reranker createReranker() {
        return new LlmReranker(
                resolveValue(
                        "memind.examples.rerank.base-url",
                        "RERANK_BASE_URL",
                        "https://api.openai.com"),
                requireValue(
                        "memind.examples.rerank.api-key",
                        "RERANK_API_KEY",
                        "Missing Rerank API key. Set system property "
                                + "'memind.examples.rerank.api-key' or environment "
                                + "variable 'RERANK_API_KEY'."),
                resolveValue("memind.examples.rerank.model", "RERANK_MODEL", "qwen3-reranker-8b"));
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
        if (detectDialect(jdbcUrl) != Dialect.SQLITE) {
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

    private static Dialect detectDialect(String jdbcUrl) {
        String normalized = jdbcUrl.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("jdbc:mysql:")) {
            return Dialect.MYSQL;
        }
        if (normalized.startsWith("jdbc:postgresql:")) {
            return Dialect.POSTGRESQL;
        }
        if (normalized.startsWith("jdbc:sqlite:")) {
            return Dialect.SQLITE;
        }
        throw new IllegalArgumentException("Unsupported JDBC URL for examples: " + jdbcUrl);
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

    private enum Dialect {
        SQLITE,
        MYSQL,
        POSTGRESQL
    }

    private record AgentScopeRuntime(Memory memory, MemoryStore store) {}
}
