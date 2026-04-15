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

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.PointOperation;
import java.util.ArrayList;
import java.util.List;

public final class PointOperationResolver {

    private PointOperationResolver() {}

    public static ResolvedPointOperations resolve(
            List<InsightPoint> existingPoints, List<PointOperation> operations) {
        var safeExisting =
                existingPoints == null ? List.<InsightPoint>of() : List.copyOf(existingPoints);
        var safeOperations =
                operations == null ? List.<PointOperation>of() : List.copyOf(operations);

        var filtered = filterValidOperations(safeExisting, safeOperations);
        if (!safeOperations.isEmpty() && filtered.validOperations().isEmpty()) {
            return new ResolvedPointOperations(
                    safeExisting,
                    false,
                    false,
                    true,
                    filtered.addCount(),
                    filtered.updateCount(),
                    filtered.deleteCount(),
                    filtered.invalidCount());
        }
        var applied = PointOperationApplier.apply(safeExisting, filtered.validOperations());

        if (!safeExisting.isEmpty() && applied.isEmpty()) {
            return new ResolvedPointOperations(
                    safeExisting,
                    false,
                    false,
                    true,
                    filtered.addCount(),
                    filtered.updateCount(),
                    filtered.deleteCount(),
                    filtered.invalidCount());
        }

        boolean changed = !applied.equals(safeExisting);
        return new ResolvedPointOperations(
                changed ? applied : safeExisting,
                changed,
                !changed,
                false,
                filtered.addCount(),
                filtered.updateCount(),
                filtered.deleteCount(),
                filtered.invalidCount());
    }

    private static FilteredOperations filterValidOperations(
            List<InsightPoint> existingPoints, List<PointOperation> operations) {
        var valid = new ArrayList<PointOperation>();
        int addCount = 0;
        int updateCount = 0;
        int deleteCount = 0;
        int invalidCount = 0;

        for (var operation : operations) {
            if (operation == null || operation.op() == null) {
                invalidCount++;
                continue;
            }
            switch (operation.op()) {
                case ADD -> {
                    if (operation.point() == null) {
                        invalidCount++;
                        continue;
                    }
                    valid.add(operation);
                    addCount++;
                }
                case UPDATE -> {
                    if (!isValidTarget(existingPoints, operation.targetPointId())
                            || operation.point() == null) {
                        invalidCount++;
                        continue;
                    }
                    valid.add(operation);
                    updateCount++;
                }
                case DELETE -> {
                    if (!isValidTarget(existingPoints, operation.targetPointId())) {
                        invalidCount++;
                        continue;
                    }
                    valid.add(operation);
                    deleteCount++;
                }
            }
        }

        return new FilteredOperations(
                List.copyOf(valid), addCount, updateCount, deleteCount, invalidCount);
    }

    private static boolean isValidTarget(List<InsightPoint> existingPoints, String targetIndex) {
        return targetIndex != null
                && !targetIndex.isBlank()
                && existingPoints.stream().anyMatch(point -> targetIndex.equals(point.pointId()));
    }

    private record FilteredOperations(
            List<PointOperation> validOperations,
            int addCount,
            int updateCount,
            int deleteCount,
            int invalidCount) {}
}
