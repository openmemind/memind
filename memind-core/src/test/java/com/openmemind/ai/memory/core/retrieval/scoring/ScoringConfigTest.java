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
import org.junit.jupiter.api.Test;

@DisplayName("ScoringConfig")
class ScoringConfigTest {

    @Test
    @DisplayName("defaults expose reusable sub configs")
    void defaultsExposeReusableSubConfigs() {
        var config = ScoringConfig.defaults();

        assertThat(config.fusion()).isNotNull();
        assertThat(config.timeDecay()).isNotNull();
        assertThat(config.queryWeight()).isNotNull();
        assertThat(config.candidateMultiplier()).isPositive();
        assertThat(config.rerankCandidateLimit()).isPositive();
    }

    @Test
    @DisplayName("withFusion only changes fusion branch")
    void withFusionOnlyChangesFusionBranch() {
        var base = ScoringConfig.defaults();
        var updated = base.withFusion(new ScoringConfig.FusionConfig(100, 2.0, 1.5));

        assertThat(updated.fusion()).isEqualTo(new ScoringConfig.FusionConfig(100, 2.0, 1.5));
        assertThat(updated.timeDecay()).isEqualTo(base.timeDecay());
        assertThat(updated.queryWeight()).isEqualTo(base.queryWeight());
    }
}
