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
package com.openmemind.ai.memory.server.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.openmemind.ai.memory.core.DefaultMemory;
import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.builder.ExtractionCommonOptions;
import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.InsightExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.PromptBudgetOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.insight.InsightLayer;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeReorganizer;
import com.openmemind.ai.memory.core.extraction.item.MemoryItemLayer;
import com.openmemind.ai.memory.core.extraction.item.graph.ItemGraphMaterializer;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.llm.rerank.NoopReranker;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.resource.ContentCapability;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.ObservationContext;
import com.openmemind.ai.memory.core.tracing.decorator.TracingItemGraphMaterializer;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.AudioContent;
import com.openmemind.ai.memory.plugin.rawdata.audio.parser.TranscriptionAudioContentParser;
import com.openmemind.ai.memory.plugin.rawdata.document.DocumentSemantics;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import com.openmemind.ai.memory.plugin.rawdata.image.parser.VisionImageContentParser;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeFactory;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MemindServerRuntimeConfigurationTest {

    @Test
    void runtimeFactoryForwardsBubbleTrackerStoreBeanIntoBuiltMemory() {
        var customBubbleTracker = proxy(BubbleTrackerStore.class);
        var configuration = new MemindServerRuntimeConfiguration();

        MemoryRuntimeFactory factory =
                configuration.memoryRuntimeFactory(
                        provider(StructuredChatClient.class, proxy(StructuredChatClient.class)),
                        provider(MemoryStore.class, memoryStore()),
                        provider(MemoryBuffer.class, memoryBuffer()),
                        provider(MemoryVector.class, proxy(MemoryVector.class)),
                        emptyProvider(MemoryTextSearch.class),
                        provider(Reranker.class, new NoopReranker()),
                        emptyProvider(ContentParser.class),
                        emptyProvider(RawDataPlugin.class),
                        emptyProvider(ResourceFetcher.class),
                        provider(BubbleTrackerStore.class, customBubbleTracker),
                        emptyProvider(MemoryObserver.class));

        Memory memory = factory.create(MemoryBuildOptions.defaults()).memory();
        var extractor =
                readField((DefaultMemory) memory, "extractor", DefaultMemoryExtractor.class);
        var insightLayer = readField(extractor, "insightStep", InsightLayer.class);
        var scheduler = readField(insightLayer, "scheduler", InsightBuildScheduler.class);
        var reorganizer = readField(scheduler, "treeReorganizer", InsightTreeReorganizer.class);

        assertThat(readField(reorganizer, "bubbleTracker", BubbleTrackerStore.class))
                .isSameAs(customBubbleTracker);
    }

    @Test
    void serverRuntimeFactoryShouldForwardObserverBeanIntoBuilderManagedRuntime() {
        var observer = new TestMemoryObserver();
        var configuration = new MemindServerRuntimeConfiguration();

        MemoryRuntimeFactory factory =
                configuration.memoryRuntimeFactory(
                        provider(StructuredChatClient.class, proxy(StructuredChatClient.class)),
                        provider(MemoryStore.class, memoryStore()),
                        provider(MemoryBuffer.class, memoryBuffer()),
                        provider(MemoryVector.class, proxy(MemoryVector.class)),
                        emptyProvider(MemoryTextSearch.class),
                        provider(Reranker.class, new NoopReranker()),
                        emptyProvider(ContentParser.class),
                        emptyProvider(RawDataPlugin.class),
                        emptyProvider(ResourceFetcher.class),
                        emptyProvider(BubbleTrackerStore.class),
                        provider(MemoryObserver.class, observer));

        var memory = (DefaultMemory) factory.create(graphEnabledBuildOptions()).memory();
        var extractor = readField(memory, "extractor", DefaultMemoryExtractor.class);
        var itemLayer = readField(extractor, "memoryItemStep", MemoryItemLayer.class);

        assertThat(readField(itemLayer, "graphMaterializer", ItemGraphMaterializer.class))
                .isInstanceOf(TracingItemGraphMaterializer.class);
    }

    @Test
    void runtimeFactoryPreservesSemanticSourceWindowSizeInEffectiveOptions() {
        var configuration = new MemindServerRuntimeConfiguration();

        MemoryRuntimeFactory factory =
                configuration.memoryRuntimeFactory(
                        provider(StructuredChatClient.class, proxy(StructuredChatClient.class)),
                        provider(MemoryStore.class, memoryStore()),
                        provider(MemoryBuffer.class, memoryBuffer()),
                        provider(MemoryVector.class, proxy(MemoryVector.class)),
                        emptyProvider(MemoryTextSearch.class),
                        provider(Reranker.class, new NoopReranker()),
                        emptyProvider(ContentParser.class),
                        emptyProvider(RawDataPlugin.class),
                        emptyProvider(ResourceFetcher.class),
                        emptyProvider(BubbleTrackerStore.class),
                        emptyProvider(MemoryObserver.class));

        var created =
                factory.create(
                        MemoryBuildOptions.builder()
                                .extraction(
                                        new ExtractionOptions(
                                                ExtractionCommonOptions.defaults(),
                                                RawDataExtractionOptions.defaults(),
                                                new ItemExtractionOptions(
                                                        false,
                                                        PromptBudgetOptions.defaults(),
                                                        ItemGraphOptions.defaults()
                                                                .withEnabled(true)
                                                                .withSemanticSourceWindowSize(64)),
                                                InsightExtractionOptions.defaults()))
                                .build());

        assertThat(
                        created.effectiveOptions()
                                .extraction()
                                .item()
                                .graph()
                                .semanticSourceWindowSize())
                .isEqualTo(64);
    }

    @Test
    void runtimeFactoryUsesProvidedContentParsersWithoutInjectingImplicitDocumentParser() {
        var parser =
                new ContentParser() {
                    @Override
                    public String parserId() {
                        return "document-tika";
                    }

                    @Override
                    public String contentType() {
                        return DocumentContent.TYPE;
                    }

                    @Override
                    public String contentProfile() {
                        return "document.binary";
                    }

                    @Override
                    public String governanceType() {
                        return DocumentSemantics.GOVERNANCE_BINARY;
                    }

                    @Override
                    public int priority() {
                        return 50;
                    }

                    @Override
                    public Set<String> supportedMimeTypes() {
                        return Set.of("application/pdf");
                    }

                    @Override
                    public Set<String> supportedExtensions() {
                        return Set.of(".pdf");
                    }

                    @Override
                    public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                        return Mono.just(
                                new DocumentContent(
                                        "Report",
                                        source.mimeType(),
                                        "parsed",
                                        java.util.List.of(),
                                        source.sourceUrl(),
                                        Map.of(
                                                "parserId",
                                                parserId(),
                                                "contentProfile",
                                                contentProfile())));
                    }
                };
        ResourceFetcher fetcher = request -> Mono.empty();
        var configuration = new MemindServerRuntimeConfiguration();

        MemoryRuntimeFactory factory =
                configuration.memoryRuntimeFactory(
                        provider(StructuredChatClient.class, proxy(StructuredChatClient.class)),
                        provider(MemoryStore.class, memoryStore()),
                        provider(MemoryBuffer.class, memoryBuffer()),
                        provider(MemoryVector.class, proxy(MemoryVector.class)),
                        emptyProvider(MemoryTextSearch.class),
                        provider(Reranker.class, new NoopReranker()),
                        provider(ContentParser.class, parser),
                        emptyProvider(RawDataPlugin.class),
                        provider(ResourceFetcher.class, fetcher),
                        emptyProvider(BubbleTrackerStore.class),
                        emptyProvider(MemoryObserver.class));

        Memory memory = factory.create(MemoryBuildOptions.defaults()).memory();
        var extractor =
                readField((DefaultMemory) memory, "extractor", DefaultMemoryExtractor.class);
        ContentParserRegistry registry =
                readField(extractor, "contentParserRegistry", ContentParserRegistry.class);

        assertThat(registry).isNotNull();
        assertThat(registry.capabilities())
                .extracting(ContentCapability::parserId)
                .containsExactly("document-tika");
        assertThat(readField(extractor, "resourceFetcher", ResourceFetcher.class))
                .isSameAs(fetcher);
    }

    @Test
    void runtimeFactoryForwardsRawDataPluginsAlongsideContentParsers() {
        var plugin = new TestRawDataPlugin();
        var parser =
                new ContentParser() {
                    @Override
                    public String parserId() {
                        return "test-document-parser";
                    }

                    @Override
                    public String contentType() {
                        return DocumentContent.TYPE;
                    }

                    @Override
                    public String contentProfile() {
                        return "document.binary";
                    }

                    @Override
                    public String governanceType() {
                        return DocumentSemantics.GOVERNANCE_BINARY;
                    }

                    @Override
                    public Set<String> supportedMimeTypes() {
                        return Set.of("application/pdf");
                    }

                    @Override
                    public Mono<RawContent> parse(byte[] data, SourceDescriptor source) {
                        return Mono.error(new UnsupportedOperationException("test stub"));
                    }
                };
        var configuration = new MemindServerRuntimeConfiguration();

        MemoryRuntimeFactory factory =
                configuration.memoryRuntimeFactory(
                        provider(StructuredChatClient.class, proxy(StructuredChatClient.class)),
                        provider(MemoryStore.class, memoryStore()),
                        provider(MemoryBuffer.class, memoryBuffer()),
                        provider(MemoryVector.class, proxy(MemoryVector.class)),
                        emptyProvider(MemoryTextSearch.class),
                        provider(Reranker.class, new NoopReranker()),
                        provider(ContentParser.class, parser),
                        provider(RawDataPlugin.class, plugin),
                        emptyProvider(ResourceFetcher.class),
                        emptyProvider(BubbleTrackerStore.class),
                        emptyProvider(MemoryObserver.class));

        Memory memory = factory.create(MemoryBuildOptions.defaults()).memory();
        var extractor =
                readField((DefaultMemory) memory, "extractor", DefaultMemoryExtractor.class);

        RawContentProcessorRegistry registry =
                readField(
                        extractor,
                        "rawContentProcessorRegistry",
                        RawContentProcessorRegistry.class);

        assertThat(registry.resolve(new TestRawContent()))
                .isInstanceOf(TestRawContentProcessor.class);
    }

    @Test
    void runtimeFactoryCollectsImageAndAudioParserBeansFromSpringContext() {
        ContentParser imageParser =
                new VisionImageContentParser(
                        chatModel(
                                """
                                {"description":"chart","ocrText":"Q1 revenue","metadata":{"provider":"test"}}
                                """));
        ContentParser audioParser =
                new TranscriptionAudioContentParser(transcriptionModel("speaker one said hello"));
        var configuration = new MemindServerRuntimeConfiguration();

        MemoryRuntimeFactory factory =
                configuration.memoryRuntimeFactory(
                        provider(StructuredChatClient.class, proxy(StructuredChatClient.class)),
                        provider(MemoryStore.class, memoryStore()),
                        provider(MemoryBuffer.class, memoryBuffer()),
                        provider(MemoryVector.class, proxy(MemoryVector.class)),
                        emptyProvider(MemoryTextSearch.class),
                        provider(Reranker.class, new NoopReranker()),
                        provider(
                                ContentParser.class,
                                Map.of("imageParser", imageParser, "audioParser", audioParser)),
                        emptyProvider(RawDataPlugin.class),
                        emptyProvider(ResourceFetcher.class),
                        emptyProvider(BubbleTrackerStore.class),
                        emptyProvider(MemoryObserver.class));

        Memory memory = factory.create(MemoryBuildOptions.defaults()).memory();
        var extractor =
                readField((DefaultMemory) memory, "extractor", DefaultMemoryExtractor.class);
        ContentParserRegistry registry =
                readField(extractor, "contentParserRegistry", ContentParserRegistry.class);

        assertThat(registry).isNotNull();
        assertThat(registry.capabilities())
                .extracting(
                        ContentCapability::parserId,
                        ContentCapability::contentType,
                        ContentCapability::contentProfile)
                .containsExactlyInAnyOrder(
                        tuple("image-vision", ImageContent.TYPE, "image.caption-ocr"),
                        tuple("audio-transcription", AudioContent.TYPE, "audio.transcript"));
    }

    @Test
    void runtimeFactoryReturnsSanitizedEffectiveOptionsForInvalidMemoryThreadDerivation() {
        var configuration = new MemindServerRuntimeConfiguration();
        var requested =
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
                        .memoryThread(
                                MemoryBuildOptions.defaults()
                                        .memoryThread()
                                        .withEnabled(true)
                                        .withDerivation(
                                                MemoryBuildOptions.defaults()
                                                        .memoryThread()
                                                        .derivation()
                                                        .withEnabled(true)))
                        .build();

        MemoryRuntimeFactory factory =
                configuration.memoryRuntimeFactory(
                        provider(StructuredChatClient.class, proxy(StructuredChatClient.class)),
                        provider(MemoryStore.class, memoryStore()),
                        provider(MemoryBuffer.class, memoryBuffer()),
                        provider(MemoryVector.class, proxy(MemoryVector.class)),
                        emptyProvider(MemoryTextSearch.class),
                        provider(Reranker.class, new NoopReranker()),
                        emptyProvider(ContentParser.class),
                        emptyProvider(RawDataPlugin.class),
                        emptyProvider(ResourceFetcher.class),
                        emptyProvider(BubbleTrackerStore.class),
                        emptyProvider(MemoryObserver.class));

        var created = factory.create(requested);

        assertThat(created.memory()).isInstanceOf(DefaultMemory.class);
        assertThat(created.effectiveOptions().memoryThread().enabled()).isTrue();
        assertThat(created.effectiveOptions().memoryThread().derivation().enabled()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static ChatModel chatModel(String responseText) {
        ChatResponse response =
                new ChatResponse(
                        java.util.List.of(new Generation(new AssistantMessage(responseText))));
        return (ChatModel)
                Proxy.newProxyInstance(
                        MemindServerRuntimeConfigurationTest.class.getClassLoader(),
                        new Class<?>[] {ChatModel.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "call" -> {
                                        if (args.length == 1 && args[0] instanceof Prompt) {
                                            yield response;
                                        }
                                        throw new UnsupportedOperationException(method.getName());
                                    }
                                    case "toString" -> "FakeChatModel";
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }

    @SuppressWarnings("unchecked")
    private static TranscriptionModel transcriptionModel(String transcript) {
        AudioTranscriptionResponse response =
                new AudioTranscriptionResponse(new AudioTranscription(transcript));
        return (TranscriptionModel)
                Proxy.newProxyInstance(
                        MemindServerRuntimeConfigurationTest.class.getClassLoader(),
                        new Class<?>[] {TranscriptionModel.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "call" -> {
                                        if (args.length == 1
                                                && args[0] instanceof AudioTranscriptionPrompt) {
                                            yield response;
                                        }
                                        throw new UnsupportedOperationException(method.getName());
                                    }
                                    case "toString" -> "FakeTranscriptionModel";
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        var beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("bean", bean);
        return beanFactory.getBeanProvider(type);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, Map<String, T> beans) {
        var beanFactory = new StaticListableBeanFactory();
        beans.forEach(beanFactory::addBean);
        return beanFactory.getBeanProvider(type);
    }

    private static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        return new StaticListableBeanFactory().getBeanProvider(type);
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
                            if (returnType == boolean.class) {
                                return false;
                            }
                            if (returnType == int.class) {
                                return 0;
                            }
                            if (returnType == long.class) {
                                return 0L;
                            }
                            if (returnType == Mono.class) {
                                return Mono.empty();
                            }
                            if (returnType == Flux.class) {
                                return Flux.empty();
                            }
                            if (returnType == java.util.List.class) {
                                return java.util.List.of();
                            }
                            if (returnType == java.util.Map.class) {
                                return Map.of();
                            }
                            return null;
                        });
    }

    private static MemoryStore memoryStore() {
        return MemoryStore.of(
                proxy(RawDataOperations.class),
                proxy(ItemOperations.class),
                proxy(InsightOperations.class));
    }

    private static MemoryBuffer memoryBuffer() {
        return MemoryBuffer.of(
                proxy(InsightBuffer.class),
                proxy(PendingConversationBuffer.class),
                proxy(RecentConversationBuffer.class));
    }

    private static MemoryBuildOptions graphEnabledBuildOptions() {
        return MemoryBuildOptions.builder()
                .extraction(
                        new ExtractionOptions(
                                ExtractionCommonOptions.defaults(),
                                RawDataExtractionOptions.defaults(),
                                new ItemExtractionOptions(
                                        false,
                                        PromptBudgetOptions.defaults(),
                                        ItemGraphOptions.defaults().withEnabled(true)),
                                InsightExtractionOptions.defaults()))
                .build();
    }

    private static final class TestRawDataPlugin implements RawDataPlugin {

        @Override
        public String pluginId() {
            return "test-rawdata-plugin";
        }

        @Override
        public java.util.List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
            return java.util.List.of(new TestRawContentProcessor());
        }

        @Override
        public java.util.List<ContentParser> parsers(RawDataPluginContext context) {
            return java.util.List.of();
        }
    }

    private static final class TestRawContentProcessor
            implements RawContentProcessor<TestRawContent> {

        @Override
        public String contentType() {
            return "TEST";
        }

        @Override
        public Class<TestRawContent> contentClass() {
            return TestRawContent.class;
        }

        @Override
        public Mono<
                        java.util.List<
                                com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment>>
                chunk(TestRawContent content) {
            return Mono.just(java.util.List.of());
        }
    }

    private static final class TestRawContent extends RawContent {

        @Override
        public String contentType() {
            return "TEST";
        }

        @Override
        public String toContentString() {
            return "test";
        }

        @Override
        public String getContentId() {
            return "test";
        }
    }

    private static final class TestMemoryObserver implements MemoryObserver {

        @Override
        public <T> Mono<T> observeMono(
                ObservationContext<T> ctx, java.util.function.Supplier<Mono<T>> operation) {
            return operation.get();
        }

        @Override
        public <T> Flux<T> observeFlux(
                ObservationContext<T> ctx, java.util.function.Supplier<Flux<T>> operation) {
            return operation.get();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String fieldName, Class<T> fieldType) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) fieldType.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
