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
package com.openmemind.ai.memory.core.extraction.item.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionDiagnostics;
import com.openmemind.ai.memory.core.extraction.item.graph.link.semantic.SemanticItemLinker;
import com.openmemind.ai.memory.core.extraction.item.graph.link.temporal.TemporalItemLinker;
import org.junit.jupiter.api.Test;

class ItemGraphMaterializationResultTest {

    @Test
    void materializationStatsBuilderRoundTripsStructuredAndRolloutFields() {
        var stats =
                ItemGraphMaterializationResult.Stats.withTemporalAndSemantic(
                                2,
                                3,
                                1,
                                new TemporalItemLinker.TemporalLinkingStats(
                                        2,
                                        1,
                                        4,
                                        3,
                                        2,
                                        2,
                                        10L,
                                        11L,
                                        12L,
                                        1,
                                        0.60d,
                                        1.0d,
                                        "0.60-0.74=1,0.90-1.00=1",
                                        false),
                                EntityResolutionDiagnostics.empty(),
                                SemanticItemLinker.SemanticLinkingStats.empty(),
                                1,
                                "organization=1",
                                0,
                                0,
                                0,
                                0,
                                0,
                                0)
                        .withStructuredBatchDegraded(true)
                        .withDerivedMaintenanceDegraded();

        assertThat(stats.toBuilder().build()).isEqualTo(stats);
    }
}
