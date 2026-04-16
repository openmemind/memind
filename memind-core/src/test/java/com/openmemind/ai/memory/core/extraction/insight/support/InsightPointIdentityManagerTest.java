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
package com.openmemind.ai.memory.core.extraction.insight.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.PointOperation;
import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class InsightPointIdentityManagerTest {

    @Test
    void normalizePersistedPointsRetriesUntilGeneratedIdsBecomeUnique() {
        var manager =
                new InsightPointIdentityManager(
                        generatedIds(
                                "pt_generated",
                                "pt_generated",
                                "pt_generated_2",
                                "pt_generated_2",
                                "pt_generated_3"));

        var normalized =
                manager.normalizePersistedPoints(
                        List.of(
                                new InsightPoint(
                                        InsightPoint.PointType.SUMMARY, "first", List.of("1")),
                                new InsightPoint(
                                        "pt_generated",
                                        InsightPoint.PointType.SUMMARY,
                                        "second",
                                        List.of("2")),
                                new InsightPoint(
                                        "pt_generated",
                                        InsightPoint.PointType.REASONING,
                                        "duplicate",
                                        List.of("3"))));

        assertThat(normalized)
                .extracting(InsightPoint::pointId)
                .containsExactly("pt_generated", "pt_generated_2", "pt_generated_3");
    }

    @Test
    void normalizeGeneratedOperationsRetriesAddIdsUntilTheyDoNotConflictWithExistingOrNewPoints() {
        var manager =
                new InsightPointIdentityManager(
                        generatedIds(
                                "pt_existing_2",
                                "pt_generated_1",
                                "pt_generated_1",
                                "pt_generated_2"));

        var existingPoints =
                List.of(
                        new InsightPoint(
                                "pt_existing_1",
                                InsightPoint.PointType.SUMMARY,
                                "keep",
                                List.of("0")),
                        new InsightPoint(
                                "pt_existing_2",
                                InsightPoint.PointType.REASONING,
                                "untouched",
                                List.of("9")));

        var operations =
                manager.normalizeGeneratedOperations(
                        existingPoints,
                        List.of(
                                new PointOperation(
                                        PointOperation.OpType.ADD,
                                        null,
                                        new InsightPoint(
                                                InsightPoint.PointType.SUMMARY,
                                                "new",
                                                List.of("1")),
                                        "new signal"),
                                new PointOperation(
                                        PointOperation.OpType.ADD,
                                        null,
                                        new InsightPoint(
                                                InsightPoint.PointType.REASONING,
                                                "second",
                                                List.of("2")),
                                        "second signal"),
                                new PointOperation(
                                        PointOperation.OpType.UPDATE,
                                        "pt_existing_1",
                                        new InsightPoint(
                                                "pt_wrong",
                                                InsightPoint.PointType.SUMMARY,
                                                "updated",
                                                List.of("1", "2")),
                                        "merge")));

        assertThat(operations.getFirst().point().pointId()).isEqualTo("pt_generated_1");
        assertThat(operations.get(1).point().pointId()).isEqualTo("pt_generated_2");
        assertThat(operations.get(2).point().pointId()).isEqualTo("pt_existing_1");
    }

    @Test
    void reusePointIdsForFullRewriteRetriesFreshIdsUntilTheyAreUnique() {
        var manager =
                new InsightPointIdentityManager(generatedIds("pt_existing_1", "pt_generated_1"));

        var existing =
                List.of(
                        new InsightPoint(
                                "pt_existing_1",
                                InsightPoint.PointType.SUMMARY,
                                "User prefers async communication",
                                List.of("1", "2")));

        var rewritten =
                manager.reusePointIdsForFullRewrite(
                        existing,
                        List.of(
                                new InsightPoint(
                                        InsightPoint.PointType.SUMMARY,
                                        "User prefers async communication",
                                        List.of("2", "1")),
                                new InsightPoint(
                                        InsightPoint.PointType.REASONING,
                                        "The user optimizes for uninterrupted work blocks",
                                        List.of("3", "4"))));

        assertThat(rewritten.getFirst().pointId()).isEqualTo("pt_existing_1");
        assertThat(rewritten.get(1).pointId()).isEqualTo("pt_generated_1");
    }

    private Supplier<String> generatedIds(String... ids) {
        var queue = new ArrayDeque<>(List.of(ids));
        return queue::removeFirst;
    }
}
