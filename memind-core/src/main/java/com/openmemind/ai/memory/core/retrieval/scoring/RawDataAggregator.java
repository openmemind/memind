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
package com.openmemind.ai.memory.core.retrieval.scoring;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Group and aggregate {@link ScoredResult} of ITEM type by rawDataId.
 *
 * <p>{@link #aggregate} outputs:
 * <ul>
 *   <li>List of items that retain the original text (after deduplication)</li>
 *   <li>List of aggregated rawData (caption + maxScore + itemIds)</li>
 * </ul>
 *
 */
public final class RawDataAggregator {

    private RawDataAggregator() {}

    /** Aggregation result: List of items that retain the original text + rawData caption list */
    public record AggregationResult(
            List<ScoredResult> items, List<RetrievalResult.RawDataResult> rawDataResults) {}

    /**
     * Aggregate ITEM type scoring results by rawDataId, while returning original items and rawData aggregation results.
     *
     * @param items    List of ITEM scoring results to be aggregated
     * @param memoryId Memory identifier
     * @param store    Memory storage
     * @return Aggregation result
     */
    public static AggregationResult aggregate(
            List<ScoredResult> items, MemoryId memoryId, MemoryStore store) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        Objects.requireNonNull(store, "store must not be null");

        if (items.isEmpty()) {
            return new AggregationResult(List.of(), List.of());
        }

        // 1. Parse sourceId -> Long, collect parsable IDs
        record Parsed(ScoredResult result, Long itemId) {}

        List<Parsed> parsedList =
                items.stream()
                        .map(
                                r -> {
                                    try {
                                        return new Parsed(r, Long.parseLong(r.sourceId()));
                                    } catch (NumberFormatException e) {
                                        return new Parsed(r, null);
                                    }
                                })
                        .toList();

        List<Long> validIds =
                parsedList.stream().map(Parsed::itemId).filter(Objects::nonNull).toList();

        // 2. Batch query MemoryItem
        Map<Long, MemoryItem> itemMap =
                validIds.isEmpty()
                        ? Map.of()
                        : store.itemOperations().getItemsByIds(memoryId, validIds).stream()
                                .collect(Collectors.toMap(MemoryItem::id, mi -> mi, (a, b) -> a));

        // 3. Group by rawDataId
        Map<String, List<Parsed>> groups = new LinkedHashMap<>();
        int uniqueCounter = 0;

        for (Parsed parsed : parsedList) {
            String groupKey;
            if (parsed.itemId() == null) {
                groupKey = "##unparseable-" + (uniqueCounter++);
            } else {
                MemoryItem mi = itemMap.get(parsed.itemId());
                String rawDataId = (mi != null) ? mi.rawDataId() : null;
                groupKey =
                        (rawDataId != null && !rawDataId.isBlank())
                                ? rawDataId
                                : "##no-rawdata-" + (uniqueCounter++);
            }
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(parsed);
        }

        // 4. Build deduplicated items (retain original text) and rawData aggregation results
        // Use dedupKey for deduplication, retaining high scores
        var dedupItems = new LinkedHashMap<String, ScoredResult>();
        List<RetrievalResult.RawDataResult> rawDataResults = new ArrayList<>();

        for (var entry : groups.entrySet()) {
            String groupKey = entry.getKey();
            List<Parsed> group = entry.getValue();

            // Retain original text (deduplicated) for all items in each group
            for (Parsed p : group) {
                dedupItems.merge(
                        p.result().dedupKey(),
                        p.result(),
                        (a, b) -> a.finalScore() >= b.finalScore() ? a : b);
            }

            // rawData aggregation: only generate RawDataResult for groupKeys that do not start with
            // ##
            if (!groupKey.startsWith("##")) {
                double maxScore =
                        group.stream().mapToDouble(p -> p.result().finalScore()).max().orElse(0);
                List<String> itemIds = group.stream().map(p -> p.result().sourceId()).toList();
                Optional<MemoryRawData> rawData =
                        store.rawDataOperations().getRawData(memoryId, groupKey);
                String caption = rawData.map(MemoryRawData::caption).orElse(null);
                if (caption != null && !caption.isBlank()) {
                    rawDataResults.add(
                            new RetrievalResult.RawDataResult(
                                    groupKey, caption, maxScore, itemIds));
                }
            }
        }

        // 5. Sort
        List<ScoredResult> sortedItems =
                dedupItems.values().stream()
                        .sorted(Comparator.comparingDouble(ScoredResult::finalScore).reversed())
                        .toList();
        rawDataResults.sort(
                Comparator.comparingDouble(RetrievalResult.RawDataResult::maxScore).reversed());

        return new AggregationResult(sortedItems, List.copyOf(rawDataResults));
    }

    /**
     * Aggregate ITEM type scoring results by rawDataId (compatible with old interface).
     *
     * <p>Take the highest finalScore from each group, replacing the display text with caption.
     *
     * @param items    List of ITEM scoring results to be aggregated
     * @param memoryId Memory identifier
     * @param store    Memory storage
     * @return List of results sorted in descending order by finalScore
     */
    public static List<ScoredResult> aggregateByRawDataId(
            List<ScoredResult> items, MemoryId memoryId, MemoryStore store) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        Objects.requireNonNull(store, "store must not be null");

        if (items.isEmpty()) {
            return List.of();
        }

        // 1. Parse sourceId -> Long, collect parsable IDs
        record Parsed(ScoredResult result, Long itemId) {}

        List<Parsed> parsedList =
                items.stream()
                        .map(
                                r -> {
                                    try {
                                        return new Parsed(r, Long.parseLong(r.sourceId()));
                                    } catch (NumberFormatException e) {
                                        return new Parsed(r, null);
                                    }
                                })
                        .toList();

        List<Long> validIds =
                parsedList.stream().map(Parsed::itemId).filter(Objects::nonNull).toList();

        // 2. Batch query MemoryItem
        Map<Long, MemoryItem> itemMap =
                validIds.isEmpty()
                        ? Map.of()
                        : store.itemOperations().getItemsByIds(memoryId, validIds).stream()
                                .collect(Collectors.toMap(MemoryItem::id, mi -> mi, (a, b) -> a));

        // 3. Group by rawDataId (each null rawDataId is a separate group)
        Map<String, List<ScoredResult>> groupsLegacy = new LinkedHashMap<>();
        int uniqueCounter = 0;

        for (Parsed parsed : parsedList) {
            String groupKey;
            if (parsed.itemId() == null) {
                groupKey = "##unparseable-" + (uniqueCounter++);
            } else {
                MemoryItem mi = itemMap.get(parsed.itemId());
                String rawDataId = (mi != null) ? mi.rawDataId() : null;
                groupKey =
                        (rawDataId != null && !rawDataId.isBlank())
                                ? rawDataId
                                : "##no-rawdata-" + (uniqueCounter++);
            }
            groupsLegacy.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(parsed.result());
        }

        // 4. For each group, take the entry with max finalScore, and replace text with caption
        List<ScoredResult> aggregated = new ArrayList<>(groupsLegacy.size());

        for (var entry : groupsLegacy.entrySet()) {
            String groupKey = entry.getKey();
            List<ScoredResult> group = entry.getValue();

            ScoredResult best =
                    group.stream()
                            .max(Comparator.comparingDouble(ScoredResult::finalScore))
                            .orElseThrow();

            if (groupKey.startsWith("##")) {
                aggregated.add(best);
            } else {
                Optional<MemoryRawData> rawData =
                        store.rawDataOperations().getRawData(memoryId, groupKey);
                String caption = rawData.map(MemoryRawData::caption).orElse(null);
                String text = (caption != null && !caption.isBlank()) ? caption : best.text();

                aggregated.add(
                        new ScoredResult(
                                best.sourceType(),
                                best.sourceId(),
                                text,
                                best.vectorScore(),
                                best.finalScore(),
                                best.occurredAt()));
            }
        }

        // 5. Sort in descending order by finalScore
        aggregated.sort(Comparator.comparingDouble(ScoredResult::finalScore).reversed());
        return List.copyOf(aggregated);
    }

    /**
     * Batch fill timestamps for ITEM type results with occurredAt as null.
     *
     * <p>ScoredResult created by BM25 channel does not carry occurredAt, this method fills it by
     * batch querying MemoryStore.
     *
     * @param results  List of results to be filled
     * @param memoryId Memory identifier
     * @param store    Memory storage (nullable, returns the original list directly if null)
     * @return List of results after filling
     */
    public static List<ScoredResult> backfillOccurredAt(
            List<ScoredResult> results, MemoryId memoryId, MemoryStore store) {
        if (store == null || results.isEmpty()) {
            return results;
        }

        // Collect ITEM sourceIds that need to be filled
        List<Long> missingIds =
                results.stream()
                        .filter(
                                r ->
                                        r.sourceType() == ScoredResult.SourceType.ITEM
                                                && r.occurredAt() == null)
                        .map(
                                r -> {
                                    try {
                                        return Long.parseLong(r.sourceId());
                                    } catch (NumberFormatException e) {
                                        return null;
                                    }
                                })
                        .filter(Objects::nonNull)
                        .toList();

        if (missingIds.isEmpty()) {
            return results;
        }

        Map<Long, Instant> idToOccurredAt =
                store.itemOperations().getItemsByIds(memoryId, missingIds).stream()
                        .filter(mi -> mi.occurredAt() != null)
                        .collect(
                                Collectors.toMap(
                                        MemoryItem::id, MemoryItem::occurredAt, (a, b) -> a));

        if (idToOccurredAt.isEmpty()) {
            return results;
        }

        return results.stream()
                .map(
                        r -> {
                            if (r.sourceType() == ScoredResult.SourceType.ITEM
                                    && r.occurredAt() == null) {
                                try {
                                    Instant ts = idToOccurredAt.get(Long.parseLong(r.sourceId()));
                                    if (ts != null) {
                                        return r.withOccurredAt(ts);
                                    }
                                } catch (NumberFormatException ignored) {
                                }
                            }
                            return r;
                        })
                .toList();
    }
}
