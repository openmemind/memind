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
package com.openmemind.ai.memory.autoconfigure.extraction;

import com.openmemind.ai.memory.autoconfigure.MemoryExtractionProperties;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.extraction.MemoryExtractionPipeline;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig;
import com.openmemind.ai.memory.core.extraction.context.ContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.context.LlmContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.insight.InsightLayer;
import com.openmemind.ai.memory.core.extraction.insight.generator.InsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.generator.LlmInsightGenerator;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.insight.group.InsightGroupRouter;
import com.openmemind.ai.memory.core.extraction.insight.group.LlmInsightGroupClassifier;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
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
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.LlmConversationChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ConversationContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ToolCallContentProcessor;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.buffer.InMemoryRecentConversationBuffer;
import com.openmemind.ai.memory.core.store.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.store.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.utils.IdUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Extraction pipeline auto-configuration.
 *
 * <p>Mirrors all beans from the server's {@code ExtractionConfiguration}, each guarded by
 * {@link ConditionalOnMissingBean} so that users can override any bean.
 *
 * <p>Content processors are registered as individual beans so that users can override them
 * or add custom processors. The {@link RawDataLayer} and {@link DefaultMemoryItemExtractor}
 * receive all processors via {@code List<RawContentProcessor<?>>} injection.
 */
@AutoConfiguration
@AutoConfigureAfter(
        name = {
            "com.openmemind.ai.memory.plugin.ai.spring.autoconfigure.SpringAiLlmAutoConfiguration",
            "com.openmemind.ai.memory.plugin.ai.spring.autoconfigure.SpringAiVectorAutoConfiguration",
            "com.openmemind.ai.memory.plugin.jdbc.autoconfigure.JdbcPluginAutoConfiguration",
            "com.openmemind.ai.memory.plugin.store.mybatis.MemoryMybatisPlusAutoConfiguration"
        })
