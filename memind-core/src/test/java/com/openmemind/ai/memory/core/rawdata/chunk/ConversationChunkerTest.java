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

import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConversationChunker Unit Test")
class ConversationChunkerTest {

    private final ConversationChunker chunker = new ConversationChunker();

    @Nested
    @DisplayName("Basic Chunking")
    class BasicChunkingTests {

        @Test
        @DisplayName("Should chunk by message count")
        void shouldChunkByMessageCount() {
            List<Message> messages =
                    List.of(
                            Message.user("1"),
                            Message.assistant("2"),
                            Message.user("3"),
                            Message.assistant("4"),
                            Message.user("5"),
                            Message.assistant("6"),
                            Message.user("7"),
                            Message.assistant("8"));
            ConversationChunkingConfig config = new ConversationChunkingConfig(4);

            List<Segment> segments = chunker.chunk(messages, config);

            assertThat(segments).hasSize(2);

            // First chunk: messages 0-3
            assertThat(segments.getFirst().boundary()).isEqualTo(new MessageBoundary(0, 4));
            assertThat(segments.getFirst().content()).contains("user: 1");
            assertThat(segments.getFirst().content()).contains("assistant: 4");

            // Second chunk: messages 4-7
            assertThat(segments.getLast().boundary()).isEqualTo(new MessageBoundary(4, 8));
        }

        @Test
        @DisplayName("Should return a single chunk when less than config")
        void shouldReturnSingleChunkWhenLessThanConfig() {
            List<Message> messages = List.of(Message.user("Hello"), Message.assistant("Hello!"));

            List<Segment> segments = chunker.chunk(messages, ConversationChunkingConfig.DEFAULT);

            assertThat(segments).hasSize(1);
            assertThat(segments.getFirst().boundary()).isEqualTo(new MessageBoundary(0, 2));
        }

        @Test
        @DisplayName("Empty message list should return empty list")
        void shouldReturnEmptyListForEmptyMessages() {
            List<Segment> segments = chunker.chunk(List.of(), ConversationChunkingConfig.DEFAULT);

            assertThat(segments).isEmpty();
        }
    }

    @Nested
    @DisplayName("Message Formatting")
    class MessageFormattingTests {

        @Test
        @DisplayName("Should format messages correctly")
        void shouldFormatMessagesCorrectly() {
            List<Message> messages =
                    List.of(Message.user("Hello"), Message.assistant("Hello! How can I help you?"));

            List<Segment> segments = chunker.chunk(messages, ConversationChunkingConfig.DEFAULT);

            String expectedContent = "user: Hello\nassistant: Hello! How can I help you?";
            assertThat(segments.getFirst().content()).isEqualTo(expectedContent);
        }
    }

    @Nested
    @DisplayName("Runtime Context")
    class RuntimeContextTests {

        @Test
        @DisplayName("Should expose runtime context instead of persisting source messages")
        void shouldExposeRuntimeContextInsteadOfPersistingSourceMessages() {
            List<Message> messages =
                    List.of(
                            Message.user("Hello", Instant.parse("2026-03-27T02:17:00Z"), "Alice"),
                            new Message(
                                    Message.Role.ASSISTANT,
                                    List.of(),
                                    Instant.parse("2026-03-27T02:18:00Z"),
                                    null));

            List<Segment> segments = chunker.chunk(messages, ConversationChunkingConfig.DEFAULT);

            assertThat(segments.getFirst().metadata()).doesNotContainKey("messages");
            assertThat(segments.getFirst().runtimeContext()).isNotNull();
            assertThat(segments.getFirst().runtimeContext().startTime())
                    .isEqualTo(Instant.parse("2026-03-27T02:17:00Z"));
            assertThat(segments.getFirst().runtimeContext().observedAt())
                    .isEqualTo(Instant.parse("2026-03-27T02:18:00Z"));
            assertThat(segments.getFirst().runtimeContext().userName()).isEqualTo("Alice");
        }
    }
}
