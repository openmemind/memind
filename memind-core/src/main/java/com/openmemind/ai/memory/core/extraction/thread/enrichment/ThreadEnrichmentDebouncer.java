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
package com.openmemind.ai.memory.core.extraction.thread.enrichment;

import com.openmemind.ai.memory.core.builder.MemoryThreadEnrichmentOptions;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Replay-derived debounce rules for optional write-side thread enrichment.
 */
public final class ThreadEnrichmentDebouncer {

    public Evaluation evaluate(
            MemoryThreadProjection thread,
            List<MemoryThreadEvent> itemBackedEvents,
            Instant now,
            MemoryThreadEnrichmentOptions options) {
        Objects.requireNonNull(thread, "thread");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(options, "options");
        List<MemoryThreadEvent> events = itemBackedEvents == null ? List.of() : List.copyOf(itemBackedEvents);
        long meaningfulCount = events.stream().filter(ThreadEnrichmentDebouncer::isItemBackedMeaningful).count();

        Baseline baseline = baseline(thread);
        if (baseline == null) {
            boolean eligible = events.size() >= options.minimumEventCountForFirstEnrichment();
            return new Evaluation(eligible, meaningfulCount, null, null);
        }

        long meaningfulDelta = Math.max(0L, meaningfulCount - baseline.lastMeaningfulEventCount());
        boolean gapSatisfied =
                baseline.lastEnrichedAt() == null
                        || !baseline.lastEnrichedAt()
                                .plus(options.minimumWallClockGapBetweenRuns())
                                .isAfter(now);
        boolean eligible =
                gapSatisfied
                        && meaningfulDelta >= options.minimumMeaningfulEventDeltaForReenrichment();
        return new Evaluation(eligible, meaningfulCount, baseline.lastEnrichedAt(), baseline.lastMeaningfulEventCount());
    }

    static boolean isItemBackedMeaningful(MemoryThreadEvent event) {
        return event != null && event.meaningful() && isItemBacked(event);
    }

    static boolean isItemBacked(MemoryThreadEvent event) {
        if (event == null) {
            return false;
        }
        Object sources = event.eventPayloadJson().get("sources");
        if (!(sources instanceof List<?> sourceList)) {
            return false;
        }
        return sourceList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(map -> map.get("sourceType"))
                .map(String::valueOf)
                .anyMatch("ITEM"::equals);
    }

    private static Baseline baseline(MemoryThreadProjection thread) {
        Object facetsValue = thread.snapshotJson().get("facets");
        if (!(facetsValue instanceof Map<?, ?> facets)) {
            return null;
        }
        Object enrichmentValue = facets.get("enrichment");
        if (!(enrichmentValue instanceof Map<?, ?> enrichment)) {
            return null;
        }
        Long meaningfulCount = number(enrichment.get("lastEnrichedMeaningfulEventCount"));
        Instant lastEnrichedAt = instant(enrichment.get("lastEnrichedAt"));
        if (meaningfulCount == null && lastEnrichedAt == null) {
            return null;
        }
        return new Baseline(meaningfulCount != null ? meaningfulCount : 0L, lastEnrichedAt);
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Instant.parse(text);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private record Baseline(long lastMeaningfulEventCount, Instant lastEnrichedAt) {}

    public record Evaluation(
            boolean eligible,
            long meaningfulEventCount,
            Instant lastEnrichedAt,
            Long lastEnrichedMeaningfulEventCount) {}
}
