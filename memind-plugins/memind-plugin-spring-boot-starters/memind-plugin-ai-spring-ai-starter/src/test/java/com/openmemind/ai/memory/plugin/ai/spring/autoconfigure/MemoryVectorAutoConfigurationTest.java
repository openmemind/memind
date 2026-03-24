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

import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.plugin.ai.spring.FileSimpleVectorStore;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiMemoryVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayName("MemoryVectorAutoConfiguration Test")
class MemoryVectorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(SpringAiVectorAutoConfiguration.class));

    @Nested
    @DisplayName("Default Configuration")
    class Defaults {

        @Test
        @DisplayName("Back off cleanly when EmbeddingModel is absent")
        void backsOffWhenEmbeddingModelMissing() {
            contextRunner.run(
                    context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(VectorStore.class);
                        assertThat(context).doesNotHaveBean(MemoryVector.class);
                    });
        }

        @Test
        @DisplayName(
                "Automatically register VectorStore and MemoryVector when EmbeddingModel is"
                        + " present")
        void registersBeansWhenEmbeddingModelPresent() {
            contextRunner
                    .withUserConfiguration(EmbeddingModelConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasSingleBean(VectorStore.class);
                                assertThat(context).hasSingleBean(MemoryVector.class);
                                assertThat(context.getBean(VectorStore.class))
                                        .isInstanceOf(FileSimpleVectorStore.class);
                                assertThat(context.getBean(MemoryVector.class))
                                        .isInstanceOf(SpringAiMemoryVector.class);
                            });
        }
    }

    @Nested
    @DisplayName("Custom Bean Override")
    class CustomBeans {

        @Test
        @DisplayName("Do not register default when user defines VectorStore")
        void userVectorStoreTakesPrecedence() {
            contextRunner
                    .withUserConfiguration(
                            EmbeddingModelConfig.class, CustomVectorStoreConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasSingleBean(VectorStore.class);
                                assertThat(context.getBean(VectorStore.class))
                                        .isSameAs(
                                                context.getBean(CustomVectorStoreConfig.class)
                                                        .customStore);
                            });
        }

        @Test
        @DisplayName(
                "Do not register default VectorStore or require EmbeddingModel when user defines"
                        + " MemoryVector")
        void userMemoryVectorPreventsDefaultVectorStoreCreation() {
            contextRunner
                    .withUserConfiguration(CustomMemoryVectorConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThat(context).hasSingleBean(MemoryVector.class);
                                assertThat(context).doesNotHaveBean(VectorStore.class);
                                assertThat(context.getBean(MemoryVector.class))
                                        .isSameAs(
                                                context.getBean(CustomMemoryVectorConfig.class)
                                                        .customVector);
                            });
        }
    }

    @Configuration
    static class EmbeddingModelConfig {
        @Bean
        EmbeddingModel embeddingModel() {
            return Mockito.mock(EmbeddingModel.class);
        }
    }

    @Configuration
    static class CustomVectorStoreConfig {
        VectorStore customStore = Mockito.mock(VectorStore.class);

        @Bean
        VectorStore vectorStore() {
            return customStore;
        }
    }

    @Configuration
    static class CustomMemoryVectorConfig {
        MemoryVector customVector = Mockito.mock(MemoryVector.class);

        @Bean
        MemoryVector memoryVector() {
            return customVector;
        }
    }
}
