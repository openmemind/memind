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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.llm.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.deep.ExpandedQuery;
import com.openmemind.ai.memory.core.retrieval.deep.TypedQueryExpander;
import com.openmemind.ai.memory.core.retrieval.graph.NoOpRetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistResult;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphAssistant;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.DefaultRetrievalResultMerger;
import com.openmemind.ai.memory.core.retrieval.scoring.RawDataAggregator;
import com.openmemind.ai.memory.core.retrieval.scoring.ResultMerger;
import com.openmemind.ai.memory.core.retrieval.scoring.RetrievalResultMerger;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.TimeDecay;
import com.openmemind.ai.memory.core.retrieval.sufficiency.SufficiencyGate;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistResult;
import com.openmemind.ai.memory.core.retrieval.thread.MemoryThreadAssistant;
import com.openmemind.ai.memory.core.retrieval.thread.NoOpMemoryThreadAssistant;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierSearch;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTreeExpander;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierSearch;
import com.openmemind.ai.memory.core.retrieval.tier.TierResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Deep retrieval strategy (two-layer drilling + BM25 probing + type-based routing + single-level RRF +
 * integrated Rerank + rawDataId aggregation)
 *
 * <p>Process:
 * <ol>
 *   <li>Tier1 (Insight) + Tier2 initial retrieval (BM25 + vector search) start in parallel</li>
 *   <li>Sufficiency check (insight + items merge input) → if sufficient, return directly (with evidences)</li>
 *   <li>If insufficient, continue Tier2 pipeline:
 *       <ul>
 *         <li>Strong signal → skip query expansion, directly merge RRF</li>
 *         <li>Non-strong signal → TypedQueryExpander expansion, route by type (LEX→BM25, VEC/HYDE→vector)</li>
 *         <li>Single-level Weighted RRF merges all channels</li>
 *         <li>rawDataId aggregation + Rerank</li>
 *       </ul>
 *   </li>
 *   <li>Return three layers of results: insights + items (original text) + rawData (caption)</li>
 * </ol>
 *
 */
public class DeepRetrievalStrategy implements RetrievalStrategy {

    private static final Logger log = LoggerFactory.getLogger(DeepRetrievalStrategy.class);

    private final InsightTierSearch insightRetriever;
    private final ItemTierSearch itemRetriever;
    private final SufficiencyGate sufficiencyGate;
    private final TypedQueryExpander typedQueryExpander;
    private final Reranker reranker;
    private final MemoryTextSearch textSearch;
    private final MemoryStore memoryStore; // nullable
    private final DeepStrategyConfig defaultStrategyConfig;
    private final RetrievalGraphAssistant graphAssistant;
    private final MemoryThreadAssistant memoryThreadAssistant;
    private final RetrievalResultMerger resultMerger;

    /** Tier2 initial retrieval result */
    private record Tier2InitResult(
            List<TextSearchResult> bm25ProbeResults, TierResult vectorResult) {}

    public DeepRetrievalStrategy(
            InsightTierSearch insightRetriever,
            ItemTierSearch itemRetriever,
            SufficiencyGate sufficiencyGate,
            TypedQueryExpander typedQueryExpander,
            Reranker reranker,
            MemoryStore memoryStore) {
        this(
                insightRetriever,
                itemRetriever,
                sufficiencyGate,
                typedQueryExpander,
                reranker,
                memoryStore,
                DeepStrategyConfig.defaults(),
                NoOpRetrievalGraphAssistant.INSTANCE,
                NoOpMemoryThreadAssistant.INSTANCE);
    }

