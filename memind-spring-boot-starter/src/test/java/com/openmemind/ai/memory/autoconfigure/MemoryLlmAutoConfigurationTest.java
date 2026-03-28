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

import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@DisplayName("Memory LLM auto-configuration")
class MemoryLlmAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(MemoryLlmAutoConfiguration.class));

    @Test
    @DisplayName("Create ChatClientRegistry from default and slot-specific client beans")
    void createsChatClientRegistryFromNamedClients() {
        contextRunner
                .withUserConfiguration(MultiClientConfig.class)
                .withPropertyValues(
                        "memind.llm.slots.item-extraction=smartClient",
                        "memind.llm.slots.query-expander=fastClient")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(ChatClientRegistry.class);

                            ChatClientRegistry registry = context.getBean(ChatClientRegistry.class);
                            StructuredChatClient defaultClient =
                                    context.getBean(
                                            "structuredChatClient", StructuredChatClient.class);
                            StructuredChatClient smartClient =
                                    context.getBean("smartClient", StructuredChatClient.class);
                            StructuredChatClient fastClient =
                                    context.getBean("fastClient", StructuredChatClient.class);

                            assertThat(registry.defaultClient()).isSameAs(defaultClient);
                            assertThat(registry.resolve(ChatClientSlot.ITEM_EXTRACTION))
                                    .isSameAs(smartClient);
                            assertThat(registry.resolve(ChatClientSlot.QUERY_EXPANDER))
                                    .isSameAs(fastClient);
                            assertThat(registry.resolve(ChatClientSlot.INSIGHT_GENERATOR))
                                    .isSameAs(defaultClient);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class MultiClientConfig {

        @Bean
        @Primary
        StructuredChatClient structuredChatClient() {
            return new NoopStructuredChatClient();
        }

        @Bean
        StructuredChatClient smartClient() {
            return new NoopStructuredChatClient();
        }

        @Bean
        StructuredChatClient fastClient() {
            return new NoopStructuredChatClient();
        }
    }

    private static final class NoopStructuredChatClient implements StructuredChatClient {

        @Override
        public Mono<String> call(List<com.openmemind.ai.memory.core.llm.ChatMessage> messages) {
            return Mono.empty();
        }

        @Override
        public <T> Mono<T> call(
                List<com.openmemind.ai.memory.core.llm.ChatMessage> messages,
                Class<T> responseType) {
            return Mono.empty();
        }
    }
}
