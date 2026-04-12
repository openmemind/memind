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
package com.openmemind.ai.memory.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.buffer.InMemoryConversationBuffer;
import com.openmemind.ai.memory.core.buffer.InMemoryRecentConversationBuffer;
import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.builder.DeepRetrievalOptions;
import com.openmemind.ai.memory.core.builder.ExtractionCommonOptions;
import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.InsightExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.builder.RetrievalAdvancedOptions;
import com.openmemind.ai.memory.core.builder.RetrievalCommonOptions;
import com.openmemind.ai.memory.core.builder.RetrievalOptions;
import com.openmemind.ai.memory.core.builder.SimpleRetrievalOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.context.ContextRequest;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
@DisplayName("DefaultMemory — getContext / commit")
class DefaultMemoryContextTest {

    @Mock
    MemoryExtractor extractor;
    @Mock MemoryRetriever retriever;
    @Mock MemoryStore memoryStore;
    @Mock MemoryVector vector;

    PendingConversationBuffer pendingConversationBuffer;
    RecentConversationBuffer recentConversationBuffer;
    MemoryBuffer memoryBuffer;
    DefaultMemory memory;
    MemoryId memoryId;

    @BeforeEach
    void setUp() {
        pendingConversationBuffer = new InMemoryConversationBuffer();
        recentConversationBuffer = new InMemoryRecentConversationBuffer();
        memoryBuffer =
                MemoryBuffer.of(
                        mock(InsightBuffer.class),
                        pendingConversationBuffer,
                        recentConversationBuffer);
        memory =
                new DefaultMemory(
                        extractor,
                        retriever,
                        memoryStore,
                        memoryBuffer,
                        vector,
                        null,
                        null,
                        MemoryBuildOptions.defaults());
        memoryId = DefaultMemoryId.of("user1", "agent1");
    }

    @Nested
    @DisplayName("getContext")
    class GetContext {

