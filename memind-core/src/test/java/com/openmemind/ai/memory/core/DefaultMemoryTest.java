package com.openmemind.ai.memory.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.ToolCallStats;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.stats.ToolStatsService;
import com.openmemind.ai.memory.core.store.MemoryStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
@DisplayName("DefaultMemory Test")
class DefaultMemoryTest {

    @Mock private MemoryExtractor extractor;
    @Mock private MemoryRetriever retriever;
    @Mock private MemoryStore store;
    @Mock private ToolStatsService toolStatsService;

    private Memory memind;
    private MemoryId memoryId;

    @BeforeEach
    void setUp() {
        memind = new DefaultMemory(extractor, retriever, store, toolStatsService);
        memoryId = DefaultMemoryId.of("user1", "agent1");
    }

    private ExtractionResult successResult() {
        return ExtractionResult.success(
                memoryId,
                RawDataResult.empty(),
                MemoryItemResult.empty(),
                InsightResult.empty(),
                Duration.ZERO);
    }

    @Nested
    @DisplayName("Conversation Memory Extraction")
    class Memorize {

        @Test
        @DisplayName("addMessages(memoryId, messages) Delegate extract batch mode")
        void memorizeWithDefaultConfig() {
            var messages = List.of(Message.user("hello"), Message.assistant("hi"));
            var result = ExtractionResult.failed(memoryId, Duration.ZERO, null);
            when(extractor.extract(any(ExtractionRequest.class))).thenReturn(Mono.just(result));

            StepVerifier.create(memind.addMessages(memoryId, messages))
                    .expectNext(result)
                    .verifyComplete();

            verify(extractor).extract(any(ExtractionRequest.class));
        }

        @Test
        @DisplayName("addMessage Delegate extractor.addMessage")
        void addMessageDelegates() {
            var message = Message.user("hello");
            when(extractor.addMessage(eq(memoryId), eq(message), any())).thenReturn(Mono.empty());

            StepVerifier.create(memind.addMessage(memoryId, message)).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Memory Retrieval")
    class Retrieve {

        @Test
        @DisplayName("retrieve(memoryId, query) Delegate retriever")
        void retrieveSimple() {
            var result = RetrievalResult.empty("default", "User Preferences");
            when(retriever.retrieve(any(RetrievalRequest.class))).thenReturn(Mono.just(result));

            StepVerifier.create(
                            memind.retrieve(
                                    memoryId, "User Preferences", RetrievalConfig.Strategy.SIMPLE))
                    .expectNext(result)
                    .verifyComplete();
        }
    }

    // ===== Agent Memory Reporting =====

    @Nested
    @DisplayName("reportToolCall")
    class ReportToolCall {

        @Test
        @DisplayName("Should delegate ToolCallRecord to MemoryExtractor for extraction")
        void shouldDelegateToExtractor() {
            var record =
                    new ToolCallRecord(
                            "web_search",
                            "{\"q\":\"java 21\"}",
                            "Found 10 results",
                            "success",
                            2300L,
                            150,
                            75,
                            "hash1",
                            Instant.now());

            when(extractor.extract(any(ExtractionRequest.class)))
                    .thenReturn(Mono.just(successResult()));

            StepVerifier.create(memind.reportToolCall(memoryId, record))
                    .assertNext(result -> assertThat(result).isNotNull())
                    .verifyComplete();

            verify(extractor).extract(any(ExtractionRequest.class));
        }

        @Test
        @DisplayName("Should return ExtractionResult instead of Void")
        void shouldReturnExtractionResult() {
            var record =
                    new ToolCallRecord(
                            "code_exec",
                            "print('hello')",
                            "hello",
                            "success",
                            1000L,
                            50,
                            25,
                            "hash2",
                            Instant.now());

            when(extractor.extract(any(ExtractionRequest.class)))
                    .thenReturn(Mono.just(successResult()));

            StepVerifier.create(memind.reportToolCall(memoryId, record))
                    .assertNext(
                            result -> {
                                assertThat(result.isSuccess()).isTrue();
                                assertThat(result.memoryId()).isEqualTo(memoryId);
                            })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("reportToolCalls")
    class ReportToolCalls {

        @Test
        @DisplayName("Should report multiple ToolCallRecords in batch")
        void shouldReportMultipleToolCalls() {
            var records =
                    List.of(
                            new ToolCallRecord(
                                    "t1", "i1", "o1", "success", 1000L, 10, 5, "h1", Instant.now()),
                            new ToolCallRecord(
                                    "t2", "i2", "o2", "error", 2000L, 20, 10, "h2", Instant.now()));

            when(extractor.extract(any(ExtractionRequest.class)))
                    .thenReturn(Mono.just(successResult()));

            StepVerifier.create(memind.reportToolCalls(memoryId, records))
                    .assertNext(result -> assertThat(result).isNotNull())
                    .verifyComplete();

            verify(extractor).extract(any(ExtractionRequest.class));
        }
    }

    @Nested
    @DisplayName("getToolStats")
    class GetToolStats {

        @Test
        @DisplayName("Should delegate to ToolStatsService and return statistics")
        void shouldDelegateToToolStatsService() {
            var stats = new ToolCallStats(3, 3, 0.67, 1.833, 0.0, 116.67);
            when(toolStatsService.getToolStats(memoryId, "web_search"))
                    .thenReturn(Mono.just(stats));

            StepVerifier.create(memind.getToolStats(memoryId, "web_search"))
                    .assertNext(
                            result -> {
                                assertThat(result.totalCalls()).isEqualTo(3);
                                assertThat(result.successRate()).isGreaterThan(0.6);
                            })
                    .verifyComplete();

            verify(toolStatsService).getToolStats(memoryId, "web_search");
        }

        @Test
        @DisplayName("Should return empty stats when no matching tools")
        void shouldReturnEmptyStatsWhenNoTools() {
            var emptyStats = new ToolCallStats(0, 0, 0.0, 0.0, 0.0, 0.0);
            when(toolStatsService.getToolStats(memoryId, "nonexistent"))
                    .thenReturn(Mono.just(emptyStats));

            StepVerifier.create(memind.getToolStats(memoryId, "nonexistent"))
                    .assertNext(
                            stats -> {
                                assertThat(stats.totalCalls()).isEqualTo(0);
                                assertThat(stats.successRate()).isEqualTo(0.0);
                            })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getAllToolStats")
    class GetAllToolStats {

        @Test
        @DisplayName(
                "Should delegate to ToolStatsService and return statistics grouped by tool name")
        void shouldDelegateToToolStatsService() {
            var webSearchStats = new ToolCallStats(2, 2, 1.0, 1.75, 0.0, 125.0);
            var codeExecStats = new ToolCallStats(1, 1, 0.0, 3.0, 0.0, 200.0);
            var statsMap = Map.of("web_search", webSearchStats, "code_exec", codeExecStats);
            when(toolStatsService.getAllToolStats(memoryId)).thenReturn(Mono.just(statsMap));

            StepVerifier.create(memind.getAllToolStats(memoryId))
                    .assertNext(
                            result -> {
                                assertThat(result).containsKeys("web_search", "code_exec");
                                assertThat(result.get("web_search").totalCalls()).isEqualTo(2);
                                assertThat(result.get("code_exec").totalCalls()).isEqualTo(1);
                            })
                    .verifyComplete();

            verify(toolStatsService).getAllToolStats(memoryId);
        }
    }
}
