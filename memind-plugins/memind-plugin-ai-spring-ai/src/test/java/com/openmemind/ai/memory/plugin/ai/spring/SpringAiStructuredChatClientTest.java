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
package com.openmemind.ai.memory.plugin.ai.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.ChatRole;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.test.StepVerifier;

@DisplayName("SpringAiStructuredChatClient")
class SpringAiStructuredChatClientTest {

    @Test
    @DisplayName("call should map messages and return raw content")
    void callShouldMapMessagesAndReturnRawContent() {
        var fixture = new ChatClientFixture("raw-result", null);

        var client = new SpringAiStructuredChatClient(fixture.chatClient());

        StepVerifier.create(
                        client.call(
                                List.of(
                                        new ChatMessage(ChatRole.SYSTEM, "system prompt"),
                                        new ChatMessage(ChatRole.USER, "user prompt"),
                                        new ChatMessage(ChatRole.ASSISTANT, "assistant prompt"))))
                .expectNext("raw-result")
                .verifyComplete();

        assertThat(fixture.messages()).hasSize(3).element(0).isInstanceOf(SystemMessage.class);
        assertThat(fixture.messages()).element(1).isInstanceOf(UserMessage.class);
        assertThat(fixture.messages()).element(2).isInstanceOf(AssistantMessage.class);
        assertThat(fixture.messages().stream().map(Message::getText).toList())
                .containsExactly("system prompt", "user prompt", "assistant prompt");
    }

    @Test
    @DisplayName("call with response type should return structured entity")
    void callWithResponseTypeShouldReturnStructuredEntity() {
        var expected = new TestResponse("structured-result");
        var fixture = new ChatClientFixture("{\"value\":\"structured-result\"}", null);

        var client = new SpringAiStructuredChatClient(fixture.chatClient());

        StepVerifier.create(
                        client.call(
                                List.of(new ChatMessage(ChatRole.USER, "user prompt")),
                                TestResponse.class))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    @DisplayName("call with response type should not append Spring AI schema instructions")
    void callWithResponseTypeShouldNotAppendSpringAiSchemaInstructions() {
        var chatModel = new CapturingChatModel("{\"value\":\"structured-result\"}");
        var client = new SpringAiStructuredChatClient(ChatClient.builder(chatModel).build());

        StepVerifier.create(
                        client.call(
                                List.of(new ChatMessage(ChatRole.USER, "Return JSON only")),
                                TestResponse.class))
                .expectNext(new TestResponse("structured-result"))
                .verifyComplete();

        assertThat(chatModel.lastPrompt().getInstructions())
                .filteredOn(UserMessage.class::isInstance)
                .map(Message::getText)
                .singleElement()
                .asString()
                .doesNotContain("JSON Schema instance")
                .doesNotContain("Your response should be in JSON format");
    }

    @Test
    @DisplayName("call with response type should parse duplicate time fields from LLM JSON")
    void callWithResponseTypeShouldParseDuplicateTimeFieldsFromLlmJson() {
        var chatModel =
                new CapturingChatModel(
                        """
                        {
                          "items": [
                            {
                              "content": "Andrew expressed admiration for Audrey's creations.",
                              "confidence": 0.95,
                              "time": null,
                              "insightTypes": ["experiences"],
                              "category": "event",
                              "occurredAt": "2023-07-11T10:08:00Z",
                              "time": {
                                "expression": "on 2023-07-11 at 10:08 UTC",
                                "start": "2023-07-11T10:08:00Z",
                                "end": null,
                                "granularity": "point"
                              }
                            }
                          ]
                        }
                        """);
        var client = new SpringAiStructuredChatClient(ChatClient.builder(chatModel).build());

        StepVerifier.create(
                        client.call(
                                List.of(new ChatMessage(ChatRole.USER, "Return JSON only")),
                                MemoryItemExtractionResponse.class))
                .assertNext(
                        response -> {
                            var item = response.items().getFirst();
                            assertThat(item.time()).isNotNull();
                            assertThat(item.time().start()).isEqualTo("2023-07-11T10:08:00Z");
                            assertThat(item.occurredAt()).isEqualTo("2023-07-11T10:08:00Z");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("call with response type should wrap top-level array for single-list record")
    void callWithResponseTypeShouldWrapTopLevelArrayForSingleListRecord() {
        var chatModel = new CapturingChatModel("[\"identity\", \"experiences\"]");
        var client = new SpringAiStructuredChatClient(ChatClient.builder(chatModel).build());

        StepVerifier.create(
                        client.call(
                                List.of(new ChatMessage(ChatRole.USER, "Return JSON only")),
                                TypesResponse.class))
                .expectNext(new TypesResponse(List.of("identity", "experiences")))
                .verifyComplete();
    }

    @Test
    @DisplayName("call should reject empty messages")
    void callShouldRejectEmptyMessages() {
        var client =
                new SpringAiStructuredChatClient(new ChatClientFixture(null, null).chatClient());

        StepVerifier.create(client.call(List.of()))
                .expectErrorSatisfies(
                        error ->
                                assertThat(error)
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessage("messages must not be empty"))
                .verify();
    }

    private static final class ChatClientFixture {

        private List<Message> messages = List.of();

        private final ChatClient chatClient;

        private ChatClientFixture(String content, Object entity) {
            var responseSpec =
                    proxy(
                            ChatClient.CallResponseSpec.class,
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "content" -> content;
                                        case "entity" -> entity;
                                        case "toString" -> "FakeCallResponseSpec";
                                        case "hashCode" -> System.identityHashCode(proxy);
                                        case "equals" -> proxy == args[0];
                                        default ->
                                                throw new UnsupportedOperationException(
                                                        method.getName());
                                    });

            var requestSpec =
                    proxy(
                            ChatClient.ChatClientRequestSpec.class,
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "messages" -> {
                                            if (args.length == 1
                                                    && args[0] instanceof List<?> captured) {
                                                this.messages =
                                                        captured.stream()
                                                                .map(Message.class::cast)
                                                                .toList();
                                                yield proxy;
                                            }
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                        }
                                        case "call" -> responseSpec;
                                        case "toString" -> "FakeChatClientRequestSpec";
                                        case "hashCode" -> System.identityHashCode(proxy);
                                        case "equals" -> proxy == args[0];
                                        default ->
                                                throw new UnsupportedOperationException(
                                                        method.getName());
                                    });

            this.chatClient =
                    proxy(
                            ChatClient.class,
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "prompt" -> requestSpec;
                                        case "toString" -> "FakeChatClient";
                                        case "hashCode" -> System.identityHashCode(proxy);
                                        case "equals" -> proxy == args[0];
                                        default ->
                                                throw new UnsupportedOperationException(
                                                        method.getName());
                                    });
        }

        private ChatClient chatClient() {
            return this.chatClient;
        }

        private List<Message> messages() {
            return this.messages;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T)
                Proxy.newProxyInstance(
                        SpringAiStructuredChatClientTest.class.getClassLoader(),
                        new Class<?>[] {type},
                        handler);
    }

    private static final class CapturingChatModel implements ChatModel {

        private final String content;
        private Prompt lastPrompt;

        private CapturingChatModel(String content) {
            this.content = content;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        }

        private Prompt lastPrompt() {
            return lastPrompt;
        }
    }

    private record TestResponse(String value) {}

    private record TypesResponse(List<String> types) {}
}
