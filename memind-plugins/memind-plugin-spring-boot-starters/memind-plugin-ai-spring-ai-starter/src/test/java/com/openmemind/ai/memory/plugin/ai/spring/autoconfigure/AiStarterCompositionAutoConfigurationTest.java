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
package com.openmemind.ai.memory.plugin.ai.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.buffer.InMemoryConversationBuffer;
import com.openmemind.ai.memory.core.buffer.InMemoryInsightBuffer;
import com.openmemind.ai.memory.core.buffer.InMemoryRecentConversationBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.extraction.MemoryExtractionPipeline;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiMemoryVector;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayName("AI starter composition auto-configuration")
class AiStarterCompositionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(AiStarterApplication.class)
                    .withPropertyValues(
                            "memind.retrieval.rerank.enabled=false",
                            "memind.vector.store-path=/tmp/memind-starter-composition-vector-store.json");

    @Test
    @DisplayName("AI starter only contributes AI infrastructure beans")
    void aiStarterOnlyContributesInfrastructureBeans() {
        contextRunner
                .withUserConfiguration(AiPrerequisitesConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(StructuredChatClient.class);
                            assertThat(context.getBean(StructuredChatClient.class))
                                    .isInstanceOf(SpringAiStructuredChatClient.class);
                            assertThat(context).hasSingleBean(MemoryVector.class);
                            assertThat(context.getBean(MemoryVector.class))
                                    .isInstanceOf(SpringAiMemoryVector.class);
                            assertThat(context).doesNotHaveBean(MemoryExtractionPipeline.class);
                            assertThat(context).doesNotHaveBean(MemoryRetriever.class);
                            assertThat(context).doesNotHaveBean(Memory.class);
                        });
    }

    @Test
    @DisplayName("AI starter still avoids memory pipeline beans when memory buffer is missing")
    void aiStarterBacksOffWithoutMemoryBuffer() {
        contextRunner
                .withUserConfiguration(AiProviderOnlyConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(StructuredChatClient.class);
                            assertThat(context).hasSingleBean(MemoryVector.class);
                            assertThat(context).doesNotHaveBean(MemoryRetriever.class);
                            assertThat(context).doesNotHaveBean(MemoryExtractionPipeline.class);
                            assertThat(context).doesNotHaveBean(Memory.class);
                        });
    }

    @Test
    @DisplayName("AI starter backs off cleanly without Spring AI provider beans")
    void aiStarterBacksOffWithoutProviderBeans() {
        contextRunner
                .withUserConfiguration(StoreOnlyConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(StructuredChatClient.class);
                            assertThat(context).doesNotHaveBean(MemoryVector.class);
                            assertThat(context).doesNotHaveBean(MemoryExtractionPipeline.class);
                            assertThat(context).doesNotHaveBean(MemoryRetriever.class);
                            assertThat(context).doesNotHaveBean(Memory.class);
                        });
    }

    @Configuration
    static class AiPrerequisitesConfig {
        @Bean
        MemoryStore memoryStore() {
            return new InMemoryMemoryStore();
        }

        @Bean
        MemoryBuffer memoryBuffer() {
            return MemoryBuffer.of(
                    new InMemoryInsightBuffer(),
                    new InMemoryConversationBuffer(),
                    new InMemoryRecentConversationBuffer());
        }

        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }

        @Bean
        ChatClient.Builder chatClientBuilder() {
            var builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(mock(ChatClient.class));
            return builder;
        }
    }

    @Configuration
    static class AiProviderOnlyConfig {
        @Bean
        MemoryStore memoryStore() {
            return new InMemoryMemoryStore();
        }

        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }

        @Bean
        ChatClient.Builder chatClientBuilder() {
            var builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(mock(ChatClient.class));
            return builder;
        }
    }

    @Configuration
    static class StoreOnlyConfig {
        @Bean
        MemoryStore memoryStore() {
            return new InMemoryMemoryStore();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
            excludeName = {
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "com.openmemind.ai.memory.plugin.store.mybatis.MemoryMybatisPlusAutoConfiguration"
            })
    static class AiStarterApplication {}
}
