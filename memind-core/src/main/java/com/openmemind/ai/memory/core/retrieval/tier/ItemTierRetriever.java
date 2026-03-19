package com.openmemind.ai.memory.core.retrieval.tier;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.retrieval.ForesightFilter;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.RawDataAggregator;
import com.openmemind.ai.memory.core.retrieval.scoring.ResultMerger;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import com.openmemind.ai.memory.core.retrieval.scoring.TimeDecay;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.truncation.AdaptiveTruncator;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Tier 2: Item Retrieval
 *
 * <p>Vector search MemoryItem, output scopeHints = list of rawDataId matching items
 *
 */
public class ItemTierRetriever implements TierSearchable {

    private static final Logger log = LoggerFactory.getLogger(ItemTierRetriever.class);

    private final MemoryStore memoryStore;
    private final MemoryVector memoryVector;
    private final MemoryTextSearch textSearch;

    public ItemTierRetriever(MemoryStore memoryStore, MemoryVector memoryVector) {
        this(memoryStore, memoryVector, null);
    }

    public ItemTierRetriever(
            MemoryStore memoryStore, MemoryVector memoryVector, MemoryTextSearch textSearch) {
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore must not be null");
        this.memoryVector = Objects.requireNonNull(memoryVector, "memoryVector must not be null");
        this.textSearch = textSearch;
    }

    /** Expose textSearch for DeepRetrievalStrategy to directly call BM25 channel */
    public MemoryTextSearch textSearch() {
        return textSearch;
    }

    /**
     * Pure vector search + Salience scoring (without BM25 fusion and truncation)
     *
     * <p>For DeepRetrievalStrategy to use, BM25 probing and RRF fusion are done uniformly externally.
     *
     * @param context Query context
     * @param config  Retrieval configuration
     * @return Scoring results + scopeHints (rawDataIds)
     */
    public Mono<TierResult> searchByVector(QueryContext context, RetrievalConfig config) {
        if (!config.tier2().enabled()) {
            return Mono.just(TierResult.empty());
        }

        int topK = config.tier2().topK();
        double minScore = config.tier2().minScore();

        return memoryVector
                .search(
                        context.memoryId(),
                        context.searchQuery(),
                        topK * config.scoring().candidateMultiplier(),
                        minScore,
                        null)
                .collectList()
                .flatMap(
                        vectorResults -> {
                            List<String> vectorIds =
                                    vectorResults.stream()
                                            .map(VectorSearchResult::vectorId)
                                            .toList();
                            Map<String, MemoryItem> vectorIdToItem =
                                    memoryStore
                                            .getItemsByVectorIds(context.memoryId(), vectorIds)
                                            .stream()
                                            .collect(
                                                    Collectors.toMap(
                                                            MemoryItem::vectorId,
                                                            Function.identity(),
                                                            (a, b) -> a));

                            var filteredItems = vectorIdToItem;
                            if (context.scope() != null || context.categories() != null) {
                                filteredItems =
                                        vectorIdToItem.entrySet().stream()
                                                .filter(
                                                        e -> {
                                                            var item = e.getValue();
                                                            if (context.scope() != null
                                                                    && !context.scope()
                                                                            .equals(item.scope()))
                                                                return false;
                                                            if (context.categories() != null
                                                                    && item.category() != null
                                                                    && !context.categories()
                                                                            .contains(
                                                                                    item
                                                                                            .category()))
                                                                return false;
                                                            return true;
                                                        })
                                                .collect(
                                                        Collectors.toMap(
                                                                Map.Entry::getKey,
                                                                Map.Entry::getValue));
                            }

                            List<ScoredResult> scoredResults = new ArrayList<>();
                            List<String> rawDataIds = new ArrayList<>();

                            for (VectorSearchResult vr : vectorResults) {
                                MemoryItem item = filteredItems.get(vr.vectorId());
                                if (item == null) {
                                    continue;
                                }

                                if (!ForesightFilter.isNotExpired(item)) {
                                    continue;
                                }

                                double finalScore = vr.score();
                                finalScore *= timeDecayFactor(item, context, config.scoring());

                                if (finalScore < minScore) {
                                    continue;
                                }

                                scoredResults.add(
                                        new ScoredResult(
                                                        ScoredResult.SourceType.ITEM,
                                                        String.valueOf(item.id()),
                                                        formatItemText(item),
                                                        vr.score(),
                                                        finalScore)
                                                .withOccurredAt(item.occurredAt()));

                                if (item.rawDataId() != null) {
                                    rawDataIds.add(item.rawDataId());
                                }

                                if (scoredResults.size() >= topK) {
                                    break;
                                }
                            }

                            List<String> uniqueRawDataIds = rawDataIds.stream().distinct().toList();

                            scoredResults.sort(
                                    (a, b) -> Double.compare(b.finalScore(), a.finalScore()));

                            log.debug(
                                    "searchByVector completed: {} results, scopeHints={}",
                                    scoredResults.size(),
                                    uniqueRawDataIds.size());

                            return Mono.just(new TierResult(scoredResults, uniqueRawDataIds));
                        })
                .onErrorResume(
                        e -> {
                            log.warn("searchByVector failed, returning empty result", e);
                            return Mono.just(TierResult.empty());
                        });
    }