    public DeepRetrievalStrategy(
            InsightTierSearch insightRetriever,
            ItemTierSearch itemRetriever,
            SufficiencyGate sufficiencyGate,
            TypedQueryExpander typedQueryExpander,
            Reranker reranker,
            MemoryStore memoryStore,
            RetrievalGraphAssistant graphAssistant,
            MemoryThreadAssistant memoryThreadAssistant) {
        this(
                insightRetriever,
                itemRetriever,
                sufficiencyGate,
                typedQueryExpander,
                reranker,
                memoryStore,
                DeepStrategyConfig.defaults(),
                graphAssistant,
                memoryThreadAssistant,
                DefaultRetrievalResultMerger.INSTANCE);
    }

    public DeepRetrievalStrategy(
            InsightTierSearch insightRetriever,
            ItemTierSearch itemRetriever,
            SufficiencyGate sufficiencyGate,
            TypedQueryExpander typedQueryExpander,
            Reranker reranker,
            MemoryStore memoryStore,
            RetrievalGraphAssistant graphAssistant) {
        this(
                insightRetriever,
                itemRetriever,
                sufficiencyGate,
                typedQueryExpander,
                reranker,
                memoryStore,
                DeepStrategyConfig.defaults(),
                graphAssistant,
                NoOpMemoryThreadAssistant.INSTANCE);
    }

    public DeepRetrievalStrategy(
            InsightTierSearch insightRetriever,
            ItemTierSearch itemRetriever,
            SufficiencyGate sufficiencyGate,
            TypedQueryExpander typedQueryExpander,
            Reranker reranker,
            MemoryStore memoryStore,
            DeepStrategyConfig defaultStrategyConfig,
            RetrievalGraphAssistant graphAssistant,
            MemoryThreadAssistant memoryThreadAssistant) {
        this(
                insightRetriever,
                itemRetriever,
                sufficiencyGate,
                typedQueryExpander,
                reranker,
                memoryStore,
                defaultStrategyConfig,
                graphAssistant,
                memoryThreadAssistant,
                DefaultRetrievalResultMerger.INSTANCE);
    }

    public DeepRetrievalStrategy(
            InsightTierSearch insightRetriever,
            ItemTierSearch itemRetriever,
            SufficiencyGate sufficiencyGate,
            TypedQueryExpander typedQueryExpander,
            Reranker reranker,
            MemoryStore memoryStore,
            DeepStrategyConfig defaultStrategyConfig,
            RetrievalGraphAssistant graphAssistant,
            MemoryThreadAssistant memoryThreadAssistant,
            RetrievalResultMerger resultMerger) {
        this.insightRetriever =
                Objects.requireNonNull(insightRetriever, "insightRetriever must not be null");
        this.itemRetriever =
                Objects.requireNonNull(itemRetriever, "itemRetriever must not be null");
        this.sufficiencyGate =
                Objects.requireNonNull(sufficiencyGate, "sufficiencyGate must not be null");
        this.typedQueryExpander =
                Objects.requireNonNull(typedQueryExpander, "typedQueryExpander must not be null");
        this.reranker = reranker; // nullable
        this.textSearch = itemRetriever.textSearch(); // nullable
        this.memoryStore = memoryStore; // nullable
        this.defaultStrategyConfig =
                defaultStrategyConfig != null
                        ? defaultStrategyConfig
                        : DeepStrategyConfig.defaults();
        this.graphAssistant =
                graphAssistant != null ? graphAssistant : NoOpRetrievalGraphAssistant.INSTANCE;
        this.memoryThreadAssistant =
                memoryThreadAssistant != null
                        ? memoryThreadAssistant
                        : NoOpMemoryThreadAssistant.INSTANCE;
        this.resultMerger =
                resultMerger != null ? resultMerger : DefaultRetrievalResultMerger.INSTANCE;
    }

    @Override
    public String name() {
        return RetrievalStrategies.DEEP_RETRIEVAL;
    }

    @Override
    public Mono<RetrievalResult> retrieve(QueryContext context, RetrievalConfig config) {
        log.debug("DeepRetrieval strategy started: query={}", context.searchQuery());

        return executePipeline(context, config);
    }

