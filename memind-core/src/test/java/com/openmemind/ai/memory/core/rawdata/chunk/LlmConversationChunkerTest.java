package com.openmemind.ai.memory.core.rawdata.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig.ConversationSegmentStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.LlmConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmConversationChunker Unit Test")
class LlmConversationChunkerTest {

    @Mock private ChatClient chatClient;
    @Mock private ConversationChunker fallback;
    @Mock private ChatClientRequestSpec requestSpec;
    @Mock private CallResponseSpec responseSpec;

    private LlmConversationChunker chunker;

    private final ConversationChunkingConfig config =
            new ConversationChunkingConfig(10, ConversationSegmentStrategy.LLM, 5);

    @BeforeEach
    void setUp() {
        chunker = new LlmConversationChunker(chatClient, fallback);
    }

    @Nested
    @DisplayName("Empty Message")
    class EmptyMessageTests {

        @Test
        @DisplayName("Empty message list should return empty list")
        void shouldReturnEmptyForEmptyMessages() {
            StepVerifier.create(chunker.chunk(List.of(), config))
                    .assertNext(segments -> assertThat(segments).isEmpty())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("LLM Segmentation")
    class LlmSegmentationTests {

        @Test
        @DisplayName("Should segment by LLM returned boundaries")
        void shouldSegmentByLlmBoundaries() {
            List<Message> messages =
                    List.of(
                            Message.user("Hello"),
                            Message.assistant("Hello!"),
                            Message.user("How's the weather?"),
                            Message.assistant("It's sunny today"),
                            Message.user("Recommend a book"),
                            Message.assistant("I recommend 'The Three-Body Problem'"));

            String llmResponse =
                    """
                    {"segments": [{"start": 0, "end": 4}, {"start": 4, "end": 6}]}
                    """;

            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(responseSpec);
            when(responseSpec.content()).thenReturn(llmResponse);

            StepVerifier.create(chunker.chunk(messages, config))
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
        }

        @Test
        @DisplayName("Should handle markdown wrapped JSON response")
        void shouldHandleMarkdownWrappedJson() {
            List<Message> messages = List.of(Message.user("Hello"), Message.assistant("Hello!"));

            String llmResponse =
                    """
                    ```json
                    {"segments": [{"start": 0, "end": 2}]}
                    ```
                    """;

            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(responseSpec);
            when(responseSpec.content()).thenReturn(llmResponse);

            StepVerifier.create(chunker.chunk(messages, config))
                    .assertNext(
                            segments -> {
                                assertThat(segments).hasSize(1);
                                assertThat(segments.getFirst().boundary())
                                        .isEqualTo(new MessageBoundary(0, 2));
                            })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Boundary Repair")
    class BoundaryRepairTests {

        @Test
        @DisplayName("Should extend last segment when tail is missing")
        void shouldExtendLastSegmentWhenTailIsMissing() {
            List<Message> messages =
                    List.of(
                            Message.user("m0"),
                            Message.assistant("m1"),
                            Message.user("m2"),
                            Message.assistant("m3"),
                            Message.user("m4"),
                            Message.assistant("m5"),
                            Message.user("m6"),
                            Message.assistant("m7"));

            // LLM only returns [0-4] and [4-6], missing [6-8]
            String llmResponse =
                    """
                    {"segments": [{"start": 0, "end": 4}, {"start": 4, "end": 6}]}
                    """;

            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(responseSpec);
            when(responseSpec.content()).thenReturn(llmResponse);

            StepVerifier.create(chunker.chunk(messages, config))
                    .assertNext(
                            segments -> {
                                assertThat(segments).hasSize(2);
                                assertThat(segments.getFirst().boundary())
                                        .isEqualTo(new MessageBoundary(0, 4));
                                // The last segment should extend to 8
                                assertThat(segments.getLast().boundary())
                                        .isEqualTo(new MessageBoundary(4, 8));
                                assertThat(segments.getLast().content()).contains("m6");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Empty boundaries should return a single segment covering all messages")
        void shouldReturnSingleSegmentForEmptyBoundaries() {
            List<Message> messages =
                    List.of(
                            Message.user("m0"),
                            Message.assistant("m1"),
                            Message.user("m2"),
                            Message.assistant("m3"));

            // LLM returns empty segments
            String llmResponse =
                    """
                    {"segments": []}
                    """;

            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(responseSpec);
            when(responseSpec.content()).thenReturn(llmResponse);

            StepVerifier.create(chunker.chunk(messages, config))
                    .assertNext(
                            segments -> {
                                assertThat(segments).hasSize(1);
                                assertThat(segments.getFirst().boundary())
                                        .isEqualTo(new MessageBoundary(0, 4));
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should not modify boundaries when fully covered")
        void shouldNotModifyCompleteBoundaries() {
            List<Message> messages =
                    List.of(
                            Message.user("m0"),
                            Message.assistant("m1"),
                            Message.user("m2"),
                            Message.assistant("m3"),
                            Message.user("m4"),
                            Message.assistant("m5"));

            String llmResponse =
                    """
                    {"segments": [{"start": 0, "end": 3}, {"start": 3, "end": 6}]}
                    """;

            when(chatClient.prompt()).thenReturn(requestSpec);
            when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(responseSpec);
            when(responseSpec.content()).thenReturn(llmResponse);

            StepVerifier.create(chunker.chunk(messages, config))
                    .assertNext(
                            segments -> {
                                assertThat(segments).hasSize(2);
                                assertThat(segments.getFirst().boundary())
                                        .isEqualTo(new MessageBoundary(0, 3));
                                assertThat(segments.getLast().boundary())
                                        .isEqualTo(new MessageBoundary(3, 6));
                            })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Fallback")
    class FallbackTests {

        @Test
        @DisplayName("Should fallback to fixed segmentation when LLM call fails")
        void shouldFallbackOnLlmError() {
            List<Message> messages = List.of(Message.user("Hello"), Message.assistant("Hello!"));

            when(chatClient.prompt()).thenThrow(new RuntimeException("LLM unavailable"));

            List<Segment> fallbackSegments =
                    List.of(
                            new Segment(
                                    "user: Hello\nassistant: Hello!",
                                    null,
                                    new MessageBoundary(0, 2),
                                    Map.of()));
            when(fallback.chunk(messages, config)).thenReturn(fallbackSegments);

            StepVerifier.create(chunker.chunk(messages, config))
                    .assertNext(
                            segments -> {
                                assertThat(segments).hasSize(1);
                                assertThat(segments.getFirst().boundary())
                                        .isEqualTo(new MessageBoundary(0, 2));
                            })
                    .verifyComplete();

            verify(fallback).chunk(messages, config);
        }
    }
}
