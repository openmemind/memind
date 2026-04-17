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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.utils.JsonUtils;
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

    @Test
    @DisplayName("legacy deep strategy json without graph assist still deserializes")
    void legacyDeepStrategyJsonWithoutGraphAssistStillDeserializes() throws Exception {
        var mapper = JsonUtils.newMapper();

        StrategyConfig config =
                mapper.readValue(
                        """
                        {
                          "type":"deep",
                          "queryExpansion":{"maxExpandedQueries":4},
                          "sufficiency":{"itemTopK":12},
                          "tier2InitTopK":40,
                          "bm25InitTopK":30,
                          "minScore":0.25
                        }
                        """,
                        StrategyConfig.class);

        assertThat(config).isInstanceOf(DeepStrategyConfig.class);
        var deep = (DeepStrategyConfig) config;
        assertThat(deep.graphAssist()).isEqualTo(DeepStrategyConfig.GraphAssistConfig.defaults());
        assertThat(deep.queryExpansion().maxExpandedQueries()).isEqualTo(4);
    }

    @Test
    @DisplayName("legacy deep strategy constructor defaults graph assist")
    void legacyDeepStrategyConstructorDefaultsGraphAssist() {
        var config =
                new DeepStrategyConfig(
                        new DeepStrategyConfig.QueryExpansionConfig(4),
                        new DeepStrategyConfig.SufficiencyConfig(12),
                        40,
                        30,
                        0.25d);

        assertThat(config.graphAssist()).isEqualTo(DeepStrategyConfig.GraphAssistConfig.defaults());
        assertThat(config.queryExpansion().maxExpandedQueries()).isEqualTo(4);
    }

    @Test
    @DisplayName("invalid deep strategy graph assist weight fails fast")
    void invalidDeepStrategyGraphAssistWeightFailsFast() {
        var mapper = JsonUtils.newMapper();

        assertThatThrownBy(
                        () ->
                                mapper.readValue(
                                        """
                                        {
                                          "type":"deep",
                                          "graphAssist":{"enabled":true,"graphChannelWeight":1.0,"timeout":"PT0.3S"}
                                        }
                                        """,
                                        StrategyConfig.class))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("graph retrieval weights must keep graph below direct");
    }

    @Test
    @DisplayName("legacy simple strategy json without graph assist still deserializes")
    void legacySimpleStrategyJsonWithoutGraphAssistStillDeserializes() throws Exception {
        var mapper = JsonUtils.newMapper();

        StrategyConfig config =
                mapper.readValue(
                        """
                        {"type":"simple","enableKeywordSearch":false}
                        """,
                        StrategyConfig.class);

        assertThat(config).isInstanceOf(SimpleStrategyConfig.class);
        assertThat(((SimpleStrategyConfig) config).enableKeywordSearch()).isFalse();
        assertThat(((SimpleStrategyConfig) config).graphAssist())
                .isEqualTo(SimpleStrategyConfig.GraphAssistConfig.defaults());
    }

    @Test
    @DisplayName("invalid simple strategy graph assist json fails fast")
    void invalidSimpleStrategyGraphAssistJsonFailsFast() {
        var mapper = JsonUtils.newMapper();

        assertThatThrownBy(
                        () ->
                                mapper.readValue(
                                        """
                                        {
                                          "type":"simple",
                                          "enableKeywordSearch":true,
                                          "graphAssist":{"enabled":true,"maxSeedItems":0,"timeout":"PT0.2S"}
                                        }
                                        """,
                                        StrategyConfig.class))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("graph retrieval caps must be non-negative");
    }

    @Test
    @DisplayName("invalid simple strategy graph assist weight fails fast")
    void invalidSimpleStrategyGraphAssistWeightFailsFast() {
        var mapper = JsonUtils.newMapper();

        assertThatThrownBy(
                        () ->
                                mapper.readValue(
                                        """
                                        {
                                          "type":"simple",
                                          "enableKeywordSearch":true,
                                          "graphAssist":{"enabled":true,"graphChannelWeight":1.0,"timeout":"PT0.2S"}
                                        }
                                        """,
                                        StrategyConfig.class))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("graph retrieval weights must keep graph below direct");
    }
}
