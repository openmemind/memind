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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchmarkSettingsTest {

    @Test
    void loadUsesDefaultsAndPropertyOverrides() {
        BenchmarkSettings settings =
                BenchmarkSettings.load(
                        new MapValueResolver(
                                Map.of(
                                        "memind.benchmark.openai.api-key", "test-key",
                                        "memind.benchmark.output-dir", "target/benchmark-output"),
                                Map.of()));

        assertThat(settings.openAi().apiKey()).isEqualTo("test-key");
        assertThat(settings.openAi().baseUrl()).isEqualTo("https://api.openai.com");
        assertThat(settings.openAi().chatModel()).isEqualTo("gpt-4o-mini");
        assertThat(settings.openAi().embeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(settings.jdbc().url())
                .isEqualTo(
                        "jdbc:sqlite:"
                                + Path.of("target", "benchmark-output", "runtime", "memind.db")
                                        .toAbsolutePath()
                                        .normalize());
        assertThat(settings.benchmark().dataDir())
                .isEqualTo(Path.of("data").toAbsolutePath().normalize());
        assertThat(settings.benchmark().outputDir())
                .isEqualTo(Path.of("target", "benchmark-output").toAbsolutePath().normalize());
        assertThat(settings.benchmark().evalModel()).isEqualTo("gpt-4o");
    }

    @Test
    void loadPrefersSystemPropertiesOverEnvironmentValues() {
        BenchmarkSettings settings =
                BenchmarkSettings.load(
                        new MapValueResolver(
                                Map.of(
                                        "memind.benchmark.openai.api-key", "property-key",
                                        "memind.benchmark.eval-model", "property-eval-model"),
                                Map.of(
                                        "OPENAI_API_KEY", "env-key",
                                        "MEMIND_BENCHMARK_EVAL_MODEL", "env-eval-model")));

        assertThat(settings.openAi().apiKey()).isEqualTo("property-key");
        assertThat(settings.benchmark().evalModel()).isEqualTo("property-eval-model");
    }

    @Test
    void loadFailsFastWhenOpenAiKeyIsMissing() {
        assertThatThrownBy(() -> BenchmarkSettings.load(new MapValueResolver(Map.of(), Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
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
