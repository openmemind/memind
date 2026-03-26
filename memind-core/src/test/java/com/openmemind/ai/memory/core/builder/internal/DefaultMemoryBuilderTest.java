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
package com.openmemind.ai.memory.core.builder.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.DefaultMemory;
import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuilder;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig;
import com.openmemind.ai.memory.core.extraction.context.LlmContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.rawdata.RawDataLayer;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ConversationContentProcessor;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.buffer.ConversationBuffer;
import com.openmemind.ai.memory.core.store.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.store.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.store.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DefaultMemoryBuilderTest {

    private static final StructuredChatClient CHAT_CLIENT = proxy(StructuredChatClient.class);
    private static final RawDataOperations RAW_DATA_OPERATIONS = proxy(RawDataOperations.class);
    private static final ItemOperations ITEM_OPERATIONS = proxy(ItemOperations.class);
    private static final InsightOperations INSIGHT_OPERATIONS = proxy(InsightOperations.class);
    private static final MemoryTextSearch TEXT_SEARCH = proxy(MemoryTextSearch.class);
    private static final InsightBuffer INSIGHT_BUFFER_STORE = proxy(InsightBuffer.class);
    private static final ConversationBuffer CONVERSATION_BUFFER_STORE =
            proxy(ConversationBuffer.class);
    private static final RecentConversationBuffer RECENT_CONVERSATION_BUFFER =
            proxy(RecentConversationBuffer.class);
    private static final MemoryVector MEMORY_VECTOR = proxy(MemoryVector.class);
    private static final MemoryStore MEMORY_STORE =
            new FixedMemoryStore(RAW_DATA_OPERATIONS, ITEM_OPERATIONS, INSIGHT_OPERATIONS, null);
    private static final MemoryBuffer MEMORY_BUFFER =
            new FixedMemoryBuffer(
                    INSIGHT_BUFFER_STORE,
                    CONVERSATION_BUFFER_STORE,
                    RECENT_CONVERSATION_BUFFER,
                    null);

    @Test
    void memoryExposesStaticBuilderEntryPoint() {
        assertThat(Memory.builder()).isNotNull().isInstanceOf(MemoryBuilder.class);
    }

    @Test
    void buildFailsWhenChatClientIsMissing() {
        assertThatThrownBy(() -> Memory.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chat client");
    }

    @Test
    void buildFailsWhenStoreIsMissing() {
        assertThatThrownBy(
                        () ->
                                Memory.builder()
                                        .chatClient(CHAT_CLIENT)
                                        .buffer(MEMORY_BUFFER)
                                        .vector(MEMORY_VECTOR)
                                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("store");
    }

    @Test
    void buildFailsWhenBufferIsMissing() {
        assertThatThrownBy(
                        () ->
                                Memory.builder()
                                        .chatClient(CHAT_CLIENT)
                                        .store(MEMORY_STORE)
                                        .vector(MEMORY_VECTOR)
                                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("buffer");
    }

    @Test
    void buildFailsWhenVectorIsMissing() {
        assertThatThrownBy(
                        () ->
                                Memory.builder()
                                        .chatClient(CHAT_CLIENT)
                                        .store(MEMORY_STORE)
                                        .buffer(MEMORY_BUFFER)
                                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector");
    }

    @Test
    void buildCreatesDefaultMemoryWhenRequiredComponentsAreProvided() {
        Memory memory =
                Memory.builder()
                        .chatClient(CHAT_CLIENT)
                        .store(MEMORY_STORE)
                        .buffer(MEMORY_BUFFER)
                        .vector(MEMORY_VECTOR)
                        .build();

        assertThat(memory).isNotNull().isInstanceOf(DefaultMemory.class);
    }

    @Test
    void buildReusesSingleCaptionGeneratorAcrossRawDataPipeline() {
        DefaultMemory memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .build();

        MemoryExtractor extractor = readField(memory, "extractor", MemoryExtractor.class);
        RawDataLayer rawDataLayer = readField(extractor, "rawDataStep", RawDataLayer.class);
        CaptionGenerator defaultCaptionGenerator =
                readField(rawDataLayer, "defaultCaptionGenerator", CaptionGenerator.class);
        @SuppressWarnings("unchecked")
        java.util.Map<Class<?>, Object> processors =
                readField(rawDataLayer, "processors", java.util.Map.class);
        ConversationContentProcessor conversationProcessor =
                (ConversationContentProcessor) processors.get(ConversationContent.class);
        CaptionGenerator processorCaptionGenerator =
                readField(conversationProcessor, "captionGenerator", CaptionGenerator.class);

        assertThat(processorCaptionGenerator).isSameAs(defaultCaptionGenerator);
    }

    @Test
    void buildUsesBoundaryDetectorConfigFromBuildOptions() {
        CommitDetectorConfig config = new CommitDetectorConfig(5, 1024, 3);
        DefaultMemory memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .options(
                                        MemoryBuildOptions.builder()
                                                .boundaryDetector(config)
                                                .build())
                                .build();

        MemoryExtractor extractor = readField(memory, "extractor", MemoryExtractor.class);
        LlmContextCommitDetector boundaryDetector =
                readField(extractor, "contextCommitDetector", LlmContextCommitDetector.class);
        CommitDetectorConfig actualConfig =
                readField(boundaryDetector, "config", CommitDetectorConfig.class);

        assertThat(actualConfig).isEqualTo(config);
    }

    @Test
    void defaultBuildOptionsExposeBoundaryDetectorDefaults() {
        assertThat(MemoryBuildOptions.defaults().boundaryDetector())
                .isEqualTo(CommitDetectorConfig.defaults());
    }

    @Test
    void buildUsesStoreAndBufferSubcomponents() {
        DefaultMemory memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .build();

        MemoryExtractor extractor = readField(memory, "extractor", MemoryExtractor.class);
        RawDataLayer rawDataLayer = readField(extractor, "rawDataStep", RawDataLayer.class);
        MemoryStore rawDataStore = readField(rawDataLayer, "memoryStore", MemoryStore.class);
        ConversationBuffer pendingConversationBuffer =
                readField(extractor, "pendingConversationBuffer", ConversationBuffer.class);
        MemoryBuffer memoryBuffer = readField(memory, "memoryBuffer", MemoryBuffer.class);

        assertThat(rawDataStore).isSameAs(MEMORY_STORE);
        assertThat(pendingConversationBuffer).isSameAs(CONVERSATION_BUFFER_STORE);
        assertThat(memoryBuffer).isSameAs(MEMORY_BUFFER);
    }

    @Test
    void closeClosesCloseableComponentsOnlyOnce() {
        AtomicInteger storeCloseCount = new AtomicInteger();
        AtomicInteger bufferCloseCount = new AtomicInteger();
        AtomicInteger textSearchCloseCount = new AtomicInteger();
        AtomicInteger chatClientCloseCount = new AtomicInteger();
        AtomicInteger vectorCloseCount = new AtomicInteger();

        MemoryStore store =
                new FixedMemoryStore(
                        RAW_DATA_OPERATIONS, ITEM_OPERATIONS, INSIGHT_OPERATIONS, storeCloseCount);
        MemoryBuffer buffer =
                new FixedMemoryBuffer(
                        INSIGHT_BUFFER_STORE,
                        CONVERSATION_BUFFER_STORE,
                        RECENT_CONVERSATION_BUFFER,
                        bufferCloseCount);
        CloseTrackingTextSearch textSearch = new CloseTrackingTextSearch(textSearchCloseCount);
        TrackingChatClient chatClient = new TrackingChatClient(chatClientCloseCount);
        CloseTrackingVector vector = new CloseTrackingVector(vectorCloseCount);

        Memory memory =
                Memory.builder()
                        .chatClient(chatClient)
                        .store(store)
                        .buffer(buffer)
                        .textSearch(textSearch)
                        .vector(vector)
                        .build();

        memory.close();
        memory.close();

        assertThat(storeCloseCount).hasValue(1);
        assertThat(bufferCloseCount).hasValue(1);
        assertThat(textSearchCloseCount).hasValue(1);
        assertThat(chatClientCloseCount).hasValue(1);
        assertThat(vectorCloseCount).hasValue(1);
    }

    private static final class FixedMemoryStore implements MemoryStore {

        private final RawDataOperations rawDataOperations;
        private final ItemOperations itemOperations;
        private final InsightOperations insightOperations;
        private final InsightBuffer legacyInsightBuffer = proxy(InsightBuffer.class);
        private final ConversationBuffer legacyConversationBuffer = proxy(ConversationBuffer.class);
        private final AtomicInteger closeCount;

        private FixedMemoryStore(
                RawDataOperations rawDataOperations,
                ItemOperations itemOperations,
                InsightOperations insightOperations,
                AtomicInteger closeCount) {
            this.rawDataOperations = rawDataOperations;
            this.itemOperations = itemOperations;
            this.insightOperations = insightOperations;
            this.closeCount = closeCount;
        }

        @Override
        public RawDataOperations rawDataOperations() {
            return rawDataOperations;
        }

        @Override
        public ItemOperations itemOperations() {
            return itemOperations;
        }

        @Override
        public InsightOperations insightOperations() {
            return insightOperations;
        }

        @Override
        public InsightBuffer insightBufferStore() {
            return legacyInsightBuffer;
        }

        @Override
        public ConversationBuffer conversationBufferStore() {
            return legacyConversationBuffer;
        }

        @Override
        public void close() {
            if (closeCount != null) {
                closeCount.incrementAndGet();
            }
        }
    }

    private static final class FixedMemoryBuffer implements MemoryBuffer {

        private final InsightBuffer insightBuffer;
        private final ConversationBuffer pendingConversationBuffer;
        private final RecentConversationBuffer recentConversationBuffer;
        private final AtomicInteger closeCount;

        private FixedMemoryBuffer(
                InsightBuffer insightBuffer,
                ConversationBuffer pendingConversationBuffer,
                RecentConversationBuffer recentConversationBuffer,
                AtomicInteger closeCount) {
            this.insightBuffer = insightBuffer;
            this.pendingConversationBuffer = pendingConversationBuffer;
            this.recentConversationBuffer = recentConversationBuffer;
            this.closeCount = closeCount;
        }

        @Override
        public InsightBuffer insightBuffer() {
            return insightBuffer;
        }

        @Override
        public ConversationBuffer pendingConversationBuffer() {
            return pendingConversationBuffer;
        }

        @Override
        public RecentConversationBuffer recentConversationBuffer() {
            return recentConversationBuffer;
        }

        @Override
        public void close() {
            if (closeCount != null) {
                closeCount.incrementAndGet();
            }
        }
    }

    private static final class TrackingChatClient implements StructuredChatClient, AutoCloseable {

        private final AtomicInteger closeCount;

        private TrackingChatClient() {
            this(new AtomicInteger());
        }

        private TrackingChatClient(AtomicInteger closeCount) {
            this.closeCount = closeCount;
        }

        @Override
        public Mono<String> call(
                java.util.List<com.openmemind.ai.memory.core.llm.ChatMessage> messages) {
            return Mono.empty();
        }

        @Override
        public <T> Mono<T> call(
                java.util.List<com.openmemind.ai.memory.core.llm.ChatMessage> messages,
                Class<T> responseType) {
            return Mono.empty();
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }

    private static final class CloseTrackingTextSearch implements MemoryTextSearch, AutoCloseable {

        private final AtomicInteger closeCount;

        private CloseTrackingTextSearch(AtomicInteger closeCount) {
            this.closeCount = closeCount;
        }

        @Override
        public Mono<java.util.List<com.openmemind.ai.memory.core.textsearch.TextSearchResult>>
                search(MemoryId memoryId, String query, int topK, SearchTarget target) {
            return Mono.just(java.util.List.of());
        }

        @Override
        public void invalidate(MemoryId memoryId) {}

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }

    private static final class CloseTrackingVector implements MemoryVector, AutoCloseable {

        private final AtomicInteger closeCount;

        private CloseTrackingVector(AtomicInteger closeCount) {
            this.closeCount = closeCount;
        }

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            return Mono.empty();
        }

        @Override
        public Mono<java.util.List<String>> storeBatch(
                MemoryId memoryId,
                java.util.List<String> texts,
                java.util.List<Map<String, Object>> metadataList) {
            return Mono.just(java.util.List.of());
        }

        @Override
        public Mono<Void> delete(MemoryId memoryId, String vectorId) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteBatch(MemoryId memoryId, java.util.List<String> vectorIds) {
            return Mono.empty();
        }

        @Override
        public Flux<com.openmemind.ai.memory.core.vector.VectorSearchResult> search(
                MemoryId memoryId, String query, int topK) {
            return Flux.empty();
        }

        @Override
        public Flux<com.openmemind.ai.memory.core.vector.VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.empty();
        }

        @Override
        public Mono<java.util.List<Float>> embed(String text) {
            return Mono.just(java.util.List.of());
        }

        @Override
        public Mono<java.util.List<java.util.List<Float>>> embedAll(java.util.List<String> texts) {
            return Mono.just(java.util.List.of());
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (instance, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return switch (method.getName()) {
                                    case "toString" -> type.getSimpleName() + "Proxy";
                                    case "hashCode" -> System.identityHashCode(instance);
                                    case "equals" -> instance == args[0];
                                    default -> method.invoke(instance, args);
                                };
                            }

                            Class<?> returnType = method.getReturnType();
                            if (returnType == void.class) {
                                return null;
                            }
                            if (returnType == boolean.class) {
                                return false;
                            }
                            if (returnType == int.class) {
                                return 0;
                            }
                            if (returnType == long.class) {
                                return 0L;
                            }
                            if (returnType == float.class) {
                                return 0F;
                            }
                            if (returnType == double.class) {
                                return 0D;
                            }
                            if (returnType == java.util.Optional.class) {
                                return java.util.Optional.empty();
                            }
                            if (returnType == java.util.List.class) {
                                return java.util.List.of();
                            }
                            if (returnType == java.util.Set.class) {
                                return java.util.Set.of();
                            }
                            if (returnType == java.util.Map.class) {
                                return java.util.Map.of();
                            }
                            if (returnType == Mono.class) {
                                return Mono.empty();
                            }
                            if (returnType == Flux.class) {
                                return Flux.empty();
                            }
                            return null;
                        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String fieldName, Class<T> fieldType) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) fieldType.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "Failed to read field '" + fieldName + "' from " + target.getClass(), e);
        }
    }
}
