package com.openmemind.ai.memory.core.rawdata.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
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
    @DisplayName("Metadata")
    class MetadataTests {

        @Test
        @DisplayName("Should include original messages in metadata")
        void shouldIncludeOriginalMessagesInMetadata() {
            List<Message> messages = List.of(Message.user("Hello"), Message.assistant("Hello!"));

            List<Segment> segments = chunker.chunk(messages, ConversationChunkingConfig.DEFAULT);

            assertThat(segments.getFirst().metadata()).containsKey("messages");
            @SuppressWarnings("unchecked")
            List<Message> storedMessages =
                    (List<Message>) segments.getFirst().metadata().get("messages");
            assertThat(storedMessages).hasSize(2);
        }
    }
}
