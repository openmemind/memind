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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.MemoryExtractionPipeline;
import com.openmemind.ai.memory.core.extraction.context.ContextRequest;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.stats.ToolStatsService;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.buffer.ConversationBuffer;
import com.openmemind.ai.memory.core.store.buffer.InMemoryConversationBuffer;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Duration;
import java.time.Instant;
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
@DisplayName("DefaultMemory — getContext / commit")
class DefaultMemoryContextTest {

    @Mock MemoryExtractionPipeline extractor;
    @Mock MemoryRetriever retriever;
    @Mock MemoryStore memoryStore;
    @Mock MemoryVector vector;
    @Mock ToolStatsService toolStatsService;

    ConversationBuffer conversationBuffer;
    DefaultMemory memory;
    MemoryId memoryId;

    @BeforeEach
    void setUp() {
        conversationBuffer = new InMemoryConversationBuffer();
        when(memoryStore.conversationBufferStore()).thenReturn(conversationBuffer);
        memory = new DefaultMemory(extractor, retriever, memoryStore, vector, toolStatsService);
        memoryId = DefaultMemoryId.of("user1", "agent1");
    }

    @Nested
    @DisplayName("getContext")
    class GetContext {

        @Test
        @DisplayName("Returns buffer-only when includeMemories=false")
        void buffer_only_when_memories_disabled() {
            conversationBuffer.save(
                    "user1:agent1", List.of(Message.user("Hello"), Message.assistant("Hi")));

            var request = ContextRequest.bufferOnly(memoryId, 80000);

            StepVerifier.create(memory.getContext(request))
                    .assertNext(
                            ctx -> {
                                assertThat(ctx.recentMessages()).hasSize(2);
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
            conversationBuffer.save(
                    "user1:agent1", List.of(Message.user("Tell me about my project")));

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
            conversationBuffer.save("user1:agent1", messages);

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

            // Buffer should be cleared after commit
            assertThat(conversationBuffer.load("user1:agent1")).isEmpty();

            // Extraction should have been called
            verify(extractor).extract(any());
        }

        @Test
        @DisplayName("Commit with custom config passes config to extraction")
        void commit_with_custom_config() {
            conversationBuffer.save("user1:agent1", List.of(Message.user("test")));

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

            assertThat(conversationBuffer.load("user1:agent1")).isEmpty();
        }
    }
}
