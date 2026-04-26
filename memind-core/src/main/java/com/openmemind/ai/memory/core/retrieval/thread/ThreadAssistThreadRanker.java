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

import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic thread ranking for retrieval assist.
 */
class ThreadAssistThreadRanker {

    private final ThreadProjectionStore threadStore;
    private final ItemOperations itemStore;
    private final Duration dormantAfter;
    private final Clock clock;

    ThreadAssistThreadRanker(MemoryStore store, Duration dormantAfter, Clock clock) {
        Objects.requireNonNull(store, "store");
        this.threadStore = Objects.requireNonNull(store.threadOperations(), "threadStore");
        this.itemStore = Objects.requireNonNull(store.itemOperations(), "itemStore");
        this.dormantAfter = Objects.requireNonNull(dormantAfter, "dormantAfter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    RankOutcome rank(
            QueryContext context,
            List<ScoredResult> seeds,
            RetrievalMemoryThreadSettings settings) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(seeds, "seeds");
        Objects.requireNonNull(settings, "settings");
        if (seeds.isEmpty() || settings.maxThreads() <= 0) {
            return new RankOutcome(0, List.of());
        }

        Map<String, CandidateAccumulator> candidatesByThreadKey = new LinkedHashMap<>();
        for (ScoredResult seed : seeds) {
            Long seedItemId = ThreadAssistSeedResolver.parseItemIdOrNull(seed);
            if (seedItemId == null) {
                continue;
            }
            for (MemoryThreadProjection projection :
                    threadStore.listThreadsByItemId(context.memoryId(), seedItemId)) {
                CandidateAccumulator accumulator =
                        candidatesByThreadKey.computeIfAbsent(
                                projection.threadKey(),
                                ignored -> new CandidateAccumulator(projection));
                accumulator.coveredSeedItemIds.add(seedItemId);
                accumulator.bestDirectScore =
                        Math.max(accumulator.bestDirectScore, seed.finalScore());
            }
        }
        if (candidatesByThreadKey.isEmpty()) {
            return new RankOutcome(0, List.of());
        }

        int seedThreadCount = candidatesByThreadKey.size();
        List<RankedThread> ranked = new ArrayList<>();
        for (CandidateAccumulator candidate : candidatesByThreadKey.values()) {
            MemoryThreadProjection projection = candidate.projection;
            if (projection.memberCount() < 2
                    || projection.objectState() == MemoryThreadObjectState.UNCERTAIN) {
                continue;
            }
            int remainingEligibleCount =
                    remainingEligibleNonDirectMemberCount(
                            context.memoryId(),
                            projection.threadKey(),
                            candidate.coveredSeedItemIds);
            if (remainingEligibleCount <= 0) {
                continue;
            }
            ranked.add(
                    new RankedThread(
                            projection,
                            List.copyOf(candidate.coveredSeedItemIds),
                            candidate.bestDirectScore,
                            remainingEligibleCount));
        }
        ranked.sort(rankComparator());
        return new RankOutcome(seedThreadCount, List.copyOf(ranked));
    }

    private Comparator<RankedThread> rankComparator() {
        return (left, right) -> {
            int compareCoverage =
                    Integer.compare(
                            right.coveredSeedItemIds().size(), left.coveredSeedItemIds().size());
            if (compareCoverage != 0) {
                return compareCoverage;
            }
            int compareBestSeed = Double.compare(right.bestDirectScore(), left.bestDirectScore());
            if (compareBestSeed != 0) {
                return compareBestSeed;
            }
            int compareBucket = Integer.compare(bucket(right.thread()), bucket(left.thread()));
            if (compareBucket != 0) {
                return compareBucket;
            }
            int compareLastEventAt =
                    compareInstantDescending(
                            left.thread().lastEventAt(), right.thread().lastEventAt());
            if (compareLastEventAt != 0) {
                return compareLastEventAt;
            }
            int compareEligibleCount =
                    Integer.compare(
                            right.remainingEligibleNonDirectMemberCount(),
                            left.remainingEligibleNonDirectMemberCount());
            if (compareEligibleCount != 0) {
                return compareEligibleCount;
            }
            return left.thread().threadKey().compareTo(right.thread().threadKey());
        };
    }

    private int remainingEligibleNonDirectMemberCount(
            com.openmemind.ai.memory.core.data.MemoryId memoryId,
            String threadKey,
            Set<Long> seedItemIds) {
        List<Long> candidateItemIds =
                threadStore.listMemberships(memoryId, threadKey).stream()
                        .map(MemoryThreadMembership::itemId)
                        .filter(itemId -> !seedItemIds.contains(itemId))
                        .distinct()
                        .toList();
        if (candidateItemIds.isEmpty()) {
            return 0;
        }
        Set<Long> loadableItemIds =
                itemStore.getItemsByIds(memoryId, candidateItemIds).stream()
                        .map(MemoryItem::id)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        int count = 0;
        for (Long candidateItemId : candidateItemIds) {
            if (loadableItemIds.contains(candidateItemId)) {
                count++;
            }
        }
        return count;
    }

    private int bucket(MemoryThreadProjection projection) {
        if (projection.lifecycleStatus() == MemoryThreadLifecycleStatus.ACTIVE
                && projection.objectState() != MemoryThreadObjectState.RESOLVED
                && projection.objectState() != MemoryThreadObjectState.UNCERTAIN) {
            return 3;
        }
        if (projection.objectState() == MemoryThreadObjectState.RESOLVED) {
            Instant closureMarker =
                    projection.closedAt() != null
                            ? projection.closedAt()
                            : projection.lastEventAt();
            if (closureMarker != null
                    && !closureMarker.isBefore(Instant.now(clock).minus(dormantAfter))) {
                return 2;
            }
            return 0;
        }
        if (projection.lifecycleStatus() == MemoryThreadLifecycleStatus.DORMANT) {
            return 1;
        }
        return 0;
    }

    private static int compareInstantDescending(Instant left, Instant right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    record RankOutcome(int seedThreadCount, List<RankedThread> rankedThreads) {

        RankOutcome {
            rankedThreads = rankedThreads == null ? List.of() : List.copyOf(rankedThreads);
        }
    }

    record RankedThread(
            MemoryThreadProjection thread,
            List<Long> coveredSeedItemIds,
            double bestDirectScore,
            int remainingEligibleNonDirectMemberCount) {

        RankedThread {
            thread = Objects.requireNonNull(thread, "thread");
            coveredSeedItemIds =
                    coveredSeedItemIds == null ? List.of() : List.copyOf(coveredSeedItemIds);
        }
    }

    private static final class CandidateAccumulator {

        private final MemoryThreadProjection projection;
        private final Set<Long> coveredSeedItemIds = new LinkedHashSet<>();
        private double bestDirectScore = Double.NEGATIVE_INFINITY;

        private CandidateAccumulator(MemoryThreadProjection projection) {
            this.projection = projection;
        }
    }
}
