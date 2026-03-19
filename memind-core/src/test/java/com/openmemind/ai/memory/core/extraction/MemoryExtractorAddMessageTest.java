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
package com.openmemind.ai.memory.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import com.openmemind.ai.memory.core.extraction.step.SegmentProcessor;
import com.openmemind.ai.memory.core.extraction.streaming.BoundaryDecision;
import com.openmemind.ai.memory.core.extraction.streaming.BoundaryDetectionContext;
import com.openmemind.ai.memory.core.extraction.streaming.BoundaryDetector;
import com.openmemind.ai.memory.core.extraction.streaming.ConversationBufferStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryExtractor#addMessage")
class MemoryExtractorAddMessageTest {

    @Mock RawDataExtractStep rawDataStep;
    @Mock MemoryItemExtractStep memoryItemStep;
    @Mock InsightExtractStep insightStep;
    @Mock SegmentProcessor segmentProcessor;
    @Mock BoundaryDetector boundaryDetector;
    @Mock ConversationBufferStore bufferStore;

    MemoryExtractor extractor;
    MemoryId memoryId;

    @BeforeEach
    void setUp() {
        extractor =
                new MemoryExtractor(
                        rawDataStep,
                        memoryItemStep,
                        insightStep,
                        segmentProcessor,
                        boundaryDetector,
                        bufferStore);
        memoryId = DefaultMemoryId.of("user1", "agent1");
    }

    @Nested
    @DisplayName("ASSISTANT Message")
    class AssistantMessage {

        @Test
        @DisplayName("Directly accumulate to buffer, no boundary detection triggered")
        void accumulates_without_boundary_check() {
            Message msg = Message.assistant("Hello");
            when(bufferStore.load("user1:agent1")).thenReturn(List.of());
            when(bufferStore.loadMessageCount("user1:agent1")).thenReturn(0);

            StepVerifier.create(extractor.addMessage(memoryId, msg, ExtractionConfig.defaults()))
                    .verifyComplete();

            verify(bufferStore).save("user1:agent1", List.of(msg));
        }
    }

    @Nested
    @DisplayName("USER Message - Boundary not triggered")
    class UserMessageNoBoundary {

        @Test
        @DisplayName("Append to buffer, return empty")
        void appends_to_buffer_returns_empty() {
            Message msg = Message.user("How are you?");
            when(bufferStore.load("user1:agent1")).thenReturn(new ArrayList<>());
            when(bufferStore.loadMessageCount("user1:agent1")).thenReturn(0);
            when(boundaryDetector.shouldSeal(anyList(), any(BoundaryDetectionContext.class)))
                    .thenReturn(Mono.just(BoundaryDecision.hold()));

            StepVerifier.create(extractor.addMessage(memoryId, msg, ExtractionConfig.defaults()))
                    .verifyComplete();

            verify(bufferStore).save("user1:agent1", List.of(msg));
        }
    }

    @Nested
    @DisplayName("USER Message - Boundary triggered")
    class UserMessageBoundaryTriggered {

        @Test
        @DisplayName("Seal buffer, extract memory, trigger message into new buffer")
        void seals_buffer_and_extracts() {
            Message existing = Message.assistant("I am fine");
            Message trigger = Message.user("Tell me more");
            List<Message> existingBuffer = new ArrayList<>(List.of(existing));

            when(bufferStore.load("user1:agent1")).thenReturn(existingBuffer);
            when(bufferStore.loadMessageCount("user1:agent1")).thenReturn(1);
            when(boundaryDetector.shouldSeal(anyList(), any(BoundaryDetectionContext.class)))
                    .thenReturn(Mono.just(BoundaryDecision.seal(0.9, "test")));
            when(segmentProcessor.processSegment(any(), any(), any(), any(), any()))
                    .thenReturn(Mono.just(RawDataResult.empty()));

            StepVerifier.create(
                            extractor.addMessage(
                                    memoryId, trigger, ExtractionConfig.withoutInsight()))
                    .assertNext(result -> assertThat(result).isNotNull())
                    .verifyComplete();

            verify(bufferStore).clear("user1:agent1");
            verify(bufferStore).save("user1:agent1", List.of(trigger));
        }
    }
}
