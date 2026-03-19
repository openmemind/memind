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
package com.openmemind.ai.memory.autoconfigure.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.retrieval.DefaultMemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.cache.RetrievalCache;
import com.openmemind.ai.memory.core.retrieval.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierRetriever;
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
 * Smoke tests for {@link MemoryRetrievalAutoConfiguration}.
 *
 */
@DisplayName("MemoryRetrievalAutoConfiguration Test")
class MemoryRetrievalAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(MemoryRetrievalAutoConfiguration.class))
                    .withPropertyValues("memind.retrieval.rerank.enabled=false");

    @Nested
    @DisplayName("Default Configuration")
    class Defaults {

        @Test
        @DisplayName(
                "Automatically register DefaultMemoryRetriever when all required dependencies are"
                        + " provided")
        void registersDefaultMemoryRetrieverWhenAllDependenciesPresent() {
            contextRunner
                    .withUserConfiguration(RequiredDependenciesConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThat(context).hasSingleBean(MemoryRetriever.class);
                                assertThat(context).hasSingleBean(DefaultMemoryRetriever.class);
                                assertThat(context).hasSingleBean(ScoringConfig.class);
                                assertThat(context).hasSingleBean(RetrievalConfig.class);
                                assertThat(context).hasSingleBean(RetrievalCache.class);
                                assertThat(context).hasSingleBean(InsightTierRetriever.class);
                                assertThat(context).hasSingleBean(ItemTierRetriever.class);
                                assertThat(context).hasSingleBean(SimpleRetrievalStrategy.class);
                                assertThat(context).hasSingleBean(Reranker.class);
                            });
        }
    }

    @Nested
    @DisplayName("Custom Bean Override")
    class CustomBeans {

        @Test
        @DisplayName("Do not register default when user defines MemoryRetriever")
        void userMemoryRetrieverTakesPrecedence() {
            contextRunner
                    .withUserConfiguration(
                            RequiredDependenciesConfig.class, CustomMemoryRetrieverConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThat(context).hasSingleBean(MemoryRetriever.class);
                                assertThat(context).doesNotHaveBean(DefaultMemoryRetriever.class);
                                assertThat(context.getBean(MemoryRetriever.class))
                                        .isSameAs(
                                                context.getBean(CustomMemoryRetrieverConfig.class)
                                                        .customRetriever);
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
    static class CustomMemoryRetrieverConfig {

        MemoryRetriever customRetriever = mock(MemoryRetriever.class);

        @Bean
        MemoryRetriever memoryRetriever() {
            return customRetriever;
        }
    }
}