    /**
     * Execute Tier 2 retrieval
     *
     * @param context    Query context
     * @param config     Retrieval configuration
     * @param scopeHints Scope constraints passed from Tier 1 (empty list means no constraints)
     * @return Single layer result (scopeHints = list of rawDataId matching items)
     */
    public Mono<TierResult> retrieve(
            QueryContext context, RetrievalConfig config, List<String> scopeHints) {
        if (!config.tier2().enabled()) {
            return Mono.just(TierResult.empty());
        }

        int topK = config.tier2().topK();
        double minScore = config.tier2().minScore();

        return memoryVector
                .search(
                        context.memoryId(),
                        context.searchQuery(),
                        topK * config.scoring().candidateMultiplier(),
                        minScore,
                        null)
                .collectList()
                .flatMap(
                        vectorResults -> {
                            List<String> vectorIds =
                                    vectorResults.stream()
                                            .map(VectorSearchResult::vectorId)
                                            .toList();
                            Map<String, MemoryItem> vectorIdToItem =
                                    memoryStore
                                            .getItemsByVectorIds(context.memoryId(), vectorIds)
                                            .stream()
                                            .collect(
                                                    Collectors.toMap(
                                                            MemoryItem::vectorId,
                                                            Function.identity(),
                                                            (a, b) -> a));

                            final Map<String, MemoryItem> filteredItems;
                            if (context.scope() != null || context.categories() != null) {
                                filteredItems =
                                        vectorIdToItem.entrySet().stream()
                                                .filter(
                                                        e -> {
                                                            var item = e.getValue();
                                                            if (context.scope() != null
                                                                    && !context.scope()
                                                                            .equals(item.scope()))
                                                                return false;
                                                            if (context.categories() != null
                                                                    && item.category() != null
                                                                    && !context.categories()
                                                                            .contains(
                                                                                    item
                                                                                            .category()))
                                                                return false;
                                                            return true;
                                                        })
                                                .collect(
                                                        Collectors.toMap(
                                                                Map.Entry::getKey,
                                                                Map.Entry::getValue));
                            } else {
                                filteredItems = vectorIdToItem;
                            }

                            List<ScoredResult> scoredResults = new ArrayList<>();
                            List<String> rawDataIds = new ArrayList<>();

                            for (VectorSearchResult vr : vectorResults) {
                                MemoryItem item = filteredItems.get(vr.vectorId());
                                if (item == null) {
                                    continue;
                                }

                                if (!ForesightFilter.isNotExpired(item)) {
                                    continue;
                                }

                                double finalScore = vr.score();
                                finalScore *= timeDecayFactor(item, context, config.scoring());

                                if (finalScore < minScore) {
                                    continue;
                                }

                                scoredResults.add(
                                        new ScoredResult(
                                                        ScoredResult.SourceType.ITEM,
                                                        String.valueOf(item.id()),
                                                        formatItemText(item),
                                                        vr.score(),
                                                        finalScore)
                                                .withOccurredAt(item.occurredAt()));

                                if (item.rawDataId() != null) {
                                    rawDataIds.add(item.rawDataId());
                                }

                                if (scoredResults.size() >= topK) {
                                    break;
                                }
                            }

                            List<String> uniqueRawDataIds = rawDataIds.stream().distinct().toList();

                            // BM25 fusion
                            return ResultMerger.mergeKeywordResults(
                                            textSearch,
                                            context,
                                            config.scoring(),
                                            scoredResults,
                                            topK,
                                            MemoryTextSearch.SearchTarget.ITEM,
                                            ScoredResult.SourceType.ITEM)
                                    .map(
                                            merged -> {
                                                merged.sort(
                                                        (a, b) ->
                                                                Double.compare(
                                                                        b.finalScore(),
                                                                        a.finalScore()));

                                                // Adaptive truncation
                                                var truncationResult =
                                                        AdaptiveTruncator.truncate(
                                                                merged,
                                                                config.tier2().truncation());
                                                var finalResults = truncationResult.results();

                                                List<String> finalScopeHints;
                                                if (truncationResult.originalSize()
                                                        != finalResults.size()) {
                                                    Set<String> survivingIds =
                                                            finalResults.stream()
                                                                    .map(ScoredResult::sourceId)
                                                                    .collect(Collectors.toSet());
                                                    finalScopeHints =
                                                            filteredItems.values().stream()
                                                                    .filter(
                                                                            item ->
                                                                                    survivingIds
                                                                                            .contains(
                                                                                                    String
                                                                                                            .valueOf(
                                                                                                                    item
                                                                                                                            .id())))
                                                                    .map(MemoryItem::rawDataId)
                                                                    .filter(Objects::nonNull)
                                                                    .distinct()
                                                                    .toList();
                                                } else {
                                                    finalScopeHints = uniqueRawDataIds;
                                                }

                                                log.debug(
                                                        "Tier 2 completed: {}/{}"
                                                                + " results,"
                                                                + " scopeHints={}",
                                                        finalResults.size(),
                                                        truncationResult.originalSize(),
                                                        finalScopeHints.size());

                                                return new TierResult(
                                                        finalResults, finalScopeHints);
                                            });
                        })
                .onErrorResume(
                        e -> {
                            log.warn("Tier 2 (Item) retrieval failed, returning empty result", e);
                            return Mono.just(TierResult.empty());
                        });
    }

