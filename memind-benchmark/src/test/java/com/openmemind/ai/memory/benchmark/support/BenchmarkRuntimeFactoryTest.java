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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkRuntimeFactoryTest {

    @TempDir Path tempDir;

    @Test
    void prepareRuntimeLayoutCreatesRunDirectoryAndSqliteParent() throws Exception {
        BenchmarkSettings settings =
                BenchmarkSettings.load(
                        new MapValueResolver(
                                Map.of(
                                        "memind.benchmark.openai.api-key",
                                        "key",
                                        "memind.benchmark.output-dir",
                                        tempDir.toString()),
                                Map.of()));

        Path runtimeDir = tempDir.resolve("locomo").resolve("run-1").resolve("runtime");
        BenchmarkRuntimeFactory.prepareRuntimeLayout(runtimeDir, settings);

        assertThat(Files.isDirectory(runtimeDir)).isTrue();
        assertThat(Files.isDirectory(runtimeDir.resolve("vector-store"))).isTrue();
        assertThat(Files.isDirectory(runtimeDir.resolve("memind.db").getParent())).isTrue();
    }

    @Test
    void createBuildsClosableRuntimeWithSqliteDefaults() throws Exception {
        BenchmarkSettings settings =
                BenchmarkSettings.load(
                        new MapValueResolver(
                                Map.of(
                                        "memind.benchmark.openai.api-key",
                                        "key",
                                        "memind.benchmark.output-dir",
                                        tempDir.toString()),
                                Map.of()));

        Path runtimeDir = tempDir.resolve("longmemeval").resolve("run-1").resolve("runtime");
        try (BenchmarkRuntime runtime =
                BenchmarkRuntimeFactory.create(
                        "longmemeval", runtimeDir, MemoryBuildOptions.defaults(), settings)) {
            assertThat(runtime.memory()).isNotNull();
            assertThat(runtime.store()).isNotNull();
            assertThat(runtime.settings()).isSameAs(settings);
            assertThat(runtime.runtimeDir()).isEqualTo(runtimeDir.toAbsolutePath().normalize());
        }
    }

    record MapValueResolver(Map<String, String> properties, Map<String, String> environment)
            implements BenchmarkSettings.ValueResolver {

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