    private DeepStrategyConfig deepConfig(RetrievalConfig config) {
        return config.strategyConfig() instanceof DeepStrategyConfig deep
                ? deep
                : defaultStrategyConfig;
    }

    private Mono<RetrievalResult> executePipeline(QueryContext context, RetrievalConfig config) {
        RetrievalConfig tier2InitConfig = buildTier2InitConfig(config);

        // Parallel: Tier1 + Tier2 initial retrieval (BM25 probe + vector search)
        Mono<TierResult> tier1Mono =
                executeTier1(context, config)
                        .onErrorResume(
                                e -> {
                                    log.warn("DeepRetrieval Tier1 failed", e);
                                    return Mono.just(TierResult.empty());
                                });
        Mono<Tier2InitResult> tier2InitMono = executeTier2Init(context, tier2InitConfig);

        return Mono.zip(tier1Mono, tier2InitMono)
                .flatMap(
                        tuple -> {
                            TierResult tier1Result = tuple.getT1();
                            Tier2InitResult tier2Init = tuple.getT2();
                            var insightResults = tier1Result.results();
                            var expandedInsights = tier1Result.expandedInsights();
                            var itemInitResults = tier2Init.vectorResult().results();

                            if (insightResults.isEmpty() && itemInitResults.isEmpty()) {
                                return Mono.just(
                                        RetrievalResult.empty(name(), context.searchQuery()));
                            }

                            // Build bounded sufficiency input (asynchronous, may trigger caption
                            // rerank)
                            return buildSufficiencyInput(
                                            insightResults, itemInitResults, context, config)
                                    .flatMap(
                                            boundedInput ->
                                                    sufficiencyGate.check(context, boundedInput))
                                    .flatMap(
                                            sufficiency -> {
                                                if (sufficiency.sufficient()) {
                                                    log.debug(
                                                            "Sufficient, return insights + items"
                                                                    + " directly");
                                                    var aggregation =
                                                            aggregateItems(
                                                                    itemInitResults, context);
                                                    return Mono.just(
                                                            buildResultWithEvidences(
                                                                    insightResults,
                                                                    aggregation,
                                                                    sufficiency.evidences(),
                                                                    expandedInsights,
                                                                    context));
                                                }
                                                // Insufficient → continue Tier2 follow-up
                                                return continueTier2Pipeline(
                                                        context,
                                                        config,
                                                        insightResults,
                                                        sufficiency.gaps(),
                                                        sufficiency.keyInformation(),
                                                        tier2Init,
                                                        expandedInsights);
                                            });
                        })
                .onErrorResume(
                        e -> {
                            log.warn("DeepRetrieval pipeline failed", e);
                            return Mono.just(RetrievalResult.empty(name(), context.searchQuery()));
                        });
    }

    /** Tier1: RAG vector retrieval */
    private Mono<TierResult> executeTier1(QueryContext context, RetrievalConfig config) {
        if (!config.tier1().enabled()) {
            return Mono.just(TierResult.empty());
        }
        return insightRetriever.retrieve(context, config);
    }

    /** Tier2 initial retrieval: BM25 probing + vector search in parallel */
    private Mono<Tier2InitResult> executeTier2Init(
            QueryContext context, RetrievalConfig tier2Config) {
        Mono<List<TextSearchResult>> bm25ProbeMono = executeBm25Probe(context, tier2Config);
        Mono<TierResult> vectorMono =
                itemRetriever
                        .searchByVector(context, tier2Config)
                        .onErrorResume(
                                e -> {
                                    log.warn("Tier2 init vector search failed", e);
                                    return Mono.just(TierResult.empty());
                                });
        return Mono.zip(bm25ProbeMono, vectorMono)
                .map(t -> new Tier2InitResult(t.getT1(), t.getT2()));
    }

