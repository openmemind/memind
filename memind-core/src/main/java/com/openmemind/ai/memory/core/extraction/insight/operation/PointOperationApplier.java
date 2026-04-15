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
import java.util.List;

/**
 * Apply the operations output by LLM to the existing point list.
 *
 * <p>targetIndex is 1-based, corresponding to the numbers P1, P2, ... in the prompt. All targetIndex references the original list position before the operation.
 * DELETE takes precedence over UPDATE: when the same index is both UPDATE and DELETE, DELETE takes effect.
 */
public final class PointOperationApplier {
    private PointOperationApplier() {}

    public static List<InsightPoint> apply(
            List<InsightPoint> existingPoints, List<PointOperation> operations) {

        // 1. Collect all DELETE indices
        var toDelete = new HashSet<Integer>();
        for (var op : operations) {
            if (op.op() == PointOperation.OpType.DELETE && op.targetIndex() != null) {
                int idx = op.targetIndex() - 1;
                if (idx >= 0 && idx < existingPoints.size()) {
                    toDelete.add(idx);
                }
            }
        }

        // 2. Apply UPDATE (skip indices that are DELETE)
        var updated = new ArrayList<>(existingPoints);
        for (var op : operations) {
            if (op.op() == PointOperation.OpType.UPDATE && op.targetIndex() != null) {
                int idx = op.targetIndex() - 1;
                if (idx >= 0 && idx < updated.size() && !toDelete.contains(idx)) {
                    updated.set(idx, op.point());
                }
            }
        }

        // 3. Build the result: keep non-DELETE points, then append ADD
        var result = new ArrayList<InsightPoint>();
        for (int i = 0; i < updated.size(); i++) {
            if (!toDelete.contains(i)) {
                result.add(updated.get(i));
            }
        }
        for (var op : operations) {
            if (op.op() == PointOperation.OpType.ADD && op.point() != null) {
                result.add(op.point());
            }
        }
        return List.copyOf(result);
    }
}
