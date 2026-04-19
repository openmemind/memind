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
package com.openmemind.ai.memory.core.extraction.item.graph.link.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import org.junit.jupiter.api.Test;

class TemporalProbePolicyTest {

    @Test
    void probePolicyShouldClampDerivedProbeLimitsFromPairCap() {
        assertThat(
                        TemporalProbePolicy.fromOptions(
                                new ItemGraphOptions(true, 8, 2, 1, 5, 0.82d, 4, 1, 128)))
                .extracting(
                        TemporalProbePolicy::overlapLimit,
                        TemporalProbePolicy::beforeLimit,
                        TemporalProbePolicy::afterLimit)
                .containsExactly(4, 8, 8);

        assertThat(
                        TemporalProbePolicy.fromOptions(
                                new ItemGraphOptions(true, 8, 2, 5, 5, 0.82d, 4, 1, 128)))
                .extracting(
                        TemporalProbePolicy::overlapLimit,
                        TemporalProbePolicy::beforeLimit,
                        TemporalProbePolicy::afterLimit)
                .containsExactly(5, 10, 10);

        assertThat(
                        TemporalProbePolicy.fromOptions(
                                new ItemGraphOptions(true, 8, 2, 40, 5, 0.82d, 4, 1, 128)))
                .extracting(
                        TemporalProbePolicy::overlapLimit,
                        TemporalProbePolicy::beforeLimit,
                        TemporalProbePolicy::afterLimit)
                .containsExactly(16, 32, 32);
    }
}
