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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public final class RetrievalTraceSupport {

    private RetrievalTraceSupport() {}

    public static <T> Mono<T> traceStage(
            Mono<T> operation,
            String stage,
            String tier,
            String method,
            Integer inputCount,
            StageResultMapper<T> mapper) {
        return Mono.deferContextual(
                context -> {
                    RetrievalTraceCollector collector = RetrievalTraceContext.collector(context);
                    if (collector instanceof NoopRetrievalTraceCollector) {
                        return operation;
                    }
                    RetrievalTraceOptions options =
                            collector instanceof BoundedRetrievalTraceCollector bounded
                                    ? bounded.options()
                                    : RetrievalTraceOptions.defaults();
                    Instant startedAt = Instant.now();
                    return operation
                            .doOnNext(
                                    result ->
                                            collector.stageCompleted(
                                                    mapper.toStageTrace(
                                                            result,
                                                            stage,
                                                            tier,
                                                            method,
                                                            inputCount,
                                                            startedAt,
                                                            elapsedMillis(startedAt),
                                                            options)))
                            .doOnError(
                                    ignored ->
                                            collector.stageCompleted(
                                                    new RetrievalStageTrace(
                                                            stage,
                                                            tier,
                                                            method,
                                                            "error",
                                                            inputCount,
                                                            null,
                                                            0,
                                                            false,
                                                            false,
                                                            startedAt,
                                                            elapsedMillis(startedAt),
                                                            Map.of(),
                                                            List.of())));
                });
    }

    public static List<RetrievalCandidateTrace> candidates(
            List<ScoredResult> results, int maxCandidates, int maxTextLength) {
        if (results == null || maxCandidates <= 0) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, Math.min(maxCandidates, results.size()))
                .mapToObj(index -> candidate(results.get(index), index + 1, maxTextLength))
                .toList();
    }

    private static RetrievalCandidateTrace candidate(
            ScoredResult result, int rank, int maxTextLength) {
        return new RetrievalCandidateTrace(
                result.sourceType() == null ? "unknown" : result.sourceType().name().toLowerCase(),
                rank,
                result.finalScore(),
                result.vectorScore(),
                preview(result.text(), maxTextLength));
    }

    public static long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    public static String preview(String text, int maxTextLength) {
        if (text == null || maxTextLength <= 0) {
            return null;
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxTextLength) {
            return compact;
        }
        return compact.substring(0, maxTextLength);
    }

    @FunctionalInterface
    public interface StageResultMapper<T> {
        RetrievalStageTrace toStageTrace(
                T result,
                String stage,
                String tier,
                String method,
                Integer inputCount,
                Instant startedAt,
                long durationMillis,
                RetrievalTraceOptions options);
    }
}
