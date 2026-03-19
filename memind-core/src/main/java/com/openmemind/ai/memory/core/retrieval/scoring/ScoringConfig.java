package com.openmemind.ai.memory.core.retrieval.scoring;

/**
 * Scoring configuration: unifying the hard-coded constants of the original RetrievalConstants into configurable parameters
 *
 * <p>All parameters have reasonable default values, which can be obtained through {@link #defaults()}.
 * Advanced users can override individual parameters through with* methods.
 *
 */
public record ScoringConfig(
        FusionConfig fusion,
        TimeDecayConfig timeDecay,
        RecencyConfig recency,
        PositionBonusConfig positionBonus,
        KeywordSearchConfig keywordSearch,
        QueryWeightConfig queryWeight,
        int candidateMultiplier,
        int rerankCandidateLimit,
        int rawDataKeyInfoMaxLines,
        int insightLlmThreshold) {

    public static ScoringConfig defaults() {
        return new ScoringConfig(
                FusionConfig.defaults(),
                TimeDecayConfig.defaults(),
                RecencyConfig.defaults(),
                PositionBonusConfig.defaults(),
                KeywordSearchConfig.defaults(),
                QueryWeightConfig.defaults(),
                2,
                40,
                5,
                15);
    }

    public ScoringConfig withFusion(FusionConfig fusion) {
        return new ScoringConfig(
                fusion,
                timeDecay,
                recency,
                positionBonus,
                keywordSearch,
                queryWeight,
                candidateMultiplier,
                rerankCandidateLimit,
                rawDataKeyInfoMaxLines,
                insightLlmThreshold);
    }

    public ScoringConfig withTimeDecay(TimeDecayConfig timeDecay) {
        return new ScoringConfig(
                fusion,
                timeDecay,
                recency,
                positionBonus,
                keywordSearch,
                queryWeight,
                candidateMultiplier,
                rerankCandidateLimit,
                rawDataKeyInfoMaxLines,
                insightLlmThreshold);
    }

    public ScoringConfig withRecency(RecencyConfig recency) {
        return new ScoringConfig(
                fusion,
                timeDecay,
                recency,
                positionBonus,
                keywordSearch,
                queryWeight,
                candidateMultiplier,
                rerankCandidateLimit,
                rawDataKeyInfoMaxLines,
                insightLlmThreshold);
    }

    public ScoringConfig withPositionBonus(PositionBonusConfig positionBonus) {
        return new ScoringConfig(
                fusion,
                timeDecay,
                recency,
                positionBonus,
                keywordSearch,
                queryWeight,
                candidateMultiplier,
                rerankCandidateLimit,
                rawDataKeyInfoMaxLines,
                insightLlmThreshold);
    }

    public ScoringConfig withKeywordSearch(KeywordSearchConfig keywordSearch) {
        return new ScoringConfig(
                fusion,
                timeDecay,
                recency,
                positionBonus,
                keywordSearch,
                queryWeight,
                candidateMultiplier,
                rerankCandidateLimit,
                rawDataKeyInfoMaxLines,
                insightLlmThreshold);
    }

    public ScoringConfig withQueryWeight(QueryWeightConfig queryWeight) {
        return new ScoringConfig(
                fusion,
                timeDecay,
                recency,
                positionBonus,
                keywordSearch,
                queryWeight,
                candidateMultiplier,
                rerankCandidateLimit,
                rawDataKeyInfoMaxLines,
                insightLlmThreshold);
    }

    /** RRF fusion parameters */
    public record FusionConfig(int k, double vectorWeight, double keywordWeight) {
        public static FusionConfig defaults() {
            return new FusionConfig(60, 1.5, 1.0);
        }
    }

    /** Time decay parameters (half-life of about 30 days) */
    public record TimeDecayConfig(double rate, double floor, double outOfRangePenalty) {
        public static TimeDecayConfig defaults() {
            return new TimeDecayConfig(0.023, 0.3, 0.5);
        }
    }

    /** Global recency preference (half-life of about 365 days) */
    public record RecencyConfig(double rate, double floor) {
        public static RecencyConfig defaults() {
            return new RecencyConfig(0.0019, 0.7);
        }
    }

    /** Position bonus */
    public record PositionBonusConfig(double top1, double top3) {
        public static PositionBonusConfig defaults() {
            return new PositionBonusConfig(0.05, 0.02);
        }
    }

    /** BM25 keyword search and Strong Signal detection parameters */
    public record KeywordSearchConfig(
            int probeTopK, double strongSignalMinScore, double strongSignalMinGap) {
        public static KeywordSearchConfig defaults() {
            return new KeywordSearchConfig(10, 0.85, 0.15);
        }
    }

    /** Query weight (weight of original query vs expanded query in RRF) */
    public record QueryWeightConfig(double originalWeight, double expandedWeight) {
        public static QueryWeightConfig defaults() {
            return new QueryWeightConfig(2.0, 1.0);
        }
    }
}
