package com.openmemind.ai.memory.core.retrieval.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StrategyConfig")
class StrategyConfigTest {

    @Nested
    @DisplayName("SimpleStrategyConfig")
    class Simple {

        @Test
        @DisplayName("defaults should enable keyword search")
        void defaults() {
            var config = SimpleStrategyConfig.defaults();
            assertThat(config.enableKeywordSearch()).isTrue();
            assertThat(config.strategyName()).isEqualTo(RetrievalStrategies.SIMPLE);
        }

        @Test
        @DisplayName("pattern matching should be available")
        void patternMatching() {
            StrategyConfig config = SimpleStrategyConfig.defaults();
            var result =
                    switch (config) {
                        case SimpleStrategyConfig s -> s.enableKeywordSearch();
                        case DeepStrategyConfig d -> false;
                    };
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("DeepStrategyConfig")
    class Deep {

        @Test
        @DisplayName("defaults should return reasonable default values")
        void defaults() {
            var config = DeepStrategyConfig.defaults();
            assertThat(config.strategyName()).isEqualTo(RetrievalStrategies.DEEP_RETRIEVAL);
            assertThat(config.queryExpansion().maxExpandedQueries()).isEqualTo(3);
            assertThat(config.sufficiency().itemTopK()).isEqualTo(20);
            assertThat(config.tier2InitTopK()).isEqualTo(50);
            assertThat(config.bm25InitTopK()).isEqualTo(50);
            assertThat(config.minScore()).isEqualTo(0.3);
        }

        @Test
        @DisplayName("with methods should only modify target fields")
        void withMethods() {
            var config =
                    DeepStrategyConfig.defaults()
                            .withQueryExpansion(
                                    new DeepStrategyConfig.QueryExpansionConfig(5, 3.0, 1.5));
            assertThat(config.queryExpansion().maxExpandedQueries()).isEqualTo(5);
            assertThat(config.sufficiency().itemTopK()).isEqualTo(20); // unchanged
        }
    }
}
