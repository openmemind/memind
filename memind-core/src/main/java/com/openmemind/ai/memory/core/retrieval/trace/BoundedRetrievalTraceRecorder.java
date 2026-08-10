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

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory recorder for one retrieval response trace.
 *
 * <p>The recorder can be called from different Reactor worker threads because retrieval stages may
 * run concurrently. Stage events are stored in a concurrent queue and ordered by start time only
 * when the server builds the response snapshot. Merge and final result summaries are single-slot
 * values because each retrieval should expose only the latest aggregate summary for those sections.
 */
public final class BoundedRetrievalTraceRecorder implements RetrievalTraceRecorder {

    private final String traceId;
    private final Instant startedAt;
    private final RetrievalTraceOptions options;
    private final Queue<RetrievalStageTrace> stages = new ConcurrentLinkedQueue<>();
    private final AtomicInteger stageCount = new AtomicInteger();
    private final AtomicBoolean truncated = new AtomicBoolean();
    private final AtomicReference<RetrievalMergeTrace> merge = new AtomicReference<>();
    private final AtomicReference<RetrievalFinalTrace> finalResults = new AtomicReference<>();

    public BoundedRetrievalTraceRecorder(RetrievalTraceOptions options) {
        this(UUID.randomUUID().toString(), Instant.now(), options);
    }

    public BoundedRetrievalTraceRecorder(
            String traceId, Instant startedAt, RetrievalTraceOptions options) {
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.options = options == null ? RetrievalTraceOptions.defaults() : options;
    }

    @Override
    public void record(RetrievalTraceEvent event) {
        if (event == null || event.payload() == null) {
            return;
        }
        // The recorder owns the public debug model; observations only emit normalized events.
        switch (event.payload()) {
            case RetrievalTraceEvent.StagePayload stage -> recordStage(toStageTrace(event, stage));
            case RetrievalTraceEvent.MergePayload merge -> recordMerge(toMergeTrace(event, merge));
            case RetrievalTraceEvent.FinalPayload finalPayload ->
                    recordFinal(toFinalTrace(event, finalPayload));
        }
    }

    private void recordStage(RetrievalStageTrace event) {
        if (event == null) {
            return;
        }
        // The response trace is bounded so a pathological retrieval cannot create a huge payload.
        if (stageCount.incrementAndGet() > options.maxStages()) {
            truncated.set(true);
            return;
        }
        stages.add(event);
    }

    private void recordMerge(RetrievalMergeTrace event) {
        if (event != null) {
            merge.set(event);
        }
    }

    private void recordFinal(RetrievalFinalTrace event) {
        if (event != null) {
            finalResults.set(event);
        }
    }

    @Override
    public Optional<RetrievalDebugTrace> snapshot() {
        // Stage observations may finish on different threads; sort once for deterministic output.
        List<RetrievalStageTrace> orderedStages =
                stages.stream()
                        .sorted(Comparator.comparing(RetrievalStageTrace::startedAt))
                        .toList();
        return Optional.of(
                new RetrievalDebugTrace(
                        traceId,
                        startedAt,
                        Instant.now(),
                        truncated.get(),
                        orderedStages,
                        merge.get(),
                        finalResults.get()));
    }

    public RetrievalTraceOptions options() {
        return options;
    }

    private RetrievalStageTrace toStageTrace(
            RetrievalTraceEvent event, RetrievalTraceEvent.StagePayload payload) {
        return new RetrievalStageTrace(
                payload.stage(),
                payload.tier(),
                payload.method(),
                event.status(),
                payload.inputCount(),
                payload.candidateCount(),
                payload.resultCount(),
                payload.degraded(),
                payload.skipped(),
                event.startedAt(),
                event.durationMillis(),
                payload.attributes() == null ? Map.of() : payload.attributes(),
                toCandidateTraces(payload.candidates()));
    }

    private RetrievalMergeTrace toMergeTrace(
            RetrievalTraceEvent event, RetrievalTraceEvent.MergePayload payload) {
        return new RetrievalMergeTrace(
                payload.inputCount(),
                payload.outputCount(),
                payload.deduplicatedCount(),
                payload.sourceCount(),
                event.status());
    }

    private RetrievalFinalTrace toFinalTrace(
            RetrievalTraceEvent event, RetrievalTraceEvent.FinalPayload payload) {
        return new RetrievalFinalTrace(
                payload.strategy(),
                event.status(),
                payload.itemCount(),
                payload.insightCount(),
                payload.rawDataCount(),
                payload.evidenceCount());
    }

    private List<RetrievalCandidateTrace> toCandidateTraces(
            List<RetrievalTraceEvent.CandidatePayload> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(
                        candidate ->
                                new RetrievalCandidateTrace(
                                        candidate.sourceType(),
                                        candidate.rank(),
                                        candidate.finalScore(),
                                        candidate.vectorScore(),
                                        candidate.preview()))
                .toList();
    }
}
