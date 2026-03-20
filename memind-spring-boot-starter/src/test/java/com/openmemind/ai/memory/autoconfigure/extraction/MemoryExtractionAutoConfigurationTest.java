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
package com.openmemind.ai.memory.autoconfigure.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.LlmConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ConversationContentProcessor;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Smoke tests for {@link MemoryExtractionAutoConfiguration}.
 *
 */
@DisplayName("MemoryExtractionAutoConfiguration Test")
class MemoryExtractionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(MemoryExtractionAutoConfiguration.class));

    @Nested
    @DisplayName("Default Configuration")
    class Defaults {

        @Test
        @DisplayName(
                "Automatically register MemoryExtractor when all required dependencies are"
                        + " provided")
        void registersMemoryExtractorWhenAllDependenciesPresent() {
            contextRunner
                    .withUserConfiguration(RequiredDependenciesConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThat(context).hasSingleBean(MemoryExtractor.class);
                            });
        }

        @Test
        @DisplayName(
                "Do not register llmConversationChunker bean when chunking strategy is not LLM")
        void doesNotRegisterLlmChunkerBeanWhenStrategyIsNotLlm() {
            contextRunner
                    .withUserConfiguration(RequiredDependenciesConfig.class)
                    .withPropertyValues("memind.extraction.chunking.strategy=FIXED_SIZE")
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThat(context)
                                        .hasSingleBean(ConversationContentProcessor.class)
                                        .doesNotHaveBean(LlmConversationChunker.class)
                                        .doesNotHaveBean("llmConversationChunker");
                            });
        }
    }

    @Nested
    @DisplayName("Custom Bean Override")
    class CustomBeans {

        @Test
        @DisplayName("Do not register default when user-defined MemoryExtractor is present")
        void userMemoryExtractorTakesPrecedence() {
            contextRunner
                    .withUserConfiguration(
                            RequiredDependenciesConfig.class, CustomMemoryExtractorConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThat(context).hasSingleBean(MemoryExtractor.class);
                            });
        }
    }

    @Configuration
    static class RequiredDependenciesConfig {

        @Bean
        MemoryStore memoryStore() {
            return mock(MemoryStore.class);
        }

        @Bean
        MemoryVector memoryVector() {
            return mock(MemoryVector.class);
        }

        @Bean
        ChatClient.Builder chatClientBuilder() {
            var builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(mock(ChatClient.class));
            return builder;
        }
    }

    @Configuration
    static class CustomMemoryExtractorConfig {
        @Bean
        MemoryExtractor memoryExtractor() {
            return mock(MemoryExtractor.class);
        }
    }
}
