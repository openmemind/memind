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
package com.openmemind.ai.memory.server.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.llm.rerank.LlmReranker;
import com.openmemind.ai.memory.core.llm.rerank.NoopReranker;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("Memind server rerank configuration")
class MemindServerRerankConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(MemindServerRerankConfiguration.class));

    @Test
    @DisplayName("Creates LlmReranker when rerank properties are configured")
    void createsLlmRerankerWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "memind.rerank.base-url=https://rerank.example",
                        "memind.rerank.api-key=test-key",
                        "memind.rerank.model=test-model")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(Reranker.class);
                            assertThat(context.getBean(Reranker.class))
                                    .isInstanceOf(LlmReranker.class);
                            assertThat(
                                            ReflectionTestUtils.getField(
                                                    context.getBean(Reranker.class), "rerankUrl"))
                                    .isEqualTo("https://rerank.example/v1/rerank");
                            assertThat(
                                            ReflectionTestUtils.getField(
                                                    context.getBean(Reranker.class), "apiKey"))
                                    .isEqualTo("test-key");
                            assertThat(
                                            ReflectionTestUtils.getField(
                                                    context.getBean(Reranker.class), "model"))
                                    .isEqualTo("test-model");
                        });
    }

    @Test
    @DisplayName("Does not create reranker when required properties are blank")
    void doesNotCreateRerankerWhenPropertiesBlank() {
        contextRunner
                .withPropertyValues(
                        "memind.rerank.base-url=",
                        "memind.rerank.api-key=test-key",
                        "memind.rerank.model=test-model")
                .run(context -> assertThat(context).doesNotHaveBean(Reranker.class));
    }

    @Test
    @DisplayName("Backs off when user already provides a reranker bean")
    void backsOffWhenUserProvidesRerankerBean() {
        contextRunner
                .withPropertyValues(
                        "memind.rerank.base-url=https://rerank.example",
                        "memind.rerank.api-key=test-key",
                        "memind.rerank.model=test-model")
                .withUserConfiguration(UserProvidedRerankerConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(Reranker.class);
                            assertThat(context.getBean(Reranker.class))
                                    .isInstanceOf(NoopReranker.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserProvidedRerankerConfig {

        @Bean
        Reranker customReranker() {
            return new NoopReranker();
        }
    }
}
