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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StrategyConfig")
class StrategyConfigTest {

    @Test
    @DisplayName("simple strategy with keyword switch preserves strategy name")
    void simpleStrategyWithKeywordSwitchPreservesStrategyName() {
        var config = SimpleStrategyConfig.defaults().withKeywordSearch(false);

        assertThat(config.strategyName()).isEqualTo(RetrievalStrategies.SIMPLE);
        assertThat(config.enableKeywordSearch()).isFalse();
    }

    @Test
    @DisplayName("deep strategy query expansion only changes target field")
    void deepStrategyQueryExpansionOnlyChangesTargetField() {
        var base = DeepStrategyConfig.defaults();
        var updated = base.withQueryExpansion(new DeepStrategyConfig.QueryExpansionConfig(5));

        assertThat(updated.queryExpansion().maxExpandedQueries()).isEqualTo(5);
        assertThat(updated.sufficiency()).isEqualTo(base.sufficiency());
        assertThat(updated.strategyName()).isEqualTo(RetrievalStrategies.DEEP_RETRIEVAL);
    }
}
