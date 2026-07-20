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

import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.plugin.ai.spring.SpringAiStructuredChatClient;
import com.openmemind.ai.memory.plugin.ai.spring.autoconfigure.MemindAiProperties.AiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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
    @DisplayName("creates configured chat clients and slot routing")
    void createsConfiguredChatClientsAndSlotRouting() {
        contextRunner
                .withPropertyValues(
                        "memind.ai.chat.default-client=ds",
                        "memind.ai.chat.clients.ds.provider=openai",
                        "memind.ai.chat.clients.ds.base-url=https://api.deepseek.com",
                        "memind.ai.chat.clients.ds.api-key=test-key",
                        "memind.ai.chat.clients.ds.model=deepseek-chat",
                        "memind.ai.chat.clients.ds-reasoner.provider=openai",
                        "memind.ai.chat.clients.ds-reasoner.base-url=https://api.deepseek.com",
                        "memind.ai.chat.clients.ds-reasoner.api-key=test-key",
                        "memind.ai.chat.clients.ds-reasoner.model=deepseek-reasoner",
                        "memind.ai.chat.slots.INSIGHT_GENERATOR=ds-reasoner")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(MemindChatClients.class);
                            assertThat(context).hasSingleBean(StructuredChatClient.class);

                            MemindChatClients clients = context.getBean(MemindChatClients.class);
                            assertThat(clients.defaultClientId()).isEqualTo("ds");
                            assertThat(clients.clientIds())
                                    .containsExactlyInAnyOrder("ds", "ds-reasoner");
                            assertThat(clients.slotClientIds())
                                    .containsEntry(ChatClientSlot.INSIGHT_GENERATOR, "ds-reasoner");
                            assertThat(clients.slotClients())
                                    .containsKey(ChatClientSlot.INSIGHT_GENERATOR);
                        });
    }

    @Test
    @DisplayName("creates OpenAI chat client from Spring AI defaults")
    void createsOpenAiChatClientFromSpringAiDefaults() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.openai.base-url=https://openrouter.ai/api",
                        "spring.ai.openai.api-key=test-key",
                        "spring.ai.openai.chat.options.model=openai/gpt-4o-mini",
                        "memind.ai.chat.default-client=openai",
                        "memind.ai.chat.clients.openai.provider=openai")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(MemindChatClients.class);
                            assertThat(context.getBean(MemindChatClients.class).defaultClientId())
                                    .isEqualTo("openai");
                        });
    }

    @Test
    @DisplayName("preserves Spring AI Anthropic chat options")
    void preservesSpringAiAnthropicChatOptions() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.anthropic.api-key=test-key",
                        "spring.ai.anthropic.chat.model=claude-test",
                        "spring.ai.anthropic.chat.stop-sequences[0]=HALT",
                        "memind.ai.chat.default-client=anthropic",
                        "memind.ai.chat.clients.anthropic.provider=anthropic")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();

                            AnthropicChatModel model =
                                    chatModel(
                                            context.getBean(MemindChatClients.class)
                                                    .defaultClient(),
                                            AnthropicChatModel.class);

                            assertThat(model.getOptions().getStopSequences())
                                    .containsExactly("HALT");
                        });
    }

    @Test
    @DisplayName("preserves Spring AI Google chat options")
    void preservesSpringAiGoogleChatOptions() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.google.genai.api-key=test-key",
                        "spring.ai.google.genai.chat.model=gemini-test",
                        "spring.ai.google.genai.chat.response-mime-type=application/json",
                        "spring.ai.google.genai.chat.response-schema={\"type\":\"object\"}",
                        "memind.ai.chat.default-client=google",
                        "memind.ai.chat.clients.google.provider=google")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();

                            GoogleGenAiChatModel model =
                                    chatModel(
                                            context.getBean(MemindChatClients.class)
                                                    .defaultClient(),
                                            GoogleGenAiChatModel.class);

                            assertThat(model.getOptions().getResponseMimeType())
                                    .isEqualTo("application/json");
                            assertThat(model.getOptions().getResponseSchema())
                                    .isEqualTo("{\"type\":\"object\"}");
                        });
    }

    @Test
    @DisplayName("binds canonical providers to enum values")
    void bindsCanonicalProvidersToEnumValues() {
        contextRunner
                .withPropertyValues(
                        "memind.ai.chat.default-client=openai",
                        "memind.ai.chat.clients.openai.provider=openai",
                        "memind.ai.chat.clients.openai.base-url=https://api.openai.com",
                        "memind.ai.chat.clients.openai.api-key=test-key",
                        "memind.ai.chat.clients.openai.model=gpt-4o-mini",
                        "memind.ai.chat.clients.anthropic.provider=anthropic",
                        "memind.ai.chat.clients.google.provider=google",
                        "memind.ai.chat.clients.ollama.provider=ollama")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            MemindAiProperties properties =
                                    context.getBean(MemindAiProperties.class);
                            assertThat(
                                            properties
                                                    .getChat()
                                                    .getClients()
                                                    .get("openai")
                                                    .getProvider())
                                    .isEqualTo(AiProvider.OPENAI);
                            assertThat(
                                            properties
                                                    .getChat()
                                                    .getClients()
                                                    .get("anthropic")
                                                    .getProvider())
                                    .isEqualTo(AiProvider.ANTHROPIC);
                            assertThat(
                                            properties
                                                    .getChat()
                                                    .getClients()
                                                    .get("google")
                                                    .getProvider())
                                    .isEqualTo(AiProvider.GOOGLE);
                            assertThat(
                                            properties
                                                    .getChat()
                                                    .getClients()
                                                    .get("ollama")
                                                    .getProvider())
                                    .isEqualTo(AiProvider.OLLAMA);
                        });
    }

    @Test
    @DisplayName("does not create or validate unreferenced chat clients")
    void doesNotCreateOrValidateUnreferencedChatClients() {
        contextRunner
                .withPropertyValues(
                        "memind.ai.chat.default-client=ds",
                        "memind.ai.chat.clients.ds.provider=openai",
                        "memind.ai.chat.clients.ds.base-url=https://api.deepseek.com",
                        "memind.ai.chat.clients.ds.api-key=test-key",
                        "memind.ai.chat.clients.ds.model=deepseek-chat",
                        "memind.ai.chat.clients.unused.provider=openai")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(MemindChatClients.class).clientIds())
                                    .containsExactly("ds");
                        });
    }

    @Test
    @DisplayName("fails fast when default client is not configured")
    void failsWhenDefaultClientIsMissing() {
        contextRunner
                .withPropertyValues(
                        "memind.ai.chat.default-client=missing",
                        "memind.ai.chat.clients.ds.provider=openai",
                        "memind.ai.chat.clients.ds.base-url=https://api.deepseek.com",
                        "memind.ai.chat.clients.ds.api-key=test-key",
                        "memind.ai.chat.clients.ds.model=deepseek-chat")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasMessageContaining(
                                                "memind.ai.chat.default-client 'missing'"));
    }

    @Test
    @DisplayName("fails fast when a slot references an unknown client")
    void failsWhenSlotReferencesUnknownClient() {
        contextRunner
                .withPropertyValues(
                        "memind.ai.chat.default-client=ds",
                        "memind.ai.chat.clients.ds.provider=openai",
                        "memind.ai.chat.clients.ds.base-url=https://api.deepseek.com",
                        "memind.ai.chat.clients.ds.api-key=test-key",
                        "memind.ai.chat.clients.ds.model=deepseek-chat",
                        "memind.ai.chat.slots.INSIGHT_GENERATOR=missing")
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasMessageContaining(
                                                "memind.ai.chat.slots.INSIGHT_GENERATOR"));
    }

    @Test
    @DisplayName("creates configured embedding model")
    void createsConfiguredEmbeddingModel() {
        vectorContextRunner
                .withPropertyValues(
                        "memind.ai.embedding.client=embedding",
                        "memind.ai.embedding.clients.embedding.provider=openai",
                        "memind.ai.embedding.clients.embedding.base-url=https://api.siliconflow.cn/v1",
                        "memind.ai.embedding.clients.embedding.api-key=test-key",
                        "memind.ai.embedding.clients.embedding.model=BAAI/bge-m3",
                        "memind.ai.embedding.clients.embedding.dimensions=1024")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(EmbeddingModel.class);
                        });
    }

    @Test
    @DisplayName("preserves Spring AI Google embedding options")
    void preservesSpringAiGoogleEmbeddingOptions() {
        vectorContextRunner
                .withPropertyValues(
                        "spring.ai.google.genai.embedding.api-key=test-key",
                        "spring.ai.google.genai.embedding.text.model=text-embedding-test",
                        "spring.ai.google.genai.embedding.text.task-type=RETRIEVAL_DOCUMENT",
                        "spring.ai.google.genai.embedding.text.title=Document title",
                        "memind.ai.embedding.client=google",
                        "memind.ai.embedding.clients.google.provider=google")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();

                            GoogleGenAiTextEmbeddingModel model =
                                    context.getBean(GoogleGenAiTextEmbeddingModel.class);

                            assertThat(model.options.getTaskType())
                                    .isEqualTo(
                                            GoogleGenAiTextEmbeddingOptions.TaskType
                                                    .RETRIEVAL_DOCUMENT);
                            assertThat(model.options.getTitle()).isEqualTo("Document title");
                        });
    }

    @Test
    @DisplayName("creates OpenAI embedding model from Spring AI defaults")
    void createsOpenAiEmbeddingModelFromSpringAiDefaults() {
        vectorContextRunner
                .withPropertyValues(
                        "spring.ai.openai.base-url=https://openrouter.ai/api",
                        "spring.ai.openai.api-key=test-key",
                        "spring.ai.openai.embedding.options.model=openai/text-embedding-3-small",
                        "memind.ai.embedding.client=openai",
                        "memind.ai.embedding.clients.openai.provider=openai")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(EmbeddingModel.class);
                        });
    }

    private static <T extends ChatModel> T chatModel(
            StructuredChatClient structuredChatClient, Class<T> modelType) {
        assertThat(structuredChatClient).isInstanceOf(SpringAiStructuredChatClient.class);
        Object chatClient = ReflectionTestUtils.getField(structuredChatClient, "chatClient");
        assertThat(chatClient).isInstanceOf(ChatClient.class);
        Object defaultChatClientRequest =
                ReflectionTestUtils.getField(chatClient, "defaultChatClientRequest");
        Object chatModel = ReflectionTestUtils.getField(defaultChatClientRequest, "chatModel");
        assertThat(chatModel).isInstanceOf(modelType);
        return modelType.cast(chatModel);
    }
}