    /**
     * Continue Tier2 pipeline: determine strong signal → expand channel routing → single-level RRF
     * → rawDataId aggregation → Rerank → second round
     */
    private Mono<RetrievalResult> continueTier2Pipeline(
            QueryContext context,
            RetrievalConfig config,
            List<ScoredResult> insightResults,
            List<String> gaps,
            List<String> keyInformation,
            Tier2InitResult tier2Init,
            List<InsightTreeExpander.ExpandedInsight> expandedInsights) {

        List<TextSearchResult> bm25ProbeResults = tier2Init.bm25ProbeResults();
        TierResult vectorResult = tier2Init.vectorResult();
        boolean strongSignal = ResultMerger.isStrongSignal(config.scoring(), bm25ProbeResults);

        if (strongSignal) {
            log.debug("BM25 strong signal, skip query expansion");
            return mergeAndFinalize(
                    context,
                    config,
                    insightResults,
                    bm25ProbeResults,
                    vectorResult,
                    List.of(),
                    expandedInsights);
        }

        var deep = deepConfig(config);
        // Non-strong signal: expand and route (pass in keyInformation)
        return typedQueryExpander
                .expand(
                        context.originalQuery(),
                        gaps,
                        keyInformation,
                        context.conversationHistory(),
                        deep.queryExpansion().maxExpandedQueries())
                .flatMap(
                        expandedQueries ->
                                executeExpandedChannels(expandedQueries, context, config)
                                        .flatMap(
                                                expandedChannelResults ->
                                                        mergeAndFinalize(
                                                                context,
                                                                config,
                                                                insightResults,
                                                                bm25ProbeResults,
                                                                vectorResult,
                                                                expandedChannelResults,
                                                                expandedInsights)))
                .onErrorResume(
                        e -> {
                            log.warn("Tier2 expansion process failed", e);
                            return mergeAndFinalize(
                                    context,
                                    config,
                                    insightResults,
                                    bm25ProbeResults,
                                    vectorResult,
                                    List.of(),
                                    expandedInsights);
                        });
    }

    /** BM25 initial retrieval (strong signal can skip expansion) */
    private Mono<List<TextSearchResult>> executeBm25Probe(
            QueryContext context, RetrievalConfig config) {
        if (textSearch == null) {
            return Mono.just(List.of());
        }
        return textSearch
                .search(
                        context.memoryId(),
                        context.searchQuery(),
                        deepConfig(config).bm25InitTopK(),
                        MemoryTextSearch.SearchTarget.ITEM)
                .onErrorResume(
                        e -> {
                            log.warn("BM25 probing failed", e);
                            return Mono.just(List.of());
                        });
    }

