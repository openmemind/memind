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
import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.builder.DeepRetrievalGraphOptions;
import com.openmemind.ai.memory.core.builder.DeepRetrievalOptions;
import com.openmemind.ai.memory.core.builder.ExtractionCommonOptions;
import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.InsightExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuilder;
import com.openmemind.ai.memory.core.builder.MemoryThreadDerivationOptions;
import com.openmemind.ai.memory.core.builder.MemoryThreadOptions;
import com.openmemind.ai.memory.core.builder.PromptBudgetOptions;
import com.openmemind.ai.memory.core.builder.QueryExpansionOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.builder.RetrievalAdvancedOptions;
import com.openmemind.ai.memory.core.builder.RetrievalCommonOptions;
import com.openmemind.ai.memory.core.builder.RetrievalOptions;
import com.openmemind.ai.memory.core.builder.SimpleRetrievalGraphOptions;
import com.openmemind.ai.memory.core.builder.SimpleRetrievalOptions;
import com.openmemind.ai.memory.core.builder.SufficiencyOptions;
import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.context.LlmContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.insight.InsightLayer;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeReorganizer;
import com.openmemind.ai.memory.core.extraction.item.MemoryItemLayer;
import com.openmemind.ai.memory.core.extraction.item.extractor.DefaultMemoryItemExtractor;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.item.graph.NoOpItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionStrategy;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.ExactCanonicalEntityResolutionStrategy;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.DefaultItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.DefaultItemGraphPlanner;
import com.openmemind.ai.memory.core.extraction.item.strategy.LlmItemExtractionStrategy;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.retrieval.DefaultMemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.deep.LlmTypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphMode;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategies;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.support.RecordingMemoryObserver;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.NoopMemoryObserver;
import com.openmemind.ai.memory.core.tracing.decorator.TracingItemGraphMaterializer;
import com.openmemind.ai.memory.core.tracing.decorator.TracingMemoryExtractor;
import com.openmemind.ai.memory.core.tracing.decorator.TracingMemoryRetriever;
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
    private static final InsightBuffer INSIGHT_BUFFER = proxy(InsightBuffer.class);
    private static final PendingConversationBuffer PENDING_CONVERSATION_BUFFER =
            proxy(PendingConversationBuffer.class);
    private static final RecentConversationBuffer RECENT_CONVERSATION_BUFFER =
            proxy(RecentConversationBuffer.class);
    private static final MemoryVector MEMORY_VECTOR = proxy(MemoryVector.class);
    private static final MemoryStore MEMORY_STORE =
            new FixedMemoryStore(RAW_DATA_OPERATIONS, ITEM_OPERATIONS, INSIGHT_OPERATIONS, null);
    private static final MemoryBuffer MEMORY_BUFFER =
            new FixedMemoryBuffer(
                    INSIGHT_BUFFER, PENDING_CONVERSATION_BUFFER, RECENT_CONVERSATION_BUFFER, null);

    @Test
    void memoryExposesStaticBuilderEntryPoint() {
        assertThat(Memory.builder()).isNotNull().isInstanceOf(MemoryBuilder.class);
    }

    @Test
    void buildFailsWhenAnyRequiredComponentIsMissing() {
        assertThatThrownBy(() -> Memory.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chat client");

        assertThatThrownBy(
                        () ->
                                Memory.builder()
                                        .chatClient(CHAT_CLIENT)
                                        .buffer(MEMORY_BUFFER)
                                        .vector(MEMORY_VECTOR)
                                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("store");

        assertThatThrownBy(
                        () ->
                                Memory.builder()
                                        .chatClient(CHAT_CLIENT)
                                        .store(MEMORY_STORE)
                                        .vector(MEMORY_VECTOR)
                                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("buffer");

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
    void builderPropagatesConfiguredBuildOptionsIntoDefaultMemory() {
        var configured =
                MemoryBuildOptions.builder()
                        .extraction(
                                new ExtractionOptions(
                                        ExtractionCommonOptions.defaults(),
                                        RawDataExtractionOptions.defaults(),
                                        new ItemExtractionOptions(
                                                false,
                                                PromptBudgetOptions.defaults(),
                                                ItemGraphOptions.defaults().withEnabled(false)),
                                        InsightExtractionOptions.defaults()))
                        .retrieval(
                                new RetrievalOptions(
                                        new RetrievalCommonOptions(false),
                                        SimpleRetrievalOptions.defaults(),
                                        DeepRetrievalOptions.defaults(),
                                        RetrievalAdvancedOptions.defaults()))
                        .build();

        var memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .options(configured)
                                .build();

        assertThat(readField(memory, "buildOptions", MemoryBuildOptions.class))
                .isEqualTo(configured);
    }

    @Test
    void builderStoresSanitizedBuildOptionsOnDefaultMemory() {
        var configured =
                MemoryBuildOptions.builder()
                        .extraction(
                                new ExtractionOptions(
                                        ExtractionCommonOptions.defaults(),
                                        RawDataExtractionOptions.defaults(),
                                        new ItemExtractionOptions(
                                                false,
                                                PromptBudgetOptions.defaults(),
                                                ItemGraphOptions.defaults().withEnabled(true)),
                                        InsightExtractionOptions.defaults()))
                        .memoryThread(
                                MemoryThreadOptions.defaults()
                                        .withEnabled(true)
                                        .withDerivation(
                                                MemoryThreadDerivationOptions.defaults()
                                                        .withEnabled(true)))
                        .build();

        var memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .options(configured)
                                .build();

        assertThat(
                        readField(memory, "buildOptions", MemoryBuildOptions.class)
                                .memoryThread()
                                .derivation()
                                .enabled())
                .isFalse();
    }

    @Test
    void builderWrapsExtractorWithTracingDecoratorWhenObserverConfigured() {
        var observer = new RecordingMemoryObserver();

        var memory = buildMinimalMemory(observer);

        var extractor = readField(memory, "extractor", MemoryExtractor.class);
        assertThat(extractor).isInstanceOf(TracingMemoryExtractor.class);
    }

    @Test
    void builderWrapsRetrieverWithTracingDecoratorWhenObserverConfigured() {
        var observer = new RecordingMemoryObserver();

        var memory = buildMinimalMemory(observer);

        var retriever = readField(memory, "retriever", MemoryRetriever.class);
        assertThat(retriever).isInstanceOf(TracingMemoryRetriever.class);
    }

    @Test
    void builderKeepsTopLevelDelegatesUnwrappedForNoopObserver() {
        var memory = buildMinimalMemory(new NoopMemoryObserver());

        var extractor = readField(memory, "extractor", MemoryExtractor.class);
        var retriever = readField(memory, "retriever", MemoryRetriever.class);

        assertThat(extractor).isNotInstanceOf(TracingMemoryExtractor.class);
        assertThat(retriever).isNotInstanceOf(TracingMemoryRetriever.class);
    }

    @Test
    void defaultBuilderShouldKeepStage1aGraphHardeningEnabledOnlyThroughBuiltInDefaults() {
        var graph = MemoryBuildOptions.defaults().extraction().item().graph();

        assertThat(graph.enabled()).isTrue();
        assertThat(graph.maxEntitiesPerItem()).isEqualTo(8);
    }

    @Test
    void defaultRetrievalConfigMapsSimpleGraphAssistSemanticEvidenceDecayFactor() {
        var configured =
                MemoryBuildOptions.builder()
                        .retrieval(
                                new RetrievalOptions(
                                        RetrievalCommonOptions.defaults(),
                                        new SimpleRetrievalOptions(
                                                java.time.Duration.ofSeconds(10),
                                                5,
                                                15,
                                                5,
                                                true,
                                                new SimpleRetrievalGraphOptions(
                                                        true,
                                                        RetrievalGraphMode.ASSIST,
                                                        6,
                                                        12,
                                                        2,
                                                        2,
                                                        2,
                                                        3,
                                                        8,
                                                        0.35d,
                                                        0.55d,
                                                        0.70f,
                                                        3,
                                                        0.65d,
                                                        java.time.Duration.ofMillis(200))),
                                        DeepRetrievalOptions.defaults(),
                                        RetrievalAdvancedOptions.defaults()))
                        .build();

        var memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .options(configured)
                                .build();

        RetrievalConfig runtimeConfig =
                invokeMethod(
                        memory,
                        "defaultRetrievalConfig",
                        RetrievalConfig.class,
                        new Class<?>[] {RetrievalConfig.Strategy.class},
                        RetrievalConfig.Strategy.SIMPLE);

        assertThat(runtimeConfig.strategyConfig()).isInstanceOf(SimpleStrategyConfig.class);
        var simpleConfig = (SimpleStrategyConfig) runtimeConfig.strategyConfig();
        assertThat(simpleConfig.graphAssist().mode()).isEqualTo(RetrievalGraphMode.ASSIST);
        assertThat(simpleConfig.graphAssist().semanticEvidenceDecayFactor()).isEqualTo(0.65d);
    }

    @Test
    void defaultRetrievalConfigMapsDeepGraphAssistOptionsIntoRuntimeStrategyConfig() {
        var configured =
                MemoryBuildOptions.builder()
                        .retrieval(
                                new RetrievalOptions(
                                        RetrievalCommonOptions.defaults(),
                                        SimpleRetrievalOptions.defaults(),
                                        new DeepRetrievalOptions(
                                                java.time.Duration.ofSeconds(90),
                                                5,
                                                40,
                                                false,
                                                0,
                                                QueryExpansionOptions.defaults(),
                                                SufficiencyOptions.defaults(),
                                                new DeepRetrievalGraphOptions(
                                                        true,
                                                        RetrievalGraphMode.ASSIST,
                                                        8,
                                                        16,
                                                        2,
                                                        2,
                                                        2,
                                                        4,
                                                        8,
                                                        0.30d,
                                                        0.55d,
                                                        0.70f,
                                                        5,
                                                        0.45d,
                                                        java.time.Duration.ofMillis(300))),
                                        RetrievalAdvancedOptions.defaults()))
                        .build();

        var memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .options(configured)
                                .build();

        RetrievalConfig runtimeConfig =
                invokeMethod(
                        memory,
                        "defaultRetrievalConfig",
                        RetrievalConfig.class,
                        new Class<?>[] {RetrievalConfig.Strategy.class},
                        RetrievalConfig.Strategy.DEEP);

        assertThat(runtimeConfig.strategyConfig()).isInstanceOf(DeepStrategyConfig.class);
        var deepConfig = (DeepStrategyConfig) runtimeConfig.strategyConfig();
        assertThat(deepConfig.graphAssist().enabled()).isTrue();
        assertThat(deepConfig.graphAssist().mode()).isEqualTo(RetrievalGraphMode.ASSIST);
        assertThat(deepConfig.graphAssist().maxExpandedItems()).isEqualTo(16);
        assertThat(deepConfig.graphAssist().protectDirectTopK()).isEqualTo(5);
        assertThat(deepConfig.graphAssist().semanticEvidenceDecayFactor()).isEqualTo(0.45d);
    }

    @Test
    void buildRoutesSlotSpecificClients() {
        var itemExtractionClient = new TrackingChatClient();
        var queryExpanderClient = new TrackingChatClient();

        var memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .chatClient(ChatClientSlot.ITEM_EXTRACTION, itemExtractionClient)
                                .chatClient(ChatClientSlot.QUERY_EXPANDER, queryExpanderClient)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .build();

        var extractor = underlyingExtractor(memory);
        var memoryItemLayer = readField(extractor, "memoryItemStep", MemoryItemLayer.class);
        var itemExtractor =
                readField(memoryItemLayer, "extractor", DefaultMemoryItemExtractor.class);
        var itemStrategy =
                readField(itemExtractor, "defaultStrategy", LlmItemExtractionStrategy.class);
        var retriever = underlyingRetriever(memory);
        @SuppressWarnings("unchecked")
        var strategies = readField(retriever, "strategies", Map.class);
        var deepStrategy =
                (DeepRetrievalStrategy) strategies.get(RetrievalStrategies.DEEP_RETRIEVAL);
        var typedQueryExpander =
                readField(deepStrategy, "typedQueryExpander", LlmTypedQueryExpander.class);

        assertThat(readField(itemStrategy, "structuredChatClient", StructuredChatClient.class))
                .isSameAs(itemExtractionClient);
        assertThat(
                        readField(
                                typedQueryExpander,
                                "structuredChatClient",
                                StructuredChatClient.class))
                .isSameAs(queryExpanderClient);
    }

    @Test
    void buildRoutesRuntimeRegistryAndFetcherIntoExtractor() {
        var registry = proxy(ContentParserRegistry.class);
        var fetcher = proxy(ResourceFetcher.class);

        var memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .contentParserRegistry(registry)
                                .resourceFetcher(fetcher)
                                .build();

        var extractor = underlyingExtractor(memory);

        assertThat(readField(extractor, "contentParserRegistry", ContentParserRegistry.class))
                .isSameAs(registry);
        assertThat(readField(extractor, "resourceFetcher", ResourceFetcher.class))
                .isSameAs(fetcher);
    }

    @Test
    void builderInjectsCustomBubbleTrackerStoreIntoInsightRuntime() {
        var customBubbleTracker = proxy(BubbleTrackerStore.class);

        var memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .bubbleTrackerStore(customBubbleTracker)
                                .build();

        var extractor = underlyingExtractor(memory);
        var insightLayer = readField(extractor, "insightStep", InsightLayer.class);
        var scheduler = readField(insightLayer, "scheduler", InsightBuildScheduler.class);
        var reorganizer = readField(scheduler, "treeReorganizer", InsightTreeReorganizer.class);

        assertThat(readField(reorganizer, "bubbleTracker", BubbleTrackerStore.class))
                .isSameAs(customBubbleTracker);
    }

    @Test
    void buildPropagatesPromptRegistryAcrossExtractionAndRetrievalAssemblies() {
        var promptRegistry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.TYPED_QUERY_EXPAND, "custom query expand")
                        .build();

        var memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .promptRegistry(promptRegistry)
                                .build();

        var extractor = underlyingExtractor(memory);
        var contextCommitDetector =
                readField(extractor, "contextCommitDetector", LlmContextCommitDetector.class);
        var extractionPromptRegistry =
                readField(contextCommitDetector, "promptRegistry", PromptRegistry.class);
        var retriever = underlyingRetriever(memory);
        @SuppressWarnings("unchecked")
        var strategies = readField(retriever, "strategies", Map.class);
        var deepStrategy =
                (DeepRetrievalStrategy) strategies.get(RetrievalStrategies.DEEP_RETRIEVAL);
        var typedQueryExpander =
                readField(deepStrategy, "typedQueryExpander", LlmTypedQueryExpander.class);
        var retrievalPromptRegistry =
                readField(typedQueryExpander, "promptRegistry", PromptRegistry.class);

        assertThat(extractionPromptRegistry).isSameAs(promptRegistry);
        assertThat(retrievalPromptRegistry).isSameAs(promptRegistry);
    }

    @Test
    void builderManagedRuntimeShouldWrapGraphMaterializerWithTracingObserver() {
        var observer = new RecordingMemoryObserver();

        var memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(new InMemoryMemoryStore())
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .memoryObserver(observer)
                                .options(graphEnabledBuildOptions())
                                .build();

        var extractor = underlyingExtractor(memory);
        var itemLayer = readField(extractor, "memoryItemStep", MemoryItemLayer.class);

        assertThat(readField(itemLayer, "graphMaterializer", ItemGraphMaterializer.class))
                .isInstanceOf(TracingItemGraphMaterializer.class);
    }

    @Test
    void builderManagedRuntimeShouldWireExactResolutionByDefault() {
        var memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(new InMemoryMemoryStore())
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .options(graphEnabledBuildOptions())
                                .build();

        var extractor = underlyingExtractor(memory);
        var itemLayer = readField(extractor, "memoryItemStep", MemoryItemLayer.class);
        var tracing = readField(itemLayer, "graphMaterializer", TracingItemGraphMaterializer.class);
        var delegate = readField(tracing, "delegate", DefaultItemGraphMaterializer.class);

        var planner = readField(delegate, "planner", DefaultItemGraphPlanner.class);

        assertThat(readField(planner, "resolutionStrategy", EntityResolutionStrategy.class))
                .isInstanceOf(ExactCanonicalEntityResolutionStrategy.class);
    }

    @Test
    void builderManagedRuntimeShouldKeepGraphMaterializerNoOpAndUntracedWhenGraphDisabled() {
        var observer = new RecordingMemoryObserver();

        var memory =
                (DefaultMemory)
                        Memory.builder()
                                .chatClient(CHAT_CLIENT)
                                .store(MEMORY_STORE)
                                .buffer(MEMORY_BUFFER)
                                .vector(MEMORY_VECTOR)
                                .memoryObserver(observer)
                                .options(MemoryBuildOptions.defaults())
                                .build();

        var extractor = underlyingExtractor(memory);
        var itemLayer = readField(extractor, "memoryItemStep", MemoryItemLayer.class);

        assertThat(readField(itemLayer, "graphMaterializer", ItemGraphMaterializer.class))
                .isInstanceOf(NoOpItemGraphMaterializer.class);
    }

    @Test
    void closeClosesCloseableComponentsOnlyOnce() {
        var storeCloseCount = new AtomicInteger();
        var bufferCloseCount = new AtomicInteger();
        var textSearchCloseCount = new AtomicInteger();
        var chatClientCloseCount = new AtomicInteger();
        var vectorCloseCount = new AtomicInteger();

        var store =
                new FixedMemoryStore(
                        RAW_DATA_OPERATIONS, ITEM_OPERATIONS, INSIGHT_OPERATIONS, storeCloseCount);
        var buffer =
                new FixedMemoryBuffer(
                        INSIGHT_BUFFER,
                        PENDING_CONVERSATION_BUFFER,
                        RECENT_CONVERSATION_BUFFER,
                        bufferCloseCount);
        var textSearch = new CloseTrackingTextSearch(textSearchCloseCount);
        var chatClient = new TrackingChatClient(chatClientCloseCount);
        var vector = new CloseTrackingVector(vectorCloseCount);

        var memory =
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

    @Test
    void externallyManagedBuilderDoesNotCloseInjectedCloseables() {
        var storeCloseCount = new AtomicInteger();
        var bufferCloseCount = new AtomicInteger();
        var textSearchCloseCount = new AtomicInteger();
        var chatClientCloseCount = new AtomicInteger();
        var vectorCloseCount = new AtomicInteger();

        var store =
                new FixedMemoryStore(
                        RAW_DATA_OPERATIONS, ITEM_OPERATIONS, INSIGHT_OPERATIONS, storeCloseCount);
        var buffer =
                new FixedMemoryBuffer(
                        INSIGHT_BUFFER,
                        PENDING_CONVERSATION_BUFFER,
                        RECENT_CONVERSATION_BUFFER,
                        bufferCloseCount);
        var textSearch = new CloseTrackingTextSearch(textSearchCloseCount);
        var chatClient = new TrackingChatClient(chatClientCloseCount);
        var vector = new CloseTrackingVector(vectorCloseCount);

        var memory =
                Memory.builder()
                        .chatClient(chatClient)
                        .store(store)
                        .buffer(buffer)
                        .textSearch(textSearch)
                        .vector(vector)
                        .externallyManaged(true)
                        .build();

        memory.close();

        assertThat(storeCloseCount).hasValue(0);
        assertThat(bufferCloseCount).hasValue(0);
        assertThat(textSearchCloseCount).hasValue(0);
        assertThat(chatClientCloseCount).hasValue(0);
        assertThat(vectorCloseCount).hasValue(0);
    }

    private static final class FixedMemoryStore implements MemoryStore {

        private final RawDataOperations rawDataOperations;
        private final ItemOperations itemOperations;
        private final InsightOperations insightOperations;
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
        public void close() {
            if (closeCount != null) {
                closeCount.incrementAndGet();
            }
        }
    }

    private static MemoryBuildOptions graphEnabledBuildOptions() {
        return MemoryBuildOptions.builder()
                .extraction(
                        new ExtractionOptions(
                                ExtractionCommonOptions.defaults(),
                                RawDataExtractionOptions.defaults(),
                                new ItemExtractionOptions(
                                        false,
                                        com.openmemind.ai.memory.core.builder.PromptBudgetOptions
                                                .defaults(),
                                        ItemGraphOptions.defaults().withEnabled(true)),
                                InsightExtractionOptions.defaults()))
                .build();
    }

    private static final class FixedMemoryBuffer implements MemoryBuffer {

        private final InsightBuffer insightBuffer;
        private final PendingConversationBuffer pendingConversationBuffer;
        private final RecentConversationBuffer recentConversationBuffer;
        private final AtomicInteger closeCount;

        private FixedMemoryBuffer(
                InsightBuffer insightBuffer,
                PendingConversationBuffer pendingConversationBuffer,
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
        public PendingConversationBuffer pendingConversationBuffer() {
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
                search(
                        com.openmemind.ai.memory.core.data.MemoryId memoryId,
                        String query,
                        int topK,
                        SearchTarget target) {
            return Mono.just(java.util.List.of());
        }

        @Override
        public void invalidate(com.openmemind.ai.memory.core.data.MemoryId memoryId) {}

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
        public Mono<String> store(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                String text,
                Map<String, Object> metadata) {
            return Mono.empty();
        }

        @Override
        public Mono<java.util.List<String>> storeBatch(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                java.util.List<String> texts,
                java.util.List<Map<String, Object>> metadataList) {
            return Mono.just(java.util.List.of());
        }

        @Override
        public Mono<Void> delete(
                com.openmemind.ai.memory.core.data.MemoryId memoryId, String vectorId) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteBatch(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                java.util.List<String> vectorIds) {
            return Mono.empty();
        }

        @Override
        public Flux<com.openmemind.ai.memory.core.vector.VectorSearchResult> search(
                com.openmemind.ai.memory.core.data.MemoryId memoryId, String query, int topK) {
            return Flux.empty();
        }

        @Override
        public Flux<com.openmemind.ai.memory.core.vector.VectorSearchResult> search(
                com.openmemind.ai.memory.core.data.MemoryId memoryId,
                String query,
                int topK,
                Map<String, Object> filter) {
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

    private DefaultMemory buildMinimalMemory(MemoryObserver observer) {
        return (DefaultMemory)
                Memory.builder()
                        .chatClient(CHAT_CLIENT)
                        .store(new InMemoryMemoryStore())
                        .buffer(MEMORY_BUFFER)
                        .vector(MEMORY_VECTOR)
                        .memoryObserver(observer)
                        .build();
    }

    private static DefaultMemoryExtractor underlyingExtractor(DefaultMemory memory) {
        MemoryExtractor extractor = readField(memory, "extractor", MemoryExtractor.class);
        if (extractor instanceof TracingMemoryExtractor tracing) {
            return readField(tracing, "delegate", DefaultMemoryExtractor.class);
        }
        return DefaultMemoryExtractor.class.cast(extractor);
    }

    private static DefaultMemoryRetriever underlyingRetriever(DefaultMemory memory) {
        MemoryRetriever retriever = readField(memory, "retriever", MemoryRetriever.class);
        if (retriever instanceof TracingMemoryRetriever tracing) {
            return readField(tracing, "delegate", DefaultMemoryRetriever.class);
        }
        return DefaultMemoryRetriever.class.cast(retriever);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeMethod(
            Object target,
            String methodName,
            Class<T> returnType,
            Class<?>[] parameterTypes,
            Object... args) {
        try {
            var method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return (T) returnType.cast(method.invoke(target, args));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "Failed to invoke method '" + methodName + "' on " + target.getClass(), e);
        }
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
