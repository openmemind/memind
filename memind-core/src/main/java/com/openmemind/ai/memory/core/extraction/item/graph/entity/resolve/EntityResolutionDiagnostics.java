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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded per-batch resolution diagnostics.
 */
public record EntityResolutionDiagnostics(
        int candidateCount,
        Map<EntityResolutionSource, Integer> candidateSourceCounts,
        Map<EntityResolutionRejectReason, Integer> candidateRejectCounts,
        int mergeAcceptedCount,
        int mergeRejectedCount,
        int createNewCount,
        int exactFallbackCount,
        int candidateCapHitCount,
        int aliasEvidenceObservedCount,
        int aliasEvidenceMergedCount,
        int specialBypassCount,
        String mergeScoreHistogramSummary) {

    public EntityResolutionDiagnostics {
        candidateSourceCounts = immutableCopy(candidateSourceCounts);
        candidateRejectCounts = immutableCopy(candidateRejectCounts);
        mergeScoreHistogramSummary =
                mergeScoreHistogramSummary == null ? "" : mergeScoreHistogramSummary;
    }

    public static EntityResolutionDiagnostics empty() {
        return new EntityResolutionDiagnostics(0, Map.of(), Map.of(), 0, 0, 0, 0, 0, 0, 0, 0, "");
    }

    public static EntityResolutionDiagnostics exactFallback(
            int mentionCount, int aliasEvidenceObservedCount) {
        return new EntityResolutionDiagnostics(
                0,
                Map.of(),
                Map.of(),
                0,
                0,
                0,
                mentionCount,
                0,
                aliasEvidenceObservedCount,
                0,
                0,
                "");
    }

    private static <K> Map<K, Integer> immutableCopy(Map<K, Integer> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
