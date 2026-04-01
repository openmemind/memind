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

import java.nio.file.Path;
import java.util.Objects;

/**
 * Centralized configuration surface for the pure Java examples.
 *
 * <p>Non-sensitive defaults live here so users can discover what is configurable in one place.
 * Sensitive values such as API keys still prefer system properties and environment variables.
 */
public final class ExampleSettings {

    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_CHAT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final String DEFAULT_RERANK_MODEL = "qwen3-reranker-8b";
    private static final String DEFAULT_VECTOR_STORE_FILE_NAME = "vector-store.json";
    private static final String DEFAULT_SQLITE_FILE_NAME = "memind.db";

    private final OpenAiSettings openAi;
    private final RerankSettings rerank;
    private final StoreSettings store;
    private final RuntimeSettings runtime;

    private ExampleSettings(
            OpenAiSettings openAi,
            RerankSettings rerank,
            StoreSettings store,
            RuntimeSettings runtime) {
        this.openAi = Objects.requireNonNull(openAi, "openAi");
        this.rerank = Objects.requireNonNull(rerank, "rerank");
        this.store = Objects.requireNonNull(store, "store");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public static ExampleSettings load(String scenario) {
        return load(scenario, ValueResolver.system());
    }

    static ExampleSettings load(String scenario, ValueResolver resolver) {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(resolver, "resolver");

        RuntimeSettings runtime =
                new RuntimeSettings(
                        resolveRuntimeRootDir(resolver),
                        resolveValue(
                                resolver,
                                "memind.examples.vector-store.file-name",
                                "MEMIND_EXAMPLES_VECTOR_STORE_FILE_NAME",
                                DEFAULT_VECTOR_STORE_FILE_NAME),
                        resolveValue(
                                resolver,
                                "memind.examples.sqlite.file-name",
                                "MEMIND_EXAMPLES_SQLITE_FILE_NAME",
                                DEFAULT_SQLITE_FILE_NAME));

        return new ExampleSettings(
                new OpenAiSettings(
                        requireValue(
                                resolver,
                                "memind.examples.openai.api-key",
                                "OPENAI_API_KEY",
                                "Missing OpenAI API key. Set system property "
                                        + "'memind.examples.openai.api-key' or environment "
                                        + "variable 'OPENAI_API_KEY'."),
                        resolveValue(
                                resolver,
                                "memind.examples.openai.base-url",
                                "OPENAI_BASE_URL",
                                DEFAULT_OPENAI_BASE_URL),
                        resolveValue(
                                resolver,
                                "memind.examples.openai.chat-model",
                                "OPENAI_CHAT_MODEL",
                                DEFAULT_CHAT_MODEL),
                        resolveValue(
                                resolver,
                                "memind.examples.openai.embedding-model",
                                "OPENAI_EMBEDDING_MODEL",
                                DEFAULT_EMBEDDING_MODEL)),
                new RerankSettings(
                        Boolean.parseBoolean(
                                resolveValue(
                                        resolver,
                                        "memind.examples.rerank.enabled",
                                        "MEMIND_EXAMPLES_RERANK_ENABLED",
                                        "false")),
                        resolveValue(
                                resolver, "memind.examples.rerank.api-key", "RERANK_API_KEY", ""),
                        resolveValue(
                                resolver,
                                "memind.examples.rerank.base-url",
                                "RERANK_BASE_URL",
                                DEFAULT_OPENAI_BASE_URL),
                        resolveValue(
                                resolver,
                                "memind.examples.rerank.model",
                                "RERANK_MODEL",
                                DEFAULT_RERANK_MODEL)),
                new StoreSettings(
                        resolveJdbcUrl(resolver, runtime, scenario),
                        resolveValue(
                                resolver,
                                "memind.examples.jdbc.username",
                                "MEMIND_EXAMPLES_JDBC_USERNAME",
                                ""),
                        resolveValue(
                                resolver,
                                "memind.examples.jdbc.password",
                                "MEMIND_EXAMPLES_JDBC_PASSWORD",
                                "")),
                runtime);
    }

    public OpenAiSettings openAi() {
        return openAi;
    }

    public RerankSettings rerank() {
        return rerank;
    }

    public StoreSettings store() {
        return store;
    }

    public RuntimeSettings runtime() {
        return runtime;
    }

    private static Path resolveRuntimeRootDir(ValueResolver resolver) {
        return Path.of(
                        resolveValue(
                                resolver,
                                "memind.examples.runtime-root-dir",
                                "MEMIND_EXAMPLES_RUNTIME_ROOT_DIR",
                                Path.of("target", "example-runtime").toString()))
                .toAbsolutePath()
                .normalize();
    }

    private static String resolveJdbcUrl(
            ValueResolver resolver, RuntimeSettings runtime, String scenario) {
        return resolveValue(
                resolver,
                "memind.examples.jdbc.url",
                "MEMIND_EXAMPLES_JDBC_URL",
                "jdbc:sqlite:" + runtime.scenarioSqlitePath(scenario));
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

    public record RerankSettings(boolean enabled, String apiKey, String baseUrl, String model) {}

    public record StoreSettings(String jdbcUrl, String username, String password) {}

    public record RuntimeSettings(
            Path runtimeRootDir, String vectorStoreFileName, String sqliteFileName) {

        public RuntimeSettings {
            Objects.requireNonNull(runtimeRootDir, "runtimeRootDir");
            Objects.requireNonNull(vectorStoreFileName, "vectorStoreFileName");
            Objects.requireNonNull(sqliteFileName, "sqliteFileName");
        }

        public Path scenarioRuntimeDir(String scenario) {
            return runtimeRootDir.resolve(scenario).toAbsolutePath().normalize();
        }

        public Path scenarioVectorStorePath(String scenario) {
            return scenarioRuntimeDir(scenario).resolve(vectorStoreFileName);
        }

        public Path scenarioSqlitePath(String scenario) {
            return scenarioRuntimeDir(scenario).resolve(sqliteFileName);
        }
    }

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
