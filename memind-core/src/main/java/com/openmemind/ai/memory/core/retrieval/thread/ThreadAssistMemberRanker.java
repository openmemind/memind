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
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.extraction.thread.ThreadEventTimeResolver;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
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

/**
 * Deterministic per-thread member admission and ranking.
 */
final class ThreadAssistMemberRanker {

    private final ThreadProjectionStore threadStore;
    private final ItemOperations itemStore;

    ThreadAssistMemberRanker(MemoryStore store) {
        Objects.requireNonNull(store, "store");
        this.threadStore = Objects.requireNonNull(store.threadOperations(), "threadStore");
        this.itemStore = Objects.requireNonNull(store.itemOperations(), "itemStore");
    }

    List<ScoredResult> admit(
            QueryContext context,
            ThreadAssistThreadRanker.RankedThread rankedThread,
            List<ScoredResult> directItems,
            int effectivePerThreadCap) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(rankedThread, "rankedThread");
        Objects.requireNonNull(directItems, "directItems");
        if (effectivePerThreadCap <= 0) {
            return List.of();
        }

        Set<Long> directItemIds = parseItemIds(directItems);
        if (directItemIds.isEmpty() && rankedThread.coveredSeedItemIds().isEmpty()) {
            return List.of();
        }

        List<MemoryThreadMembership> memberships =
                threadStore.listMemberships(context.memoryId(), rankedThread.thread().threadKey());
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Long> candidateItemIds =
                memberships.stream()
                        .map(MemoryThreadMembership::itemId)
                        .filter(itemId -> !directItemIds.contains(itemId))
                        .distinct()
                        .toList();
        if (candidateItemIds.isEmpty()) {
            return List.of();
        }

        Map<Long, MemoryItem> itemsById =
                itemStore.getItemsByIds(context.memoryId(), candidateItemIds).stream()
                        .collect(
                                LinkedHashMap::new,
                                (map, item) -> map.put(item.id(), item),
                                Map::putAll);
        if (itemsById.isEmpty()) {
            return List.of();
        }

        List<MemoryThreadEvent> events =
                threadStore.listEvents(context.memoryId(), rankedThread.thread().threadKey());
        Map<Long, ItemEventStats> eventStatsByItemId = itemEventStats(events);
        Map<Long, MemoryItem> seedItemsById =
                itemStore.getItemsByIds(context.memoryId(), rankedThread.coveredSeedItemIds()).stream()
                        .collect(
                                LinkedHashMap::new,
                                (map, item) -> map.put(item.id(), item),
                                Map::putAll);

