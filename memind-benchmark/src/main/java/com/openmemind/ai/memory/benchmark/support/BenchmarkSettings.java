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

import java.nio.file.Path;
import java.util.Objects;

/**
 * Centralized settings for the pure Java benchmark module.
 */
public final class BenchmarkSettings {

    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_CHAT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final String DEFAULT_RERANK_MODEL = "qwen3-reranker-8b";
    private static final String DEFAULT_EVAL_MODEL = "gpt-4o";

    private final OpenAiSettings openAi;
    private final JdbcSettings jdbc;
    private final RerankSettings rerank;
    private final BenchmarkRunSettings benchmark;

    private BenchmarkSettings(
            OpenAiSettings openAi,
            JdbcSettings jdbc,
            RerankSettings rerank,
            BenchmarkRunSettings benchmark) {
        this.openAi = Objects.requireNonNull(openAi, "openAi");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.rerank = Objects.requireNonNull(rerank, "rerank");
        this.benchmark = Objects.requireNonNull(benchmark, "benchmark");
    }

    public static BenchmarkSettings load() {
        return load(ValueResolver.system());
    }

    static BenchmarkSettings load(ValueResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");

        Path outputDir =
                Path.of(
                                resolveValue(
                                        resolver,
                                        "memind.benchmark.output-dir",
                                        "MEMIND_BENCHMARK_OUTPUT_DIR",
                                        Path.of("target", "benchmark-output").toString()))
                        .toAbsolutePath()
                        .normalize();

        Path dataDir =
                Path.of(
                                resolveValue(
                                        resolver,
                                        "memind.benchmark.data-dir",
                                        "MEMIND_BENCHMARK_DATA_DIR",
                                        "data"))
                        .toAbsolutePath()
                        .normalize();

        return new BenchmarkSettings(
                new OpenAiSettings(
                        requireValue(
                                resolver,
                                "memind.benchmark.openai.api-key",
                                "OPENAI_API_KEY",
                                "Missing OpenAI API key. Set system property "
                                        + "'memind.benchmark.openai.api-key' or environment "
                                        + "variable 'OPENAI_API_KEY'."),
                        resolveValue(
                                resolver,
                                "memind.benchmark.openai.base-url",
                                "OPENAI_BASE_URL",
                                DEFAULT_OPENAI_BASE_URL),
                        resolveValue(
                                resolver,
                                "memind.benchmark.openai.chat-model",
                                "OPENAI_CHAT_MODEL",
                                DEFAULT_CHAT_MODEL),
                        resolveValue(
                                resolver,
                                "memind.benchmark.openai.embedding-model",
                                "OPENAI_EMBEDDING_MODEL",
                                DEFAULT_EMBEDDING_MODEL)),
                new JdbcSettings(
                        resolveValue(
                                resolver,
                                "memind.benchmark.jdbc.url",
                                "MEMIND_BENCHMARK_JDBC_URL",
                                "jdbc:sqlite:"
                                        + outputDir.resolve("runtime").resolve("memind.db"))),
                new RerankSettings(
                        Boolean.parseBoolean(
                                resolveValue(
                                        resolver,
                                        "memind.benchmark.rerank.enabled",
                                        "MEMIND_BENCHMARK_RERANK_ENABLED",
                                        "false")),
                        resolveValue(
                                resolver,
                                "memind.benchmark.rerank.model",
                                "MEMIND_BENCHMARK_RERANK_MODEL",
                                DEFAULT_RERANK_MODEL)),
                new BenchmarkRunSettings(
                        dataDir,
                        outputDir,
                        resolveValue(
                                resolver,
                                "memind.benchmark.eval-model",
                                "MEMIND_BENCHMARK_EVAL_MODEL",
                                DEFAULT_EVAL_MODEL)));
    }

    public OpenAiSettings openAi() {
        return openAi;
    }

    public JdbcSettings jdbc() {
        return jdbc;
    }

    public RerankSettings rerank() {
        return rerank;
    }

    public BenchmarkRunSettings benchmark() {
        return benchmark;
    }

    private static String requireValue(
            ValueResolver resolver, String propertyName, String envName, String errorMessage) {
        String value = resolveValue(resolver, propertyName, envName, "");
        if (value.isBlank()) {
            throw new IllegalStateException(errorMessage);
        }
        return value;
    }

    private static String resolveValue(
            ValueResolver resolver, String propertyName, String envName, String defaultValue) {
        String propertyValue = trimToNull(resolver.systemProperty(propertyName));
        if (propertyValue != null) {
            return propertyValue;
        }

        String environmentValue = trimToNull(resolver.environmentVariable(envName));
        if (environmentValue != null) {
            return environmentValue;
        }

        return defaultValue;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record OpenAiSettings(
            String apiKey, String baseUrl, String chatModel, String embeddingModel) {}

    public record JdbcSettings(String url) {}

    public record RerankSettings(boolean enabled, String model) {}

    public record BenchmarkRunSettings(Path dataDir, Path outputDir, String evalModel) {}

    interface ValueResolver {

        String systemProperty(String key);

        String environmentVariable(String key);

        static ValueResolver system() {
            return new ValueResolver() {
                @Override
                public String systemProperty(String key) {
                    return System.getProperty(key);
                }

                @Override
                public String environmentVariable(String key) {
                    return System.getenv(key);
                }
            };
        }
    }
}
