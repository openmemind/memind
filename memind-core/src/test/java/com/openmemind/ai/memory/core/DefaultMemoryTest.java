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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.builder.DeepRetrievalOptions;
import com.openmemind.ai.memory.core.builder.ExtractionCommonOptions;
import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.InsightExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.QueryExpansionOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.builder.RetrievalAdvancedOptions;
import com.openmemind.ai.memory.core.builder.RetrievalCommonOptions;
import com.openmemind.ai.memory.core.builder.RetrievalOptions;
import com.openmemind.ai.memory.core.builder.SimpleRetrievalOptions;
import com.openmemind.ai.memory.core.builder.SufficiencyOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.source.FileExtractionSource;
import com.openmemind.ai.memory.core.extraction.source.UrlExtractionSource;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepStrategyConfig;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.support.TestMemoryIds;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultMemory Test")
class DefaultMemoryTest {

    @Mock private DefaultMemoryExtractor extractor;
    @Mock private MemoryRetriever retriever;
    @Mock private MemoryStore store;
    @Mock private MemoryBuffer memoryBuffer;
    @Mock private ItemOperations itemOperations;
    @Mock private InsightOperations insightOperations;
    @Mock private MemoryVector vector;

    private Memory memind;
    private MemoryId memoryId;

    @BeforeEach
    void setUp() {
        lenient().when(store.itemOperations()).thenReturn(itemOperations);
        lenient().when(store.insightOperations()).thenReturn(insightOperations);
        memind =
                new DefaultMemory(
                        extractor,
                        retriever,
                        store,
                        memoryBuffer,
                        vector,
                        null,
                        null,
                        MemoryBuildOptions.defaults());
        memoryId = TestMemoryIds.userAgent();
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
        @DisplayName("addMessages wraps conversation content and uses builder extraction defaults")
        void addMessagesWrapsConversationContentAndUsesBuilderExtractionDefaults() {
            var messages = List.of(Message.user("hello"), Message.assistant("hi"));
            var memory =
                    new DefaultMemory(
                            extractor,
                            retriever,
                            store,
                            memoryBuffer,
                            vector,
                            null,
                            null,
                            MemoryBuildOptions.builder()
                                    .extraction(
                                            new ExtractionOptions(
                                                    new ExtractionCommonOptions(
                                                            MemoryScope.AGENT,
                                                            Duration.ofSeconds(40),
                                                            "Chinese"),
                                                    RawDataExtractionOptions.defaults(),
                                                    ItemExtractionOptions.defaults(),
                                                    InsightExtractionOptions.defaults()))
                                    .build());
            when(extractor.extract(any(ExtractionRequest.class)))
                    .thenReturn(Mono.just(successResult()));

            StepVerifier.create(memory.addMessages(memoryId, messages))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(extractor)
                    .extract(
                            argThat(
                                    request ->
                                            request.content() instanceof ConversationContent cc
                                                    && cc.getMessages().equals(messages)
                                                    && request.config().scope() == MemoryScope.AGENT
                                                    && request.config()
                                                            .timeout()
                                                            .equals(Duration.ofSeconds(40))
                                                    && request.config()
                                                            .language()
                                                            .equals("Chinese")));
        }

        @Test
        @DisplayName("extract(memoryId, content) uses builder-level extraction defaults")
        void extractUsesBuilderLevelExtractionDefaults() {
            var memory =
                    new DefaultMemory(
                            extractor,
                            retriever,
                            store,
                            memoryBuffer,
                            vector,
                            null,
                            null,
                            MemoryBuildOptions.builder()
                                    .extraction(
                                            new ExtractionOptions(
                                                    new ExtractionCommonOptions(
                                                            MemoryScope.AGENT,
                                                            Duration.ofSeconds(40),
                                                            "Chinese"),
                                                    RawDataExtractionOptions.defaults(),
                                                    ItemExtractionOptions.defaults(),
                                                    InsightExtractionOptions.defaults()))
                                    .build());
            when(extractor.extract(any(ExtractionRequest.class)))
                    .thenReturn(Mono.just(successResult()));

            StepVerifier.create(
                            memory.extract(
                                    memoryId,
                                    new ConversationContent(List.of(Message.user("test")))))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(extractor)
                    .extract(
                            argThat(
                                    request ->
                                            request.config().scope() == MemoryScope.AGENT
                                                    && request.config()
                                                            .timeout()
                                                            .equals(Duration.ofSeconds(40))
                                                    && request.config()
                                                            .language()
                                                            .equals("Chinese")));
        }

        @Test
        @DisplayName("extract(request) supports file requests and applies builder defaults")
        void extractRequestSupportsFileRequestsAndAppliesBuilderDefaults() {
            var memory =
                    new DefaultMemory(
                            extractor,
                            retriever,
                            store,
                            memoryBuffer,
                            vector,
                            null,
                            null,
                            MemoryBuildOptions.builder()
                                    .extraction(
                                            new ExtractionOptions(
                                                    new ExtractionCommonOptions(
                                                            MemoryScope.AGENT,
                                                            Duration.ofSeconds(40),
                                                            "Chinese"),
                                                    RawDataExtractionOptions.defaults(),
                                                    ItemExtractionOptions.defaults(),
                                                    InsightExtractionOptions.defaults()))
                                    .build());
            when(extractor.extract(any(ExtractionRequest.class)))
                    .thenReturn(Mono.just(successResult()));

            StepVerifier.create(
                            memory.extract(
                                    ExtractionRequest.file(
                                            memoryId,
                                            "report.pdf",
                                            new byte[] {1, 2, 3},
                                            "application/pdf")))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(extractor)
                    .extract(
                            argThat(
                                    request ->
                                            request.memoryId().equals(memoryId)
                                                    && request.source()
                                                            instanceof FileExtractionSource source
                                                    && source.fileName().equals("report.pdf")
                                                    && source.mimeType().equals("application/pdf")
                                                    && request.config().scope() == MemoryScope.AGENT
                                                    && request.config()
                                                            .timeout()
                                                            .equals(Duration.ofSeconds(40))
                                                    && request.config()
                                                            .language()
                                                            .equals("Chinese")));
        }

        @Test
        @DisplayName("extract(request) preserves explicit request config")
        void extractRequestPreservesExplicitRequestConfig() {
            var config =
                    ExtractionConfig.agentOnly()
                            .withEnableForesight(true)
                            .withTimeout(Duration.ofSeconds(12))
                            .withLanguage("Japanese");
            when(extractor.extract(any(ExtractionRequest.class)))
                    .thenReturn(Mono.just(successResult()));

            StepVerifier.create(
                            memind.extract(
                                    ExtractionRequest.url(
                                                    memoryId,
                                                    "https://example.com/report.pdf",
                                                    "report.pdf",
                                                    "application/pdf")
                                            .withConfig(config)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(extractor)
                    .extract(
                            argThat(
                                    request ->
                                            request.source() instanceof UrlExtractionSource
                                                    && request.config().equals(config)));
        }

        @Test
        @DisplayName("addMessage uses builder-level extraction defaults")
        void addMessageUsesBuilderLevelExtractionDefaults() {
            var memory =
                    new DefaultMemory(
                            extractor,
                            retriever,
                            store,
                            memoryBuffer,
                            vector,
                            null,
                            null,
                            MemoryBuildOptions.builder()
                                    .extraction(
                                            new ExtractionOptions(
                                                    new ExtractionCommonOptions(
                                                            MemoryScope.USER,
                                                            Duration.ofSeconds(30),
                                                            "Chinese"),
                                                    RawDataExtractionOptions.defaults(),
                                                    new ItemExtractionOptions(true),
                                                    InsightExtractionOptions.defaults()))
                                    .build());
            var message = Message.user("test");
            when(extractor.addMessage(eq(memoryId), eq(message), any()))
                    .thenReturn(Mono.just(successResult()));

            StepVerifier.create(memory.addMessage(memoryId, message))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(extractor)
                    .addMessage(
                            eq(memoryId),
                            eq(message),
                            argThat(
                                    config ->
                                            config.language().equals("Chinese")
                                                    && config.enableForesight()
                                                    && config.timeout()
                                                            .equals(Duration.ofSeconds(30))));
        }
    }

    @Nested
    @DisplayName("Memory Retrieval")
    class Retrieve {

        @Test
        @DisplayName("retrieve deep materializes retrieval config from build options")
        void retrieveDeepMaterializesRetrievalConfigFromBuildOptions() {
            var memory =
                    new DefaultMemory(
                            extractor,
                            retriever,
                            store,
                            memoryBuffer,
                            vector,
                            null,
                            null,
                            MemoryBuildOptions.builder()
                                    .retrieval(
                                            new RetrievalOptions(
                                                    new RetrievalCommonOptions(false),
                                                    SimpleRetrievalOptions.defaults(),
                                                    new DeepRetrievalOptions(
                                                            Duration.ofSeconds(45),
                                                            5,
                                                            22,
                                                            false,
                                                            0,
                                                            new QueryExpansionOptions(5),
                                                            new SufficiencyOptions(9)),
                                                    RetrievalAdvancedOptions.defaults()))
                                    .build());
            when(retriever.retrieve(any(RetrievalRequest.class)))
                    .thenReturn(Mono.just(RetrievalResult.empty("deep_retrieval", "test")));

            StepVerifier.create(memory.retrieve(memoryId, "test", RetrievalConfig.Strategy.DEEP))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(retriever)
                    .retrieve(
                            argThat(
                                    request -> {
                                        var strategyConfig =
                                                (DeepStrategyConfig)
                                                        request.config().strategyConfig();
                                        var expandedQueries =
                                                strategyConfig
                                                        .queryExpansion()
                                                        .maxExpandedQueries();
                                        var sufficiencyTopK =
                                                strategyConfig.sufficiency().itemTopK();
                                        return request.config()
                                                        .timeout()
                                                        .equals(Duration.ofSeconds(45))
                                                && !request.config().enableCache()
                                                && request.config().tier2().topK() == 22
                                                && expandedQueries == 5
                                                && sufficiencyTopK == 9;
                                    }));
        }
    }

    @Nested
    @DisplayName("Deletion APIs")
    class DeleteApis {

        @Test
        @DisplayName("deleteItems deletes vectors before store rows and invalidates retriever")
        void deleteItemsDeletesVectorsThenStoreRowsAndInvalidatesRetriever() {
            var itemIds = List.of(1L, 2L);
            var items = List.of(memoryItem(1L, "vec-1"), memoryItem(2L, "vec-2"));
            when(itemOperations.getItemsByIds(memoryId, itemIds)).thenReturn(items);
            when(vector.deleteBatch(memoryId, List.of("vec-1", "vec-2"))).thenReturn(Mono.empty());

            StepVerifier.create(memind.deleteItems(memoryId, itemIds)).verifyComplete();

            InOrder inOrder = inOrder(itemOperations, vector, retriever);
            inOrder.verify(itemOperations).getItemsByIds(memoryId, itemIds);
            inOrder.verify(vector).deleteBatch(memoryId, List.of("vec-1", "vec-2"));
            inOrder.verify(itemOperations).deleteItems(memoryId, itemIds);
            inOrder.verify(retriever).onDataChanged(memoryId);
        }

        @Test
        @DisplayName("deleteItems skips vector delete when no vector ids exist")
        void deleteItemsSkipsVectorDeleteWhenNoVectorIdsExist() {
            var itemIds = List.of(3L);
            when(itemOperations.getItemsByIds(memoryId, itemIds))
                    .thenReturn(List.of(memoryItem(3L, null)));

            StepVerifier.create(memind.deleteItems(memoryId, itemIds)).verifyComplete();

            InOrder inOrder = inOrder(itemOperations, retriever);
            inOrder.verify(itemOperations).getItemsByIds(memoryId, itemIds);
            inOrder.verify(itemOperations).deleteItems(memoryId, itemIds);
            inOrder.verify(retriever).onDataChanged(memoryId);
            verifyNoInteractions(vector);
        }

        @Test
        @DisplayName("deleteInsights deletes only requested insights and invalidates retriever")
        void deleteInsightsDeletesOnlyRequestedInsightsAndInvalidatesRetriever() {
            var insightIds = List.of(11L, 12L);

            StepVerifier.create(memind.deleteInsights(memoryId, insightIds)).verifyComplete();

            InOrder inOrder = inOrder(insightOperations, retriever);
            inOrder.verify(insightOperations).deleteInsights(memoryId, insightIds);
            inOrder.verify(retriever).onDataChanged(memoryId);
            verifyNoInteractions(vector);
        }
    }

    @Test
    @DisplayName("close is idempotent")
    void closeIsIdempotent() {
        AtomicInteger closeCount = new AtomicInteger();
        memind =
                new DefaultMemory(
                        extractor,
                        retriever,
                        store,
                        memoryBuffer,
                        vector,
                        null,
                        closeCount::incrementAndGet,
                        MemoryBuildOptions.defaults());

        memind.close();
        memind.close();

        assertThat(closeCount).hasValue(1);
    }

    private MemoryItem memoryItem(Long itemId, String vectorId) {
        return new MemoryItem(
                itemId,
                memoryId.toIdentifier(),
                "content-" + itemId,
                MemoryScope.USER,
                MemoryCategory.PROFILE,
                "conversation",
                vectorId,
                "raw-" + itemId,
                "hash-" + itemId,
                Instant.now(),
                Instant.now(),
                Map.of(),
                Instant.now(),
                MemoryItemType.FACT);
    }
}
