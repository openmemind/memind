package com.openmemind.ai.memory.autoconfigure.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
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
                                assertThat(context.getBean(MemoryExtractor.class))
                                        .isSameAs(
                                                context.getBean(CustomMemoryExtractorConfig.class)
                                                        .customExtractor);
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

        MemoryExtractor customExtractor = mock(MemoryExtractor.class);

        @Bean
        MemoryExtractor memoryExtractor() {
            return customExtractor;
        }
    }
}
