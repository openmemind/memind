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
package com.openmemind.ai.memory.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.retrieval.strategy.DeepStrategyConfig;
import com.openmemind.ai.memory.core.retrieval.strategy.SimpleStrategyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RetrievalConfig")
class RetrievalConfigTest {

    @Test
    @DisplayName("simple factory uses passed strategy config and disables rerank")
    void simpleFactoryUsesPassedStrategyConfigAndDisablesRerank() {
        var config = RetrievalConfig.simple(new SimpleStrategyConfig(false));

        assertThat(config.strategyName()).isEqualTo("simple");
        assertThat(((SimpleStrategyConfig) config.strategyConfig()).enableKeywordSearch())
                .isFalse();
        assertThat(config.rerank().enabled()).isFalse();
        assertThat(config.tier1().enabled()).isTrue();
    }

    @Test
    @DisplayName("deep factory keeps deep strategy semantics")
    void deepFactoryKeepsDeepStrategySemantics() {
        var config = RetrievalConfig.deep();

        assertThat(config.strategyName()).isEqualTo("deep_retrieval");
        assertThat(config.strategyConfig()).isInstanceOf(DeepStrategyConfig.class);
        assertThat(config.rerank().enabled()).isTrue();
        assertThat(config.tier3().enabled()).isFalse();
    }

    @Test
    @DisplayName("withTier1 only changes tier1")
    void withTier1OnlyChangesTier1() {
        var base = RetrievalConfig.deep();
        var updated = base.withTier1(RetrievalConfig.TierConfig.disabled());

        assertThat(updated.tier1().enabled()).isFalse();
        assertThat(updated.tier2()).isEqualTo(base.tier2());
        assertThat(updated.rerank()).isEqualTo(base.rerank());
    }
}
