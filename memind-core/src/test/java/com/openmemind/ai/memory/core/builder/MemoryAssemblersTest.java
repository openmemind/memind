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
package com.openmemind.ai.memory.core.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig;
import com.openmemind.ai.memory.core.extraction.context.LlmContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.rawdata.RawDataLayer;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ConversationContentProcessor;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.retrieval.DefaultMemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategies;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.buffer.ConversationBuffer;
import com.openmemind.ai.memory.core.store.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MemoryAssemblersTest {

    private static final StructuredChatClient CHAT_CLIENT = proxy(StructuredChatClient.class);
    private static final RawDataOperations RAW_DATA_OPERATIONS = proxy(RawDataOperations.class);
    private static final ItemOperations ITEM_OPERATIONS = proxy(ItemOperations.class);
    private static final InsightOperations INSIGHT_OPERATIONS = proxy(InsightOperations.class);
    private static final MemoryTextSearch TEXT_SEARCH = proxy(MemoryTextSearch.class);
    private static final InsightBuffer INSIGHT_BUFFER_STORE = proxy(InsightBuffer.class);
    private static final ConversationBuffer CONVERSATION_BUFFER_STORE =
            proxy(ConversationBuffer.class);
    private static final MemoryVector MEMORY_VECTOR = proxy(MemoryVector.class);
    private static final MemoryStore MEMORY_STORE =
            new MemoryStore() {
                @Override
                public RawDataOperations rawDataOperations() {
                    return RAW_DATA_OPERATIONS;
                }

                @Override
                public ItemOperations itemOperations() {
                    return ITEM_OPERATIONS;
                }

                @Override
                public InsightOperations insightOperations() {
                    return INSIGHT_OPERATIONS;
                }

                @Override
                public InsightBuffer insightBufferStore() {
                    return INSIGHT_BUFFER_STORE;
                }

                @Override
                public ConversationBuffer conversationBufferStore() {
                    return CONVERSATION_BUFFER_STORE;
                }
            };

    @Test
    void extractionAssemblerUsesUnifiedStoreBuffersAndBoundaryOptions() {
        CommitDetectorConfig boundaryDetector = new CommitDetectorConfig(6, 2048, 4);
        MemoryAssemblyContext context =
                new MemoryAssemblyContext(
                        CHAT_CLIENT,
                        MEMORY_STORE,
                        TEXT_SEARCH,
                        MEMORY_VECTOR,
                        MemoryBuildOptions.builder().boundaryDetector(boundaryDetector).build());

        MemoryExtractor extractor =
                (MemoryExtractor) new MemoryExtractionAssembler().assemble(context).pipeline();

        RawDataLayer rawDataLayer = readField(extractor, "rawDataStep", RawDataLayer.class);
        CaptionGenerator defaultCaptionGenerator =
                readField(rawDataLayer, "defaultCaptionGenerator", CaptionGenerator.class);
        @SuppressWarnings("unchecked")
        Map<Class<?>, Object> processors = readField(rawDataLayer, "processors", Map.class);
        ConversationContentProcessor conversationProcessor =
                (ConversationContentProcessor) processors.get(ConversationContent.class);
        CaptionGenerator processorCaptionGenerator =
                readField(conversationProcessor, "captionGenerator", CaptionGenerator.class);
        LlmContextCommitDetector actualBoundaryDetector =
                readField(extractor, "contextCommitDetector", LlmContextCommitDetector.class);
        MemoryStore rawDataStore = readField(rawDataLayer, "memoryStore", MemoryStore.class);
        ConversationBuffer conversationBuffer =
                readField(extractor, "conversationBuffer", ConversationBuffer.class);

        assertThat(processorCaptionGenerator).isSameAs(defaultCaptionGenerator);
        assertThat(readField(actualBoundaryDetector, "config", CommitDetectorConfig.class))
                .isEqualTo(boundaryDetector);
        assertThat(rawDataStore).isSameAs(MEMORY_STORE);
        assertThat(conversationBuffer).isSameAs(CONVERSATION_BUFFER_STORE);
    }

    @Test
    void retrievalAssemblerRegistersBuiltInStrategiesAndUsesDataStore() {
        MemoryAssemblyContext context =
                new MemoryAssemblyContext(
                        CHAT_CLIENT,
                        MEMORY_STORE,
                        TEXT_SEARCH,
                        MEMORY_VECTOR,
                        MemoryBuildOptions.defaults());

        DefaultMemoryRetriever retriever = new MemoryRetrievalAssembler().assemble(context);

        @SuppressWarnings("unchecked")
        Map<String, Object> strategies = readField(retriever, "strategies", Map.class);
        MemoryStore store = readField(retriever, "memoryStore", MemoryStore.class);
        assertThat(strategies.keySet())
                .containsExactlyInAnyOrder(
                        RetrievalStrategies.SIMPLE, RetrievalStrategies.DEEP_RETRIEVAL);
        assertThat(store).isSameAs(MEMORY_STORE);
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
