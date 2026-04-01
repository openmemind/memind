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
package com.openmemind.ai.memory.benchmark.support;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.llm.rerank.LlmReranker;
import com.openmemind.ai.memory.core.llm.rerank.NoopReranker;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiFileVector;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient;
import com.openmemind.ai.memory.plugin.jdbc.JdbcMemoryAccess;
import com.openmemind.ai.memory.plugin.jdbc.JdbcStore;
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

public final class BenchmarkRuntimeFactory {

    private BenchmarkRuntimeFactory() {}

    public static BenchmarkRuntime create(
            String benchmarkName,
            Path runtimeDir,
            MemoryBuildOptions options,
            BenchmarkSettings settings) {
        Objects.requireNonNull(benchmarkName, "benchmarkName");
        Objects.requireNonNull(runtimeDir, "runtimeDir");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(settings, "settings");

        try {
            Path normalizedRuntimeDir = prepareRuntimeLayout(runtimeDir, settings);
            JdbcMemoryAccess jdbcAccess =
                    JdbcStore.sqlite(normalizedRuntimeDir.resolve("memind.db").toString());
            EmbeddingModel embeddingModel = createEmbeddingModel(settings.openAi());

            Memory memory =
                    Memory.builder()
                            .chatClient(createChatClient(settings.openAi()))
                            .store(jdbcAccess.store())
                            .buffer(jdbcAccess.buffer())
                            .textSearch(jdbcAccess.textSearch())
                            .vector(
                                    SpringAiFileVector.file(
                                            normalizedRuntimeDir
                                                    .resolve("vector-store")
                                                    .resolve("vector-store.json"),
                                            embeddingModel))
                            .reranker(createReranker(settings))
                            .options(options)
                            .build();

            return new BenchmarkRuntime(memory, jdbcAccess.store(), settings, normalizedRuntimeDir);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to create benchmark runtime for " + benchmarkName, exception);
        }
    }

    static Path prepareRuntimeLayout(Path runtimeDir, BenchmarkSettings settings) throws Exception {
        Objects.requireNonNull(runtimeDir, "runtimeDir");
        Objects.requireNonNull(settings, "settings");

        Path normalizedRuntimeDir = runtimeDir.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRuntimeDir);
        Files.createDirectories(normalizedRuntimeDir.resolve("vector-store"));
        Files.createDirectories(normalizedRuntimeDir.resolve("memind.db").getParent());
        return normalizedRuntimeDir;
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
        throw new IllegalStateException("Unsupported JDBC URL for benchmark runtime: " + jdbcUrl);
    }

    private static StructuredChatClient createChatClient(
            BenchmarkSettings.OpenAiSettings settings) {
        OpenAiChatModel chatModel =
                OpenAiChatModel.builder()
                        .openAiApi(createOpenAiApi(settings))
                        .defaultOptions(
                                OpenAiChatOptions.builder().model(settings.chatModel()).build())
                        .observationRegistry(ObservationRegistry.NOOP)
                        .build();
        return new SpringAiStructuredChatClient(ChatClient.builder(chatModel).build());
    }

    private static EmbeddingModel createEmbeddingModel(BenchmarkSettings.OpenAiSettings settings) {
        return new OpenAiEmbeddingModel(
                createOpenAiApi(settings),
                MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder().model(settings.embeddingModel()).build());
    }

    private static OpenAiApi createOpenAiApi(BenchmarkSettings.OpenAiSettings settings) {
        return OpenAiApi.builder().apiKey(settings.apiKey()).baseUrl(settings.baseUrl()).build();
    }

    private static Reranker createReranker(BenchmarkSettings settings) {
        if (!settings.rerank().enabled()) {
            return new NoopReranker();
        }
        return new LlmReranker(
                settings.openAi().baseUrl(), settings.openAi().apiKey(), settings.rerank().model());
    }
}
