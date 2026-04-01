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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExampleSettingsTest {

    @Test
    void loadUsesDefaultsAndScenarioSpecificSqlitePath() {
        ExampleSettings settings =
                ExampleSettings.load(
                        "quickstart",
                        new MapValueResolver(
                                Map.of("memind.examples.openai.api-key", "test-openai-key"),
                                Map.of()));

        assertThat(settings.openAi().apiKey()).isEqualTo("test-openai-key");
        assertThat(settings.openAi().baseUrl()).isEqualTo("https://api.openai.com");
        assertThat(settings.openAi().chatModel()).isEqualTo("gpt-4o-mini");
        assertThat(settings.openAi().embeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(settings.rerank().enabled()).isFalse();
        assertThat(settings.store().jdbcUrl())
                .isEqualTo(
                        "jdbc:sqlite:"
                                + Path.of("target", "example-runtime", "quickstart", "memind.db")
                                        .toAbsolutePath()
                                        .normalize());
        assertThat(settings.runtime().runtimeRootDir())
                .isEqualTo(Path.of("target", "example-runtime").toAbsolutePath().normalize());
        assertThat(settings.runtime().vectorStoreFileName()).isEqualTo("vector-store.json");
    }

    @Test
    void loadPrefersSystemPropertiesOverEnvironmentValues() {
        ExampleSettings settings =
                ExampleSettings.load(
                        "tool",
                        new MapValueResolver(
                                Map.of(
                                        "memind.examples.openai.api-key", "property-openai-key",
                                        "memind.examples.openai.chat-model", "property-chat",
                                        "memind.examples.rerank.enabled", "true",
                                        "memind.examples.rerank.api-key", "property-rerank-key"),
                                Map.of(
                                        "OPENAI_API_KEY", "env-openai-key",
                                        "OPENAI_CHAT_MODEL", "env-chat",
                                        "RERANK_API_KEY", "env-rerank-key")));

        assertThat(settings.openAi().apiKey()).isEqualTo("property-openai-key");
        assertThat(settings.openAi().chatModel()).isEqualTo("property-chat");
        assertThat(settings.rerank().enabled()).isTrue();
        assertThat(settings.rerank().apiKey()).isEqualTo("property-rerank-key");
    }

    @Test
    void loadFailsFastWhenOpenAiKeyIsMissing() {
        assertThatThrownBy(
                        () ->
                                ExampleSettings.load(
                                        "quickstart", new MapValueResolver(Map.of(), Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
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
