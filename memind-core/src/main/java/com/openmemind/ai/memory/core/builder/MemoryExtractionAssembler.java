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

import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.extraction.MemoryExtractionPipeline;
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
import com.openmemind.ai.memory.core.extraction.item.strategy.LlmToolCallItemExtractionStrategy;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawDataLayer;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.LlmConversationCaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.LlmConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ConversationContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ToolCallContentProcessor;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.utils.IdUtils;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class MemoryExtractionAssembler {

    MemoryExtractionAssembly assemble(MemoryAssemblyContext context) {
        StructuredChatClient chatClient = context.chatClient();
        CaptionGenerator captionGenerator = new LlmConversationCaptionGenerator(chatClient);
        List<RawContentProcessor<?>> processors =
                createProcessors(chatClient, captionGenerator, context.options());
        RawDataLayer rawDataLayer =
                new RawDataLayer(
                        processors,
                        captionGenerator,
                        context.memoryStore(),
                        context.memoryVector());

        MemoryItemExtractor itemExtractor = createMemoryItemExtractor(chatClient, processors);
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
                        null,
                        conversationCategories());

        InsightGenerator insightGenerator = new LlmInsightGenerator(chatClient);
        InsightGroupClassifier insightGroupClassifier = new LlmInsightGroupClassifier(chatClient);
        BubbleTrackerStore bubbleTrackerStore = new BubbleTracker();
        InsightTreeReorganizer insightTreeReorganizer =
                new InsightTreeReorganizer(
                        insightGenerator,
                        context.memoryVector(),
                        context.memoryStore(),
                        bubbleTrackerStore,
                        IdUtils.snowflake());
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
                        context.options().insightBuild(),
                        null);
        InsightLayer insightLayer =
                new InsightLayer(
                        context.memoryStore(),
                        insightBuildScheduler,
                        unsupportedInsightTypes(processors));

        ContextCommitDetector contextCommitDetector =
                new LlmContextCommitDetector(context.options().boundaryDetector(), chatClient);
        MemoryExtractionPipeline pipeline =
                new MemoryExtractor(
                        rawDataLayer,
                        memoryItemLayer,
                        insightLayer,
                        rawDataLayer,
                        contextCommitDetector,
                        context.pendingConversationBuffer(),
                        context.recentConversationBuffer());
        return new MemoryExtractionAssembly(pipeline, insightLayer, insightBuildScheduler);
    }

    private List<RawContentProcessor<?>> createProcessors(
            StructuredChatClient chatClient,
            CaptionGenerator captionGenerator,
            MemoryBuildOptions options) {
        ConversationChunker conversationChunker = new ConversationChunker();
        LlmConversationChunker llmConversationChunker =
                new LlmConversationChunker(chatClient, conversationChunker);

        ConversationContentProcessor conversationProcessor =
                new ConversationContentProcessor(
                        conversationChunker,
                        llmConversationChunker,
                        options.conversationChunking(),
                        captionGenerator,
                        null);
        ToolCallContentProcessor toolCallProcessor =
                new ToolCallContentProcessor(new LlmToolCallItemExtractionStrategy(chatClient));

        return List.of(conversationProcessor, toolCallProcessor);
    }

    private MemoryItemExtractor createMemoryItemExtractor(
            StructuredChatClient chatClient, List<RawContentProcessor<?>> processors) {
        var strategies = new HashMap<String, ItemExtractionStrategy>();
        for (var processor : processors) {
            if (processor.itemExtractionStrategy() != null) {
                strategies.put(processor.contentType(), processor.itemExtractionStrategy());
            }
        }

        var defaultStrategy = new LlmItemExtractionStrategy(chatClient, conversationCategories());
        return new DefaultMemoryItemExtractor(defaultStrategy, strategies);
    }

    private Set<String> unsupportedInsightTypes(List<RawContentProcessor<?>> processors) {
        return processors.stream()
                .filter(processor -> !processor.supportsInsight())
                .map(RawContentProcessor::contentType)
                .collect(Collectors.toSet());
    }

    private EnumSet<MemoryCategory> conversationCategories() {
        return EnumSet.of(
                MemoryCategory.PROFILE,
                MemoryCategory.BEHAVIOR,
                MemoryCategory.EVENT,
                MemoryCategory.DIRECTIVE,
                MemoryCategory.PLAYBOOK,
                MemoryCategory.RESOLUTION);
    }
}
