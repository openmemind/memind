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

import com.openmemind.ai.memory.core.llm.ChatClientRegistry;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.retrieval.DefaultMemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.admission.DefaultRetrievalAdmissionPolicy;
import com.openmemind.ai.memory.core.retrieval.cache.CaffeineRetrievalCache;
import com.openmemind.ai.memory.core.retrieval.cache.RetrievalCache;
import com.openmemind.ai.memory.core.retrieval.deep.LlmTypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.graph.DefaultGraphItemChannel;
import com.openmemind.ai.memory.core.retrieval.graph.DefaultRetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.graph.GraphExpansionEngine;
import com.openmemind.ai.memory.core.retrieval.graph.GraphItemChannel;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.query.LlmLongQueryCondenser;
import com.openmemind.ai.memory.core.retrieval.query.LongQueryCondenser;
import com.openmemind.ai.memory.core.retrieval.scoring.DefaultRetrievalResultMerger;
import com.openmemind.ai.memory.core.retrieval.scoring.RetrievalResultMerger;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleRetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.sufficiency.LlmSufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.temporal.DefaultTemporalConstraintExtractor;
import com.openmemind.ai.memory.core.retrieval.temporal.DefaultTemporalItemChannel;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannel;
import com.openmemind.ai.memory.core.retrieval.thread.DefaultMemoryThreadAssistant;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistConfigMapper;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistant;
import com.openmemind.ai.memory.core.retrieval.thread.NoOpMemoryThreadAssistant;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierSearch;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTypeRouter;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierSearch;
import com.openmemind.ai.memory.core.retrieval.tier.LlmInsightTypeRouter;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.NoopMemoryObserver;
import com.openmemind.ai.memory.core.tracing.decorator.TracingGraphItemChannel;
import com.openmemind.ai.memory.core.tracing.decorator.TracingInsightTierRetriever;
import com.openmemind.ai.memory.core.tracing.decorator.TracingItemTierRetriever;
import com.openmemind.ai.memory.core.tracing.decorator.TracingMemoryThreadAssistant;
import com.openmemind.ai.memory.core.tracing.decorator.TracingRetrievalGraphAssistant;
import com.openmemind.ai.memory.core.tracing.decorator.TracingRetrievalResultMerger;
import com.openmemind.ai.memory.core.tracing.decorator.TracingTemporalItemChannel;

final class MemoryRetrievalAssembler {

    DefaultMemoryRetriever assemble(MemoryAssemblyContext context) {
        ChatClientRegistry registry = context.chatClientRegistry();
        InsightTypeRouter insightTypeRouter =
                new LlmInsightTypeRouter(
                        registry.resolve(ChatClientSlot.INSIGHT_TYPE_ROUTER),
                        context.promptRegistry());
        InsightTierSearch insightTierRetriever =
                tracingInsightTierRetriever(
                        new InsightTierRetriever(
                                context.memoryStore(), context.memoryVector(), insightTypeRouter),
                        context.memoryObserver());
        ItemTierSearch itemTierRetriever =
                tracingItemTierRetriever(
                        new ItemTierRetriever(
                                context.memoryStore(),
                                context.memoryVector(),
                                context.textSearch()),
                        context.memoryObserver());
        SufficiencyGate sufficiencyGate =
                new LlmSufficiencyGate(
                        registry.resolve(ChatClientSlot.SUFFICIENCY_GATE),
                        context.promptRegistry());
        TypedQueryExpander typedQueryExpander =
                new LlmTypedQueryExpander(
                        registry.resolve(ChatClientSlot.QUERY_EXPANDER), context.promptRegistry());
        var graphExpansionEngine = new GraphExpansionEngine(context.memoryStore());
        RetrievalGraphAssistant graphAssistant = buildGraphAssistant(context, graphExpansionEngine);
        GraphItemChannel graphItemChannel =
                tracingGraphItemChannel(
                        new DefaultGraphItemChannel(graphExpansionEngine),
                        context.memoryObserver());
        MemoryThreadAssistant memoryThreadAssistant = buildMemoryThreadAssistant(context);
        RetrievalResultMerger resultMerger =
                tracingRetrievalResultMerger(
                        DefaultRetrievalResultMerger.INSTANCE, context.memoryObserver());
        SimpleStrategyConfig simpleStrategyConfig = simpleStrategyConfig(context.options());
        DeepStrategyConfig deepStrategyConfig = deepStrategyConfig(context.options());
        DeepRetrievalStrategy deepRetrievalStrategy =
                new DeepRetrievalStrategy(
                        insightTierRetriever,
                        itemTierRetriever,
                        sufficiencyGate,
                        typedQueryExpander,
                        context.reranker(),
                        context.memoryStore(),
                        deepStrategyConfig,
                        graphAssistant,
                        memoryThreadAssistant,
                        resultMerger);
        SimpleRetrievalStrategy simpleRetrievalStrategy =
                new SimpleRetrievalStrategy(
                        insightTierRetriever,
                        itemTierRetriever,
                        context.textSearch(),
                        context.memoryStore(),
                        simpleStrategyConfig,
                        graphAssistant,
                        memoryThreadAssistant,
                        new DefaultTemporalConstraintExtractor(),
                        tracingTemporalItemChannel(
                                new DefaultTemporalItemChannel(context.memoryStore()),
                                context.memoryObserver()),
                        graphItemChannel,
                        resultMerger,
                        java.time.Clock.systemDefaultZone());

        RetrievalCache retrievalCache = new CaffeineRetrievalCache();
        var admissionOptions = context.options().retrieval().common().admission();
        var admissionPolicy = new DefaultRetrievalAdmissionPolicy(admissionOptions);
        LongQueryCondenser longQueryCondenser =
                new LlmLongQueryCondenser(
                        registry.resolve(ChatClientSlot.LONG_QUERY_CONDENSER),
                        context.promptRegistry());
        DefaultMemoryRetriever memoryRetriever =
                new DefaultMemoryRetriever(
                        retrievalCache,
                        context.memoryStore(),
                        context.textSearch(),
                        null,
                        admissionPolicy,
                        admissionOptions,
                        longQueryCondenser);
        memoryRetriever.registerStrategy(simpleRetrievalStrategy);
        memoryRetriever.registerStrategy(deepRetrievalStrategy);
        return memoryRetriever;
    }