    /** Execute all expanded query channels in parallel, route by type */
    private Mono<List<List<ScoredResult>>> executeExpandedChannels(
            List<ExpandedQuery> expandedQueries, QueryContext baseContext, RetrievalConfig config) {
        if (expandedQueries.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(expandedQueries)
                .flatMap(
                        eq ->
                                executeChannel(eq, baseContext, config)
                                        .onErrorResume(
                                                e -> {
                                                    log.warn(
                                                            "Expanded channel search failed:"
                                                                    + " type={}, text={}",
                                                            eq.queryType(),
                                                            eq.text(),
                                                            e);
                                                    return Mono.just(List.of());
                                                }),
                        deepConfig(config).queryExpansion().maxExpandedQueries())
                .collectList();
    }

    /** Route to the corresponding retrieval channel by query type */
    private Mono<List<ScoredResult>> executeChannel(
            ExpandedQuery eq, QueryContext baseContext, RetrievalConfig config) {
        return switch (eq.queryType()) {
            case LEX ->
                    executeBm25Channel(baseContext.memoryId(), eq.text(), config.tier2().topK());
            case VEC, HYDE -> {
                var ctx =
                        new QueryContext(
                                baseContext.memoryId(),
                                baseContext.originalQuery(),
                                eq.text(),
                                baseContext.conversationHistory(),
                                baseContext.metadata(),
                                null,
                                null);
                yield itemRetriever.searchByVector(ctx, config).map(TierResult::results);
            }
        };
    }

    /** BM25 keyword search channel */
    private Mono<List<ScoredResult>> executeBm25Channel(MemoryId memoryId, String query, int topK) {
        if (textSearch == null) {
            return Mono.just(List.of());
        }
        return textSearch
                .search(memoryId, query, topK, MemoryTextSearch.SearchTarget.ITEM)
                .map(
                        hits ->
                                hits.stream()
                                        .map(
                                                tr ->
                                                        new ScoredResult(
                                                                ScoredResult.SourceType.ITEM,
                                                                tr.documentId(),
                                                                tr.text(),
                                                                0f,
                                                                ResultMerger.sigmoidNormalize(
                                                                        tr.score())))
                                        .toList())
                .onErrorResume(
                        e -> {
                            log.warn("LEX BM25 channel failed", e);
                            return Mono.just(List.of());
                        });
    }

    /** Merge all channel results → graph assist → Rerank → rawDataId aggregation */
    private Mono<RetrievalResult> mergeAndFinalize(
            QueryContext context,
            RetrievalConfig baseConfig,
            List<ScoredResult> insightResults,
            List<TextSearchResult> bm25ProbeResults,
            TierResult vectorResult,
            List<List<ScoredResult>> expandedChannelResults,
            List<InsightTreeExpander.ExpandedInsight> expandedInsights) {

        var queryWeight = baseConfig.scoring().queryWeight();

        // Build ranked lists and weights
        List<List<ScoredResult>> rankedLists = new ArrayList<>();
        List<Double> weightsList = new ArrayList<>();

        // Channel 0: BM25 probing results
        if (!bm25ProbeResults.isEmpty()) {
            List<ScoredResult> bm25Scored =
                    bm25ProbeResults.stream()
                            .map(
                                    tr ->
                                            new ScoredResult(
                                                    ScoredResult.SourceType.ITEM,
                                                    tr.documentId(),
                                                    tr.text(),
                                                    0f,
                                                    ResultMerger.sigmoidNormalize(tr.score())))
                            .toList();
            rankedLists.add(bm25Scored);
            weightsList.add(queryWeight.originalWeight());
        }

        // Channel 1: Vector search results
        if (!vectorResult.results().isEmpty()) {
            rankedLists.add(vectorResult.results());
            weightsList.add(queryWeight.originalWeight());
        }

        // Channels 2-N: Expanded query results
        for (List<ScoredResult> channelResults : expandedChannelResults) {
            if (!channelResults.isEmpty()) {
                rankedLists.add(channelResults);
                weightsList.add(queryWeight.expandedWeight());
            }
        }

        return mergeRankedLists(baseConfig, rankedLists, weightsList)
                .flatMap(
                        merged -> {
                            // BM25-only results backfill occurredAt
                            merged =
                                    RawDataAggregator.backfillOccurredAt(
                                            merged, context.memoryId(), memoryStore);

                            // BM25-only results apply time decay
                            merged =
                                    TimeDecay.applyToBm25Only(
                                            merged, context, baseConfig.scoring());
                            merged =
                                    merged.stream()
                                            .sorted(
                                                    Comparator.comparingDouble(
                                                                    ScoredResult::finalScore)
                                                            .reversed())
                                            .toList();

                            int directWindowSize = baseConfig.tier2().topK();
                            List<ScoredResult> directWindow =
                                    merged.size() > directWindowSize
                                            ? merged.subList(0, directWindowSize)
                                            : merged;

                            log.debug("RRF merged truncation: {} candidates", directWindow.size());

                            return applyMemoryThreadAssist(context, baseConfig, directWindow);
                        })
                .flatMap(candidatePool -> applyGraphAssist(context, baseConfig, candidatePool))
                .flatMap(
                        candidatePool -> {
                            Mono<List<ScoredResult>> rerankedMono;
                            if (reranker != null
                                    && baseConfig.rerank().enabled()
                                    && !candidatePool.isEmpty()) {
                                rerankedMono =
                                        reranker.rerank(
                                                context.searchQuery(),
                                                candidatePool,
                                                baseConfig.rerank().topK());
                            } else {
                                rerankedMono = Mono.just(candidatePool);
                            }

                            return rerankedMono.map(
                                    rerankedItems -> {
                                        var aggregation = aggregateItems(rerankedItems, context);
                                        return buildResult(
                                                insightResults,
                                                aggregation,
                                                expandedInsights,
                                                context);
                                    });
                        });
    }

    private Mono<List<ScoredResult>> mergeRankedLists(
            RetrievalConfig baseConfig,
            List<List<ScoredResult>> rankedLists,
            List<Double> weightsList) {
        if (rankedLists.isEmpty()) {
            return Mono.just(List.of());
        }
        if (rankedLists.size() == 1) {
            return Mono.just(rankedLists.getFirst());
        }
        double[] weights = weightsList.stream().mapToDouble(Double::doubleValue).toArray();
        return resultMerger.merge(baseConfig.scoring(), rankedLists, weights);
    }

    /**
     * Build bounded sufficiency check input: top-N insight + top-sufficiencyItemTopK item + top-rawDataTopK caption
     */
    private Mono<List<ScoredResult>> buildSufficiencyInput(
            List<ScoredResult> insightResults,
            List<ScoredResult> itemResults,
            QueryContext context,
            RetrievalConfig config) {

        var deep = deepConfig(config);

        // 1. Aggregate to get caption (reuse existing aggregateItems)
        var aggregation = aggregateItems(itemResults, context);

        // 2. Items take top sufficiencyItemTopK by finalScore
        var topItems =
                aggregation.items().stream()
                        .sorted(Comparator.comparingDouble(ScoredResult::finalScore).reversed())
                        .limit(deep.sufficiency().itemTopK())
                        .toList();

        // 3. Caption → ScoredResult(RAW_DATA), already sorted by maxScore descending
        var captionResults =
                aggregation.rawDataResults().stream()
                        .map(
                                rd ->
                                        new ScoredResult(
                                                ScoredResult.SourceType.RAW_DATA,
                                                rd.rawDataId(),
                                                rd.caption(),
                                                0f,
                                                rd.maxScore()))
                        .toList();

        // 4. If caption > rawDataTopK then rerank, otherwise truncate directly
        int captionLimit = config.tier3().enabled() ? config.tier3().topK() : 5;
        Mono<List<ScoredResult>> captionsMono;
        if (captionResults.size() > captionLimit && reranker != null) {
            captionsMono =
                    reranker.rerank(context.searchQuery(), captionResults, captionLimit)
                            .onErrorResume(
                                    e -> {
                                        log.warn("Caption rerank failed, truncate by score", e);
                                        return Mono.just(captionResults.subList(0, captionLimit));
                                    });
        } else {
            captionsMono =
                    Mono.just(
                            captionResults.size() > captionLimit
                                    ? captionResults.subList(0, captionLimit)
                                    : captionResults);
        }

        // 5. Merge three layers: insight + item + caption
        return captionsMono.map(
                captions -> {
                    var combined =
                            new ArrayList<ScoredResult>(
                                    insightResults.size() + topItems.size() + captions.size());
                    combined.addAll(insightResults);
                    combined.addAll(topItems);
                    combined.addAll(captions);
                    return List.copyOf(combined);
                });
    }

    /** Perform rawDataId aggregation on items */
    private RawDataAggregator.AggregationResult aggregateItems(
            List<ScoredResult> items, QueryContext context) {
        if (memoryStore != null && !items.isEmpty()) {
            return RawDataAggregator.aggregate(items, context.memoryId(), memoryStore);
        }
        return new RawDataAggregator.AggregationResult(items, List.of());
    }

    private Mono<List<ScoredResult>> applyMemoryThreadAssist(
            QueryContext context, RetrievalConfig config, List<ScoredResult> directWindow) {
        var settings = deepConfig(config).memoryThreadAssist();
        if (directWindow.isEmpty() || !settings.enabled()) {
            return Mono.just(directWindow);
        }

        return memoryThreadAssistant
                .assist(context, config, settings, directWindow)
                .onErrorResume(
                        error -> {
                            log.warn("DeepRetrieval memory thread assist failed", error);
                            return Mono.just(
                                    MemoryThreadAssistResult.directOnly(directWindow, true));
                        })
                .map(result -> normalizeAssistWindow(directWindow, result.items(), settings));
    }

    private Mono<List<ScoredResult>> applyGraphAssist(
            QueryContext context, RetrievalConfig config, List<ScoredResult> directWindow) {
        var graphSettings = deepConfig(config).graphAssist();
        if (directWindow.isEmpty() || !graphSettings.enabled()) {
            return Mono.just(directWindow);
        }

        return graphAssistant
                .assist(context, config, graphSettings, directWindow)
                .onErrorResume(
                        error -> {
                            log.warn("DeepRetrieval graph assist failed", error);
                            return Mono.just(
                                    RetrievalGraphAssistResult.directOnly(
                                            directWindow, graphSettings.enabled()));
                        })
                .map(result -> boundGraphWindow(result.items(), directWindow.size()));
    }

    private List<ScoredResult> normalizeAssistWindow(
            List<ScoredResult> directWindow,
            List<ScoredResult> assistedItems,
            DeepStrategyConfig.MemoryThreadAssistConfig settings) {
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

    private List<ScoredResult> boundGraphWindow(
            List<ScoredResult> graphEnrichedItems, int directWindowSize) {
        if (graphEnrichedItems.size() <= directWindowSize) {
            return graphEnrichedItems;
        }
        return graphEnrichedItems.subList(0, directWindowSize);
    }

    /** Build result (without evidences) */
    private RetrievalResult buildResult(
            List<ScoredResult> insightScored,
            RawDataAggregator.AggregationResult aggregation,
            List<InsightTreeExpander.ExpandedInsight> expandedInsights,
            QueryContext context) {
        return buildResultWithEvidences(
                insightScored, aggregation, List.of(), expandedInsights, context);
    }

    /** Build three-layer result: insights + items (original text) + rawData (caption), with evidences */
    private RetrievalResult buildResultWithEvidences(
            List<ScoredResult> insightScored,
            RawDataAggregator.AggregationResult aggregation,
            List<String> evidences,
            List<InsightTreeExpander.ExpandedInsight> expandedInsights,
            QueryContext context) {
        List<RetrievalResult.InsightResult> insights =
                new ArrayList<>(
                        insightScored.stream()
                                .map(r -> new RetrievalResult.InsightResult(r.sourceId(), r.text()))
                                .toList());

        // Append expanded insights sorted by tier: ROOT → BRANCH → LEAF
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
                aggregation.items().stream()
                        .sorted((a, b) -> Double.compare(b.finalScore(), a.finalScore()))
                        .toList();

        return new RetrievalResult(
                sortedItems,
                insights,
                aggregation.rawDataResults(),
                evidences,
                name(),
                context.searchQuery());
    }

    /** Tier2 initial retrieval uses a larger candidate pool (tier2InitTopK), expanded channels still use tier2.topK */
    private RetrievalConfig buildTier2InitConfig(RetrievalConfig config) {
        var deep = deepConfig(config);
        return config.withTier2(
                RetrievalConfig.TierConfig.enabled(deep.tier2InitTopK(), deep.minScore()));
    }

    @Override
    public void onDataChanged(MemoryId memoryId) {
        insightRetriever.invalidateCache(memoryId);
    }
}
