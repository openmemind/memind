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
                            .withQueryExpansion(new DeepStrategyConfig.QueryExpansionConfig(5));
            assertThat(config.queryExpansion().maxExpandedQueries()).isEqualTo(5);
            assertThat(config.sufficiency().itemTopK()).isEqualTo(20); // unchanged
        }
    }
}
