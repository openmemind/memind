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
package com.openmemind.ai.memory.core.retrieval.graph;

import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.util.List;

/**
 * Graph-assist output plus bounded observability counters.
 */
public record RetrievalGraphAssistResult(List<ScoredResult> items, GraphAssistStats stats) {

    public RetrievalGraphAssistResult {
        items = items == null ? List.of() : List.copyOf(items);
        stats = stats == null ? GraphAssistStats.disabled() : stats;
    }

    public static RetrievalGraphAssistResult directOnly(
            List<ScoredResult> items, boolean graphEnabled) {
        return new RetrievalGraphAssistResult(
                items, new GraphAssistStats(graphEnabled, false, false, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    public static RetrievalGraphAssistResult degraded(
            List<ScoredResult> items, boolean graphEnabled, boolean timedOut) {
        return new RetrievalGraphAssistResult(
                items, new GraphAssistStats(graphEnabled, true, timedOut, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    public record GraphAssistStats(
            boolean graphEnabled,
            boolean degraded,
            boolean timedOut,
            int seedCount,
            int linkExpansionCount,
            int entityExpansionCount,
            int dedupedCandidateCount,
            int admittedGraphCandidateCount,
            int displacedDirectCount,
            int overlapCount,
            int skippedOverFanoutEntityCount) {

        public static GraphAssistStats disabled() {
            return new GraphAssistStats(false, false, false, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
