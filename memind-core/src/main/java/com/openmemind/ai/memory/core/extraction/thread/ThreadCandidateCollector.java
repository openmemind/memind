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
package com.openmemind.ai.memory.core.extraction.thread;

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collects deterministic attachment candidates before scoring.
 */
final class ThreadCandidateCollector {

    List<ThreadCandidate> collect(
            MemoryItem item,
            MemoryThreadType threadType,
            List<CanonicalizedSignal> canonicalSignals,
            Map<String, MemoryThreadProjection> projectionsByKey,
            Map<String, List<MemoryThreadMembership>> membershipsByThreadKey,
            Map<Long, List<MemoryThreadMembership>> membershipsByItemId,
            List<ItemLink> adjacentLinks) {
        Objects.requireNonNull(item, "item");
        if (canonicalSignals == null || canonicalSignals.isEmpty()) {
            return List.of();
        }

        Set<String> candidateThreadKeys = new LinkedHashSet<>();
        Set<String> exactAnchorThreadKeys = new LinkedHashSet<>();
        Set<Long> continuityTargetIds =
                canonicalSignals.stream()
                        .map(CanonicalizedSignal::signal)
                        .flatMap(signal -> signal.supportingItemIds().stream())
                        .filter(Objects::nonNull)
                        .filter(targetItemId -> targetItemId > 0L && targetItemId < item.id())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        for (CanonicalizedSignal canonicalSignal : canonicalSignals) {
            MemoryThreadProjection projection = projectionsByKey.get(canonicalSignal.anchor().threadKey());
            if (projection != null && projection.threadType() == threadType) {
                candidateThreadKeys.add(projection.threadKey());
                exactAnchorThreadKeys.add(projection.threadKey());
            }
        }

        for (Long targetItemId : continuityTargetIds) {
            membershipsByItemId.getOrDefault(targetItemId, List.of()).stream()
                    .filter(
                            membership ->
                                    threadTypeForMembership(membership, projectionsByKey)
                                            == threadType)
                    .map(MemoryThreadMembership::threadKey)
                    .forEach(candidateThreadKeys::add);
        }

        for (ItemLink link : adjacentLinks == null ? List.<ItemLink>of() : adjacentLinks) {
            Long otherItemId = otherItemId(item.id(), link);
            if (otherItemId == null || otherItemId >= item.id()) {
                continue;
            }
            membershipsByItemId.getOrDefault(otherItemId, List.of()).stream()
                    .filter(
                            membership ->
                                    threadTypeForMembership(membership, projectionsByKey)
                                            == threadType)
                    .map(MemoryThreadMembership::threadKey)
                    .forEach(candidateThreadKeys::add);
        }

        return candidateThreadKeys.stream()
                .map(
                        threadKey -> {
                            MemoryThreadProjection thread = projectionsByKey.get(threadKey);
                            if (thread == null) {
                                return null;
                            }
                            Set<Long> memberItemIds =
                                    membershipsByThreadKey.getOrDefault(threadKey, List.of()).stream()
                                            .map(MemoryThreadMembership::itemId)
                                            .collect(Collectors.toCollection(LinkedHashSet::new));
                            boolean explicitContinuityMatch =
                                    continuityTargetIds.stream().anyMatch(memberItemIds::contains);
                            return new ThreadCandidate(
                                    thread,
                                    memberItemIds,
                                    exactAnchorThreadKeys.contains(threadKey),
                                    explicitContinuityMatch);
                        })
                .filter(Objects::nonNull)
                .toList();
    }

    private static MemoryThreadType threadTypeForMembership(
            MemoryThreadMembership membership,
            Map<String, MemoryThreadProjection> projectionsByKey) {
        MemoryThreadProjection projection = projectionsByKey.get(membership.threadKey());
        return projection == null ? null : projection.threadType();
    }

    private static Long otherItemId(long triggerItemId, ItemLink link) {
        if (link.sourceItemId() == triggerItemId) {
            return link.targetItemId();
        }
        if (link.targetItemId() == triggerItemId) {
            return link.sourceItemId();
        }
        return null;
    }
}
