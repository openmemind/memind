package com.openmemind.ai.memory.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.retrieval.strategy.DeepStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RetrievalConfig")
class RetrievalConfigTest {

    @Nested
    @DisplayName("simple()")
    class Simple {
        @Test
        @DisplayName("should return Simple strategy configuration")
        void shouldReturnSimpleConfig() {
            var config = RetrievalConfig.simple();
            assertThat(config.strategyConfig()).isInstanceOf(SimpleStrategyConfig.class);
            assertThat(config.tier1().enabled()).isTrue();
            assertThat(config.tier2().enabled()).isTrue();
            assertThat(config.tier3().enabled()).isTrue();
            assertThat(config.tier3().topK()).isEqualTo(5);
            assertThat(config.rerank().enabled()).isFalse();
            assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(10));
            var simple = (SimpleStrategyConfig) config.strategyConfig();
            assertThat(simple.enableKeywordSearch()).isTrue();
        }
    }

    @Nested
    @DisplayName("deep() default")
    class Defaults {
        @Test
        @DisplayName("should return Deep strategy configuration")
        void shouldReturnDeepConfig() {
            var config = RetrievalConfig.deep();
            assertThat(config.strategyConfig()).isInstanceOf(DeepStrategyConfig.class);
            assertThat(config.tier1().enabled()).isTrue();
            assertThat(config.tier2().enabled()).isTrue();
            assertThat(config.tier3().enabled()).isFalse();
            assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(120));
        }
    }

    @Nested
    @DisplayName("deep() factory method")
    class DeepFactory {
        @Test
        @DisplayName("deep() without parameters should be the same as defaults()")
        void deepNoArgs() {
            var config = RetrievalConfig.deep();
            assertThat(config.strategyConfig()).isInstanceOf(DeepStrategyConfig.class);
        }

        @Test
        @DisplayName("deep(config) should use custom DeepStrategyConfig")
        void deepWithConfig() {
            var deepConfig = DeepStrategyConfig.defaults().withMinScore(0.5);
            var config = RetrievalConfig.deep(deepConfig);
            var deep = (DeepStrategyConfig) config.strategyConfig();
            assertThat(deep.minScore()).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("with* methods")
    class WithMethods {
        @Test
        @DisplayName("withTier1 should only modify tier1")
        void withTier1() {
            var config = RetrievalConfig.deep().withTier1(RetrievalConfig.TierConfig.disabled());
            assertThat(config.tier1().enabled()).isFalse();
            assertThat(config.tier2().enabled()).isTrue();
        }

        @Test
        @DisplayName("withRerank should set rerank configuration")
        void withRerank() {
            var config = RetrievalConfig.deep().withRerank(RetrievalConfig.RerankConfig.blend(20));
            assertThat(config.rerank().enabled()).isTrue();
            assertThat(config.rerank().topK()).isEqualTo(20);
        }

        @Test
        @DisplayName("strategyName should be obtained from strategyConfig")
        void strategyName() {
            assertThat(RetrievalConfig.simple().strategyName()).isEqualTo("simple");
            assertThat(RetrievalConfig.deep().strategyName()).isEqualTo("deep_retrieval");
        }
    }
}
