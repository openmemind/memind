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

import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Time decay utility class
 *
 * <p>Extracts the time decay logic of ItemTierRetriever as an independent tool, supporting batch application
 * of decay to ScoredResult list.
 *
 */
public final class TimeDecay {

    private TimeDecay() {}

    /**
     * Calculate time decay factor based on occurredAt
     *
     * <p>Returns 1.0 within the query time range, outside the range decays exponentially (half-life of 30 days,
     * lower limit of 0.3).
     * If occurredAt is null, returns 1.0 (no decay).
     * If context has no time range, applies global freshness decay (half-life of 365 days, lower limit of 0.7).
     *
     * @param occurredAt Memory occurrence time
     * @param context    Query context
     * @param scoring    Scoring configuration
     * @return Decay factor (0, 1.0]
     */
    public static double factor(Instant occurredAt, QueryContext context, ScoringConfig scoring) {
        if (occurredAt == null) {
            return 1.0;
        }

        if (!context.hasTimeRange()) {
            long daysAge = Duration.between(occurredAt, Instant.now()).toDays();
            if (daysAge <= 0) {
                return 1.0;
            }
            return Math.max(
                    scoring.recency().floor(), Math.exp(-scoring.recency().rate() * daysAge));
        }

        Instant start = context.timeRangeStart();
        Instant end = context.timeRangeEnd();
        long daysOutside = 0;

        if (start != null && occurredAt.isBefore(start)) {
            daysOutside = Duration.between(occurredAt, start).toDays();
        } else if (end != null && occurredAt.isAfter(end)) {
            daysOutside = Duration.between(end, occurredAt).toDays();
        }

        if (daysOutside <= 0) {
            return 1.0;
        }

        return Math.max(
                scoring.timeDecay().floor(), Math.exp(-scoring.timeDecay().rate() * daysOutside));
    }

    /**
     * Apply time decay only to BM25-only results (vectorScore == 0)
     *
     * <p>Skips results where occurredAt is null (no time information to decay),
     * avoiding double decay on results that already have vector scores.
     *
     * @param results Results list to process
     * @param context Query context
     * @param scoring Scoring configuration
     * @return Results list after applying decay
     */
    public static List<ScoredResult> applyToBm25Only(
            List<ScoredResult> results, QueryContext context, ScoringConfig scoring) {
        if (results.isEmpty()) {
            return results;
        }

        boolean anyChanged = false;
        List<ScoredResult> updated = new ArrayList<>(results.size());

        for (ScoredResult r : results) {
            if (r.vectorScore() == 0 && r.occurredAt() != null) {
                double decayFactor = factor(r.occurredAt(), context, scoring);
                if (decayFactor < 1.0) {
                    anyChanged = true;
                    updated.add(
                            new ScoredResult(
                                    r.sourceType(),
                                    r.sourceId(),
                                    r.text(),
                                    r.vectorScore(),
                                    r.finalScore() * decayFactor,
                                    r.occurredAt()));
                    continue;
                }
            }
            updated.add(r);
        }

        return anyChanged ? List.copyOf(updated) : results;
    }
}
