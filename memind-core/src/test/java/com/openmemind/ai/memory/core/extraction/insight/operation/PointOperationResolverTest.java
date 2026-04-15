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

@DisplayName("PointOperationResolver")
class PointOperationResolverTest {

    @Test
    void resolveUsesTargetPointIdForUpdate() {
        var existing =
                List.of(
                        new InsightPoint(
                                "pt_keep", InsightPoint.PointType.SUMMARY, "keep", List.of("1")),
                        new InsightPoint(
                                "pt_update", InsightPoint.PointType.SUMMARY, "old", List.of("2")));

        var resolved =
                PointOperationResolver.resolve(
                        existing,
                        List.of(
                                new PointOperation(
                                        PointOperation.OpType.UPDATE,
                                        "pt_update",
                                        new InsightPoint(
                                                "pt_update",
                                                InsightPoint.PointType.SUMMARY,
                                                "new",
                                                List.of("2", "3")),
                                        "merge")));

        assertThat(resolved.changed()).isTrue();
        assertThat(resolved.points())
                .extracting(InsightPoint::content)
                .containsExactly("keep", "new");
    }

    @Test
    void resolveRequestsFallbackWhenAllOperationsAreInvalid() {
        var existing =
                List.of(
                        new InsightPoint(
                                "pt_existing",
                                InsightPoint.PointType.SUMMARY,
                                "existing",
                                List.of("1")));

        var resolved =
                PointOperationResolver.resolve(
                        existing,
                        List.of(
                                new PointOperation(
                                        PointOperation.OpType.DELETE,
                                        "pt_missing",
                                        null,
                                        "missing target")));

        assertThat(resolved.fallbackRequired()).isTrue();
        assertThat(resolved.invalidCount()).isEqualTo(1);
    }
}