        List<MemberCandidate> candidates = new ArrayList<>();
        for (MemoryThreadMembership membership : memberships) {
            MemoryItem item = itemsById.get(membership.itemId());
            if (item == null) {
                continue;
            }
            candidates.add(
                    new MemberCandidate(
                            membership,
                            item,
                            proximity(
                                    membership.itemId(),
                                    rankedThread.coveredSeedItemIds(),
                                    eventStatsByItemId,
                                    seedItemsById,
                                    item),
                            recency(membership.itemId(), eventStatsByItemId, item)));
        }
        candidates.sort(memberComparator());
        return candidates.stream()
                .limit(effectivePerThreadCap)
                .map(
                        candidate ->
                                new ScoredResult(
                                        ScoredResult.SourceType.ITEM,
                                        Long.toString(candidate.item().id()),
                                        candidate.item().content(),
                                        0.0f,
                                        candidate.membership().relevanceWeight(),
                                        ThreadEventTimeResolver.resolve(candidate.item())))
                .toList();
    }

    private Comparator<MemberCandidate> memberComparator() {
        return (left, right) -> {
            int compareWeight =
                    Double.compare(
                            right.membership().relevanceWeight(),
                            left.membership().relevanceWeight());
            if (compareWeight != 0) {
                return compareWeight;
            }
            int compareProximity = compareProximity(left.proximity(), right.proximity());
            if (compareProximity != 0) {
                return compareProximity;
            }
            int compareRecency = compareRecency(left.recency(), right.recency());
            if (compareRecency != 0) {
                return compareRecency;
            }
            return Long.compare(left.item().id(), right.item().id());
        };
    }

    private static int compareProximity(Proximity left, Proximity right) {
        if (left.sequenceDistance() != null && right.sequenceDistance() != null) {
            return Long.compare(left.sequenceDistance(), right.sequenceDistance());
        }
        if (left.timeDistanceMillis() != null && right.timeDistanceMillis() != null) {
            return Long.compare(left.timeDistanceMillis(), right.timeDistanceMillis());
        }
        if (left.timeDistanceMillis() != null) {
            return -1;
        }
        if (right.timeDistanceMillis() != null) {
            return 1;
        }
        return 0;
    }

    private static int compareRecency(Instant left, Instant right) {
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

    private static Instant recency(
            long itemId, Map<Long, ItemEventStats> eventStatsByItemId, MemoryItem item) {
        ItemEventStats stats = eventStatsByItemId.get(itemId);
        if (stats != null && stats.latestEventTime() != null) {
            return stats.latestEventTime();
        }
        return ThreadEventTimeResolver.resolve(item);
    }

    private static Proximity proximity(
            long itemId,
            List<Long> seedItemIds,
            Map<Long, ItemEventStats> eventStatsByItemId,
            Map<Long, MemoryItem> seedItemsById,
            MemoryItem item) {
        ItemEventStats memberStats = eventStatsByItemId.get(itemId);
        Long sequenceDistance = null;
        if (memberStats != null && !memberStats.eventSeqs().isEmpty()) {
            for (Long seedItemId : seedItemIds) {
                ItemEventStats seedStats = eventStatsByItemId.get(seedItemId);
                if (seedStats == null || seedStats.eventSeqs().isEmpty()) {
                    continue;
                }
                for (Long memberSeq : memberStats.eventSeqs()) {
                    for (Long seedSeq : seedStats.eventSeqs()) {
                        long candidate = Math.abs(memberSeq - seedSeq);
                        sequenceDistance =
                                sequenceDistance == null
                                        ? candidate
                                        : Math.min(sequenceDistance, candidate);
                    }
                }
            }
        }

        Long timeDistanceMillis = null;
        List<Instant> memberTimes = eventTimesOrFallback(memberStats, item);
        if (!memberTimes.isEmpty()) {
            for (Long seedItemId : seedItemIds) {
                List<Instant> seedTimes =
                        eventTimesOrFallback(eventStatsByItemId.get(seedItemId), seedItemsById.get(seedItemId));
                for (Instant memberTime : memberTimes) {
                    for (Instant seedTime : seedTimes) {
                        long candidate =
                                Math.abs(Duration.between(seedTime, memberTime).toMillis());
                        timeDistanceMillis =
                                timeDistanceMillis == null
                                        ? candidate
                                        : Math.min(timeDistanceMillis, candidate);
                    }
                }
            }
        }

        return new Proximity(sequenceDistance, timeDistanceMillis);
    }

    private static List<Instant> eventTimesOrFallback(ItemEventStats stats, MemoryItem item) {
        if (stats != null && !stats.eventTimes().isEmpty()) {
            return stats.eventTimes();
        }
        Instant fallback = item != null ? ThreadEventTimeResolver.resolve(item) : null;
        return fallback == null ? List.of() : List.of(fallback);
    }

    private static Map<Long, ItemEventStats> itemEventStats(List<MemoryThreadEvent> events) {
        Map<Long, List<Long>> eventSeqsByItemId = new LinkedHashMap<>();
        Map<Long, List<Instant>> eventTimesByItemId = new LinkedHashMap<>();
        if (events == null) {
            return Map.of();
        }
        for (MemoryThreadEvent event : events) {
            Object rawSources = event.eventPayloadJson().get("sources");
            if (!(rawSources instanceof List<?> sources)) {
                continue;
            }
            for (Object rawSource : sources) {
                if (!(rawSource instanceof Map<?, ?> source)) {
                    continue;
                }
                Object sourceType = source.get("sourceType");
                if (!(sourceType instanceof String type) || !"ITEM".equalsIgnoreCase(type)) {
                    continue;
                }
                Long itemId = itemId(source.get("itemId"));
                if (itemId == null) {
                    continue;
                }
                eventSeqsByItemId.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(event.eventSeq());
                eventTimesByItemId.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(event.eventTime());
            }
        }
        Map<Long, ItemEventStats> statsByItemId = new LinkedHashMap<>();
        Set<Long> itemIds = new LinkedHashSet<>();
        itemIds.addAll(eventSeqsByItemId.keySet());
        itemIds.addAll(eventTimesByItemId.keySet());
        for (Long itemId : itemIds) {
            statsByItemId.put(
                    itemId,
                    new ItemEventStats(
                            List.copyOf(eventSeqsByItemId.getOrDefault(itemId, List.of())),
                            List.copyOf(eventTimesByItemId.getOrDefault(itemId, List.of()))));
        }
        return statsByItemId;
    }

    private static Set<Long> parseItemIds(List<ScoredResult> results) {
        Set<Long> itemIds = new LinkedHashSet<>();
        for (ScoredResult result : results) {
            Long itemId = ThreadAssistSeedResolver.parseItemIdOrNull(result);
            if (itemId != null) {
                itemIds.add(itemId);
            }
        }
        return itemIds;
    }

    private static Long itemId(Object rawItemId) {
        if (rawItemId instanceof Number number) {
            return number.longValue();
        }
        if (rawItemId instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record MemberCandidate(
            MemoryThreadMembership membership,
            MemoryItem item,
            Proximity proximity,
            Instant recency) {}

    private record Proximity(Long sequenceDistance, Long timeDistanceMillis) {}

    private record ItemEventStats(List<Long> eventSeqs, List<Instant> eventTimes) {

        private Instant latestEventTime() {
            Instant latest = null;
            for (Instant eventTime : eventTimes) {
                if (latest == null || eventTime.isAfter(latest)) {
                    latest = eventTime;
                }
            }
            return latest;
        }
    }
}
