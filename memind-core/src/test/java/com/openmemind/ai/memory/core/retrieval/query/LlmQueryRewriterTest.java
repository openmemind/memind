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
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("LlmQueryRewriter Unit Test")
class LlmQueryRewriterTest {

    @Test
    @DisplayName("constructor with prompt registry should use override instruction")
    void constructorWithPromptRegistryUsesOverrideInstruction() {
        var client = new CapturingStructuredChatClient(new RuntimeException("LLM unavailable"));
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.QUERY_REWRITE, "Custom query rewrite instruction")
                        .build();
        var rewriter = new LlmQueryRewriter(client, registry);

        StepVerifier.create(
                        rewriter.rewrite(
                                DefaultMemoryId.of("u1", null),
                                "What about the logging part?",
                                List.of("We discussed Spring Boot configuration.")))
                .expectNext("What about the logging part?")
                .verifyComplete();

        assertThat(client.lastMessages().getFirst().content())
                .contains("Custom query rewrite instruction");
    }

    @Test
    @DisplayName("legacy constructor should delegate to empty prompt registry")
    void legacyConstructorDelegatesToEmptyPromptRegistry() {
        var client = new CapturingStructuredChatClient(new RuntimeException("LLM unavailable"));
        var rewriter = new LlmQueryRewriter(client);

        StepVerifier.create(
                        rewriter.rewrite(
                                DefaultMemoryId.of("u1", null),
                                "What about the logging part?",
                                List.of("We discussed Spring Boot configuration.")))
                .expectNext("What about the logging part?")
                .verifyComplete();

        assertThat(client.lastMessages().getFirst().content())
                .contains("You are a query rewriter for a memory retrieval system.");
    }

    private static final class CapturingStructuredChatClient implements StructuredChatClient {

        private final RuntimeException error;
        private List<ChatMessage> lastMessages = List.of();

        private CapturingStructuredChatClient(RuntimeException error) {
            this.error = error;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            lastMessages = List.copyOf(messages);
            return Mono.error(error);
        }

        private List<ChatMessage> lastMessages() {
            return lastMessages;
        }
    }
}
