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

import com.openmemind.ai.memory.core.retrieval.graph.NoOpRetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistResult;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.RawDataAggregator;
import com.openmemind.ai.memory.core.retrieval.scoring.ResultMerger;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.TimeDecay;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTreeExpander;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierRetriever;
import com.openmemind.ai.memory.core.retrieval.tier.TierResult;
import com.openmemind.ai.memory.core.retrieval.truncation.AdaptiveTruncator;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
    private final RetrievalGraphAssistant graphAssistant;

    public SimpleRetrievalStrategy(
            InsightTierRetriever insightRetriever,
            ItemTierRetriever itemRetriever,
            MemoryTextSearch textSearch,
            MemoryStore memoryStore,
            RetrievalGraphAssistant graphAssistant) {
        this.insightRetriever =
                Objects.requireNonNull(insightRetriever, "insightRetriever must not be null");
        this.itemRetriever =
                Objects.requireNonNull(itemRetriever, "itemRetriever must not be null");
        this.textSearch = textSearch; // nullable
        this.memoryStore = memoryStore; // nullable
        this.graphAssistant =
                graphAssistant != null ? graphAssistant : NoOpRetrievalGraphAssistant.INSTANCE;
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
                NoOpRetrievalGraphAssistant.INSTANCE);
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
        var simpleConfig =
                config.strategyConfig() instanceof SimpleStrategyConfig simple
                        ? simple
                        : SimpleStrategyConfig.defaults();

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

        // Three channels in parallel
        return Mono.zip(insightMono, itemVectorMono, bm25Mono)
                .flatMap(
                        tuple -> {
                            TierResult insightResult = tuple.getT1();
                            TierResult itemVectorResult = tuple.getT2();
                            List<TextSearchResult> bm25Results = tuple.getT3();

                            // Item vector + BM25 -> Weighted RRF merge
                            List<ScoredResult> mergedItems =
                                    mergeItemResults(
                                            itemVectorResult.results(), bm25Results, config);

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
                            return graphAssistant
                                    .assist(context, config, simpleConfig, directItems)
                                    .onErrorResume(
                                            error -> {
                                                log.warn("Simple: Graph assist failed", error);
                                                return Mono.just(
                                                        RetrievalGraphAssistResult.directOnly(
                                                                directItems,
                                                                simpleConfig
                                                                        .graphAssist()
                                                                        .enabled()));
                                            })
                                    .map(
                                            result ->
                                                    new PipelineInputs(
                                                            insightResult,
                                                            result.items()));
                        })
                .map(inputs -> finalizeResult(context, config, inputs));
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
            QueryContext context,
            RetrievalConfig config,
            SimpleStrategyConfig simpleConfig) {
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
            RetrievalConfig config) {

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

        if (vectorResults.isEmpty() && bm25Scored.isEmpty()) {
            return List.of();
        }
        if (bm25Scored.isEmpty()) {
            return vectorResults;
        }
        if (vectorResults.isEmpty()) {
            return bm25Scored;
        }

        // Weighted RRF: vector 1.5, BM25 1.0
        return ResultMerger.merge(
                config.scoring(),
                List.of(vectorResults, bm25Scored),
                config.scoring().fusion().vectorWeight(),
                config.scoring().fusion().keywordWeight());
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
