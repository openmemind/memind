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

import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayName("SpringAiLlmAutoConfiguration Test")
class SpringAiLlmAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SpringAiLlmAutoConfiguration.class));

    @Nested
    @DisplayName("Default Configuration")
    class Defaults {

        @Test
        @DisplayName("Back off cleanly when ChatClient.Builder is absent")
        void backsOffWhenChatClientBuilderMissing() {
            contextRunner.run(
                    context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(StructuredChatClient.class);
                    });
        }

        @Test
        @DisplayName("Register StructuredChatClient from ChatClient.Builder when missing")
        void registersStructuredLlmClientWhenChatClientBuilderPresent() {
            contextRunner
                    .withUserConfiguration(ChatClientBuilderConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasSingleBean(StructuredChatClient.class);
                                assertThat(context.getBean(StructuredChatClient.class))
                                        .isInstanceOf(SpringAiStructuredChatClient.class);
                            });
        }
    }

    @Nested
    @DisplayName("Custom Bean Override")
    class CustomBeans {

        @Test
        @DisplayName("Do not register default when user defines StructuredChatClient")
        void userStructuredLlmClientTakesPrecedence() {
            contextRunner
                    .withUserConfiguration(
                            ChatClientBuilderConfig.class, CustomStructuredLlmClientConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasSingleBean(StructuredChatClient.class);
                                assertThat(context.getBean(StructuredChatClient.class))
                                        .isSameAs(
                                                context.getBean(
                                                                CustomStructuredLlmClientConfig
                                                                        .class)
                                                        .customClient);
                            });
        }
    }

    @Configuration
    static class ChatClientBuilderConfig {
        @Bean
        ChatClient.Builder chatClientBuilder() {
            var builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(mock(ChatClient.class));
            return builder;
        }
    }

    @Configuration
    static class CustomStructuredLlmClientConfig {

        StructuredChatClient customClient = mock(StructuredChatClient.class);

        @Bean
        StructuredChatClient structuredLlmClient() {
            return customClient;
        }
    }
}
