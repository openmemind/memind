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
import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.utils.IdUtils;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class MemoryExtractionAssembler {

    MemoryExtractionAssembly assemble(MemoryAssemblyContext context) {
        ChatClientRegistry registry = context.chatClientRegistry();
        CaptionGenerator captionGenerator =
                new LlmConversationCaptionGenerator(
                        registry.resolve(ChatClientSlot.CAPTION_GENERATOR),
                        context.promptRegistry());
        List<RawContentProcessor<?>> processors =
                createProcessors(
                        registry, captionGenerator, context.promptRegistry(), context.options());
        RawDataLayer rawDataLayer =
                new RawDataLayer(
                        processors,
                        captionGenerator,
                        context.memoryStore(),
                        context.memoryVector());

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
                        null,
                        conversationCategories());

        InsightGenerator insightGenerator =
                new LlmInsightGenerator(
                        registry.resolve(ChatClientSlot.INSIGHT_GENERATOR),
                        context.promptRegistry());
        InsightGroupClassifier insightGroupClassifier =
                new LlmInsightGroupClassifier(
                        registry.resolve(ChatClientSlot.INSIGHT_GROUP_CLASSIFIER),
                        context.promptRegistry());
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
                        context.options().extraction().insight().build(),
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
                        options.extraction().rawdata().chunking(),
                        captionGenerator,
                        null);
        ToolCallContentProcessor toolCallProcessor =
                new ToolCallContentProcessor(
                        new LlmToolCallItemExtractionStrategy(
                                registry.resolve(ChatClientSlot.TOOL_CALL_EXTRACTION),
                                promptRegistry));

        return List.of(conversationProcessor, toolCallProcessor);
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
                        registry.resolve(ChatClientSlot.ITEM_EXTRACTION),
                        conversationCategories(),
                        promptRegistry);
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
