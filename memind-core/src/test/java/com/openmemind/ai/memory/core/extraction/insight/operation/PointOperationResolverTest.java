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
    @DisplayName("resolve should return noop when operations are empty and points are unchanged")
    void resolveReturnsNoopWhenOperationsAreEmptyAndPointsAreUnchanged() {
        var existing =
                List.of(
                        new InsightPoint(
                                InsightPoint.PointType.SUMMARY,
                                "existing",
                                0.8f,
                                List.of("1", "2")));

        var resolved = PointOperationResolver.resolve(existing, List.of());

        assertThat(resolved.changed()).isFalse();
        assertThat(resolved.noop()).isTrue();
        assertThat(resolved.fallbackRequired()).isFalse();
        assertThat(resolved.points()).containsExactlyElementsOf(existing);
    }

    @Test
    @DisplayName("resolve should request fallback when non-empty existing points are fully deleted")
    void resolveRequestsFallbackWhenAppliedPointsBecomeEmptyFromNonEmptyExisting() {
        var existing =
                List.of(
                        new InsightPoint(
                                InsightPoint.PointType.SUMMARY,
                                "existing",
                                0.8f,
                                List.of("1", "2")));
        var ops = List.of(new PointOperation(PointOperation.OpType.DELETE, 1, null, "drop"));

        var resolved = PointOperationResolver.resolve(existing, ops);

        assertThat(resolved.changed()).isFalse();
        assertThat(resolved.noop()).isFalse();
        assertThat(resolved.fallbackRequired()).isTrue();
        assertThat(resolved.points()).containsExactlyElementsOf(existing);
    }

    @Test
    @DisplayName("resolve should drop invalid operations and keep valid ones")
    void resolveDropsInvalidOperationsButKeepsValidOnes() {
        var existing =
                List.of(
                        new InsightPoint(
                                InsightPoint.PointType.SUMMARY,
                                "existing",
                                0.8f,
                                List.of("1", "2")));
        var ops =
                List.of(
                        new PointOperation(
                                PointOperation.OpType.UPDATE,
                                5,
                                new InsightPoint(
                                        InsightPoint.PointType.SUMMARY,
                                        "ignored",
                                        0.7f,
                                        List.of("9")),
                                null),
                        new PointOperation(
                                PointOperation.OpType.ADD,
                                null,
                                new InsightPoint(
                                        InsightPoint.PointType.SUMMARY,
                                        "added",
                                        0.85f,
                                        List.of("3", "4")),
                                null));

        var resolved = PointOperationResolver.resolve(existing, ops);

        assertThat(resolved.changed()).isTrue();
        assertThat(resolved.noop()).isFalse();
        assertThat(resolved.fallbackRequired()).isFalse();
        assertThat(resolved.invalidCount()).isEqualTo(1);
        assertThat(resolved.addCount()).isEqualTo(1);
        assertThat(resolved.updateCount()).isEqualTo(0);
        assertThat(resolved.deleteCount()).isEqualTo(0);
        assertThat(resolved.points())
                .extracting(InsightPoint::content)
                .containsExactly("existing", "added");
    }

    @Test
    @DisplayName("resolve should request fallback when all operations are invalid")
    void resolveRequestsFallbackWhenAllOperationsAreInvalid() {
        var existing =
                List.of(
                        new InsightPoint(
                                InsightPoint.PointType.SUMMARY,
                                "existing",
                                0.8f,
                                List.of("1", "2")));
        var ops =
                List.of(
                        new PointOperation(
                                PointOperation.OpType.UPDATE,
                                5,
                                new InsightPoint(
                                        InsightPoint.PointType.SUMMARY,
                                        "ignored",
                                        0.7f,
                                        List.of("9")),
                                null));

        var resolved = PointOperationResolver.resolve(existing, ops);

        assertThat(resolved.changed()).isFalse();
        assertThat(resolved.noop()).isFalse();
        assertThat(resolved.fallbackRequired()).isTrue();
        assertThat(resolved.invalidCount()).isEqualTo(1);
        assertThat(resolved.points()).containsExactlyElementsOf(existing);
    }
}
