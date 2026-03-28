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
package com.openmemind.ai.memory.core.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ChatClientRegistryTest {

    private final StructuredChatClient defaultClient = new StubClient("default");
    private final StructuredChatClient smartClient = new StubClient("smart");
    private final StructuredChatClient fastClient = new StubClient("fast");

    @Test
    void resolveReturnsBoundClientForSlot() {
        var registry =
                new ChatClientRegistry(
                        defaultClient, Map.of(ChatClientSlot.ITEM_EXTRACTION, smartClient));

        assertThat(registry.resolve(ChatClientSlot.ITEM_EXTRACTION)).isSameAs(smartClient);
    }

    @Test
    void resolveFallsBackToDefaultForUnboundSlot() {
        var registry =
                new ChatClientRegistry(
                        defaultClient, Map.of(ChatClientSlot.ITEM_EXTRACTION, smartClient));

        assertThat(registry.resolve(ChatClientSlot.QUERY_EXPANDER)).isSameAs(defaultClient);
    }

    @Test
    void resolveReturnsDefaultWhenNoSlotsBound() {
        var registry = new ChatClientRegistry(defaultClient, Map.of());

        assertThat(registry.resolve(ChatClientSlot.INSIGHT_GENERATOR)).isSameAs(defaultClient);
    }

    @Test
    void multipleSlotsBoundToSameClient() {
        var registry =
                new ChatClientRegistry(
                        defaultClient,
                        Map.of(
                                ChatClientSlot.ITEM_EXTRACTION, smartClient,
                                ChatClientSlot.INSIGHT_GENERATOR, smartClient,
                                ChatClientSlot.QUERY_EXPANDER, fastClient));

        assertThat(registry.resolve(ChatClientSlot.ITEM_EXTRACTION)).isSameAs(smartClient);
        assertThat(registry.resolve(ChatClientSlot.INSIGHT_GENERATOR)).isSameAs(smartClient);
        assertThat(registry.resolve(ChatClientSlot.QUERY_EXPANDER)).isSameAs(fastClient);
        assertThat(registry.resolve(ChatClientSlot.SUFFICIENCY_GATE)).isSameAs(defaultClient);
    }

    @Test
    void constructorRejectsNullDefaultClient() {
        assertThatThrownBy(() -> new ChatClientRegistry(null, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsNullSlotClients() {
        assertThatThrownBy(() -> new ChatClientRegistry(defaultClient, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defaultClientAccessor() {
        var registry = new ChatClientRegistry(defaultClient, Map.of());

        assertThat(registry.defaultClient()).isSameAs(defaultClient);
    }

    private static final class StubClient implements StructuredChatClient {

        private final String name;

        private StubClient(String name) {
            this.name = name;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.just(name);
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            return Mono.empty();
        }

        @Override
        public String toString() {
            return "StubClient[" + name + "]";
        }
    }
}
