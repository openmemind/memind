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
package com.openmemind.ai.memory.core.retrieval.thread;

import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Memory-thread assist output plus bounded observability counters.
 */
public record MemoryThreadAssistResult(List<ScoredResult> items, Stats stats) {

    public MemoryThreadAssistResult {
        items = items == null ? List.of() : List.copyOf(items);
        stats = stats == null ? Stats.disabled() : stats;
    }

    public static MemoryThreadAssistResult directOnly(
            List<ScoredResult> items, boolean memoryThreadEnabled) {
        return new MemoryThreadAssistResult(
                items, memoryThreadEnabled ? Stats.success(0, 0, 0, false) : Stats.disabled());
    }

    public static MemoryThreadAssistResult degraded(
            List<ScoredResult> items, boolean memoryThreadEnabled, boolean timedOut) {
        return new MemoryThreadAssistResult(
                items, memoryThreadEnabled ? Stats.degraded(timedOut) : Stats.disabled());
    }

    public MemoryThreadAssistResult reboundTo(int maxItems, int pinnedCount) {
        if (maxItems < 0) {
            throw new IllegalArgumentException("maxItems must be non-negative");
        }
        if (pinnedCount < 0) {
            throw new IllegalArgumentException("pinnedCount must be non-negative");
        }
        if (items.size() <= maxItems) {
            return this;
        }

        int pinned = Math.min(Math.min(pinnedCount, items.size()), maxItems);
        int tailStart = Math.min(pinnedCount, items.size());
        int tailLimit = Math.max(0, maxItems - pinned);

        List<ScoredResult> bounded = new ArrayList<>(maxItems);
        bounded.addAll(items.subList(0, pinned));
        if (tailLimit > 0 && tailStart < items.size()) {
            bounded.addAll(items.subList(tailStart, Math.min(items.size(), tailStart + tailLimit)));
        }

        int tailCapacity = Math.max(0, maxItems - Math.min(pinnedCount, maxItems));
        return new MemoryThreadAssistResult(
                bounded,
                stats.withClamped(true)
                        .withAdmittedMemberCount(
                                Math.min(stats.admittedMemberCount(), tailCapacity)));
    }

    public record Stats(
            int seedThreadCount,
            int candidateCount,
            int admittedMemberCount,
            boolean clamped,
            boolean degraded,
            boolean timedOut) {

        public static Stats success(
                int seedThreadCount, int candidateCount, int admittedMemberCount, boolean clamped) {
            return new Stats(
                    seedThreadCount, candidateCount, admittedMemberCount, clamped, false, false);
        }

        public static Stats degraded(boolean timedOut) {
            return new Stats(0, 0, 0, false, true, timedOut);
        }

        public static Stats disabled() {
            return new Stats(0, 0, 0, false, false, false);
        }

        public Stats withClamped(boolean clamped) {
            return new Stats(
                    seedThreadCount,
                    candidateCount,
                    admittedMemberCount,
                    clamped,
                    degraded,
                    timedOut);
        }

        public Stats withAdmittedMemberCount(int admittedMemberCount) {
            return new Stats(
                    seedThreadCount,
                    candidateCount,
                    admittedMemberCount,
                    clamped,
                    degraded,
                    timedOut);
        }
    }
}
