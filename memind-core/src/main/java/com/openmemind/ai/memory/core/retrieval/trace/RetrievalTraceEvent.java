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
package com.openmemind.ai.memory.core.retrieval.trace;

import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Normalized event emitted by a retrieval-related Observation when it stops.
 *
 * <p>Component observation contexts own the domain-specific data they collected while the
 * operation was running. When Micrometer calls {@link RetrievalTraceObservationHandler#onStop},
 * those contexts convert that data into one of the payload shapes below. Keeping this event small
 * avoids coupling the recorder to every retrieval component's internal result type.
 */
public record RetrievalTraceEvent(
        String observationName,
        String contextualName,
        String status,
        Instant startedAt,
        Instant completedAt,
        long durationMillis,
        Map<String, String> lowCardinality,
        Map<String, String> highCardinality,
        Payload payload) {

    /** Marker for the three trace sections exposed by the debug response. */
    public sealed interface Payload permits StagePayload, MergePayload, FinalPayload {}

    /**
     * A retrieval stage, such as an item tier, insight tier, graph expansion, rerank, or gate.
     *
     * <p>Counts describe the shape of the stage; candidates contain a bounded preview of concrete
     * returned results when that stage naturally produces ranked items.
     */
    public record StagePayload(
            String stage,
            String tier,
            String method,
            Integer inputCount,
            Integer candidateCount,
            Integer resultCount,
            boolean degraded,
            boolean skipped,
            Map<String, Object> attributes,
            List<CandidatePayload> candidates)
            implements Payload {}

    /** Summary of the merge/dedup step that combines stage outputs. */
    public record MergePayload(
            int inputCount, int outputCount, int deduplicatedCount, int sourceCount)
            implements Payload {}

    /** Final retrieval result summary after strategy execution completes. */
    public record FinalPayload(
            String strategy, int itemCount, int insightCount, int rawDataCount, int evidenceCount)
            implements Payload {}

    /** Bounded candidate preview for debug display; full item/raw data stays in the main result. */
    public record CandidatePayload(
            String sourceType, int rank, Double finalScore, Float vectorScore, String preview) {}

    /** Converts ranked results into bounded candidate previews according to request trace limits. */
    public static List<CandidatePayload> candidates(
            List<ScoredResult> results, int maxCandidates, int maxTextLength) {
        if (results == null || maxCandidates <= 0) {
            return List.of();
        }
        return IntStream.range(0, Math.min(maxCandidates, results.size()))
                .mapToObj(index -> candidate(results.get(index), index + 1, maxTextLength))
                .toList();
    }

    private static CandidatePayload candidate(ScoredResult result, int rank, int maxTextLength) {
        return new CandidatePayload(
                result.sourceType() == null ? "unknown" : result.sourceType().name().toLowerCase(),
                rank,
                result.finalScore(),
                result.vectorScore(),
                preview(result.text(), maxTextLength));
    }

    private static String preview(String text, int maxTextLength) {
        if (text == null || maxTextLength <= 0) {
            return null;
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxTextLength) {
            return compact;
        }
        return compact.substring(0, maxTextLength);
    }
}
