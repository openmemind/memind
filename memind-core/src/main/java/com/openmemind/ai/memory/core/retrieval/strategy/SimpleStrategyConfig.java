package com.openmemind.ai.memory.core.retrieval.strategy;

/**
 * Simple Strategy Configuration
 *
 * @param enableKeywordSearch Whether to enable BM25 keyword search (only effective at the Item level)
 */
public record SimpleStrategyConfig(boolean enableKeywordSearch) implements StrategyConfig {

    @Override
    public String strategyName() {
        return RetrievalStrategies.SIMPLE;
    }

    public static SimpleStrategyConfig defaults() {
        return new SimpleStrategyConfig(true);
    }

    public SimpleStrategyConfig withKeywordSearch(boolean enabled) {
        return new SimpleStrategyConfig(enabled);
    }
}
