package com.openmemind.ai.memory.core.extraction.insight.scheduler;

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
final class PointOperationApplier {
    private PointOperationApplier() {}

    static List<InsightPoint> apply(
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
