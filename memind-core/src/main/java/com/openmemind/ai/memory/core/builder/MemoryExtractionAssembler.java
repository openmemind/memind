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

import com.openmemind.ai.memory.core.extraction.DefaultMemoryExtractor;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.context.ContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.context.LlmContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.insight.InsightLayer;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.generator.LlmInsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupRouter;
import com.openmemind.ai.memory.core.extraction.insight.group.LlmInsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildScheduler;
import com.openmemind.ai.memory.core.extraction.insight.support.InsightPointEvidenceNormalizer;
import com.openmemind.ai.memory.core.extraction.insight.support.InsightPointIdentityManager;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTracker;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeReorganizer;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.item.MemoryItemLayer;
import com.openmemind.ai.memory.core.extraction.item.dedup.CompositeDeduplicator;
import com.openmemind.ai.memory.core.extraction.item.dedup.HashBasedDeduplicator;
import com.openmemind.ai.memory.core.extraction.item.dedup.MemoryItemDeduplicator;
import com.openmemind.ai.memory.core.extraction.item.extractor.DefaultMemoryItemExtractor;
import com.openmemind.ai.memory.core.extraction.item.extractor.MemoryItemExtractor;
import com.openmemind.ai.memory.core.extraction.item.strategy.LlmItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.RawDataLayer;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.LlmConversationCaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.LlmConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ConversationContentProcessor;
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicy;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicyRegistry;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.DefaultContentParserRegistry;
import com.openmemind.ai.memory.core.resource.HttpResourceFetcher;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.utils.IdUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class MemoryExtractionAssembler {

    private static final ResourceFetcher DEFAULT_RESOURCE_FETCHER = new HttpResourceFetcher();

    MemoryExtractionAssembly assemble(MemoryAssemblyContext context) {
        ChatClientRegistry registry = context.chatClientRegistry();
        var promptRegistry = context.promptRegistry();
        CaptionGenerator captionGenerator =
                new LlmConversationCaptionGenerator(
                        registry.resolve(ChatClientSlot.CAPTION_GENERATOR), promptRegistry);
        ConversationContentProcessor conversationProcessor =
                conversationProcessor(
                        registry, captionGenerator, promptRegistry, context.options());
        RawDataPluginContext pluginContext =
                new RawDataPluginContext(registry, promptRegistry, context.options());
        List<RawDataPlugin> plugins = resolvePlugins(context);
        List<RawContentProcessor<?>> processors = new ArrayList<>();
        processors.add(conversationProcessor);
        plugins.forEach(plugin -> processors.addAll(pluginProcessors(plugin, pluginContext)));
        RawContentJackson.pluginSubtypeMappings(
                plugins.stream().flatMap(plugin -> pluginTypeRegistrars(plugin).stream()).toList());
        RawContentProcessorRegistry processorRegistry = new RawContentProcessorRegistry(processors);
        RawDataIngestionPolicyRegistry ingestionPolicyRegistry =
                resolveIngestionPolicyRegistry(plugins);
        ContentParserRegistry effectiveParserRegistry =
                resolveContentParserRegistry(context, plugins, pluginContext);
        RawDataLayer rawDataLayer =
                new RawDataLayer(
                        processorRegistry,
                        captionGenerator,
                        context.memoryStore(),
                        context.memoryVector(),
                        context.options().extraction().rawdata().vectorBatchSize());

        MemoryItemExtractor itemExtractor =
                createMemoryItemExtractor(registry, processors, context.promptRegistry());
        MemoryItemDeduplicator deduplicator =
                new CompositeDeduplicator(
                        List.of(new HashBasedDeduplicator(context.memoryStore())));
        MemoryItemLayer memoryItemLayer =
                new MemoryItemLayer(
                        itemExtractor,
                        deduplicator,
                        context.memoryStore(),
                        context.memoryVector(),
                        IdUtils.snowflake(),
                        null);

        InsightGenerator insightGenerator =
                new LlmInsightGenerator(
                        registry.resolve(ChatClientSlot.INSIGHT_GENERATOR),
                        context.promptRegistry());
        InsightGroupClassifier insightGroupClassifier =
                new LlmInsightGroupClassifier(
                        registry.resolve(ChatClientSlot.INSIGHT_GROUP_CLASSIFIER),
                        context.promptRegistry());
        var identityManager = new InsightPointIdentityManager();
        var evidenceNormalizer = new InsightPointEvidenceNormalizer();
        BubbleTrackerStore bubbleTrackerStore =
                context.bubbleTrackerStore() != null
                        ? context.bubbleTrackerStore()
                        : new BubbleTracker();
        InsightTreeReorganizer insightTreeReorganizer =
                new InsightTreeReorganizer(
                        insightGenerator,
                        context.memoryVector(),
                        context.memoryStore(),
                        bubbleTrackerStore,
                        IdUtils.snowflake(),
                        identityManager,
                        evidenceNormalizer);
        InsightGroupRouter insightGroupRouter = new InsightGroupRouter(insightGroupClassifier);
        InsightBuildScheduler insightBuildScheduler =
                new InsightBuildScheduler(
                        context.insightBuffer(),
                        context.memoryStore(),
                        insightGenerator,
                        insightGroupClassifier,
                        insightGroupRouter,
                        insightTreeReorganizer,
                        context.memoryVector(),
                        IdUtils.snowflake(),
                        context.options().extraction().insight().build(),
                        identityManager,
                        evidenceNormalizer,
                        null);
        InsightLayer insightLayer =
                new InsightLayer(
                        context.memoryStore(),
                        insightBuildScheduler,
                        unsupportedInsightTypes(processors));

        ContextCommitDetector contextCommitDetector =
                new LlmContextCommitDetector(
                        context.options().extraction().rawdata().commitDetection(),
                        registry.resolve(ChatClientSlot.CONTEXT_COMMIT_DETECTOR),
                        context.promptRegistry());
        MemoryExtractor pipeline =
                new DefaultMemoryExtractor(
                        rawDataLayer,
                        memoryItemLayer,
                        insightLayer,
                        rawDataLayer,
                        contextCommitDetector,
                        context.pendingConversationBuffer(),
                        context.recentConversationBuffer(),
                        processorRegistry,
                        effectiveParserRegistry,
                        context.memoryStore().resourceStore(),
                        resolveResourceFetcher(context.resourceFetcher()),
                        ingestionPolicyRegistry,
                        context.options().extraction().rawdata(),
                        context.options().extraction().item());
        return new MemoryExtractionAssembly(pipeline, insightLayer, insightBuildScheduler);
    }

    private ResourceFetcher resolveResourceFetcher(ResourceFetcher runtimeFetcher) {
        return runtimeFetcher != null ? runtimeFetcher : DEFAULT_RESOURCE_FETCHER;
    }

    private ConversationContentProcessor conversationProcessor(
            ChatClientRegistry registry,
            CaptionGenerator captionGenerator,
            com.openmemind.ai.memory.core.prompt.PromptRegistry promptRegistry,
            MemoryBuildOptions options) {
        ConversationChunker conversationChunker = new ConversationChunker();
        LlmConversationChunker llmConversationChunker =
                new LlmConversationChunker(
                        registry.resolve(ChatClientSlot.CONVERSATION_CHUNKER),
                        conversationChunker,
                        promptRegistry);

        ConversationContentProcessor conversationProcessor =
                new ConversationContentProcessor(
                        conversationChunker,
                        llmConversationChunker,
                        options.extraction().rawdata().conversation(),
                        captionGenerator,
                        null);
        return conversationProcessor;
    }

    private List<RawDataPlugin> resolvePlugins(MemoryAssemblyContext context) {
        List<RawDataPlugin> plugins = new ArrayList<>(context.rawDataPlugins());

        Set<String> pluginIds = new LinkedHashSet<>();
        for (RawDataPlugin plugin : plugins) {
            String pluginId = Objects.requireNonNull(plugin.pluginId(), "plugin.pluginId()");
            if (!pluginIds.add(pluginId)) {
                throw new IllegalStateException("Duplicate rawData pluginId: " + pluginId);
            }
        }
        return List.copyOf(plugins);
    }

    private List<RawContentProcessor<?>> pluginProcessors(
            RawDataPlugin plugin, RawDataPluginContext pluginContext) {
        return List.copyOf(
                Objects.requireNonNull(
                        plugin.processors(pluginContext), plugin.pluginId() + ".processors()"));
    }

    private List<ContentParser> pluginParsers(
            RawDataPlugin plugin, RawDataPluginContext pluginContext) {
        return List.copyOf(
                Objects.requireNonNull(
                        plugin.parsers(pluginContext), plugin.pluginId() + ".parsers()"));
    }

    private List<RawDataIngestionPolicy> pluginIngestionPolicies(RawDataPlugin plugin) {
        return List.copyOf(
                Objects.requireNonNull(
                        plugin.ingestionPolicies(), plugin.pluginId() + ".ingestionPolicies()"));
    }

    private List<com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar>
            pluginTypeRegistrars(RawDataPlugin plugin) {
        return List.copyOf(
                Objects.requireNonNull(
                        plugin.typeRegistrars(), plugin.pluginId() + ".typeRegistrars()"));
    }

    private ContentParserRegistry resolveContentParserRegistry(
            MemoryAssemblyContext context,
            List<RawDataPlugin> plugins,
            RawDataPluginContext pluginContext) {
        if (context.contentParserRegistry() != null) {
            return context.contentParserRegistry();
        }

        List<ContentParser> pluginParsers =
                plugins.stream()
                        .flatMap(plugin -> pluginParsers(plugin, pluginContext).stream())
                        .toList();
        return pluginParsers.isEmpty() ? null : new DefaultContentParserRegistry(pluginParsers);
    }

    private RawDataIngestionPolicyRegistry resolveIngestionPolicyRegistry(
            List<RawDataPlugin> plugins) {
        List<RawDataIngestionPolicy> policies =
                plugins.stream()
                        .flatMap(plugin -> pluginIngestionPolicies(plugin).stream())
                        .toList();
        return policies.isEmpty()
                ? RawDataIngestionPolicyRegistry.empty()
                : new RawDataIngestionPolicyRegistry(policies);
    }

    private MemoryItemExtractor createMemoryItemExtractor(
            ChatClientRegistry registry,
            List<RawContentProcessor<?>> processors,
            com.openmemind.ai.memory.core.prompt.PromptRegistry promptRegistry) {
        var strategies = new HashMap<String, ItemExtractionStrategy>();
        for (var processor : processors) {
            if (processor.itemExtractionStrategy() != null) {
                strategies.put(processor.contentType(), processor.itemExtractionStrategy());
            }
        }

        var defaultStrategy =
                new LlmItemExtractionStrategy(
                        registry.resolve(ChatClientSlot.ITEM_EXTRACTION), promptRegistry);
        return new DefaultMemoryItemExtractor(defaultStrategy, strategies);
    }

    private Set<String> unsupportedInsightTypes(List<RawContentProcessor<?>> processors) {
        return processors.stream()
                .filter(processor -> !processor.supportsInsight())
                .map(RawContentProcessor::contentType)
                .collect(Collectors.toSet());
    }
}
