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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExampleRuntimeFactoryTest {

    @TempDir Path tempDir;

    @Test
    void prepareRuntimeLayoutCreatesScenarioDirectoryAndSqliteParent() throws Exception {
        ExampleSettings settings =
                ExampleSettings.load(
                        "quickstart",
                        new MapValueResolver(
                                Map.of(
                                        "memind.examples.openai.api-key",
                                        "key",
                                        "memind.examples.runtime-root-dir",
                                        tempDir.toString()),
                                Map.of()));

        Path runtimeDir = ExampleRuntimeFactory.prepareRuntimeLayout("quickstart", settings);
        Path sqlitePath =
                Path.of(settings.store().jdbcUrl().substring("jdbc:sqlite:".length()))
                        .toAbsolutePath()
                        .normalize();

        assertThat(runtimeDir)
                .isEqualTo(tempDir.resolve("quickstart").toAbsolutePath().normalize());
        assertThat(Files.isDirectory(runtimeDir)).isTrue();
        assertThat(Files.isDirectory(sqlitePath.getParent())).isTrue();
    }

    @Test
    void detectDialectSupportsTheThreeDocumentedJdbcFlavors() {
        assertThat(ExampleRuntimeFactory.detectDialect("jdbc:sqlite:/tmp/demo.db"))
                .isEqualTo("sqlite");
        assertThat(ExampleRuntimeFactory.detectDialect("jdbc:mysql://localhost:3306/demo"))
                .isEqualTo("mysql");
        assertThat(ExampleRuntimeFactory.detectDialect("jdbc:postgresql://localhost:5432/demo"))
                .isEqualTo("postgresql");
    }

    @Test
    void createBuildsRuntimeWithSqliteDependencies() throws Exception {
        ExampleSettings settings =
                ExampleSettings.load(
                        "quickstart",
                        new MapValueResolver(
                                Map.of(
                                        "memind.examples.openai.api-key",
                                        "key",
                                        "memind.examples.runtime-root-dir",
                                        tempDir.toString()),
                                Map.of()));

        try (ExampleRuntime runtime =
                ExampleRuntimeFactory.create(
                        "quickstart", ExampleMemoryOptions.defaultOptions(), settings)) {
            assertThat(runtime.memory()).isNotNull();
            assertThat(runtime.store()).isNotNull();
            assertThat(runtime.dataLoader()).isNotNull();
            assertThat(runtime.settings()).isSameAs(settings);
            assertThat(runtime.runtimeDir())
                    .isEqualTo(tempDir.resolve("quickstart").toAbsolutePath().normalize());
            assertThat(runtime.jdbcDialect()).isEqualTo("sqlite");
        }
    }

    record MapValueResolver(Map<String, String> properties, Map<String, String> environment)
            implements ExampleSettings.ValueResolver {

        @Override
        public String systemProperty(String key) {
            return properties.get(key);
        }

        @Override
        public String environmentVariable(String key) {
            return environment.get(key);
        }
    }
}
