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
package com.openmemind.ai.memory.core.extraction.item.graph.link.temporal;

import com.openmemind.ai.memory.core.data.MemoryItem;
import java.time.Duration;
import java.time.Instant;

/**
 * Resolves temporal windows and classifies temporal relations.
 */
public final class TemporalRelationClassifier {

    static final Duration NEARBY_THRESHOLD = Duration.ofHours(24);

    public TemporalWindow resolveWindow(MemoryItem item) {
        if (item == null) {
            return null;
        }
        Instant start = firstNonNull(item.occurredStart(), item.occurredAt(), item.observedAt());
        if (start == null) {
            return null;
        }
        return new TemporalWindow(start, item.occurredEnd(), start);
    }

    public String classify(TemporalWindow left, TemporalWindow right) {
        if (left == null || right == null) {
            return null;
        }
        TemporalWindow earlier = compare(left, right) <= 0 ? left : right;
        TemporalWindow later = earlier == left ? right : left;
        if (windowsOverlap(earlier, later)) {
            return "overlap";
        }
        if (earlier.end() != null && !earlier.end().isAfter(later.start())) {
            return "before";
        }
        if (Duration.between(earlier.anchor(), later.anchor()).abs().compareTo(NEARBY_THRESHOLD)
                <= 0) {
            return "nearby";
        }
        return "before";
    }

    int compare(TemporalWindow left, TemporalWindow right) {
        int anchorComparison = left.anchor().compareTo(right.anchor());
        if (anchorComparison != 0) {
            return anchorComparison;
        }
        int startComparison = left.start().compareTo(right.start());
        if (startComparison != 0) {
            return startComparison;
        }
        return left.endOrAnchor().compareTo(right.endOrAnchor());
    }

    private static boolean windowsOverlap(TemporalWindow left, TemporalWindow right) {
        return left.start().isBefore(right.endOrAnchor())
                && right.start().isBefore(left.endOrAnchor());
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
