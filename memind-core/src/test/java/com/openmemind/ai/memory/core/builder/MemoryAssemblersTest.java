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

import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig;
import com.openmemind.ai.memory.core.extraction.context.LlmContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.RawDataLayer;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.llm.rerank.NoopReranker;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategies;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.store.resource.ResourceOperations;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MemoryAssemblersTest {

    private static final StructuredChatClient CHAT_CLIENT = proxy(StructuredChatClient.class);
    private static final RawDataOperations RAW_DATA_OPERATIONS = proxy(RawDataOperations.class);
    private static final ItemOperations ITEM_OPERATIONS = proxy(ItemOperations.class);
    private static final InsightOperations INSIGHT_OPERATIONS = proxy(InsightOperations.class);
    private static final ResourceOperations RESOURCE_OPERATIONS = proxy(ResourceOperations.class);
    private static final MemoryTextSearch TEXT_SEARCH = proxy(MemoryTextSearch.class);
    private static final InsightBuffer INSIGHT_BUFFER = proxy(InsightBuffer.class);
    private static final PendingConversationBuffer PENDING_CONVERSATION_BUFFER =
            proxy(PendingConversationBuffer.class);
    private static final RecentConversationBuffer RECENT_CONVERSATION_BUFFER =
            proxy(RecentConversationBuffer.class);
    private static final MemoryBuffer MEMORY_BUFFER =
            MemoryBuffer.of(
                    INSIGHT_BUFFER, PENDING_CONVERSATION_BUFFER, RECENT_CONVERSATION_BUFFER);
    private static final MemoryVector MEMORY_VECTOR = proxy(MemoryVector.class);
    private static final ResourceStore RESOURCE_STORE = proxy(ResourceStore.class);
    private static final ContentParserRegistry CONTENT_PARSER_REGISTRY =
            proxy(ContentParserRegistry.class);
    private static final ResourceFetcher RESOURCE_FETCHER = proxy(ResourceFetcher.class);
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
                public ResourceOperations resourceOperations() {
                    return RESOURCE_OPERATIONS;
                }

                @Override
                public ResourceStore resourceStore() {
                    return RESOURCE_STORE;
                }
            };
    private static final CommitDetectorConfig CUSTOM_COMMIT_DETECTION =
            new CommitDetectorConfig(6, 2048, 4);
    private static final InsightBuildConfig CUSTOM_INSIGHT_BUILD =
            new InsightBuildConfig(7, 5, 3, 2);
    private static final int CUSTOM_VECTOR_BATCH_SIZE = 17;

    @Test
    void extractionAssemblerUsesNestedBuildOptionsForBoundaryAndInsightSchedulers() {
        var context =
                context(
                        MemoryBuildOptions.builder()
                                .extraction(
                                        new ExtractionOptions(
                                                ExtractionCommonOptions.defaults(),
                                                new RawDataExtractionOptions(
                                                        com.openmemind.ai.memory.core.extraction
                                                                .rawdata.chunk
                                                                .ConversationChunkingConfig.DEFAULT,
                                                        DocumentExtractionOptions.defaults(),
                                                        ImageExtractionOptions.defaults(),
                                                        AudioExtractionOptions.defaults(),
                                                        ToolCallChunkingOptions.defaults(),
                                                        CUSTOM_COMMIT_DETECTION,
                                                        CUSTOM_VECTOR_BATCH_SIZE),
                                                ItemExtractionOptions.defaults(),
                                                new InsightExtractionOptions(
                                                        true, CUSTOM_INSIGHT_BUILD)))
                                .build(),
                        CONTENT_PARSER_REGISTRY,
                        RESOURCE_FETCHER);

        var assembly = new MemoryExtractionAssembler().assemble(context);
        var extractor = (MemoryExtractor) assembly.pipeline();
        var rawDataLayer = readField(extractor, "rawDataStep", RawDataLayer.class);
        var boundaryDetector =
                readField(extractor, "contextCommitDetector", LlmContextCommitDetector.class);
        var scheduler =
                readField(assembly.insightLayer(), "scheduler", InsightBuildScheduler.class);

        assertThat(readField(boundaryDetector, "config", CommitDetectorConfig.class))
                .isEqualTo(CUSTOM_COMMIT_DETECTION);
        assertThat(readField(scheduler, "config", InsightBuildConfig.class))
                .isEqualTo(CUSTOM_INSIGHT_BUILD);
        assertThat(rawDataLayer).isNotNull();
        assertThat(readField(extractor, "contentParserRegistry", ContentParserRegistry.class))
                .isSameAs(CONTENT_PARSER_REGISTRY);
        assertThat(readField(extractor, "resourceStore", ResourceStore.class))
                .isSameAs(RESOURCE_STORE);
        assertThat(readField(extractor, "resourceFetcher", ResourceFetcher.class))
                .isSameAs(RESOURCE_FETCHER);
        assertThat(readField(extractor, "rawDataExtractionOptions", RawDataExtractionOptions.class))
                .isEqualTo(context.options().extraction().rawdata());
        assertThat(readField(rawDataLayer, "vectorBatchSize", Integer.class))
                .isEqualTo(CUSTOM_VECTOR_BATCH_SIZE);
        assertThat(readField(extractor, "itemExtractionOptions", ItemExtractionOptions.class))
                .isEqualTo(context.options().extraction().item());

        var processorRegistry =
                readField(rawDataLayer, "processorRegistry", RawContentProcessorRegistry.class);
        assertDefaultCoreProcessors(processorRegistry);
    }

    @Test
    void retrievalAssemblerRegistersBothBuiltInStrategies() {
        var retriever =
                new MemoryRetrievalAssembler()
                        .assemble(context(MemoryBuildOptions.defaults(), null, null));

        @SuppressWarnings("unchecked")
        var strategies = readField(retriever, "strategies", Map.class);

        assertThat(strategies.keySet())
                .containsExactlyInAnyOrder(
                        RetrievalStrategies.SIMPLE, RetrievalStrategies.DEEP_RETRIEVAL);
    }

    @Test
    void extractionAssemblerFallsBackToDefaultResourceFetcherWhenMissing() {
        var assembly =
                new MemoryExtractionAssembler()
                        .assemble(context(MemoryBuildOptions.defaults(), null, null));
        var extractor = (MemoryExtractor) assembly.pipeline();

        assertThat(readField(extractor, "resourceFetcher", ResourceFetcher.class)).isNotNull();
    }

    @Test
    void extractionAssemblerSharesProcessorRegistryBetweenRawDataLayerAndExtractor() {
        var assembly =
                new MemoryExtractionAssembler()
                        .assemble(context(MemoryBuildOptions.defaults(), null, null));
        var extractor = (MemoryExtractor) assembly.pipeline();
        var rawDataLayer = readField(extractor, "rawDataStep", RawDataLayer.class);

        assertThat(
                        readField(
                                extractor,
                                "rawContentProcessorRegistry",
                                RawContentProcessorRegistry.class))
                .isSameAs(
                        readField(
                                rawDataLayer,
                                "processorRegistry",
                                RawContentProcessorRegistry.class));
    }

    @Test
    void extractionAssemblerBuildsOnlyConversationProcessorWithoutExplicitPlugins() {
        var assembly =
                new MemoryExtractionAssembler()
                        .assemble(context(MemoryBuildOptions.defaults(), null, null, List.of()));
        var extractor = (MemoryExtractor) assembly.pipeline();
        var rawDataLayer = readField(extractor, "rawDataStep", RawDataLayer.class);

        var processorRegistry =
                readField(rawDataLayer, "processorRegistry", RawContentProcessorRegistry.class);

        assertDefaultCoreProcessors(processorRegistry);
    }

    static MemoryAssemblyContext context(
            MemoryBuildOptions options,
            ContentParserRegistry contentParserRegistry,
            ResourceFetcher resourceFetcher) {
        return context(options, contentParserRegistry, resourceFetcher, List.of());
    }

    static MemoryAssemblyContext context(
            MemoryBuildOptions options,
            ContentParserRegistry contentParserRegistry,
            ResourceFetcher resourceFetcher,
            List<RawDataPlugin> rawDataPlugins) {
        return new MemoryAssemblyContext(
                new ChatClientRegistry(CHAT_CLIENT, Map.<ChatClientSlot, StructuredChatClient>of()),
                MEMORY_STORE,
                MEMORY_BUFFER,
                TEXT_SEARCH,
                MEMORY_VECTOR,
                new NoopReranker(),
                PromptRegistry.EMPTY,
                options,
                contentParserRegistry,
                resourceFetcher,
                rawDataPlugins);
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

                            var returnType = method.getReturnType();
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
    static <T> T readField(Object target, String fieldName, Class<T> fieldType) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) fieldType.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "Failed to read field '" + fieldName + "' from " + target.getClass(), e);
        }
    }

    private static void assertDefaultCoreProcessors(RawContentProcessorRegistry processorRegistry) {
        assertThat(processorRegistry.all())
                .extracting(RawContentProcessor::contentType)
                .containsExactly("CONVERSATION");
    }
}
