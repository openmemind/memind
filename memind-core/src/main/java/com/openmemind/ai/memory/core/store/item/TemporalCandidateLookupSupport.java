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
package com.openmemind.ai.memory.core.store.item;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.link.temporal.TemporalRelationClassifier;
import com.openmemind.ai.memory.core.extraction.item.graph.link.temporal.TemporalWindow;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fallback support for stores without native bounded temporal lookup.
 */
public final class TemporalCandidateLookupSupport {

    private TemporalCandidateLookupSupport() {}

    private record RankedTemporalCandidate(MemoryItem item, TemporalWindow window) {}

    /**
     * Correctness-first fallback for stores that do not implement bounded native lookup.
     * This path may scan every item in the memory and is intentionally slower than
     * official maintained store implementations.
     */
    public static List<TemporalCandidateMatch> correctnessFirstLookup(
            ItemOperations itemOperations,
            MemoryId memoryId,
            List<TemporalCandidateRequest> requests,
            Collection<Long> excludeItemIds) {
        var classifier = new TemporalRelationClassifier();
        var excluded =
                excludeItemIds == null ? Set.<Long>of() : new LinkedHashSet<>(excludeItemIds);
        var allItems = List.copyOf(itemOperations.listItems(memoryId));
        var results = new ArrayList<TemporalCandidateMatch>();

        for (var request : requests) {
            var overlap = new ArrayList<RankedTemporalCandidate>();
            var before = new ArrayList<RankedTemporalCandidate>();
            var after = new ArrayList<RankedTemporalCandidate>();
            var seenCandidateIds = new LinkedHashSet<Long>();
            for (var candidate : allItems) {
                if (excluded.contains(candidate.id())
                        || !Objects.equals(candidate.type(), request.itemType())
                        || !Objects.equals(candidate.category(), request.category())) {
                    continue;
                }
                var window = classifier.resolveWindow(candidate);
                if (window == null) {
                    continue;
                }
                var ranked = new RankedTemporalCandidate(candidate, window);
                if (window.start().isBefore(request.sourceEndOrAnchor())
                        && request.sourceStart().isBefore(window.endOrAnchor())) {
                    overlap.add(ranked);
                } else if (window.anchor().isBefore(request.sourceAnchor())) {
                    before.add(ranked);
                } else if (window.anchor().isAfter(request.sourceAnchor())) {
                    after.add(ranked);
                }
            }

            sortByAnchorGapThenId(overlap, request.sourceAnchor());
            sortByAnchorGapThenId(before, request.sourceAnchor());
            sortByAnchorGapThenId(after, request.sourceAnchor());

            appendMatchesWithinLimit(
                    results, seenCandidateIds, request, overlap, request.overlapLimit());
            appendMatchesWithinLimit(
                    results, seenCandidateIds, request, before, request.beforeLimit());
            appendMatchesWithinLimit(
                    results, seenCandidateIds, request, after, request.afterLimit());
        }
        return List.copyOf(results);
    }

    private static void sortByAnchorGapThenId(
            List<RankedTemporalCandidate> candidates, Instant sourceAnchor) {
        candidates.sort(
                Comparator.comparing(
                                (RankedTemporalCandidate candidate) ->
                                        Duration.between(candidate.window().anchor(), sourceAnchor)
                                                .abs())
                        .thenComparing(candidate -> candidate.item().id()));
    }

    private static void appendMatchesWithinLimit(
            List<TemporalCandidateMatch> results,
            Set<Long> seenCandidateIds,
            TemporalCandidateRequest request,
            List<RankedTemporalCandidate> candidates,
            int limit) {
        int appended = 0;
        for (var candidate : candidates) {
            if (appended >= limit || !seenCandidateIds.add(candidate.item().id())) {
                continue;
            }
            results.add(new TemporalCandidateMatch(request.sourceItemId(), candidate.item()));
            appended++;
        }
    }
}
