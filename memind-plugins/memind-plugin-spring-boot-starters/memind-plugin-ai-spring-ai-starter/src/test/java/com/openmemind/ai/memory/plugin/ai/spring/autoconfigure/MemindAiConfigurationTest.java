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
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.ChatRole;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.plugin.ai.spring.multimodel.chat.MultiChatModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

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
    @DisplayName("creates configured chat clients with the default chat model and slot model ids")
    void createsConfiguredChatClientsWithDefaultChatModelAndSlotModelIds() {
        contextRunner
                .withUserConfiguration(DefaultChatModelAndMultiChatModelConfig.class)
                .withPropertyValues(
                        "memind.ai.chat.slots.ITEM_EXTRACTION=default",
                        "memind.ai.chat.slots.INSIGHT_GENERATOR=reasoning")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(MemindChatClients.class);
                            assertThat(context).hasSingleBean(StructuredChatClient.class);

                            MemindChatClients clients = context.getBean(MemindChatClients.class);
                            assertThat(clients.defaultClientId()).isEqualTo("primary");
                            assertThat(clients.clientIds())
                                    .containsExactlyInAnyOrder("primary", "default", "reasoning");
                            assertThat(clients.slotClientIds())
                                    .containsEntry(ChatClientSlot.ITEM_EXTRACTION, "default")
                                    .containsEntry(ChatClientSlot.INSIGHT_GENERATOR, "reasoning");
                            assertThat(clients.defaultClient().call(userMessage()).block())
                                    .isEqualTo("primary-call");
                            assertThat(
                                            clients.slotClients()
                                                    .get(ChatClientSlot.INSIGHT_GENERATOR)
                                                    .call(userMessage())
                                                    .block())
                                    .isEqualTo("reasoning-call");
                        });
    }

    @Test
    @DisplayName("does not create configured chat clients from the removed default property")
    void doesNotCreateConfiguredChatClientsFromRemovedDefaultProperty() {
        contextRunner
                .withUserConfiguration(MultiChatModelConfig.class)
                .withPropertyValues("memind.ai.chat.default=missing")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(MemindChatClients.class);
                        });
    }

    @Test
    @DisplayName("fails clearly when configured slot chat model id is missing")
    void failsWhenConfiguredSlotChatModelIdIsMissing() {
        contextRunner
                .withUserConfiguration(DefaultChatModelAndMultiChatModelConfig.class)
                .withPropertyValues("memind.ai.chat.slots.INSIGHT_GENERATOR=missing")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasMessageContaining(
                                                "memind.ai.chat.slots.INSIGHT_GENERATOR"
                                                        + " references missing chat model id"
                                                        + " 'missing'"));
    }

    @Test
    @DisplayName("fails clearly when configured chat routing has no multi chat model")
    void failsWhenConfiguredChatRoutingHasNoMultiChatModel() {
        contextRunner
                .withUserConfiguration(DefaultChatModelConfig.class)
                .withPropertyValues("memind.ai.chat.slots.INSIGHT_GENERATOR=reasoning")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasMessageContaining(
                                                "memind.ai.chat.slots requires a MultiChatModel"
                                                        + " bean"));
    }

    @Test
    @DisplayName("does not create configured chat clients from the old default-client alias")
    void doesNotCreateConfiguredChatClientsFromOldDefaultClientAlias() {
        contextRunner
                .withUserConfiguration(MultiChatModelConfig.class)
                .withPropertyValues("memind.ai.chat.default-client=default")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(MemindChatClients.class);
                        });
    }

    @Test
    @DisplayName("does not create configured embedding model from the removed default property")
    void doesNotCreateConfiguredEmbeddingModelFromRemovedDefaultProperty() {
        vectorContextRunner
                .withUserConfiguration(DefaultEmbeddingModelConfig.class)
                .withPropertyValues("memind.ai.embedding.default=missing")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean("memindEmbeddingModel");
                            assertThat(context.getBean(EmbeddingModel.class))
                                    .isSameAs(
                                            context.getBean(DefaultEmbeddingModelConfig.class)
                                                    .embeddingModel);
                        });
    }

    @Test
    @DisplayName("does not create configured embedding model from the old client alias")
    void doesNotCreateConfiguredEmbeddingModelFromOldClientAlias() {
        vectorContextRunner
                .withUserConfiguration(DefaultEmbeddingModelConfig.class)
                .withPropertyValues("memind.ai.embedding.client=semantic")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean("memindEmbeddingModel");
                        });
    }

    private static List<ChatMessage> userMessage() {
        return List.of(new ChatMessage(ChatRole.USER, "hello"));
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    @Configuration
    static class DefaultChatModelConfig {

        @Bean
        @Primary
        ChatModel chatModel() {
            return new RecordingChatModel("primary");
        }
    }

    @Configuration
    static class DefaultChatModelAndMultiChatModelConfig {

        @Bean
        @Primary
        ChatModel chatModel() {
            return new RecordingChatModel("primary");
        }

        @Bean
        MultiChatModel multiChatModel() {
            return new MultiChatModel(
                    "default",
                    Map.of(
                            "default",
                            new RecordingChatModel("default"),
                            "reasoning",
                            new RecordingChatModel("reasoning")));
        }
    }

    @Configuration
    static class MultiChatModelConfig {

        @Bean
        MultiChatModel multiChatModel() {
            return new MultiChatModel(
                    "default",
                    Map.of(
                            "default",
                            new RecordingChatModel("default"),
                            "reasoning",
                            new RecordingChatModel("reasoning")));
        }
    }

    @Configuration
    static class DefaultEmbeddingModelConfig {

        private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        @Bean
        EmbeddingModel embeddingModel() {
            return embeddingModel;
        }
    }

    private static final class RecordingChatModel implements ChatModel {

        private final String modelId;

        private final List<Prompt> callPrompts = new ArrayList<>();

        private RecordingChatModel(String modelId) {
            this.modelId = modelId;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            callPrompts.add(prompt);
            return response(modelId + "-call");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(response(modelId + "-stream"));
        }

        @Override
        public ChatOptions getOptions() {
            return ChatOptions.builder().model(modelId).build();
        }
    }
}
