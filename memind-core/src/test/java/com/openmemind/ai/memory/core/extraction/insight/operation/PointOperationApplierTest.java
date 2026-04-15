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
package com.openmemind.ai.memory.core.extraction.insight.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.PointOperation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PointOperationApplier")
class PointOperationApplierTest {

    @Test
    void applyUpdatesAndDeletesByPointId() {
        var existing =
                List.of(
                        new InsightPoint(
                                "pt_keep",
                                InsightPoint.PointType.SUMMARY,
                                "keep",
                                0.8f,
                                List.of("1")),
                        new InsightPoint(
                                "pt_update",
                                InsightPoint.PointType.SUMMARY,
                                "old",
                                0.8f,
                                List.of("2")),
                        new InsightPoint(
                                "pt_delete",
                                InsightPoint.PointType.REASONING,
                                "remove",
                                0.7f,
                                List.of("3")));

        var result =
                PointOperationApplier.apply(
                        existing,
                        List.of(
                                new PointOperation(
                                        PointOperation.OpType.UPDATE,
                                        "pt_update",
                                        new InsightPoint(
                                                "pt_update",
                                                InsightPoint.PointType.SUMMARY,
                                                "new",
                                                0.9f,
                                                List.of("2", "4")),
                                        null),
                                new PointOperation(
                                        PointOperation.OpType.DELETE,
                                        "pt_delete",
                                        null,
                                        "obsolete")));

        assertThat(result)
                .extracting(InsightPoint::pointId)
                .containsExactly("pt_keep", "pt_update");
        assertThat(result.get(1).content()).isEqualTo("new");
    }

    @Test
    void deleteStillOverridesUpdateWhenTargetingSamePointId() {
        var existing =
                List.of(
                        new InsightPoint(
                                "pt_same",
                                InsightPoint.PointType.SUMMARY,
                                "original",
                                0.8f,
                                List.of("1")));

        var result =
                PointOperationApplier.apply(
                        existing,
                        List.of(
                                new PointOperation(
                                        PointOperation.OpType.UPDATE,
                                        "pt_same",
                                        new InsightPoint(
                                                "pt_same",
                                                InsightPoint.PointType.SUMMARY,
                                                "updated",
                                                0.9f,
                                                List.of("1", "2")),
                                        null),
                                new PointOperation(
                                        PointOperation.OpType.DELETE,
                                        "pt_same",
                                        null,
                                        "contradicted")));

        assertThat(result).isEmpty();
    }
}
