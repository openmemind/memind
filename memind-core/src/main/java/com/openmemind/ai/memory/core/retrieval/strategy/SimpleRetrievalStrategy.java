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
package com.openmemind.ai.memory.core.retrieval.strategy;

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.graph.GraphExpansionResult;
import com.openmemind.ai.memory.core.retrieval.graph.GraphItemChannel;
import com.openmemind.ai.memory.core.retrieval.graph.NoOpRetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistResult;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.RawDataAggregator;
import com.openmemind.ai.memory.core.retrieval.scoring.ResultMerger;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.TimeDecay;
import com.openmemind.ai.memory.core.retrieval.temporal.DefaultTemporalConstraintExtractor;
import com.openmemind.ai.memory.core.retrieval.temporal.DefaultTemporalItemChannel;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalConstraintExtractor;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannel;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannelResult;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistResult;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistant;
import com.openmemind.ai.memory.core.retrieval.thread.NoOpMemoryThreadAssistant;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTreeExpander;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.TierResult;
import com.openmemind.ai.memory.core.retrieval.truncation.AdaptiveTruncator;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Simple retrieval strategy -- zero LLM calls, pure algorithm retrieval
 *
 * <p>Process:
 * <ol>
 *   <li>Insight vector search + Item vector search + Item BM25 -- three channels in parallel</li>
 *   <li>Item vector + BM25 -> Weighted RRF merge (vector weight 1.5, BM25 weight 1.0)</li>
 *   <li>rawDataId aggregation (optional)</li>
 *   <li>Adaptive truncation</li>
 *   <li>Build RetrievalResult (insights + items)</li>
 * </ol>
 *
 */
public class SimpleRetrievalStrategy implements RetrievalStrategy {

    private static final Logger log = LoggerFactory.getLogger(SimpleRetrievalStrategy.class);

    private final InsightTierRetriever insightRetriever;
    private final ItemTierRetriever itemRetriever;
    private final MemoryTextSearch textSearch; // nullable
    private final MemoryStore memoryStore; // nullable
    private final SimpleStrategyConfig defaultStrategyConfig;
    private final RetrievalGraphAssistant graphAssistant;
    private final MemoryThreadAssistant memoryThreadAssistant;
    private final TemporalConstraintExtractor temporalConstraintExtractor;
    private final TemporalItemChannel temporalItemChannel;
    private final GraphItemChannel
            graphItemChannel; // nullable: preserves legacy graph assistant behavior
    private final Clock clock;

    public SimpleRetrievalStrategy(
            InsightTierRetriever insightRetriever,
            ItemTierRetriever itemRetriever,
            MemoryTextSearch textSearch,
            MemoryStore memoryStore,
            SimpleStrategyConfig defaultStrategyConfig,
            RetrievalGraphAssistant graphAssistant,
            MemoryThreadAssistant memoryThreadAssistant) {
        this(
                insightRetriever,
                itemRetriever,
                textSearch,
                memoryStore,
                defaultStrategyConfig,
                graphAssistant,
                memoryThreadAssistant,
                new DefaultTemporalConstraintExtractor(),
                new DefaultTemporalItemChannel(memoryStore),
                null,
                Clock.systemDefaultZone());
    }

