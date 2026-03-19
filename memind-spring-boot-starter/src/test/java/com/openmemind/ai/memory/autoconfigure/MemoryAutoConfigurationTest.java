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
package com.openmemind.ai.memory.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.DefaultMemory;
import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.store.MemoryStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayName("MemoryAutoConfiguration Test")
class MemoryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(MemoryAutoConfiguration.class));

    @Nested
    @DisplayName("Default Configuration")
    class Defaults {

        @Test
        @DisplayName("Register DefaultMemory when all dependencies exist")
        void registersMemory4jBean() {
            contextRunner
                    .withUserConfiguration(DependenciesConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasSingleBean(Memory.class);
                                assertThat(context.getBean(Memory.class))
                                        .isInstanceOf(DefaultMemory.class);
                            });
        }
    }

    @Nested
    @DisplayName("Custom Bean Override")
    class CustomBeans {

        @Test
        @DisplayName("Do not register default when user customizes Memory")
        void userMemory4jTakesPrecedence() {
            contextRunner
                    .withUserConfiguration(DependenciesConfig.class, CustomMemory4jConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasSingleBean(Memory.class);
                                assertThat(context.getBean(Memory.class))
                                        .isNotInstanceOf(DefaultMemory.class);
                            });
        }
    }

    @Configuration
    static class DependenciesConfig {
        @Bean
        ChatClient.Builder chatClientBuilder() {
            return Mockito.mock(ChatClient.Builder.class);
        }

        @Bean
        MemoryExtractor memoryExtractor() {
            return Mockito.mock(MemoryExtractor.class);
        }

        @Bean
        MemoryRetriever memoryRetriever() {
            return Mockito.mock(MemoryRetriever.class);
        }

        @Bean
        MemoryStore memoryStore() {
            return Mockito.mock(MemoryStore.class);
        }
    }

    @Configuration
    static class CustomMemory4jConfig {
        @Bean
        Memory memind() {
            return Mockito.mock(Memory.class);
        }
    }
}