    /**
     * Determine if the item's occurredAt is outside the query time range
     */
    static boolean isOutOfTimeRange(MemoryItem item, QueryContext context) {
        if (!context.hasTimeRange() || item.occurredAt() == null) {
            return false;
        }
        Instant start = context.timeRangeStart();
        Instant end = context.timeRangeEnd();
        if (start != null && item.occurredAt().isBefore(start)) {
            return true;
        }
        return end != null && item.occurredAt().isAfter(end);
    }

    /**
     * Calculate time decay factor
     *
     * <p>Returns 1.0 within the query time range, outside the range decays exponentially (half-life of 30 days, lower limit of 0.3).
     * If the item has no occurredAt, returns 1.0 (no decay).
     * If the context has no time range, applies global freshness decay (half-life of 365 days, lower limit of 0.7).
     */
    static double timeDecayFactor(MemoryItem item, QueryContext context, ScoringConfig scoring) {
        return TimeDecay.factor(item.occurredAt(), context, scoring);
    }

    // ── TierSearchable Implementation ──

    /**
     * Pure vector search (TierSearchable interface method)
     *
     * <p>Build temporary RetrievalConfig delegated to existing {@link #searchByVector(QueryContext, RetrievalConfig)}.
     */
    @Override
    public Mono<List<ScoredResult>> searchByVector(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring) {
        RetrievalConfig tempConfig = buildTempConfig(tier, scoring);
        return searchByVector(context, tempConfig).map(TierResult::results);
    }

    /**
     * Pure keyword (BM25) search (TierSearchable interface method)
     *
     * <p>textSearch → sigmoid normalization → backfillOccurredAt → TimeDecay
     */
    @Override
    public Mono<List<ScoredResult>> searchByKeyword(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring) {
        if (textSearch == null || !tier.enabled()) {
            return Mono.just(List.of());
        }
        return textSearch
                .search(
                        context.memoryId(),
                        context.searchQuery(),
                        tier.topK(),
                        MemoryTextSearch.SearchTarget.ITEM)
                .map(
                        hits -> {
                            List<ScoredResult> bm25Results =
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
                                            .toList();

                            List<ScoredResult> withTime =
                                    RawDataAggregator.backfillOccurredAt(
                                            bm25Results, context.memoryId(), memoryStore);

                            List<ScoredResult> decayed =
                                    TimeDecay.applyToBm25Only(withTime, context, scoring);

                            log.debug("searchByKeyword completed: {} results", decayed.size());
                            return decayed;
                        })
                .onErrorResume(
                        e -> {
                            log.warn("searchByKeyword failed, returning empty result", e);
                            return Mono.just(List.of());
                        });
    }

    /**
     * Hybrid search (vector + BM25 RRF fusion) (TierSearchable interface method)
     *
     * <p>Execute vector search and keyword search in parallel, then RRF merge results.
     */
    @Override
    public Mono<List<ScoredResult>> searchHybrid(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring) {
        Mono<List<ScoredResult>> vectorMono =
                searchByVector(context, tier, scoring)
                        .onErrorResume(
                                e -> {
                                    log.warn("searchHybrid vector channel failed", e);
                                    return Mono.just(List.of());
                                });
        Mono<List<ScoredResult>> keywordMono =
                searchByKeyword(context, tier, scoring)
                        .onErrorResume(
                                e -> {
                                    log.warn("searchHybrid keyword channel failed", e);
                                    return Mono.just(List.of());
                                });

        return Mono.zip(vectorMono, keywordMono)
                .map(
                        tuple -> {
                            List<ScoredResult> vectorResults = tuple.getT1();
                            List<ScoredResult> keywordResults = tuple.getT2();

                            if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
                                return List.<ScoredResult>of();
                            }
                            if (vectorResults.isEmpty()) {
                                return keywordResults;
                            }
                            if (keywordResults.isEmpty()) {
                                return vectorResults;
                            }
                            return ResultMerger.merge(
                                    scoring,
                                    List.of(vectorResults, keywordResults),
                                    scoring.fusion().vectorWeight(),
                                    scoring.fusion().keywordWeight());
                        });
    }

    /** Build temporary RetrievalConfig for TierSearchable method delegation */
    private RetrievalConfig buildTempConfig(
            RetrievalConfig.TierConfig tier, ScoringConfig scoring) {
        return new RetrievalConfig(
                RetrievalConfig.TierConfig.disabled(),
                tier,
                RetrievalConfig.TierConfig.disabled(),
                RetrievalConfig.RerankConfig.disabled(),
                scoring,
                Duration.ofSeconds(30),
                false,
                SimpleStrategyConfig.defaults());
    }

    private static String formatItemText(MemoryItem item) {
        return item.content();
    }
}
