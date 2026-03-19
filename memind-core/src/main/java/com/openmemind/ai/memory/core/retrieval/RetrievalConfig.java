package com.openmemind.ai.memory.core.retrieval;

import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.DeepStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.StrategyConfig;
import com.openmemind.ai.memory.core.retrieval.truncation.TruncationConfig;
import java.time.Duration;

/**
 * Retrieval configuration
 *
 * @param tier1          Tier 1 (Insight) configuration
 * @param tier2          Tier 2 (Item) configuration
 * @param tier3          Tier 3 (RawData) configuration
 * @param rerank         Rerank configuration
 * @param scoring        Scoring parameters
 * @param timeout        Timeout duration
 * @param enableCache    Whether to enable cache
 * @param strategyConfig Strategy-specific configuration
 */
public record RetrievalConfig(
        TierConfig tier1,
        TierConfig tier2,
        TierConfig tier3,
        RerankConfig rerank,
        ScoringConfig scoring,
        Duration timeout,
        boolean enableCache,
        StrategyConfig strategyConfig) {

    // ── TierConfig ──

    public record TierConfig(
            boolean enabled, int topK, double minScore, TruncationConfig truncation) {

        public static TierConfig enabled(int topK) {
            return new TierConfig(true, topK, 0.0, TruncationConfig.disabled());
        }

        public static TierConfig enabled(int topK, double minScore) {
            return new TierConfig(true, topK, minScore, TruncationConfig.disabled());
        }

        public static TierConfig enabled(int topK, double minScore, TruncationConfig truncation) {
            return new TierConfig(true, topK, minScore, truncation);
        }

        public static TierConfig disabled() {
            return new TierConfig(false, 0, 0.0, TruncationConfig.disabled());
        }
    }

    // ── RerankConfig ──

    public record RerankConfig(
            boolean enabled,
            boolean blendWithRetrieval,
            double top3Weight,
            double top10Weight,
            double otherWeight,
            int topK) {

        public static RerankConfig pure(int topK) {
            return new RerankConfig(true, false, 0, 0, 0, topK);
        }

        public static RerankConfig blend(int topK) {
            return new RerankConfig(true, true, 0.75, 0.60, 0.40, topK);
        }

        public static RerankConfig disabled() {
            return new RerankConfig(false, false, 0, 0, 0, 0);
        }
    }

    // ── Strategy enum ──

    public enum Strategy {
        SIMPLE,
        DEEP
    }

    // ── Factory methods ──

    /** Simple mode: zero LLM calls, pure algorithm retrieval, low latency */
    public static RetrievalConfig simple() {
        return simple(SimpleStrategyConfig.defaults());
    }

    /** Simple mode + custom strategy configuration */
    public static RetrievalConfig simple(SimpleStrategyConfig config) {
        return new RetrievalConfig(
                TierConfig.enabled(5, 0.3),
                TierConfig.enabled(15, 0.1, TruncationConfig.of(0.35, 0.65)),
                TierConfig.enabled(5, 0.0),
                RerankConfig.disabled(),
                ScoringConfig.defaults(),
                Duration.ofSeconds(10),
                true,
                config);
    }

    /** Deep mode + default strategy configuration */
    public static RetrievalConfig deep() {
        return deep(DeepStrategyConfig.defaults());
    }

    /** Deep mode + custom strategy configuration */
    public static RetrievalConfig deep(DeepStrategyConfig config) {
        return new RetrievalConfig(
                TierConfig.enabled(5, 0.3),
                TierConfig.enabled(50, 0.2),
                TierConfig.disabled(),
                RerankConfig.pure(10),
                ScoringConfig.defaults(),
                Duration.ofSeconds(120),
                true,
                config);
    }

    // ── Convenient methods ──

    /** Get strategy name from strategyConfig */
    public String strategyName() {
        return strategyConfig.strategyName();
    }

    // ── with* methods ──

    public RetrievalConfig withTier1(TierConfig tier1) {
        return new RetrievalConfig(
                tier1, tier2, tier3, rerank, scoring, timeout, enableCache, strategyConfig);
    }

    public RetrievalConfig withTier2(TierConfig tier2) {
        return new RetrievalConfig(
                tier1, tier2, tier3, rerank, scoring, timeout, enableCache, strategyConfig);
    }

    public RetrievalConfig withTier3(TierConfig tier3) {
        return new RetrievalConfig(
                tier1, tier2, tier3, rerank, scoring, timeout, enableCache, strategyConfig);
    }

    public RetrievalConfig withRerank(RerankConfig rerank) {
        return new RetrievalConfig(
                tier1, tier2, tier3, rerank, scoring, timeout, enableCache, strategyConfig);
    }

    public RetrievalConfig withScoring(ScoringConfig scoring) {
        return new RetrievalConfig(
                tier1, tier2, tier3, rerank, scoring, timeout, enableCache, strategyConfig);
    }

    public RetrievalConfig withTimeout(Duration timeout) {
        return new RetrievalConfig(
                tier1, tier2, tier3, rerank, scoring, timeout, enableCache, strategyConfig);
    }

    public RetrievalConfig withoutCache() {
        return new RetrievalConfig(
                tier1, tier2, tier3, rerank, scoring, timeout, false, strategyConfig);
    }

    public RetrievalConfig withStrategyConfig(StrategyConfig strategyConfig) {
        return new RetrievalConfig(
                tier1, tier2, tier3, rerank, scoring, timeout, enableCache, strategyConfig);
    }
}