@EnableConfigurationProperties(MemoryExtractionProperties.class)
public class MemoryExtractionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConversationChunkingConfig conversationChunkingConfig(MemoryExtractionProperties props) {
        var c = props.getChunking();
        return new ConversationChunkingConfig(
                c.getMessagesPerChunk(), c.getStrategy(), c.getMinMessagesPerSegment());
    }

    @Bean
    @ConditionalOnMissingBean
    public InsightBuildConfig insightBuildConfig(MemoryExtractionProperties props) {
        var b = props.getInsightBuild();
        return new InsightBuildConfig(
                b.getGroupingThreshold(), b.getBuildThreshold(),
                b.getConcurrency(), b.getMaxRetries());
    }

    @Bean
    @ConditionalOnMissingBean
    public CommitDetectorConfig boundaryDetectorConfig(MemoryExtractionProperties props) {
        var b = props.getBoundary();
        return new CommitDetectorConfig(
                b.getMaxMessages(), b.getMaxTokens(), b.getMinMessagesForLlm());
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryBuildOptions memoryBuildOptions(
            ConversationChunkingConfig conversationChunkingConfig,
            InsightBuildConfig insightBuildConfig,
            CommitDetectorConfig commitDetectorConfig) {
        return MemoryBuildOptions.builder()
                .conversationChunking(conversationChunkingConfig)
                .insightBuild(insightBuildConfig)
                .boundaryDetector(commitDetectorConfig)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextCommitDetector boundaryDetector(CommitDetectorConfig commitDetectorConfig) {
        return new LlmContextCommitDetector(commitDetectorConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public RecentConversationBuffer recentConversationBuffer() {
        return new InMemoryRecentConversationBuffer();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({MemoryStore.class, RecentConversationBuffer.class})
    public MemoryBuffer memoryBuffer(
            MemoryStore store, RecentConversationBuffer recentConversationBuffer) {
        return MemoryBuffer.of(
                store.insightBufferStore(),
                store.conversationBufferStore(),
                recentConversationBuffer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StructuredChatClient.class)
    public CaptionGenerator captionGenerator(StructuredChatClient structuredChatClient) {
        return new LlmConversationCaptionGenerator(structuredChatClient);
    }

    // ─── Content Processors (individually overridable) ───

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StructuredChatClient.class)
    @ConditionalOnProperty(
            name = "memind.extraction.chunking.strategy",
            havingValue = "LLM",
            matchIfMissing = true)
    public LlmConversationChunker llmConversationChunker(
            ConversationChunkingConfig config, StructuredChatClient structuredChatClient) {
        return new LlmConversationChunker(structuredChatClient, new ConversationChunker());
    }

    @Bean
    @ConditionalOnMissingBean(name = "conversationContentProcessor")
    @ConditionalOnBean(CaptionGenerator.class)
    public ConversationContentProcessor conversationContentProcessor(
            CaptionGenerator captionGenerator,
            ConversationChunkingConfig conversationConfig,
            ObjectProvider<LlmConversationChunker> llmChunkerProvider) {
        return new ConversationContentProcessor(
                new ConversationChunker(),
                llmChunkerProvider.getIfAvailable(),
                conversationConfig,
                captionGenerator,
                null);
    }

    @Bean
    @ConditionalOnMissingBean(name = "toolCallContentProcessor")
    @ConditionalOnBean(StructuredChatClient.class)
    public ToolCallContentProcessor toolCallContentProcessor(
            StructuredChatClient structuredChatClient) {
        return new ToolCallContentProcessor(
                new LlmToolCallItemExtractionStrategy(structuredChatClient));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({CaptionGenerator.class, MemoryVector.class})
    public RawDataLayer rawDataLayer(
            List<RawContentProcessor<?>> processors,
            CaptionGenerator captionGenerator,
            MemoryStore store,
            MemoryVector vector) {
        return new RawDataLayer(processors, captionGenerator, store, vector);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StructuredChatClient.class)
    public MemoryItemExtractor memoryItemExtractor(
            StructuredChatClient structuredChatClient, List<RawContentProcessor<?>> processors) {
        // Build strategy map from processors that provide their own extraction strategy
        var strategies = new HashMap<String, ItemExtractionStrategy>();
        for (var p : processors) {
            if (p.itemExtractionStrategy() != null) {
                strategies.put(p.contentType(), p.itemExtractionStrategy());
            }
        }
        // All categories except TOOL (which uses its own strategy via ToolCallContentProcessor)
        var conversationCategories =
                EnumSet.of(
                        MemoryCategory.PROFILE,
                        MemoryCategory.BEHAVIOR,
                        MemoryCategory.EVENT,
                        MemoryCategory.PROCEDURAL);
        var defaultStrategy =
                new LlmItemExtractionStrategy(structuredChatClient, conversationCategories);
        return new DefaultMemoryItemExtractor(defaultStrategy, strategies);
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryItemDeduplicator memoryItemDeduplicator(MemoryStore store) {
        return new CompositeDeduplicator(List.of(new HashBasedDeduplicator(store)));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
        MemoryItemExtractor.class,
        MemoryItemDeduplicator.class,
        MemoryVector.class
    })
    public MemoryItemLayer memoryItemLayer(
            MemoryItemExtractor extractor,
            MemoryItemDeduplicator deduplicator,
            MemoryStore store,
            MemoryVector vector) {
        var conversationCategories =
                EnumSet.of(
                        MemoryCategory.PROFILE,
                        MemoryCategory.BEHAVIOR,
                        MemoryCategory.EVENT,
                        MemoryCategory.PROCEDURAL);
        return new MemoryItemLayer(
                extractor,
                deduplicator,
                store,
                vector,
                IdUtils.snowflake(),
                null,
                conversationCategories);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StructuredChatClient.class)
    public InsightGenerator insightGenerator(StructuredChatClient structuredChatClient) {
        return new LlmInsightGenerator(structuredChatClient);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StructuredChatClient.class)
    public InsightGroupClassifier insightGroupClassifier(
            StructuredChatClient structuredChatClient) {
        return new LlmInsightGroupClassifier(structuredChatClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public BubbleTrackerStore bubbleTrackerStore() {
        return new BubbleTracker();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({InsightGenerator.class, MemoryVector.class})
    public InsightTreeReorganizer insightTreeReorganizer(
            InsightGenerator generator,
            MemoryVector vector,
            MemoryStore store,
            BubbleTrackerStore bubbleTrackerStore) {
        return new InsightTreeReorganizer(
                generator, vector, store, bubbleTrackerStore, IdUtils.snowflake());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(InsightGroupClassifier.class)
    public InsightGroupRouter insightGroupRouter(InsightGroupClassifier groupClassifier) {
        return new InsightGroupRouter(groupClassifier);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
        InsightGenerator.class,
        InsightGroupClassifier.class,
        InsightGroupRouter.class,
        InsightTreeReorganizer.class,
        MemoryVector.class
    })
    public InsightBuildScheduler insightBuildScheduler(
            MemoryStore store,
            MemoryBuffer buffer,
            InsightGenerator generator,
            InsightGroupClassifier groupClassifier,
            InsightGroupRouter groupRouter,
            InsightTreeReorganizer treeReorganizer,
            MemoryVector vector,
            InsightBuildConfig insightBuildConfig,
            ObjectProvider<MemoryObserver> observerProvider) {
        return new InsightBuildScheduler(
                buffer.insightBuffer(),
                store,
                generator,
                groupClassifier,
                groupRouter,
                treeReorganizer,
                vector,
                IdUtils.snowflake(),
                insightBuildConfig,
                observerProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(InsightBuildScheduler.class)
    public InsightLayer insightLayer(
            MemoryStore store,
            InsightBuildScheduler scheduler,
            List<RawContentProcessor<?>> processors) {
        var unsupported =
                processors.stream()
                        .filter(p -> !p.supportsInsight())
                        .map(RawContentProcessor::contentType)
                        .collect(java.util.stream.Collectors.toSet());
        return new InsightLayer(store, scheduler, unsupported);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryExtractionPipeline.class)
    @ConditionalOnBean({RawDataLayer.class, MemoryItemLayer.class, InsightLayer.class})
    public MemoryExtractionPipeline memoryExtractor(
            RawDataLayer rawDataLayer,
            MemoryItemLayer memoryItemLayer,
            InsightLayer insightLayer,
            ContextCommitDetector contextCommitDetector,
            MemoryBuffer buffer) {
        return new MemoryExtractor(
                rawDataLayer,
                memoryItemLayer,
                insightLayer,
                rawDataLayer,
                contextCommitDetector,
                buffer.pendingConversationBuffer(),
                buffer.recentConversationBuffer());
    }
}
