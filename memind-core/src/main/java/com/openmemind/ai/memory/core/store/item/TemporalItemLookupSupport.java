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
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TemporalItemLookupSupport {

    private TemporalItemLookupSupport() {}

    public static List<TemporalItemLookupMatch> correctnessFirstLookup(
            ItemOperations itemOperations, MemoryId memoryId, TemporalItemLookupRequest request) {
        Objects.requireNonNull(itemOperations, "itemOperations");
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(request, "request");
        long midpoint =
                request.startInclusive().toEpochMilli()
                        + Duration.between(request.startInclusive(), request.endExclusive())
                                        .toMillis()
                                / 2L;
        return itemOperations.listItems(memoryId).stream()
                .filter(item -> item.id() != null)
                .filter(item -> !request.excludeItemIds().contains(item.id()))
                .filter(item -> request.scope() == null || item.scope() == request.scope())
                .filter(
                        item ->
                                request.categories().isEmpty()
                                        || request.categories().contains(item.category()))
                .filter(
                        item ->
                                request.itemTypes().isEmpty()
                                        || request.itemTypes().contains(item.type()))
                .map(TemporalItemLookupSupport::toMatch)
                .filter(Objects::nonNull)
                .filter(match -> overlaps(match, request))
                .sorted(
                        Comparator.comparingLong(
                                        (TemporalItemLookupMatch match) ->
                                                Math.abs(match.anchor().toEpochMilli() - midpoint))
                                .thenComparing(match -> match.item().id()))
                .limit(request.maxCandidates())
                .toList();
    }

    private static TemporalItemLookupMatch toMatch(MemoryItem item) {
        Instant start = firstNonNull(item.occurredStart(), item.occurredAt());
        if (start == null) {
            return null;
        }
        Instant end = item.occurredEnd();
        if (end == null || !start.isBefore(end)) {
            end = start.plusMillis(1);
        }
        Instant anchor = item.occurredAt() != null ? item.occurredAt() : start;
        return new TemporalItemLookupMatch(item, start, end, anchor);
    }

    private static boolean overlaps(
            TemporalItemLookupMatch match, TemporalItemLookupRequest request) {
        return match.matchedStart().isBefore(request.endExclusive())
                && request.startInclusive().isBefore(match.matchedEndExclusive());
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
