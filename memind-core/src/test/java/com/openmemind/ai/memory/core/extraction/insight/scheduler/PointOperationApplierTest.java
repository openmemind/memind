package com.openmemind.ai.memory.core.extraction.insight.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.PointOperation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PointOperationApplier")
class PointOperationApplierTest {

    @Nested
    @DisplayName("apply")
    class Apply {

        @Test
        @DisplayName("ADD operation should append point to the end of the list")
        void addShouldAppendPoint() {
            var existing =
                    List.of(
                            new InsightPoint(
                                    InsightPoint.PointType.SUMMARY,
                                    "existing",
                                    0.9f,
                                    List.of("1")));
            var newPoint =
                    new InsightPoint(InsightPoint.PointType.SUMMARY, "added", 0.85f, List.of("42"));
            var ops = List.of(new PointOperation(PointOperation.OpType.ADD, null, newPoint, null));

            var result = PointOperationApplier.apply(existing, ops);

            assertThat(result).hasSize(2);
            assertThat(result.getLast().content()).isEqualTo("added");
        }

        @Test
        @DisplayName("UPDATE operation should replace the content of the specified point")
        void updateShouldReplaceTargetPoint() {
            var existing =
                    List.of(
                            new InsightPoint(
                                    InsightPoint.PointType.SUMMARY,
                                    "old content",
                                    0.8f,
                                    List.of("1")),
                            new InsightPoint(
                                    InsightPoint.PointType.REASONING,
                                    "reasoning",
                                    0.7f,
                                    List.of("2")));
            var updated =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY,
                            "new content",
                            0.9f,
                            List.of("1", "55"));
            var ops = List.of(new PointOperation(PointOperation.OpType.UPDATE, 1, updated, null));

