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
import java.util.Comparator;

/**
 * Deterministic fusion candidate shared by graph-assist modes.
 */
public record GraphFusionCandidate(
        long itemId,
        boolean directCandidate,
        int directRank,
        double fusedScore,
        double normalizedGraphScore,
        ScoredResult result) {

    static final Comparator<GraphFusionCandidate> ORDER =
            Comparator.comparingDouble(GraphFusionCandidate::fusedScore)
                    .reversed()
                    .thenComparing(GraphFusionCandidate::directCandidate, Comparator.reverseOrder())
                    .thenComparingInt(GraphFusionCandidate::directRank)
                    .thenComparing(
                            Comparator.comparingDouble(GraphFusionCandidate::normalizedGraphScore)
                                    .reversed())
                    .thenComparingLong(GraphFusionCandidate::itemId);
}
