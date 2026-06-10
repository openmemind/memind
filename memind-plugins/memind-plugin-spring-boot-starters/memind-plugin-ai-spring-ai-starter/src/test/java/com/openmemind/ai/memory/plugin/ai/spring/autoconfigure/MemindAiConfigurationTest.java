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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
}
