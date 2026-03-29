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
package com.openmemind.ai.memory.core.retrieval.sufficiency;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("LlmSufficiencyGate Unit Test")
class LlmSufficiencyGateTest {

    @Test
    @DisplayName("constructor with prompt registry should use override instruction")
    void constructorWithPromptRegistryUsesOverrideInstruction() {
        var client = new CapturingStructuredChatClient(null, null);
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.SUFFICIENCY_GATE, "Custom sufficiency instruction")
                        .build();
        var gate = new LlmSufficiencyGate(client, registry);

        StepVerifier.create(gate.check(context(), results()))
                .expectNext(SufficiencyResult.fallbackInsufficient())
                .verifyComplete();

        assertThat(client.lastMessages().getFirst().content())
                .contains("Custom sufficiency instruction");
    }

    @Test
    @DisplayName("legacy constructor should delegate to empty prompt registry")
    void legacyConstructorDelegatesToEmptyPromptRegistry() {
        var client = new CapturingStructuredChatClient(null, null);
        var gate = new LlmSufficiencyGate(client);

        StepVerifier.create(gate.check(context(), results()))
                .expectNext(SufficiencyResult.fallbackInsufficient())
                .verifyComplete();

        assertThat(client.lastMessages().getFirst().content())
                .contains("You are a retrieval sufficiency evaluator.");
    }

    private static QueryContext context() {
        return new QueryContext(
                DefaultMemoryId.of("u1", null),
                "What database does the user use?",
                null,
                List.of("We talked about a migration project."),
                Map.of(),
                null,
                null);
    }

    private static List<ScoredResult> results() {
        return List.of(
                new ScoredResult(
                        ScoredResult.SourceType.ITEM,
                        "item-1",
                        "User is working on a migration project.",
                        0.85f,
                        0.91));
    }

    private static final class CapturingStructuredChatClient implements StructuredChatClient {

        private final Object response;
        private final RuntimeException error;
        private List<ChatMessage> lastMessages = List.of();

        private CapturingStructuredChatClient(Object response, RuntimeException error) {
            this.response = response;
            this.error = error;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            lastMessages = List.copyOf(messages);
            if (error != null) {
                return Mono.error(error);
            }
            return Mono.justOrEmpty((T) response);
        }

        private List<ChatMessage> lastMessages() {
            return lastMessages;
        }
    }
}
