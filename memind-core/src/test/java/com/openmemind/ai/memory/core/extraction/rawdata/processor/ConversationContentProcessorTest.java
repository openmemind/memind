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
package com.openmemind.ai.memory.core.extraction.rawdata.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@DisplayName("ConversationContentProcessor")
class ConversationContentProcessorTest {

    private final ConversationChunker conversationChunker = mock(ConversationChunker.class);
    private final CaptionGenerator captionGenerator = mock(CaptionGenerator.class);
    private final ConversationChunkingConfig chunkingConfig = ConversationChunkingConfig.DEFAULT;

    private final ConversationContentProcessor processor =
            new ConversationContentProcessor(
                    conversationChunker, null, chunkingConfig, captionGenerator, null);

    @Test
    @DisplayName("contentType() should return CONVERSATION")
    void contentTypeIsConversation() {
        assertThat(processor.contentType()).isEqualTo("CONVERSATION");
    }

    @Test
    @DisplayName("supportsInsight() should return true")
    void supportsInsightIsTrue() {
        assertThat(processor.supportsInsight()).isTrue();
    }

    @Test
    @DisplayName("resolveSegmentStartTime and resolveSegmentEndTime should use message boundaries")
    void resolvesSegmentTimesFromConversationMessages() {
        var first = Message.user("hello", Instant.parse("2026-04-11T10:00:00Z"));
        var second = Message.assistant("hi", Instant.parse("2026-04-11T10:00:10Z"));
        var content = new ConversationContent(List.of(first, second));
        var segment = new Segment("hello", null, new MessageBoundary(0, 2), Map.of());

        assertThat(processor.resolveSegmentStartTime(content, segment, Instant.EPOCH))
                .isEqualTo(first.timestamp());
        assertThat(processor.resolveSegmentEndTime(content, segment, Instant.EPOCH))
                .isEqualTo(second.timestamp());
    }

    @Nested
    @DisplayName("chunk()")
    class ChunkTests {

        @Test
        @DisplayName("Should delegate to ConversationChunker")
        void delegatesToConversationChunker() {
            var content =
                    ConversationContent.builder()
                            .addUserMessage("hello")
                            .addAssistantMessage("hi")
                            .build();

            var expectedSegments =
                    List.of(
                            new Segment(
                                    "user: hello\nassistant: hi",
                                    null,
                                    new MessageBoundary(0, 2),
                                    Map.of()));

            when(conversationChunker.chunk(eq(content.getMessages()), eq(chunkingConfig)))
                    .thenReturn(expectedSegments);

            StepVerifier.create(processor.chunk(content))
                    .assertNext(
                            segments -> {
                                assertThat(segments).hasSize(1);
                                assertThat(segments).isEqualTo(expectedSegments);
                            })
                    .verifyComplete();

            verify(conversationChunker).chunk(eq(content.getMessages()), eq(chunkingConfig));
        }

        @Test
        @DisplayName("Empty messages should return empty list")
        void emptyMessagesReturnEmpty() {
            var content = new ConversationContent(List.of());

            StepVerifier.create(processor.chunk(content))
                    .assertNext(segments -> assertThat(segments).isEmpty())
                    .verifyComplete();
        }
    }
}
