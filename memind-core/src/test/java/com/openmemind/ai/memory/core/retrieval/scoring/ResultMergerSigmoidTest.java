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
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ResultMerger.sigmoidNormalize")
class ResultMergerSigmoidTest {

    @Nested
    @DisplayName("P2: Sigmoid Normalization Properties")
    class SigmoidProperties {

        @Test
        @DisplayName(
                "P2.1: Monotonically Increasing — Higher BM25 raw score produces higher sigmoid")
        void monotonicallyIncreasing() {
            assertThat(ResultMerger.sigmoidNormalize(5))
                    .isGreaterThan(ResultMerger.sigmoidNormalize(2));
            assertThat(ResultMerger.sigmoidNormalize(2))
                    .isGreaterThan(ResultMerger.sigmoidNormalize(0.5));
        }

        @Test
        @DisplayName("P2.2: Range Constraint — Output is always in [0, 1)")
        void rangeConstraint() {
            for (double input : new double[] {0, 0.1, 1, 10, 100, 1000}) {
                double result = ResultMerger.sigmoidNormalize(input);
                assertThat(result).isBetween(0.0, 1.0);
                assertThat(result).isLessThan(1.0);
            }
        }

        @Test
        @DisplayName("P2.3: Zero Value — sigmoid(0) = 0")
        void zeroValue() {
            assertThat(ResultMerger.sigmoidNormalize(0)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("P2.4: Reference Points — Strong(10)≈0.91, Medium(2)≈0.67, Weak(0.5)≈0.33")
        void referencePoints() {
            assertThat(ResultMerger.sigmoidNormalize(10)).isCloseTo(0.909, within(0.01));
            assertThat(ResultMerger.sigmoidNormalize(2)).isCloseTo(0.667, within(0.01));
            assertThat(ResultMerger.sigmoidNormalize(0.5)).isCloseTo(0.333, within(0.01));
        }

        @Test
        @DisplayName("P2.5: Negative Value Handling — Take Absolute Value")
        void negativeValues() {
            assertThat(ResultMerger.sigmoidNormalize(-10))
                    .isEqualTo(ResultMerger.sigmoidNormalize(10));
        }
    }
}
