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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Apply the operations output by LLM to the existing point list.
 *
 * <p>DELETE takes precedence over UPDATE: when the same pointId is both UPDATE and DELETE,
 * DELETE takes effect.
 */
public final class PointOperationApplier {
    private PointOperationApplier() {}

    public static List<InsightPoint> apply(
            List<InsightPoint> existingPoints, List<PointOperation> operations) {
        var toDelete = new HashSet<String>();
        var updates = new LinkedHashMap<String, InsightPoint>();

        for (var op : operations) {
            if (op.op() == PointOperation.OpType.DELETE && op.targetPointId() != null) {
                toDelete.add(op.targetPointId());
            }
        }
        for (var op : operations) {
            if (op.op() == PointOperation.OpType.UPDATE
                    && op.targetPointId() != null
                    && op.point() != null
                    && !toDelete.contains(op.targetPointId())) {
                updates.put(op.targetPointId(), op.point());
            }
        }

        var result = new ArrayList<InsightPoint>();
        for (var point : existingPoints) {
            if (point.pointId() != null && toDelete.contains(point.pointId())) {
                continue;
            }
            if (point.pointId() != null && updates.containsKey(point.pointId())) {
                result.add(updates.get(point.pointId()));
                continue;
            }
            result.add(point);
        }
        for (var op : operations) {
            if (op.op() == PointOperation.OpType.ADD && op.point() != null) {
                result.add(op.point());
            }
        }
        return List.copyOf(result);
    }
}
