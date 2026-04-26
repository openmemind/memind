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
package com.openmemind.ai.memory.core.retrieval.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LlmLongQueryCondenserTest {

    @Test
    void shouldReturnCondensedQueryFromLlm() {
        var client = new CapturingStructuredChatClient("Alice Q2 budget discussion", null);
        var condenser = new LlmLongQueryCondenser(client);

        StepVerifier.create(
                        condenser.condense(
                                DefaultMemoryId.of("u1", null),
                                "long query about Alice and Q2 budget",
                                List.of("history"),
                                64))
                .assertNext(result -> assertThat(result).isEqualTo("Alice Q2 budget discussion"))
                .verifyComplete();

        assertThat(client.lastMessages().getFirst().content()).contains("retrieval-focused");
        assertThat(client.lastMessages().getLast().content())
                .contains("long query about Alice and Q2 budget");
    }

    @Test
    void shouldReturnEmptyWhenLlmReturnsBlank() {
        var client = new CapturingStructuredChatClient("   ", null);
        var condenser = new LlmLongQueryCondenser(client);

        StepVerifier.create(
                        condenser.condense(
                                DefaultMemoryId.of("u1", null), "long query", List.of(), 64))
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenLlmFails() {
        var client = new CapturingStructuredChatClient(null, new RuntimeException("boom"));
        var condenser = new LlmLongQueryCondenser(client);

        StepVerifier.create(
                        condenser.condense(
                                DefaultMemoryId.of("u1", null), "long query", List.of(), 64))
                .verifyComplete();
    }

    private static final class CapturingStructuredChatClient implements StructuredChatClient {

        private final String condensedQuery;
        private final RuntimeException error;
        private List<ChatMessage> lastMessages = List.of();

        private CapturingStructuredChatClient(String condensedQuery, RuntimeException error) {
            this.condensedQuery = condensedQuery;
            this.error = error;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            lastMessages = List.copyOf(messages);
            if (error != null) {
                return Mono.error(error);
            }
            try {
                Constructor<T> constructor = responseType.getDeclaredConstructor(String.class);
                constructor.setAccessible(true);
                return Mono.just(constructor.newInstance(condensedQuery));
            } catch (ReflectiveOperationException e) {
                return Mono.error(e);
            }
        }

        private List<ChatMessage> lastMessages() {
            return lastMessages;
        }
    }
}
