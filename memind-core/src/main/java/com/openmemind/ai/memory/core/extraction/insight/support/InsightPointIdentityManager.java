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

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.InsightPointRef;
import com.openmemind.ai.memory.core.data.PointOperation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class InsightPointIdentityManager {

    private final Supplier<String> pointIdSupplier;

    public InsightPointIdentityManager() {
        this(PointIdGenerator::generate);
    }

    public InsightPointIdentityManager(Supplier<String> pointIdSupplier) {
        this.pointIdSupplier = Objects.requireNonNull(pointIdSupplier, "pointIdSupplier");
    }

    public List<InsightPoint> normalizePersistedPoints(List<InsightPoint> points) {
        var normalized = new ArrayList<InsightPoint>();
        var usedIds = new HashSet<String>();
        for (var point : safePoints(points)) {
            var pointId = point.pointId();
            if (pointId == null || pointId.isBlank() || usedIds.contains(pointId)) {
                pointId = allocateUniquePointId(usedIds);
            } else {
                usedIds.add(pointId);
            }
            normalized.add(point.withPointId(pointId));
        }
        return List.copyOf(normalized);
    }

    public List<PointOperation> normalizeGeneratedOperations(
            List<InsightPoint> existingPoints, List<PointOperation> operations) {
        var normalized = new ArrayList<PointOperation>();
        var usedIds = new HashSet<String>();
        for (var point : safePoints(existingPoints)) {
            if (point.pointId() != null && !point.pointId().isBlank()) {
                usedIds.add(point.pointId());
            }
        }
        for (var operation : safeOperations(operations)) {
            if (operation != null
                    && operation.targetPointId() != null
                    && !operation.targetPointId().isBlank()) {
                usedIds.add(operation.targetPointId());
            }
        }
        for (var operation : safeOperations(operations)) {
            if (operation == null || operation.op() == null) {
                normalized.add(operation);
                continue;
            }
            switch (operation.op()) {
                case ADD ->
                        normalized.add(
                                new PointOperation(
                                        PointOperation.OpType.ADD,
                                        null,
                                        operation.point() == null
                                                ? null
                                                : operation
                                                        .point()
                                                        .withPointId(
                                                                allocateUniquePointId(usedIds)),
                                        operation.reason()));
                case UPDATE ->
                        normalized.add(
                                new PointOperation(
                                        PointOperation.OpType.UPDATE,
                                        operation.targetPointId(),
                                        operation.point() == null
                                                ? null
                                                : operation
                                                        .point()
                                                        .withPointId(operation.targetPointId()),
                                        operation.reason()));
                case DELETE ->
                        normalized.add(
                                new PointOperation(
                                        PointOperation.OpType.DELETE,
                                        operation.targetPointId(),
                                        null,
                                        operation.reason()));
            }
        }
        return List.copyOf(normalized);
    }

    public List<InsightPoint> reusePointIdsForFullRewrite(
            List<InsightPoint> existingPoints, List<InsightPoint> rewrittenPoints) {
        var reusableIds = new LinkedHashMap<PointSignature, Deque<String>>();
        var usedIds = new HashSet<String>();
        for (var point : normalizePersistedPoints(existingPoints)) {
            usedIds.add(point.pointId());
            reusableIds
                    .computeIfAbsent(signature(point), ignored -> new ArrayDeque<>())
                    .addLast(point.pointId());
        }

        var rewritten = new ArrayList<InsightPoint>();
        for (var point : safePoints(rewrittenPoints)) {
            var matches = reusableIds.get(signature(point));
            var pointId =
                    matches != null && !matches.isEmpty()
                            ? matches.removeFirst()
                            : allocateUniquePointId(usedIds);
            rewritten.add(point.withPointId(pointId));
        }
        return List.copyOf(rewritten);
    }

    private List<InsightPoint> safePoints(List<InsightPoint> points) {
        return points == null ? List.of() : List.copyOf(points);
    }

    private List<PointOperation> safeOperations(List<PointOperation> operations) {
        return operations == null ? List.of() : new ArrayList<>(operations);
    }

    private String allocateUniquePointId(Set<String> usedIds) {
        String pointId;
        do {
            pointId = pointIdSupplier.get();
        } while (pointId == null || pointId.isBlank() || usedIds.contains(pointId));
        usedIds.add(pointId);
        return pointId;
    }

    private PointSignature signature(InsightPoint point) {
        return new PointSignature(
                point.type(),
                point.content(),
                point.sourceItemIds().stream().sorted(Comparator.naturalOrder()).toList(),
                point.sourcePointRefs().stream()
                        .sorted(
                                Comparator.comparing(InsightPointRef::insightId)
                                        .thenComparing(InsightPointRef::pointId))
                        .toList());
    }

    private record PointSignature(
            InsightPoint.PointType type,
            String content,
            List<String> sourceItemIds,
            List<InsightPointRef> sourcePointRefs) {}
}