        @Test
        @DisplayName("Returns buffer-only when includeMemories=false")
        void buffer_only_when_memories_disabled() {
            pendingConversationBuffer.append("user1:agent1", Message.user("stale"));
            pendingConversationBuffer.append("user1:agent1", Message.assistant("pending"));
            recentConversationBuffer.append("user1:agent1", Message.user("Hello"));
            recentConversationBuffer.append("user1:agent1", Message.assistant("Hi"));

            var request = ContextRequest.bufferOnly(memoryId, 80000);

            StepVerifier.create(memory.getContext(request))
                    .assertNext(
                            ctx -> {
                                assertThat(ctx.recentMessages()).hasSize(2);
                                assertThat(ctx.recentMessages())
                                        .extracting(Message::textContent)
                                        .containsExactly("Hello", "Hi");
                                assertThat(ctx.hasMemories()).isFalse();
                                assertThat(ctx.totalTokens()).isGreaterThan(0);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Returns buffer-only when buffer is empty (even with includeMemories=true)")
        void buffer_only_when_buffer_empty() {
            var request = ContextRequest.of(memoryId, 80000);

            StepVerifier.create(memory.getContext(request))
                    .assertNext(
                            ctx -> {
                                assertThat(ctx.recentMessages()).isEmpty();
                                assertThat(ctx.hasMemories()).isFalse();
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Retrieves memories when buffer is non-empty and includeMemories=true")
        void retrieves_memories_when_buffer_non_empty() {
            recentConversationBuffer.append(
                    "user1:agent1", Message.user("Tell me about my project"));

            var scored =
                    new ScoredResult(
                            ScoredResult.SourceType.ITEM,
                            "item-1",
                            "User works on Project Atlas",
                            0.9f,
                            0.9,
                            Instant.now());
            var retrievalResult =
                    new RetrievalResult(
                            List.of(scored),
                            List.of(),
                            List.of(),
                            List.of(),
                            "SIMPLE",
                            "Tell me about my project");
            when(retriever.retrieve(any(RetrievalRequest.class)))
                    .thenReturn(Mono.just(retrievalResult));

            var request = ContextRequest.of(memoryId, 80000);

            StepVerifier.create(memory.getContext(request))
                    .assertNext(
                            ctx -> {
                                assertThat(ctx.recentMessages()).hasSize(1);
                                assertThat(ctx.hasMemories()).isTrue();
                                assertThat(ctx.totalTokens()).isGreaterThan(0);
                                assertThat(ctx.formattedContext())
                                        .contains("Project Atlas")
                                        .contains("Tell me about my project");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Uses builder-level simple retrieval defaults when loading context")
        void getContextUsesBuilderLevelSimpleDefaults() {
            memory =
                    new DefaultMemory(
                            extractor,
                            retriever,
                            memoryStore,
                            memoryBuffer,
                            vector,
                            null,
                            null,
                            MemoryBuildOptions.builder()
                                    .retrieval(
                                            new RetrievalOptions(
                                                    new RetrievalCommonOptions(false),
                                                    new SimpleRetrievalOptions(
                                                            Duration.ofSeconds(15), 5, 9, 3, true),
                                                    DeepRetrievalOptions.defaults(),
                                                    RetrievalAdvancedOptions.defaults()))
                                    .build());
            recentConversationBuffer.append(
                    "user1:agent1", Message.user("Tell me about my project"));
            when(retriever.retrieve(any(RetrievalRequest.class)))
                    .thenReturn(
                            Mono.just(RetrievalResult.empty("simple", "Tell me about my project")));

            StepVerifier.create(memory.getContext(ContextRequest.of(memoryId, 80000)))
                    .assertNext(ctx -> assertThat(ctx.hasMemories()).isFalse())
                    .verifyComplete();

            verify(retriever)
                    .retrieve(
                            argThat(
                                    request ->
                                            request.config()
                                                            .timeout()
                                                            .equals(Duration.ofSeconds(15))
                                                    && !request.config().enableCache()
                                                    && request.config().tier2().topK() == 9));
        }

        @Test
        @DisplayName("Loads only the configured recent message window")
        void loads_only_recent_message_window() {
            for (int i = 1; i <= 12; i++) {
                recentConversationBuffer.append("user1:agent1", Message.user("message-" + i));
            }

            var request =
                    new ContextRequest(memoryId, 80000, false, RetrievalConfig.Strategy.SIMPLE, 10);

            StepVerifier.create(memory.getContext(request))
                    .assertNext(
                            ctx ->
                                    assertThat(ctx.recentMessages())
                                            .extracting(Message::textContent)
                                            .containsExactly(
                                                    "message-3",
                                                    "message-4",
                                                    "message-5",
                                                    "message-6",
                                                    "message-7",
                                                    "message-8",
                                                    "message-9",
                                                    "message-10",
                                                    "message-11",
                                                    "message-12"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("commit")
    class Commit {

        @Test
        @DisplayName("Returns empty result when buffer is empty")
        void empty_result_when_buffer_empty() {
            StepVerifier.create(memory.commit(memoryId))
                    .assertNext(
                            result -> {
                                assertThat(result.status())
                                        .isEqualTo(
                                                com.openmemind.ai.memory.core.extraction
                                                        .ExtractionStatus.SUCCESS);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Clears buffer and triggers extraction when buffer is non-empty")
        void clears_buffer_and_extracts() {
            var messages =
                    List.of(Message.user("I like Python"), Message.assistant("Python is great!"));
            messages.forEach(message -> pendingConversationBuffer.append("user1:agent1", message));
            recentConversationBuffer.append("user1:agent1", Message.user("Keep recent user"));
            recentConversationBuffer.append(
                    "user1:agent1", Message.assistant("Keep recent assistant"));

            var expectedResult =
                    ExtractionResult.success(
                            memoryId,
                            RawDataResult.empty(),
                            MemoryItemResult.empty(),
                            InsightResult.empty(),
                            Duration.ofMillis(100));
            when(extractor.extract(any())).thenReturn(Mono.just(expectedResult));

            StepVerifier.create(memory.commit(memoryId))
                    .assertNext(result -> assertThat(result).isNotNull())
                    .verifyComplete();

            assertThat(pendingConversationBuffer.load("user1:agent1")).isEmpty();
            assertThat(recentConversationBuffer.loadRecent("user1:agent1", 10))
                    .extracting(Message::textContent)
                    .containsExactly("Keep recent user", "Keep recent assistant");
            verify(extractor).extract(any());
        }

        @Test
        @DisplayName("Commit with custom config passes config to extraction")
        void commit_with_custom_config() {
            pendingConversationBuffer.append("user1:agent1", Message.user("test"));

            var expectedResult =
                    ExtractionResult.success(
                            memoryId,
                            RawDataResult.empty(),
                            MemoryItemResult.empty(),
                            InsightResult.empty(),
                            Duration.ofMillis(50));
            when(extractor.extract(any())).thenReturn(Mono.just(expectedResult));

            var config = ExtractionConfig.defaults().withLanguage("Chinese");

            StepVerifier.create(memory.commit(memoryId, config))
                    .assertNext(result -> assertThat(result).isNotNull())
                    .verifyComplete();

            assertThat(pendingConversationBuffer.load("user1:agent1")).isEmpty();
        }

        @Test
        @DisplayName("Uses builder-level extraction defaults when committing")
        void commitUsesBuilderLevelExtractionDefaults() {
            pendingConversationBuffer.append("user1:agent1", Message.user("test"));
            var expectedResult =
                    ExtractionResult.success(
                            memoryId,
                            RawDataResult.empty(),
                            MemoryItemResult.empty(),
                            InsightResult.empty(),
                            Duration.ofMillis(50));
            when(extractor.extract(any())).thenReturn(Mono.just(expectedResult));

            memory =
                    new DefaultMemory(
                            extractor,
                            retriever,
                            memoryStore,
                            memoryBuffer,
                            vector,
                            null,
                            null,
                            MemoryBuildOptions.builder()
                                    .extraction(
                                            new ExtractionOptions(
                                                    new ExtractionCommonOptions(
                                                            MemoryScope.AGENT,
                                                            Duration.ofSeconds(25),
                                                            "Chinese"),
                                                    RawDataExtractionOptions.defaults(),
                                                    ItemExtractionOptions.defaults(),
                                                    InsightExtractionOptions.defaults()))
                                    .build());

            StepVerifier.create(memory.commit(memoryId))
                    .assertNext(result -> assertThat(result).isNotNull())
                    .verifyComplete();

            verify(extractor)
                    .extract(
                            argThat(
                                    request ->
                                            request.config().scope() == MemoryScope.AGENT
                                                    && request.config()
                                                            .timeout()
                                                            .equals(Duration.ofSeconds(25))
                                                    && request.config()
                                                            .language()
                                                            .equals("Chinese")));
        }

        @Test
        @DisplayName("Coordinates concurrent addMessage sealing with explicit commit")
        void coordinatesConcurrentAddMessageSealingWithExplicitCommit() throws Exception {
            var localPendingConversationBuffer = new InMemoryConversationBuffer();
            var localRecentConversationBuffer = new InMemoryRecentConversationBuffer();
            var localMemoryBuffer =
                    MemoryBuffer.of(
                            mock(InsightBuffer.class),
                            localPendingConversationBuffer,
                            localRecentConversationBuffer);
            var extractedBatches = Collections.synchronizedList(new ArrayList<String>());
            var detectionStarted = new CountDownLatch(1);
            var releaseDetection = new CountDownLatch(1);
            var existing = Message.assistant("I am fine");
            var trigger = Message.user("Tell me more");

            localPendingConversationBuffer.append("user1:agent1", existing);
            localRecentConversationBuffer.append("user1:agent1", existing);

            var localExtractor =
                    new DefaultMemoryExtractor(
                            (mid, content, contentType, metadata) ->
                                    Mono.fromCallable(
                                            () -> {
                                                extractedBatches.add(content.toContentString());
                                                return RawDataResult.empty();
                                            }),
                            (mid, rawResult, config) -> Mono.just(MemoryItemResult.empty()),
                            (mid, itemResult) -> Mono.just(InsightResult.empty()),
                            (mid, segment, type, contentId, metadata) ->
                                    Mono.fromCallable(
                                            () -> {
                                                extractedBatches.add(segment.content());
                                                return RawDataResult.empty();
                                            }),
                            input ->
                                    Mono.fromCallable(
                                            () -> {
                                                detectionStarted.countDown();
                                                assertThat(
                                                                releaseDetection.await(
                                                                        5, TimeUnit.SECONDS))
                                                        .isTrue();
                                                return com.openmemind.ai.memory.core.extraction
                                                        .context.CommitDecision.commit(0.9, "test");
                                            }),
                            localPendingConversationBuffer,
                            localRecentConversationBuffer);
            var localMemory =
                    new DefaultMemory(
                            localExtractor,
                            retriever,
                            memoryStore,
                            localMemoryBuffer,
                            vector,
                            null,
                            null,
                            MemoryBuildOptions.defaults());

            try (var executor = Executors.newFixedThreadPool(2)) {
                var addMessageFuture =
                        executor.submit(
                                () ->
                                        localMemory
                                                .addMessage(
                                                        memoryId,
                                                        trigger,
                                                        ExtractionConfig.withoutInsight())
                                                .block());

                assertThat(detectionStarted.await(5, TimeUnit.SECONDS)).isTrue();

                var commitFuture =
                        executor.submit(
                                () ->
                                        localMemory
                                                .commit(memoryId, ExtractionConfig.withoutInsight())
                                                .block());

                releaseDetection.countDown();

                assertThat(addMessageFuture.get(5, TimeUnit.SECONDS)).isNotNull();
                assertThat(commitFuture.get(5, TimeUnit.SECONDS)).isNotNull();
            }

            var pendingMessages = localPendingConversationBuffer.load("user1:agent1");
            long existingCopies =
                    countLineOccurrences(extractedBatches, "assistant: I am fine")
                            + pendingMessages.stream()
                                    .filter(message -> "I am fine".equals(message.textContent()))
                                    .count();
            long triggerCopies =
                    countLineOccurrences(extractedBatches, "user: Tell me more")
                            + pendingMessages.stream()
                                    .filter(message -> "Tell me more".equals(message.textContent()))
                                    .count();

            assertThat(existingCopies).isEqualTo(1);
            assertThat(triggerCopies).isEqualTo(1);
        }
    }

    private static long countLineOccurrences(List<String> batches, String expectedLine) {
        return batches.stream()
                .mapToLong(batch -> batch.lines().filter(expectedLine::equals).count())
                .sum();
    }
}
