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

    public sealed interface Payload permits StagePayload, MergePayload, FinalPayload {}

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

    public record MergePayload(
            int inputCount, int outputCount, int deduplicatedCount, int sourceCount)
            implements Payload {}

    public record FinalPayload(
            String strategy, int itemCount, int insightCount, int rawDataCount, int evidenceCount)
            implements Payload {}

    public record CandidatePayload(
            String sourceType, int rank, Double finalScore, Float vectorScore, String preview) {}

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
