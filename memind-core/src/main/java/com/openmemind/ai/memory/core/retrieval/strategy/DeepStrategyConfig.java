package com.openmemind.ai.memory.core.retrieval.strategy;

/**
 * Deep Retrieval strategy configuration
 *
 * <p>Includes multi-query expansion, sufficiency check, and other parameters exclusive to Deep strategies. The topK field in the original DeepRetrievalConfig has been unified into
 * RetrievalConfig.TierConfig.
 *
 * @param queryExpansion  Multi-query expansion configuration
 * @param sufficiency     Sufficiency check configuration
 * @param tier2InitTopK   Tier2 initial vector retrieval candidate amount
 * @param bm25InitTopK    Tier2 initial BM25 retrieval candidate amount
 * @param minScore        Insight/Item minimum score threshold (used to build internal TierConfig)
 */
public record DeepStrategyConfig(
        QueryExpansionConfig queryExpansion,
        SufficiencyConfig sufficiency,
        int tier2InitTopK,
        int bm25InitTopK,
        double minScore)
        implements StrategyConfig {

    @Override
    public String strategyName() {
        return RetrievalStrategies.DEEP_RETRIEVAL;
    }

    public static DeepStrategyConfig defaults() {
        return new DeepStrategyConfig(
                QueryExpansionConfig.defaults(), SufficiencyConfig.defaults(), 50, 50, 0.3);
    }

    public DeepStrategyConfig withQueryExpansion(QueryExpansionConfig queryExpansion) {
        return new DeepStrategyConfig(
                queryExpansion, sufficiency, tier2InitTopK, bm25InitTopK, minScore);
    }

    public DeepStrategyConfig withSufficiency(SufficiencyConfig sufficiency) {
        return new DeepStrategyConfig(
                queryExpansion, sufficiency, tier2InitTopK, bm25InitTopK, minScore);
    }

    public DeepStrategyConfig withTier2InitTopK(int tier2InitTopK) {
        return new DeepStrategyConfig(
                queryExpansion, sufficiency, tier2InitTopK, bm25InitTopK, minScore);
    }

    public DeepStrategyConfig withBm25InitTopK(int bm25InitTopK) {
        return new DeepStrategyConfig(
                queryExpansion, sufficiency, tier2InitTopK, bm25InitTopK, minScore);
    }

    public DeepStrategyConfig withMinScore(double minScore) {
        return new DeepStrategyConfig(
                queryExpansion, sufficiency, tier2InitTopK, bm25InitTopK, minScore);
    }

    /** Multi-query expansion */
    public record QueryExpansionConfig(
            int maxExpandedQueries, double originalWeight, double expandedWeight) {
        public static QueryExpansionConfig defaults() {
            return new QueryExpansionConfig(3, 2.0, 1.0);
        }
    }

    /** Sufficiency check */
    public record SufficiencyConfig(int itemTopK) {
        public static SufficiencyConfig defaults() {
            return new SufficiencyConfig(20);
        }
    }
}
