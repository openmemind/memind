package com.openmemind.ai.memory.core.retrieval.tier;

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Tier independent retrieval interface
 *
 * <p>Provides a unified independent retrieval method for ItemTierRetriever and RawDataTierRetriever,
 * supporting three modes: vector search, keyword search, and hybrid search.
 *
 * <p>Returns {@code List<ScoredResult>} instead of TierResult, because scopeHints
 * is a concept within the strategy's internal pipeline, which does not need to be exposed by the public retrieval API.
 *
 */
public interface TierSearchable {

    /**
     * Pure vector search
     *
     * @param context query context
     * @param tier    current layer Tier configuration (topK, minScore, enabled, truncation)
     * @param scoring scoring parameters (RRF weight, time decay, position bonus)
     * @return list of scored results
     */
    Mono<List<ScoredResult>> searchByVector(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring);

    /**
     * Pure keyword (BM25) search
     *
     * @param context query context
     * @param tier    current layer Tier configuration
     * @param scoring scoring parameters
     * @return list of scored results
     */
    Mono<List<ScoredResult>> searchByKeyword(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring);

    /**
     * Hybrid search (vector + BM25 RRF fusion)
     *
     * @param context query context
     * @param tier    current layer Tier configuration
     * @param scoring scoring parameters (vectorWeight / keywordWeight from scoring.fusion())
     * @return list of scored results
     */
    Mono<List<ScoredResult>> searchHybrid(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring);
}
