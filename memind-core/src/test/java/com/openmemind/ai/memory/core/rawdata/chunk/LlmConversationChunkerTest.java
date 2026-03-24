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
package com.openmemind.ai.memory.core.rawdata.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig.ConversationSegmentStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.LlmConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.ChatMessages;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.extraction.rawdata.ConversationSegmentationPrompts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("LlmConversationChunker Unit Test")
class LlmConversationChunkerTest {

    private static final ConversationChunkingConfig CONFIG =
            new ConversationChunkingConfig(10, ConversationSegmentStrategy.LLM, 5);

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw when structuredChatClient is null")
        void shouldThrowWhenStructuredChatClientIsNull() {
            assertThatThrownBy(() -> new LlmConversationChunker(null, new ConversationChunker()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("structuredChatClient must not be null");
        }
    }

    @Nested
    @DisplayName("chunk method")
    class ChunkTests {

        @Test
        @DisplayName("Empty message list should return empty list without calling the LLM")
        void shouldReturnEmptyForEmptyMessages() {
            var structuredLlmClient = new FakeStructuredChatClient();
            var chunker =
                    new LlmConversationChunker(structuredLlmClient, new ConversationChunker());

            StepVerifier.create(chunker.chunk(List.of(), CONFIG))
                    .assertNext(segments -> assertThat(segments).isEmpty())
                    .verifyComplete();

            assertThat(structuredLlmClient.lastMessages()).isEmpty();
        }

        @Test
        @DisplayName("Should segment by LLM returned boundaries and send prompt as ChatMessages")
        void shouldSegmentByLlmBoundaries() {
            var messages =
                    List.of(
                            Message.user("Hello"),
                            Message.assistant("Hello!"),
                            Message.user("How's the weather?"),
                            Message.assistant("It's sunny today"),
                            Message.user("Recommend a book"),
                            Message.assistant("I recommend The Three-Body Problem"));
            var llmResponse =
                    "{\"segments\": [{\"start\": 0, \"end\": 4}, {\"start\": 4, \"end\": 6}]}";
            var structuredLlmClient = new FakeStructuredChatClient(llmResponse);
            var chunker =
                    new LlmConversationChunker(structuredLlmClient, new ConversationChunker());

            StepVerifier.create(chunker.chunk(messages, CONFIG))
                    .assertNext(
                            segments -> {
                                assertThat(segments).hasSize(2);
                                assertThat(segments.getFirst().boundary())
                                        .isEqualTo(new MessageBoundary(0, 4));
                                assertThat(segments.getLast().boundary())
                                        .isEqualTo(new MessageBoundary(4, 6));
                                assertThat(segments.getFirst().content()).contains("user: Hello");
                                assertThat(segments.getLast().content())
                                        .contains("user: Recommend a book");
                            })
                    .verifyComplete();

            var prompt =
                    ConversationSegmentationPrompts.build(messages, CONFIG.minMessagesPerSegment())
                            .render(null);
            assertThat(structuredLlmClient.lastMessages())
                    .isEqualTo(ChatMessages.systemUser(prompt.systemPrompt(), prompt.userPrompt()));
        }

        @Test
        @DisplayName("Should repair boundaries when the LLM misses the tail segment")
        void shouldRepairBoundariesWhenTailIsMissing() {
            var messages =
                    List.of(
                            Message.user("m0"),
                            Message.assistant("m1"),
                            Message.user("m2"),
                            Message.assistant("m3"),
                            Message.user("m4"),
                            Message.assistant("m5"),
                            Message.user("m6"),
                            Message.assistant("m7"));
            var llmResponse =
                    "{\"segments\": [{\"start\": 0, \"end\": 4}, {\"start\": 4, \"end\": 6}]}";
            var chunker =
                    new LlmConversationChunker(
                            new FakeStructuredChatClient(llmResponse), new ConversationChunker());

            StepVerifier.create(chunker.chunk(messages, CONFIG))
                    .assertNext(
                            segments -> {
                                assertThat(segments).hasSize(2);
                                assertThat(segments.getLast().boundary())
                                        .isEqualTo(new MessageBoundary(4, 8));
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should degrade to fallback segmentation when the LLM call fails")
        void shouldDegradeToFallbackSegmentationWhenLlmFails() {
            var messages = List.of(Message.user("Hello"), Message.assistant("Hello!"));
            var fallbackSegments =
                    List.of(
                            new Segment(
                                    "fallback",
                                    null,
                                    new MessageBoundary(0, 2),
                                    Map.of("messages", messages)));
            ConversationChunker fallback =
                    new ConversationChunker() {
                        @Override
                        public List<Segment> chunk(
                                List<Message> ignoredMessages, ConversationChunkingConfig config) {
                            return fallbackSegments;
                        }
                    };
            var chunker =
                    new LlmConversationChunker(
                            new FakeStructuredChatClient(new RuntimeException("LLM unavailable")),
                            fallback);

            StepVerifier.create(chunker.chunk(messages, CONFIG))
                    .expectNext(fallbackSegments)
                    .verifyComplete();
        }
    }

    private static final class FakeStructuredChatClient implements StructuredChatClient {

        private final String contentResponse;
        private final RuntimeException error;
        private List<ChatMessage> lastMessages = List.of();

        private FakeStructuredChatClient() {
            this(null, null);
        }

        private FakeStructuredChatClient(String contentResponse) {
            this(contentResponse, null);
        }

        private FakeStructuredChatClient(RuntimeException error) {
            this(null, error);
        }

        private FakeStructuredChatClient(String contentResponse, RuntimeException error) {
            this.contentResponse = contentResponse;
            this.error = error;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            lastMessages = List.copyOf(messages);
            if (error != null) {
                return Mono.error(error);
            }
            return Mono.justOrEmpty(contentResponse);
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            return Mono.error(new UnsupportedOperationException("Not used in this test"));
        }

        private List<ChatMessage> lastMessages() {
            return lastMessages;
        }
    }
}
