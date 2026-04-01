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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.buffer.InMemoryConversationBuffer;
import com.openmemind.ai.memory.core.buffer.InMemoryRecentConversationBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.context.CommitDecision;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectionInput;
import com.openmemind.ai.memory.core.extraction.context.ContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import com.openmemind.ai.memory.core.extraction.step.SegmentProcessor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    @Mock ContextCommitDetector contextCommitDetector;
    @Mock PendingConversationBuffer pendingBufferStore;
    @Mock RecentConversationBuffer recentBufferStore;

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
                        contextCommitDetector,
                        pendingBufferStore,
                        recentBufferStore);
        memoryId = DefaultMemoryId.of("user1", "agent1");
    }

    @Nested
    @DisplayName("ASSISTANT Message")
    class AssistantMessage {

        @Test
        @DisplayName("Directly accumulate to buffer, no boundary detection triggered")
        void accumulates_without_boundary_check() {
            Message msg = Message.assistant("Hello");

            StepVerifier.create(extractor.addMessage(memoryId, msg, ExtractionConfig.defaults()))
                    .verifyComplete();

            verify(recentBufferStore).append("user1:agent1", msg);
            verify(pendingBufferStore).append("user1:agent1", msg);
        }
    }

    @Nested
    @DisplayName("USER Message - Boundary not triggered")
    class UserMessageNoBoundary {

        @Test
        @DisplayName("Append to buffer, return empty")
        void appends_to_buffer_returns_empty() {
            Message msg = Message.user("How are you?");
            when(pendingBufferStore.load("user1:agent1")).thenReturn(new ArrayList<>());

            StepVerifier.create(extractor.addMessage(memoryId, msg, ExtractionConfig.defaults()))
                    .verifyComplete();

            verify(recentBufferStore).append("user1:agent1", msg);
            verify(pendingBufferStore).append("user1:agent1", msg);
        }

        @Test
        @DisplayName("Pass history and current user message to boundary detector")
        void passes_history_and_current_message_to_detector() {
            Message existing = Message.assistant("Earlier detail");
            Message current = Message.user("New question");
            when(pendingBufferStore.load("user1:agent1")).thenReturn(List.of(existing));
            when(contextCommitDetector.shouldCommit(any(CommitDetectionInput.class)))
                    .thenReturn(Mono.just(CommitDecision.hold()));

            StepVerifier.create(
                            extractor.addMessage(memoryId, current, ExtractionConfig.defaults()))
                    .verifyComplete();

            verify(contextCommitDetector)
                    .shouldCommit(
                            argThat(
                                    input ->
                                            input.history().equals(List.of(existing))
                                                    && input.incomingMessages()
                                                            .equals(List.of(current))));
            verify(pendingBufferStore).append("user1:agent1", current);
        }
    }

    @Nested
    @DisplayName("USER Message - Boundary triggered")
    class UserMessageBoundaryTriggered {

        @Test
        @DisplayName("Seal buffer, extract memory, trigger message into new buffer")
        void seals_buffer_and_extracts() {
            var pendingStore = new InMemoryConversationBuffer();
            var recentStore = new InMemoryRecentConversationBuffer();
            var existing = Message.assistant("I am fine");
            var trigger = Message.user("Tell me more");

            pendingStore.append("user1:agent1", existing);
            recentStore.append("user1:agent1", existing);

            MemoryExtractor localExtractor =
                    new MemoryExtractor(
                            unusedRawDataStep(),
                            unusedMemoryItemStep(),
                            unusedInsightStep(),
                            (mid, segment, type, contentId, metadata) ->
                                    Mono.just(RawDataResult.empty()),
                            input -> Mono.just(CommitDecision.commit(0.9, "test")),
                            pendingStore,
                            recentStore);

            StepVerifier.create(
                            localExtractor.addMessage(
                                    memoryId, trigger, ExtractionConfig.withoutInsight()))
                    .assertNext(result -> assertThat(result).isNotNull())
                    .verifyComplete();

            assertThat(pendingStore.load("user1:agent1")).containsExactly(trigger);
            assertThat(recentStore.loadRecent("user1:agent1", 10))
                    .containsExactly(existing, trigger);
        }

        @Test
        @DisplayName("Preserve follow-up message appended while sealing is still extracting")
        void preserves_follow_up_message_during_inflight_extraction() throws Exception {
            var pendingStore = new InMemoryConversationBuffer();
            var recentStore = new InMemoryRecentConversationBuffer();
            var existing = Message.assistant("I am fine");
            pendingStore.append("user1:agent1", existing);
            recentStore.append("user1:agent1", existing);

            var extractionStarted = new CountDownLatch(1);
            var releaseExtraction = new CountDownLatch(1);

            MemoryExtractor localExtractor =
                    new MemoryExtractor(
                            unusedRawDataStep(),
                            unusedMemoryItemStep(),
                            unusedInsightStep(),
                            (mid, segment, type, contentId, metadata) ->
                                    Mono.fromCallable(
                                            () -> {
                                                extractionStarted.countDown();
                                                if (!releaseExtraction.await(5, TimeUnit.SECONDS)) {
                                                    throw new AssertionError(
                                                            "Timed out waiting to release"
                                                                    + " extraction");
                                                }
                                                return RawDataResult.empty();
                                            }),
                            input -> Mono.just(CommitDecision.commit(0.9, "test")),
                            pendingStore,
                            recentStore);

            var trigger = Message.user("Tell me more");
            var followUp = Message.assistant("Additional detail");

            try (var executor = Executors.newSingleThreadExecutor()) {
                var triggerFuture =
                        executor.submit(
                                () ->
                                        localExtractor
                                                .addMessage(
                                                        memoryId,
                                                        trigger,
                                                        ExtractionConfig.withoutInsight())
                                                .block());

                assertThat(extractionStarted.await(5, TimeUnit.SECONDS)).isTrue();

                StepVerifier.create(
                                localExtractor.addMessage(
                                        memoryId, followUp, ExtractionConfig.defaults()))
                        .verifyComplete();

                releaseExtraction.countDown();
                assertThat(triggerFuture.get(5, TimeUnit.SECONDS)).isNotNull();
            }

            assertThat(pendingStore.load("user1:agent1")).containsExactly(trigger, followUp);
            assertThat(recentStore.loadRecent("user1:agent1", 10))
                    .containsExactly(existing, trigger, followUp);
        }

        @Test
        @DisplayName("Seal path forwards extraction language to segment processor")
        void forwards_language_to_segment_processor_when_boundary_seals() {
            Message existing = Message.assistant("I am fine");
            Message trigger = Message.user("Tell me more");
            when(pendingBufferStore.load("user1:agent1")).thenReturn(List.of(existing));
            when(contextCommitDetector.shouldCommit(any(CommitDetectionInput.class)))
                    .thenReturn(Mono.just(CommitDecision.commit(0.9, "test")));
            when(segmentProcessor.processSegment(
                            eq(memoryId), any(), eq("CONVERSATION"), any(), any(), eq("zh-CN")))
                    .thenReturn(Mono.just(RawDataResult.empty()));

            StepVerifier.create(
                            extractor.addMessage(
                                    memoryId,
                                    trigger,
                                    ExtractionConfig.withoutInsight().withLanguage("zh-CN")))
                    .assertNext(result -> assertThat(result).isNotNull())
                    .verifyComplete();

            verify(segmentProcessor)
                    .processSegment(
                            eq(memoryId), any(), eq("CONVERSATION"), any(), any(), eq("zh-CN"));
        }

        @Test
        @DisplayName("Seal path passes runtime context with durable-only metadata")
        void sealPathPassesRuntimeContextWithDurableOnlyMetadata() {
            Message existing =
                    new Message(
                            Message.Role.ASSISTANT,
                            List.of(),
                            Instant.parse("2026-03-27T02:17:00Z"),
                            null);
            Message trigger =
                    Message.user("Tell me more", Instant.parse("2026-03-27T02:18:00Z"), "Alice");
            when(pendingBufferStore.load("user1:agent1")).thenReturn(List.of(existing));
            when(contextCommitDetector.shouldCommit(any(CommitDetectionInput.class)))
                    .thenReturn(Mono.just(CommitDecision.commit(0.9, "test")));
            when(segmentProcessor.processSegment(
                            eq(memoryId), any(), eq("CONVERSATION"), any(), any(), any()))
                    .thenReturn(Mono.just(RawDataResult.empty()));

            StepVerifier.create(
                            extractor.addMessage(
                                    memoryId, trigger, ExtractionConfig.withoutInsight()))
                    .assertNext(result -> assertThat(result).isNotNull())
                    .verifyComplete();

            verify(segmentProcessor)
                    .processSegment(
                            eq(memoryId),
                            argThat(
                                    segment ->
                                            !segment.metadata().containsKey("messages")
                                                    && segment.metadata().isEmpty()
                                                    && segment.runtimeContext() != null
                                                    && segment.runtimeContext()
                                                            .startTime()
                                                            .equals(
                                                                    Instant.parse(
                                                                            "2026-03-27T02:17:00Z"))
                                                    && segment.runtimeContext()
                                                            .observedAt()
                                                            .equals(
                                                                    Instant.parse(
                                                                            "2026-03-27T02:17:00Z"))
                                                    && segment.runtimeContext().userName() == null),
                            eq("CONVERSATION"),
                            any(),
                            any(),
                            any());
        }
    }

    @Nested
    @DisplayName("Concurrent append")
    class ConcurrentAppend {

        @Test
        @DisplayName("Assistant messages from concurrent callers do not lose buffered messages")
        void assistant_messages_do_not_get_lost_under_concurrency() throws Exception {
            var pendingStore = new SlowSnapshotConversationBuffer();
            var recentStore = new InMemoryRecentConversationBuffer();
            MemoryExtractor localExtractor =
                    new MemoryExtractor(
                            unusedRawDataStep(),
                            unusedMemoryItemStep(),
                            unusedInsightStep(),
                            segmentProcessor,
                            contextCommitDetector,
                            pendingStore,
                            recentStore);
            var first = Message.assistant("first");
            var second = Message.assistant("second");
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);

            try (var executor = Executors.newFixedThreadPool(2)) {
                var firstFuture =
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                                    localExtractor
                                            .addMessage(
                                                    memoryId, first, ExtractionConfig.defaults())
                                            .blockOptional();
                                    return null;
                                });
                var secondFuture =
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                                    localExtractor
                                            .addMessage(
                                                    memoryId, second, ExtractionConfig.defaults())
                                            .blockOptional();
                                    return null;
                                });

                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                firstFuture.get(5, TimeUnit.SECONDS);
                secondFuture.get(5, TimeUnit.SECONDS);
            }

            assertThat(pendingStore.load("user1:agent1")).containsExactlyInAnyOrder(first, second);
            assertThat(recentStore.loadRecent("user1:agent1", 10))
                    .containsExactlyInAnyOrder(first, second);
        }
    }

    private static RawDataExtractStep unusedRawDataStep() {
        return (mid, content, contentType, metadata) -> Mono.just(RawDataResult.empty());
    }

    private static MemoryItemExtractStep unusedMemoryItemStep() {
        return (mid, rawResult, config) ->
                Mono.just(com.openmemind.ai.memory.core.extraction.result.MemoryItemResult.empty());
    }

    private static InsightExtractStep unusedInsightStep() {
        return (mid, memoryItemResult) ->
                Mono.just(com.openmemind.ai.memory.core.extraction.result.InsightResult.empty());
    }

    private static final class SlowSnapshotConversationBuffer implements PendingConversationBuffer {

        private final ConcurrentHashMap<String, List<Message>> buffers = new ConcurrentHashMap<>();

        @Override
        public void append(String sessionId, Message message) {
            buffers.compute(
                    sessionId,
                    (ignored, existing) -> {
                        var next = new ArrayList<>(existing != null ? existing : List.of());
                        next.add(message);
                        return next;
                    });
        }

        @Override
        public List<Message> load(String sessionId) {
            var snapshot = new ArrayList<>(buffers.getOrDefault(sessionId, List.of()));
            sleepQuietly();
            return snapshot;
        }

        @Override
        public void clear(String sessionId) {
            buffers.remove(sessionId);
        }

        private void sleepQuietly() {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while coordinating concurrent load", e);
            }
        }
    }
}
