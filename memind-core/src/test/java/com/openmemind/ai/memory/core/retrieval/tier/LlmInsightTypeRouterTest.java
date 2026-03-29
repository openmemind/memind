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
package com.openmemind.ai.memory.core.retrieval.tier;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import com.openmemind.ai.memory.core.prompt.retrieval.InsightTypeRoutingPrompts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@DisplayName("LlmInsightTypeRouter Unit Test")
class LlmInsightTypeRouterTest {

    private StructuredChatClient structuredChatClient;
    private LlmInsightTypeRouter router;

    private final Map<String, String> availableTypes = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        structuredChatClient = new FakeStructuredChatClient();
        router = new LlmInsightTypeRouter(structuredChatClient);
        availableTypes.put("profile", "Personal information");
        availableTypes.put("preferences", "User preferences");
        availableTypes.put("experiences", "Activities and experiences");
    }

    @Nested
    @DisplayName("Normal Routing Tests")
    class NormalRoutingTests {

        @Test
        @DisplayName("Should correctly filter when LLM returns valid types")
        void shouldReturnValidTypes() {
            structuredChatClient = new FakeStructuredChatClient(List.of("profile"));
            router = new LlmInsightTypeRouter(structuredChatClient);

            StepVerifier.create(router.route("Query", List.of(), availableTypes))
                    .assertNext(
                            types -> {
                                assertThat(types).containsExactly("profile");
                            })
                    .verifyComplete();

            var prompt =
                    InsightTypeRoutingPrompts.build(
                                    "Query",
                                    List.copyOf(availableTypes.keySet()),
                                    availableTypes,
                                    List.of())
                            .render("English");
            assertThat(((FakeStructuredChatClient) structuredChatClient).lastMessages())
                    .isEqualTo(ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt()));
        }

        @Test
        @DisplayName("constructor with prompt registry should use override instruction")
        void constructorWithPromptRegistryUsesOverrideInstruction() {
            var registry =
                    InMemoryPromptRegistry.builder()
                            .override(
                                    PromptType.INSIGHT_TYPE_ROUTING,
                                    "Custom insight type routing instruction")
                            .build();
            structuredChatClient = new FakeStructuredChatClient(List.of("profile"));
            router = new LlmInsightTypeRouter(structuredChatClient, registry);

            StepVerifier.create(router.route("Query", List.of(), availableTypes))
                    .assertNext(types -> assertThat(types).containsExactly("profile"))
                    .verifyComplete();

            assertThat(
                            ((FakeStructuredChatClient) structuredChatClient)
                                    .lastMessages()
                                    .getFirst()
                                    .content())
                    .contains("Custom insight type routing instruction");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should fallback to all types on LLM call failure")
        void shouldFallbackToAllTypesOnError() {
            structuredChatClient =
                    new FakeStructuredChatClient(new RuntimeException("Network exception"));
            router = new LlmInsightTypeRouter(structuredChatClient);

            StepVerifier.create(router.route("Query", List.of(), availableTypes))
                    .assertNext(
                            types ->
                                    assertThat(types)
                                            .containsExactlyInAnyOrder(
                                                    "profile", "preferences", "experiences"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty list when LLM returns null response")
        void shouldReturnEmptyOnNullResponse() {
            structuredChatClient = new FakeStructuredChatClient((List<String>) null);
            router = new LlmInsightTypeRouter(structuredChatClient);

            StepVerifier.create(router.route("Query", List.of(), availableTypes))
                    .assertNext(types -> assertThat(types).isEmpty())
                    .verifyComplete();
        }
    }

    private static final class FakeStructuredChatClient implements StructuredChatClient {

        private final List<String> responseTypes;
        private final RuntimeException error;
        private List<ChatMessage> lastMessages = List.of();

        private FakeStructuredChatClient() {
            this.responseTypes = null;
            this.error = null;
        }

        private FakeStructuredChatClient(List<String> responseTypes) {
            this.responseTypes = responseTypes;
            this.error = null;
        }

        private FakeStructuredChatClient(RuntimeException error) {
            this.responseTypes = null;
            this.error = error;
        }

        @Override
        public reactor.core.publisher.Mono<String> call(List<ChatMessage> messages) {
            lastMessages = List.copyOf(messages);
            return reactor.core.publisher.Mono.just("");
        }

        @Override
        public <T> reactor.core.publisher.Mono<T> call(
                List<ChatMessage> messages, Class<T> responseType) {
            lastMessages = List.copyOf(messages);
            if (error != null) {
                return reactor.core.publisher.Mono.error(error);
            }
            if (responseTypes == null) {
                return reactor.core.publisher.Mono.empty();
            }
            var response = new LlmInsightTypeRouter.RoutingResponse(responseTypes);
            return reactor.core.publisher.Mono.fromSupplier(() -> responseType.cast(response));
        }

        private List<ChatMessage> lastMessages() {
            return lastMessages;
        }
    }
}