            var result = PointOperationApplier.apply(existing, ops);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().content()).isEqualTo("new content");
            assertThat(result.getFirst().sourceItemIds()).containsExactly("1", "55");
            assertThat(result.get(1).content()).isEqualTo("reasoning");
        }

        @Test
        @DisplayName("DELETE operation should remove the specified point")
        void deleteShouldRemoveTargetPoint() {
            var existing =
                    List.of(
                            new InsightPoint(
                                    InsightPoint.PointType.SUMMARY, "keep", 0.9f, List.of("1")),
                            new InsightPoint(
                                    InsightPoint.PointType.SUMMARY, "remove", 0.5f, List.of("2")),
                            new InsightPoint(
                                    InsightPoint.PointType.REASONING,
                                    "also keep",
                                    0.7f,
                                    List.of("3")));
            var ops =
                    List.of(
                            new PointOperation(
                                    PointOperation.OpType.DELETE, 2, null, "contradicted"));

            var result = PointOperationApplier.apply(existing, ops);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().content()).isEqualTo("keep");
            assertThat(result.getLast().content()).isEqualTo("also keep");
        }

        @Test
        @DisplayName("UPDATE out of bounds targetIndex should be ignored without error")
        void updateOutOfBoundsShouldBeIgnored() {
            var existing =
                    List.of(
                            new InsightPoint(
                                    InsightPoint.PointType.SUMMARY, "only", 0.9f, List.of("1")));
            var updated =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY, "won't apply", 0.9f, List.of("99"));
            var ops = List.of(new PointOperation(PointOperation.OpType.UPDATE, 5, updated, null));

            var result = PointOperationApplier.apply(existing, ops);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().content()).isEqualTo("only");
        }

        @Test
        @DisplayName("DELETE out of bounds targetIndex should be ignored without error")
        void deleteOutOfBoundsShouldBeIgnored() {
            var existing =
                    List.of(
                            new InsightPoint(
                                    InsightPoint.PointType.SUMMARY, "only", 0.9f, List.of("1")));
            var ops =
                    List.of(
                            new PointOperation(
                                    PointOperation.OpType.DELETE, 10, null, "no such index"));

            var result = PointOperationApplier.apply(existing, ops);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().content()).isEqualTo("only");
        }

        @Test
        @DisplayName("Mixed ADD+UPDATE+DELETE should correctly apply all operations")
        void mixedOperationsShouldApplyCorrectly() {
            var p1 = new InsightPoint(InsightPoint.PointType.SUMMARY, "point1", 0.9f, List.of("1"));
            var p2 = new InsightPoint(InsightPoint.PointType.SUMMARY, "point2", 0.8f, List.of("2"));
            var p3 =
                    new InsightPoint(
                            InsightPoint.PointType.REASONING, "point3", 0.7f, List.of("3"));
            var existing = List.of(p1, p2, p3);

            var updatedP1 =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY,
                            "updated-point1",
                            0.95f,
                            List.of("1", "10"));
            var newPoint =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY, "brand-new", 0.85f, List.of("20"));
            var ops =
                    List.of(
                            new PointOperation(PointOperation.OpType.UPDATE, 1, updatedP1, null),
                            new PointOperation(PointOperation.OpType.DELETE, 3, null, "outdated"),
                            new PointOperation(PointOperation.OpType.ADD, null, newPoint, null));

            var result = PointOperationApplier.apply(existing, ops);

            // P1 updated, P2 kept, P3 deleted, new point added
            assertThat(result).hasSize(3);
            assertThat(result.get(0).content()).isEqualTo("updated-point1");
            assertThat(result.get(1).content()).isEqualTo("point2");
            assertThat(result.get(2).content()).isEqualTo("brand-new");
        }

        @Test
        @DisplayName("Merge scenario: UPDATE P2 + DELETE P5 should execute correctly")
        void mergeScenarioShouldWork() {
            var points = new java.util.ArrayList<InsightPoint>();
            for (int i = 1; i <= 5; i++) {
                points.add(
                        new InsightPoint(
                                InsightPoint.PointType.SUMMARY,
                                "point" + i,
                                0.8f,
                                List.of(String.valueOf(i))));
            }

            var mergedP2 =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY,
                            "merged-p2-p5",
                            0.9f,
                            List.of("2", "5"));
            var ops =
                    List.of(
                            new PointOperation(PointOperation.OpType.UPDATE, 2, mergedP2, null),
                            new PointOperation(
                                    PointOperation.OpType.DELETE, 5, null, "merged into P2"));

            var result = PointOperationApplier.apply(points, ops);

            assertThat(result).hasSize(4);
            assertThat(result.get(0).content()).isEqualTo("point1");
            assertThat(result.get(1).content()).isEqualTo("merged-p2-p5");
            assertThat(result.get(2).content()).isEqualTo("point3");
            assertThat(result.get(3).content()).isEqualTo("point4");
        }

        @Test
        @DisplayName("When the same index is updated and deleted, DELETE takes precedence")
        void deleteOverridesUpdateOnSameIndex() {
            var existing =
                    List.of(
                            new InsightPoint(
                                    InsightPoint.PointType.SUMMARY,
                                    "original",
                                    0.9f,
                                    List.of("1")));
            var updated =
                    new InsightPoint(
                            InsightPoint.PointType.SUMMARY,
                            "won't apply",
                            0.95f,
                            List.of("1", "2"));
            var ops =
                    List.of(
                            new PointOperation(PointOperation.OpType.UPDATE, 1, updated, null),
                            new PointOperation(
                                    PointOperation.OpType.DELETE, 1, null, "contradicted"));

            var result = PointOperationApplier.apply(existing, ops);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Empty operations should return the original list unchanged")
        void emptyOperationsShouldReturnOriginal() {
            var existing =
                    List.of(
                            new InsightPoint(
                                    InsightPoint.PointType.SUMMARY,
                                    "unchanged",
                                    0.9f,
                                    List.of("1")));

            var result = PointOperationApplier.apply(existing, List.of());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().content()).isEqualTo("unchanged");
        }

        @Test
        @DisplayName("First time (empty existing points) all ADDs should correctly append")
        void firstTimeAllAddsShouldWork() {
            var p1 =
                    new InsightPoint(InsightPoint.PointType.SUMMARY, "first", 0.85f, List.of("42"));
            var p2 =
                    new InsightPoint(
                            InsightPoint.PointType.REASONING, "second", 0.75f, List.of("43"));
            var ops =
                    List.of(
                            new PointOperation(PointOperation.OpType.ADD, null, p1, null),
                            new PointOperation(PointOperation.OpType.ADD, null, p2, null));

            var result = PointOperationApplier.apply(List.of(), ops);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).content()).isEqualTo("first");
            assertThat(result.get(1).content()).isEqualTo("second");
        }

        @Test
        @DisplayName("Multiple DELETEs should correctly remove all targets")
        void multipleDeletesShouldRemoveAllTargets() {
            var points = new java.util.ArrayList<InsightPoint>();
            for (int i = 1; i <= 4; i++) {
                points.add(
                        new InsightPoint(
                                InsightPoint.PointType.SUMMARY,
                                "point" + i,
                                0.8f,
                                List.of(String.valueOf(i))));
            }

            var ops =
                    List.of(
                            new PointOperation(PointOperation.OpType.DELETE, 1, null, "outdated"),
                            new PointOperation(PointOperation.OpType.DELETE, 3, null, "duplicate"));

            var result = PointOperationApplier.apply(points, ops);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).content()).isEqualTo("point2");
            assertThat(result.get(1).content()).isEqualTo("point4");
        }
    }
}
