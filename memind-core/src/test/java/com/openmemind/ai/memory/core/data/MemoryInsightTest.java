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
package com.openmemind.ai.memory.core.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.enums.InsightTier;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryInsightTest {

    @Test
    void allSourceItemIdsReturnsLeafItemsOnly() {
        MemoryInsight insight =
                new MemoryInsight(
                        1L,
                        "u1:a1",
                        "preferences",
                        null,
                        "remote",
                        List.of(),
                        List.of(
                                new InsightPoint(
                                        "pt_leaf_1",
                                        InsightPoint.PointType.SUMMARY,
                                        "Prefers async work",
                                        List.of("10", "11"),
                                        List.of(),
                                        null),
                                new InsightPoint(
                                        "pt_branch_1",
                                        InsightPoint.PointType.REASONING,
                                        "Async-first pattern is stable",
                                        List.of(),
                                        List.of(new InsightPointRef(99L, "pt_other")),
                                        null)),
                        "remote",
                        Instant.now(),
                        null,
                        Instant.now(),
                        Instant.now(),
                        InsightTier.LEAF,
                        null,
                        List.of(),
                        1);

        assertThat(insight.allSourceItemIds()).containsExactly("10", "11");
    }
}