    public SimpleRetrievalStrategy(
            InsightTierRetriever insightRetriever,
            ItemTierRetriever itemRetriever,
            MemoryTextSearch textSearch,
            MemoryStore memoryStore,
            SimpleStrategyConfig defaultStrategyConfig,
            RetrievalGraphAssistant graphAssistant,
            MemoryThreadAssistant memoryThreadAssistant,
            TemporalConstraintExtractor temporalConstraintExtractor,
            TemporalItemChannel temporalItemChannel,
            GraphItemChannel graphItemChannel,
            Clock clock) {
        this.insightRetriever =
                Objects.requireNonNull(insightRetriever, "insightRetriever must not be null");
        this.itemRetriever =
                Objects.requireNonNull(itemRetriever, "itemRetriever must not be null");
        this.textSearch = textSearch; // nullable
        this.memoryStore = memoryStore; // nullable
        this.defaultStrategyConfig =
                defaultStrategyConfig != null
                        ? defaultStrategyConfig
                        : SimpleStrategyConfig.defaults();
        this.graphAssistant =
                graphAssistant != null ? graphAssistant : NoOpRetrievalGraphAssistant.INSTANCE;
        this.memoryThreadAssistant =
                memoryThreadAssistant != null
                        ? memoryThreadAssistant
                        : NoOpMemoryThreadAssistant.INSTANCE;
        this.temporalConstraintExtractor =
                temporalConstraintExtractor != null
                        ? temporalConstraintExtractor
                        : new DefaultTemporalConstraintExtractor();
        this.temporalItemChannel =
                temporalItemChannel != null
                        ? temporalItemChannel
                        : new DefaultTemporalItemChannel(memoryStore);
        this.graphItemChannel = graphItemChannel;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public SimpleRetrievalStrategy(
            InsightTierRetriever insightRetriever,
            ItemTierRetriever itemRetriever,
            MemoryTextSearch textSearch,
            MemoryStore memoryStore,
            RetrievalGraphAssistant graphAssistant,
            MemoryThreadAssistant memoryThreadAssistant) {
        this(
                insightRetriever,
                itemRetriever,
                textSearch,
                memoryStore,
                SimpleStrategyConfig.defaults(),
                graphAssistant,
                memoryThreadAssistant);
    }

    public SimpleRetrievalStrategy(
            InsightTierRetriever insightRetriever,
            ItemTierRetriever itemRetriever,
            MemoryTextSearch textSearch,
            MemoryStore memoryStore,
            RetrievalGraphAssistant graphAssistant) {
        this(
                insightRetriever,
                itemRetriever,
                textSearch,
                memoryStore,
                SimpleStrategyConfig.defaults(),
                graphAssistant,
                NoOpMemoryThreadAssistant.INSTANCE);
    }

    public SimpleRetrievalStrategy(
            InsightTierRetriever insightRetriever,
            ItemTierRetriever itemRetriever,
            MemoryTextSearch textSearch,
            MemoryStore memoryStore) {
        this(
                insightRetriever,
                itemRetriever,
                textSearch,
                memoryStore,
                SimpleStrategyConfig.defaults(),
                NoOpRetrievalGraphAssistant.INSTANCE,
                NoOpMemoryThreadAssistant.INSTANCE);
    }

    @Override
    public String name() {
        return RetrievalStrategies.SIMPLE;
    }

    @Override
    public Mono<RetrievalResult> retrieve(QueryContext context, RetrievalConfig config) {
        log.debug("Simple strategy started: query={}", context.searchQuery());

        return executePipeline(context, config);
    }

    private Mono<RetrievalResult> executePipeline(QueryContext context, RetrievalConfig config) {
        var simpleConfig = resolveStrategyConfig(config);

        Optional<com.openmemind.ai.memory.core.retrieval.temporal.TemporalConstraint>
                temporalConstraint =
                        temporalConstraintExtractor.extract(context.searchQuery(), clock.instant());

        // Channel 1: Insight vector search
        Mono<TierResult> insightMono =
                insightRetriever
                        .retrieve(context, config)
                        .onErrorResume(
                                e -> {
                                    log.warn("Simple: Insight retrieval failed", e);
                                    return Mono.just(TierResult.empty());
                                });

        // Channel 2: Item vector search
        Mono<TierResult> itemVectorMono =
                itemRetriever
                        .searchByVector(context, config)
                        .onErrorResume(
                                e -> {
                                    log.warn("Simple: Item vector search failed", e);
                                    return Mono.just(TierResult.empty());
                                });

        // Channel 3: Item BM25 search
        Mono<List<TextSearchResult>> bm25Mono = executeBm25(context, config, simpleConfig);

        // Channel 4: Temporal item search, activated only when a temporal constraint exists
        Mono<TemporalItemChannelResult> temporalMono =
                temporalItemChannel
                        .retrieve(
                                context,
                                config,
                                temporalConstraint,
                                simpleConfig.temporalRetrieval().toSettings())
                        .onErrorResume(
                                error -> {
                                    log.warn("Simple: Temporal item channel failed", error);
                                    return Mono.just(
                                            TemporalItemChannelResult.degraded(
                                                    simpleConfig.temporalRetrieval().enabled(),
                                                    temporalConstraint.isPresent()));
                                });

        // Independent channels in parallel
        return Mono.zip(insightMono, itemVectorMono, bm25Mono, temporalMono)
                .flatMap(
                        tuple -> {
                            TierResult insightResult = tuple.getT1();
                            TierResult itemVectorResult = tuple.getT2();
                            List<TextSearchResult> bm25Results = tuple.getT3();
                            TemporalItemChannelResult temporalResult = tuple.getT4();

                            // Item vector + BM25 + Temporal -> Weighted RRF merge
                            List<ScoredResult> mergedItems =
                                    mergeItemResults(
                                            itemVectorResult.results(),
                                            bm25Results,
                                            temporalResult.items(),
                                            config,
                                            simpleConfig);

                            // BM25-only results backfill occurredAt
                            mergedItems =
                                    RawDataAggregator.backfillOccurredAt(
                                            mergedItems, context.memoryId(), memoryStore);

                            // BM25-only results apply time decay
                            mergedItems =
                                    TimeDecay.applyToBm25Only(
                                            mergedItems, context, config.scoring());
                            mergedItems =
                                    mergedItems.stream()
                                            .sorted(
                                                    Comparator.comparingDouble(
                                                                    ScoredResult::finalScore)
                                                            .reversed())
                                            .toList();

                            var directItems = mergedItems;
                            return applyMemoryThreadAssist(
                                            context, config, simpleConfig, directItems)
                                    .flatMap(
                                            candidatePool ->
                                                    applyGraphPhase(
                                                                    context,
                                                                    config,
                                                                    simpleConfig,
                                                                    candidatePool)
                                                            .map(
                                                                    items ->
                                                                            new PipelineInputs(
                                                                                    insightResult,
                                                                                    items)));
                        })
                .map(inputs -> finalizeResult(context, config, inputs));
    }

    private Mono<List<ScoredResult>> applyGraphPhase(
            QueryContext context,
            RetrievalConfig config,
            SimpleStrategyConfig simpleConfig,
            List<ScoredResult> candidatePool) {
        if (candidatePool.isEmpty() || !simpleConfig.graphAssist().enabled()) {
            return Mono.just(candidatePool);
        }
        if (graphItemChannel == null) {
            return graphAssistant
                    .assist(context, config, simpleConfig.graphAssist(), candidatePool)
                    .onErrorResume(
                            error -> {
                                log.warn("Simple: Graph assist failed", error);
                                return Mono.just(
                                        RetrievalGraphAssistResult.directOnly(
                                                candidatePool,
                                                simpleConfig.graphAssist().enabled()));
                            })
                    .map(RetrievalGraphAssistResult::items);
        }
        return graphItemChannel
                .retrieve(context, config, simpleConfig.graphAssist(), candidatePool)
                .onErrorResume(
                        error -> {
                            log.warn("Simple: Graph item channel failed", error);
                            return Mono.just(GraphExpansionResult.degraded(true, false));
                        })
                .map(
                        result ->
                                mergeGraphChannelResults(
                                        candidatePool, result.graphItems(), config, simpleConfig));
    }

    private List<ScoredResult> mergeGraphChannelResults(
            List<ScoredResult> directItems,
            List<ScoredResult> graphItems,
            RetrievalConfig config,
            SimpleStrategyConfig simpleConfig) {
        if (graphItems == null || graphItems.isEmpty()) {
            return directItems;
        }
        return ResultMerger.merge(
                config.scoring(),
                List.of(directItems, graphItems),
                1.0d,
                simpleConfig.graphAssist().graphChannelWeight());
    }

    private SimpleStrategyConfig resolveStrategyConfig(RetrievalConfig config) {
        return config.strategyConfig() instanceof SimpleStrategyConfig simple
                ? simple
                : defaultStrategyConfig;
    }

    private Mono<List<ScoredResult>> applyMemoryThreadAssist(
            QueryContext context,
            RetrievalConfig config,
            SimpleStrategyConfig simpleConfig,
            List<ScoredResult> directWindow) {
        var settings = simpleConfig.memoryThreadAssist();
        if (directWindow.isEmpty() || !settings.enabled()) {
            return Mono.just(directWindow);
        }

        return memoryThreadAssistant
                .assist(context, config, settings, directWindow)
                .onErrorResume(
                        error -> {
                            log.warn("Simple: Memory thread assist failed", error);
                            return Mono.just(
                                    MemoryThreadAssistResult.directOnly(directWindow, true));
                        })
                .map(result -> normalizeAssistWindow(directWindow, result.items(), settings));
    }

    private List<ScoredResult> normalizeAssistWindow(
            List<ScoredResult> directWindow,
            List<ScoredResult> assistedItems,
            SimpleStrategyConfig.MemoryThreadAssistConfig settings) {
        int pinned = Math.min(settings.protectDirectTopK(), directWindow.size());
        List<ScoredResult> normalized = new ArrayList<>(directWindow.size());
        normalized.addAll(directWindow.subList(0, pinned));

        var usedIds =
                normalized.stream()
                        .map(ScoredResult::sourceId)
                        .collect(
                                java.util.stream.Collectors.toCollection(
                                        java.util.LinkedHashSet::new));
        if (assistedItems != null && pinned < assistedItems.size()) {
            for (ScoredResult candidate : assistedItems.subList(pinned, assistedItems.size())) {
                if (normalized.size() >= directWindow.size()) {
                    break;
                }
                if (usedIds.add(candidate.sourceId())) {
                    normalized.add(candidate);
                }
            }
        }
        for (ScoredResult direct : directWindow.subList(pinned, directWindow.size())) {
            if (normalized.size() >= directWindow.size()) {
                break;
            }
            if (usedIds.add(direct.sourceId())) {
                normalized.add(direct);
            }
        }
        return normalized;
    }

    private RetrievalResult finalizeResult(
            QueryContext context, RetrievalConfig config, PipelineInputs inputs) {
        List<ScoredResult> mergedItems = inputs.items();
        TierResult insightResult = inputs.insightResult();

        RawDataAggregator.AggregationResult aggregation;
        if (memoryStore != null && !mergedItems.isEmpty()) {
            aggregation = RawDataAggregator.aggregate(mergedItems, context.memoryId(), memoryStore);
            mergedItems = aggregation.items();
        } else {
            aggregation = new RawDataAggregator.AggregationResult(mergedItems, List.of());
        }

        var truncationResult = AdaptiveTruncator.truncate(mergedItems, config.tier2().truncation());

        var rawDataResults = aggregation.rawDataResults();
        int rawDataTopK = config.tier3().topK();
        if (config.tier3().enabled() && rawDataResults.size() > rawDataTopK) {
            rawDataResults = rawDataResults.subList(0, rawDataTopK);
        }

        log.debug(
                "Simple pipeline completed: insights={}, items={}/{}, rawData={}",
                insightResult.results().size(),
                truncationResult.results().size(),
                truncationResult.originalSize(),
                rawDataResults.size());

        return buildResult(
                insightResult.results(),
                truncationResult.results(),
                rawDataResults,
                insightResult.expandedInsights(),
                context);
    }

    /** BM25 keyword search */
    private Mono<List<TextSearchResult>> executeBm25(
            QueryContext context, RetrievalConfig config, SimpleStrategyConfig simpleConfig) {
        if (textSearch == null || !simpleConfig.enableKeywordSearch()) {
            return Mono.just(List.of());
        }
        return textSearch
                .search(
                        context.memoryId(),
                        context.searchQuery(),
                        config.tier2().topK(),
                        MemoryTextSearch.SearchTarget.ITEM)
                .onErrorResume(
                        e -> {
                            log.warn("Simple: BM25 search failed", e);
                            return Mono.just(List.of());
                        });
    }

    private record PipelineInputs(TierResult insightResult, List<ScoredResult> items) {}

    /** Merge Item vector results with BM25 results */
    private List<ScoredResult> mergeItemResults(
            List<ScoredResult> vectorResults,
            List<TextSearchResult> bm25Results,
            List<ScoredResult> temporalResults,
            RetrievalConfig config,
            SimpleStrategyConfig simpleConfig) {

        // BM25 results to ScoredResult
        List<ScoredResult> bm25Scored =
                bm25Results.stream()
                        .map(
                                tr ->
                                        new ScoredResult(
                                                ScoredResult.SourceType.ITEM,
                                                tr.documentId(),
                                                tr.text(),
                                                0f,
                                                ResultMerger.sigmoidNormalize(tr.score())))
                        .toList();

        List<List<ScoredResult>> rankedLists = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        if (!vectorResults.isEmpty()) {
            rankedLists.add(vectorResults);
            weights.add(config.scoring().fusion().vectorWeight());
        }
        if (!bm25Scored.isEmpty()) {
            rankedLists.add(bm25Scored);
            weights.add(config.scoring().fusion().keywordWeight());
        }
        if (temporalResults != null && !temporalResults.isEmpty()) {
            rankedLists.add(temporalResults);
            weights.add(simpleConfig.temporalRetrieval().channelWeight());
        }
        if (rankedLists.isEmpty()) {
            return List.of();
        }
        if (rankedLists.size() == 1) {
            return rankedLists.getFirst();
        }

        return ResultMerger.merge(
                config.scoring(),
                rankedLists,
                weights.stream().mapToDouble(Double::doubleValue).toArray());
    }

    /** Build result: insights and items returned separately, along with expandedInsights and rawDataResults */
    private RetrievalResult buildResult(
            List<ScoredResult> insightScored,
            List<ScoredResult> items,
            List<RetrievalResult.RawDataResult> rawDataResults,
            List<InsightTreeExpander.ExpandedInsight> expandedInsights,
            QueryContext context) {

        List<RetrievalResult.InsightResult> insights =
                new ArrayList<>(
                        insightScored.stream()
                                .map(r -> new RetrievalResult.InsightResult(r.sourceId(), r.text()))
                                .toList());

        // Append expanded insights sorted by tier: ROOT -> BRANCH -> LEAF
        if (expandedInsights != null && !expandedInsights.isEmpty()) {
            expandedInsights.stream()
                    .sorted(
                            Comparator.comparing(
                                    (InsightTreeExpander.ExpandedInsight e) ->
                                            e.tier() == null
                                                    ? 3
                                                    : switch (e.tier()) {
                                                        case ROOT -> 0;
                                                        case BRANCH -> 1;
                                                        case LEAF -> 2;
                                                    }))
                    .forEach(
                            e ->
                                    insights.add(
                                            new RetrievalResult.InsightResult(
                                                    e.id(), e.text(), e.tier())));
        }

        List<ScoredResult> sortedItems =
                items.stream()
                        .sorted((a, b) -> Double.compare(b.finalScore(), a.finalScore()))
                        .toList();

        return new RetrievalResult(
                sortedItems, insights, rawDataResults, List.of(), name(), context.searchQuery());
    }
}
