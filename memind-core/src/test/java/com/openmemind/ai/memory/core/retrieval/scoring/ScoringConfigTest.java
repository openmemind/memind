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
package com.openmemind.ai.memory.core.retrieval.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ScoringConfig")
class ScoringConfigTest {

    @Nested
    @DisplayName("defaults()")
    class Defaults {

        @Test
        @DisplayName("should return default values consistent with the original RetrievalConstants")
        void shouldMatchOriginalConstants() {
            var config = ScoringConfig.defaults();

            assertThat(config.candidateMultiplier()).isEqualTo(2);
            assertThat(config.rerankCandidateLimit()).isEqualTo(40);
            assertThat(config.rawDataKeyInfoMaxLines()).isEqualTo(5);
            assertThat(config.insightLlmThreshold()).isEqualTo(15);
        }

        @Test
        @DisplayName("FusionConfig default values should be correct")
        void fusionDefaults() {
            var fusion = ScoringConfig.defaults().fusion();
            assertThat(fusion.k()).isEqualTo(60);
            assertThat(fusion.vectorWeight()).isEqualTo(1.5);
            assertThat(fusion.keywordWeight()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("TimeDecayConfig default values should be correct")
        void timeDecayDefaults() {
            var td = ScoringConfig.defaults().timeDecay();
            assertThat(td.rate()).isEqualTo(0.023);
            assertThat(td.floor()).isEqualTo(0.3);
            assertThat(td.outOfRangePenalty()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("RecencyConfig default values should be correct")
        void recencyDefaults() {
            var r = ScoringConfig.defaults().recency();
            assertThat(r.rate()).isEqualTo(0.0019);
            assertThat(r.floor()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("PositionBonusConfig default values should be correct")
        void positionBonusDefaults() {
            var pb = ScoringConfig.defaults().positionBonus();
            assertThat(pb.top1()).isEqualTo(0.05);
            assertThat(pb.top3()).isEqualTo(0.02);
        }

        @Test
        @DisplayName("KeywordSearchConfig default values should be correct")
        void keywordSearchDefaults() {
            var ks = ScoringConfig.defaults().keywordSearch();
            assertThat(ks.probeTopK()).isEqualTo(10);
            assertThat(ks.strongSignalMinScore()).isEqualTo(0.85);
            assertThat(ks.strongSignalMinGap()).isEqualTo(0.15);
        }

        @Test
        @DisplayName("QueryWeightConfig default values should be correct")
        void queryWeightDefaults() {
            var qw = ScoringConfig.defaults().queryWeight();
            assertThat(qw.originalWeight()).isEqualTo(2.0);
            assertThat(qw.expandedWeight()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("with* methods")
    class WithMethods {

        @Test
        @DisplayName("withFusion should only modify fusion")
        void withFusion() {
            var config =
                    ScoringConfig.defaults()
                            .withFusion(new ScoringConfig.FusionConfig(100, 2.0, 1.5));
            assertThat(config.fusion().k()).isEqualTo(100);
            assertThat(config.timeDecay().rate()).isEqualTo(0.023); // other unchanged
        }
    }
}
