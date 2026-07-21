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

import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("Memind AI configuration")
class MemindAiConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MemindAiClientAutoConfiguration.class,
                                    SpringAiLlmAutoConfiguration.class));

    private final ApplicationContextRunner vectorContextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MemindAiClientAutoConfiguration.class,
                                    SpringAiVectorAutoConfiguration.class));

    @Test
    @DisplayName("creates configured chat clients and slot routing from Spring AI beans")
    void createsConfiguredChatClientsAndSlotRoutingFromSpringAiBeans() {
        contextRunner
                .withUserConfiguration(ChatBeansConfig.class)
                .withPropertyValues(
                        "memind.ai.chat.default=defaultChatClient",
                        "memind.ai.chat.slots.ITEM_EXTRACTION=extractionChatClient",
                        "memind.ai.chat.slots.INSIGHT_GENERATOR=reasoningChatModel")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(MemindChatClients.class);
                            assertThat(context).hasSingleBean(StructuredChatClient.class);

                            MemindChatClients clients = context.getBean(MemindChatClients.class);
                            assertThat(clients.defaultClientId()).isEqualTo("defaultChatClient");
                            assertThat(clients.clientIds())
                                    .containsExactlyInAnyOrder(
                                            "defaultChatClient",
                                            "extractionChatClient",
                                            "reasoningChatModel");
                            assertThat(clients.slotClientIds())
                                    .containsEntry(
                                            ChatClientSlot.ITEM_EXTRACTION, "extractionChatClient")
                                    .containsEntry(
                                            ChatClientSlot.INSIGHT_GENERATOR, "reasoningChatModel");
                            assertThat(clients.slotClients())
                                    .containsKey(ChatClientSlot.ITEM_EXTRACTION)
                                    .containsKey(ChatClientSlot.INSIGHT_GENERATOR);
                            assertThat(chatClient(clients.defaultClient()))
                                    .isSameAs(context.getBean("defaultChatClient"));
                            assertThat(
                                            chatClient(
                                                    clients.slotClients()
                                                            .get(ChatClientSlot.ITEM_EXTRACTION)))
                                    .isSameAs(context.getBean("extractionChatClient"));
                        });
    }

    @Test
    @DisplayName("creates configured chat clients from legacy default-client alias")
    void createsConfiguredChatClientsFromLegacyDefaultClientAlias() {
        contextRunner
                .withUserConfiguration(ChatBeansConfig.class)
                .withPropertyValues("memind.ai.chat.default-client=defaultChatClient")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            MemindChatClients clients = context.getBean(MemindChatClients.class);
                            assertThat(clients.defaultClientId()).isEqualTo("defaultChatClient");
                            assertThat(chatClient(clients.defaultClient()))
                                    .isSameAs(context.getBean("defaultChatClient"));
                        });
    }

    @Test
    @DisplayName("fails clearly when configured default chat bean is missing")
    void failsWhenConfiguredDefaultChatBeanIsMissing() {
        contextRunner
                .withPropertyValues("memind.ai.chat.default=missingChatClient")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasMessageContaining(
                                                "memind.ai.chat.default references missing Spring"
                                                        + " AI ChatClient or ChatModel bean"
                                                        + " 'missingChatClient'"));
    }

    @Test
    @DisplayName("fails clearly when configured slot chat bean is missing")
    void failsWhenConfiguredSlotChatBeanIsMissing() {
        contextRunner
                .withUserConfiguration(ChatBeansConfig.class)
                .withPropertyValues(
                        "memind.ai.chat.default=defaultChatClient",
                        "memind.ai.chat.slots.INSIGHT_GENERATOR=missingChatClient")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasMessageContaining(
                                                "memind.ai.chat.slots.INSIGHT_GENERATOR"
                                                        + " references missing Spring AI"
                                                        + " ChatClient or ChatModel bean"
                                                        + " 'missingChatClient'"));
    }

    @Test
    @DisplayName("fails clearly when configured chat bean has an unsupported type")
    void failsWhenConfiguredChatBeanHasUnsupportedType() {
        contextRunner
                .withUserConfiguration(InvalidChatBeanConfig.class)
                .withPropertyValues("memind.ai.chat.default=notAChatClient")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasMessageContaining(
                                                "memind.ai.chat.default references bean"
                                                        + " 'notAChatClient' that is not a Spring"
                                                        + " AI ChatClient or ChatModel"));
    }

    @Test
    @DisplayName("creates configured embedding model from Spring AI bean")
    void createsConfiguredEmbeddingModelFromSpringAiBean() {
        vectorContextRunner
                .withUserConfiguration(EmbeddingBeansConfig.class)
                .withPropertyValues("memind.ai.embedding.default=configuredEmbeddingModel")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasBean("memindEmbeddingModel");
                            assertThat(context.getBean("memindEmbeddingModel"))
                                    .isSameAs(context.getBean("configuredEmbeddingModel"));
                            assertThat(context.getBean(EmbeddingModel.class))
                                    .isSameAs(context.getBean("memindEmbeddingModel"));
                        });
    }

    @Test
    @DisplayName("creates configured embedding model from legacy client alias")
    void createsConfiguredEmbeddingModelFromLegacyClientAlias() {
        vectorContextRunner
                .withUserConfiguration(EmbeddingBeansConfig.class)
                .withPropertyValues("memind.ai.embedding.client=configuredEmbeddingModel")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean("memindEmbeddingModel"))
                                    .isSameAs(context.getBean("configuredEmbeddingModel"));
                        });
    }

    @Test
    @DisplayName("fails clearly when configured embedding bean is missing")
    void failsWhenConfiguredEmbeddingBeanIsMissing() {
        vectorContextRunner
                .withPropertyValues("memind.ai.embedding.default=missingEmbeddingModel")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasMessageContaining(
                                                "memind.ai.embedding.default references missing"
                                                        + " Spring AI EmbeddingModel bean"
                                                        + " 'missingEmbeddingModel'"));
    }

    @Test
    @DisplayName("fails clearly when configured embedding bean has an unsupported type")
    void failsWhenConfiguredEmbeddingBeanHasUnsupportedType() {
        vectorContextRunner
                .withUserConfiguration(InvalidEmbeddingBeanConfig.class)
                .withPropertyValues("memind.ai.embedding.default=notAnEmbeddingModel")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasMessageContaining(
                                                "memind.ai.embedding.default references bean"
                                                        + " 'notAnEmbeddingModel' that is not a"
                                                        + " Spring AI EmbeddingModel"));
    }

    private static ChatClient chatClient(StructuredChatClient structuredChatClient) {
        assertThat(structuredChatClient).isInstanceOf(SpringAiStructuredChatClient.class);
        Object chatClient = ReflectionTestUtils.getField(structuredChatClient, "chatClient");
        assertThat(chatClient).isInstanceOf(ChatClient.class);
        return (ChatClient) chatClient;
    }

    @Configuration
    static class ChatBeansConfig {

        @Bean
        ChatClient defaultChatClient() {
            return mock(ChatClient.class);
        }

        @Bean
        ChatClient extractionChatClient() {
            return mock(ChatClient.class);
        }

        @Bean
        ChatModel reasoningChatModel() {
            return mock(ChatModel.class);
        }
    }

    @Configuration
    static class InvalidChatBeanConfig {

        @Bean
        String notAChatClient() {
            return "not-a-chat-client";
        }
    }

    @Configuration
    static class EmbeddingBeansConfig {

        @Bean
        EmbeddingModel configuredEmbeddingModel() {
            return mock(EmbeddingModel.class);
        }

        @Bean
        EmbeddingModel otherEmbeddingModel() {
            return mock(EmbeddingModel.class);
        }
    }

    @Configuration
    static class InvalidEmbeddingBeanConfig {

        @Bean
        String notAnEmbeddingModel() {
            return "not-an-embedding-model";
        }
    }
}
