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
package com.openmemind.ai.memory.core.extraction.insight.tree;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InsightTreeConfig")
class InsightTreeConfigTest {

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("The default configuration should have reasonable default values")
        void shouldHaveReasonableDefaults() {
            var config = InsightTreeConfig.defaults();
            assertThat(config.branchBubbleThreshold()).isEqualTo(3);
            assertThat(config.rootBubbleThreshold()).isEqualTo(2);
            assertThat(config.minBranchesForRoot()).isEqualTo(2);
            assertThat(config.rootTargetTokens()).isEqualTo(800);
        }
    }
}