    private RetrievalGraphAssistant buildGraphAssistant(
            MemoryAssemblyContext context, GraphExpansionEngine graphExpansionEngine) {
        return new TracingRetrievalGraphAssistant(
                new DefaultRetrievalGraphAssistant(graphExpansionEngine), context.memoryObserver());
    }

    private InsightTierSearch tracingInsightTierRetriever(
            InsightTierSearch retriever, MemoryObserver observer) {
        if (observer instanceof NoopMemoryObserver
                || retriever instanceof TracingInsightTierRetriever) {
            return retriever;
        }
        return new TracingInsightTierRetriever(retriever, observer);
    }

    private ItemTierSearch tracingItemTierRetriever(
            ItemTierSearch retriever, MemoryObserver observer) {
        if (observer instanceof NoopMemoryObserver
                || retriever instanceof TracingItemTierRetriever) {
            return retriever;
        }
        return new TracingItemTierRetriever(retriever, observer);
    }

    private GraphItemChannel tracingGraphItemChannel(
            GraphItemChannel channel, MemoryObserver observer) {
        if (observer instanceof NoopMemoryObserver || channel instanceof TracingGraphItemChannel) {
            return channel;
        }
        return new TracingGraphItemChannel(channel, observer);
    }

    private TemporalItemChannel tracingTemporalItemChannel(
            TemporalItemChannel channel, MemoryObserver observer) {
        if (observer instanceof NoopMemoryObserver
                || channel instanceof TracingTemporalItemChannel) {
            return channel;
        }
        return new TracingTemporalItemChannel(channel, observer);
    }

    private RetrievalResultMerger tracingRetrievalResultMerger(
            RetrievalResultMerger merger, MemoryObserver observer) {
        if (observer instanceof NoopMemoryObserver
                || merger instanceof TracingRetrievalResultMerger) {
            return merger;
        }
        return new TracingRetrievalResultMerger(merger, observer);
    }

    private MemoryThreadAssistant buildMemoryThreadAssistant(MemoryAssemblyContext context) {
        if (!context.options().memoryThread().enabled()) {
            return NoOpMemoryThreadAssistant.INSTANCE;
        }
        return new TracingMemoryThreadAssistant(
                new DefaultMemoryThreadAssistant(
                        context.memoryStore(),
                        context.options().memoryThread().lifecycle().dormantAfter()),
                context.memoryObserver());
    }

    private SimpleStrategyConfig simpleStrategyConfig(MemoryBuildOptions options) {
        var graph = options.retrieval().simple().graphAssist();
        var temporal = options.retrieval().simple().temporalRetrieval();
        return new SimpleStrategyConfig(
                options.retrieval().simple().keywordSearchEnabled(),
                new SimpleStrategyConfig.TemporalRetrievalConfig(
                        temporal.enabled(),
                        temporal.maxWindowCandidates(),
                        temporal.channelWeight(),
                        temporal.timeout()),
                new SimpleStrategyConfig.GraphAssistConfig(
                        graph.enabled(),
                        graph.mode(),
                        graph.maxSeedItems(),
                        graph.maxExpandedItems(),
                        graph.maxSemanticNeighborsPerSeed(),
                        graph.maxTemporalNeighborsPerSeed(),
                        graph.maxCausalNeighborsPerSeed(),
                        graph.maxEntitySiblingItemsPerSeed(),
                        graph.maxItemsPerEntity(),
                        graph.graphChannelWeight(),
                        graph.minLinkStrength(),
                        graph.minMentionConfidence(),
                        graph.protectDirectTopK(),
                        graph.semanticEvidenceDecayFactor(),
                        graph.timeout()),
                MemoryThreadAssistConfigMapper.toSimpleConfig(options));
    }

    private DeepStrategyConfig deepStrategyConfig(MemoryBuildOptions options) {
        var base = DeepStrategyConfig.defaults();
        var graph = options.retrieval().deep().graphAssist();
        return new DeepStrategyConfig(
                new DeepStrategyConfig.QueryExpansionConfig(
                        options.retrieval().deep().queryExpansion().maxExpandedQueries()),
                new DeepStrategyConfig.SufficiencyConfig(
                        options.retrieval().deep().sufficiency().itemTopK()),
                base.tier2InitTopK(),
                base.bm25InitTopK(),
                base.minScore(),
                new DeepStrategyConfig.GraphAssistConfig(
                        graph.enabled(),
                        graph.mode(),
                        graph.maxSeedItems(),
                        graph.maxExpandedItems(),
                        graph.maxSemanticNeighborsPerSeed(),
                        graph.maxTemporalNeighborsPerSeed(),
                        graph.maxCausalNeighborsPerSeed(),
                        graph.maxEntitySiblingItemsPerSeed(),
                        graph.maxItemsPerEntity(),
                        graph.graphChannelWeight(),
                        graph.minLinkStrength(),
                        graph.minMentionConfidence(),
                        graph.protectDirectTopK(),
                        graph.semanticEvidenceDecayFactor(),
                        graph.timeout()),
                MemoryThreadAssistConfigMapper.toDeepConfig(options));
    }
}
