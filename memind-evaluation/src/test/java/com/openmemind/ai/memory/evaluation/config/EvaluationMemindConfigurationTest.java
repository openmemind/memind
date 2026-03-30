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
package com.openmemind.ai.memory.evaluation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.plugin.ai.spring.autoconfigure.SpringAiLlmAutoConfiguration;
import com.openmemind.ai.memory.plugin.ai.spring.autoconfigure.SpringAiVectorAutoConfiguration;
import com.openmemind.ai.memory.plugin.jdbc.autoconfigure.JdbcPluginAutoConfiguration;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayName("EvaluationMemindConfiguration Test")
class EvaluationMemindConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    SpringAiLlmAutoConfiguration.class,
                                    SpringAiVectorAutoConfiguration.class,
                                    JdbcPluginAutoConfiguration.class))
                    .withUserConfiguration(
                            MockAiProviders.class,
                            EvaluationMemindConfiguration.class,
                            EvaluationConfiguration.class)
                    .withPropertyValues(
                            "evaluation.system.memind.storage.sqlite.path=target/eval-tests/memind.db",
                            "evaluation.system.memind.extraction.boundary.max-messages=30",
                            "evaluation.system.memind.retrieval.timeout=PT5M",
                            "evaluation.system.memind.retrieval.rerank.enabled=false",
                            "memind.vector.store-path=target/eval-tests/vector-store.json");

    @Test
    @DisplayName("explicit evaluation configuration builds the memind runtime")
    void explicitEvaluationConfigurationBuildsMemindRuntime() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context).hasSingleBean(MemoryStore.class);
                    assertThat(context).hasSingleBean(MemoryBuffer.class);
                    assertThat(context).hasSingleBean(MemoryTextSearch.class);
                    assertThat(context).hasSingleBean(MemoryBuildOptions.class);
                    assertThat(context).hasSingleBean(RetrievalConfig.class);
                    assertThat(context).hasSingleBean(Reranker.class);
                    assertThat(context).hasSingleBean(Memory.class);
                    assertThat(
                                    context.getBean(MemoryBuildOptions.class)
                                            .extraction()
                                            .rawdata()
                                            .commitDetection()
                                            .maxMessages())
                            .isEqualTo(30);
                    assertThat(context.getBean(RetrievalConfig.class).timeout())
                            .isEqualTo(Duration.ofMinutes(5));
                });
    }

    @Configuration
    static class MockAiProviders {
        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }

        @Bean
        ChatClient.Builder chatClientBuilder() {
            ChatClient.Builder builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(mock(ChatClient.class));
            return builder;
        }
    }
}
