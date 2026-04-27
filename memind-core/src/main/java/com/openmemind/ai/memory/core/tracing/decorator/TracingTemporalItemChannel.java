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
package com.openmemind.ai.memory.core.tracing.decorator;

import com.openmemind.ai.memory.core.metrics.MemoryMetricsRecorder;
import com.openmemind.ai.memory.core.metrics.NoopMemoryMetricsRecorder;
import com.openmemind.ai.memory.core.metrics.RetrievalMetricsSupport;
import com.openmemind.ai.memory.core.metrics.RetrievalStageMetrics;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalConstraint;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannel;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannelResult;
import com.openmemind.ai.memory.core.retrieval.temporal.TemporalItemChannelSettings;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalStageTrace;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceOptions;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceSupport;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

/** Tracing decorator for temporal item channel retrieval. */
public final class TracingTemporalItemChannel extends TracingSupport
        implements TemporalItemChannel {

    private final TemporalItemChannel delegate;
    private final MemoryMetricsRecorder metricsRecorder;

    public TracingTemporalItemChannel(TemporalItemChannel delegate, MemoryObserver observer) {
        this(delegate, observer, NoopMemoryMetricsRecorder.INSTANCE);
    }

    public TracingTemporalItemChannel(
            TemporalItemChannel delegate,
            MemoryObserver observer,
            MemoryMetricsRecorder metricsRecorder) {
        super(Objects.requireNonNull(observer, "observer"));
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.metricsRecorder =
                metricsRecorder == null ? NoopMemoryMetricsRecorder.INSTANCE : metricsRecorder;
    }

    @Override
    public Mono<TemporalItemChannelResult> retrieve(
            QueryContext context,
            RetrievalConfig config,
            Optional<TemporalConstraint> temporalConstraint,
            TemporalItemChannelSettings settings) {
        return trace(
                MemorySpanNames.RETRIEVAL_TEMPORAL_CHANNEL,
                Map.of(
                        MemoryAttributes.MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        MemoryAttributes.RETRIEVAL_CHANNEL,
                        "temporal",
                        MemoryAttributes.RETRIEVAL_TEMPORAL_ENABLED,
                        settings != null && settings.enabled(),
                        MemoryAttributes.RETRIEVAL_TEMPORAL_CONSTRAINT_PRESENT,
                        temporalConstraint != null && temporalConstraint.isPresent()),
                result ->
                        Map.of(
                                MemoryAttributes.RETRIEVAL_RESULT_COUNT,
                                result.items().size(),
                                MemoryAttributes.RETRIEVAL_CANDIDATE_COUNT,
                                result.candidateCount(),
                                MemoryAttributes.RETRIEVAL_TEMPORAL_ENABLED,
                                result.enabled(),
                                MemoryAttributes.RETRIEVAL_TEMPORAL_CONSTRAINT_PRESENT,
                                result.constraintPresent(),
                                MemoryAttributes.RETRIEVAL_TEMPORAL_DEGRADED,
                                result.degraded()),
                () ->
                        RetrievalTraceSupport.traceStage(
                                        delegate.retrieve(
                                                context, config, temporalConstraint, settings),
                                        "channel",
                                        "item",
                                        "temporal",
                                        null,
                                        this::temporalStageTrace)
                                .doOnNext(
                                        result ->
                                                recordStage(
                                                        result == null
                                                                ? 0
                                                                : result.candidateCount(),
                                                        result == null || result.items() == null
                                                                ? 0
                                                                : result.items().size(),
                                                        result != null && result.degraded(),
                                                        result == null || !result.enabled(),
                                                        result != null && result.degraded()
                                                                ? "degraded"
                                                                : "success"))
                                .doOnError(ignored -> recordStage(0, 0, false, false, "error")));
    }

    private void recordStage(
            int candidateCount, int resultCount, boolean degraded, boolean skipped, String status) {
        RetrievalMetricsSupport.safeRecord(
                () ->
                        metricsRecorder.recordRetrievalStage(
                                new RetrievalStageMetrics(
                                        null,
                                        "channel",
                                        "item",
                                        "temporal",
                                        status,
                                        null,
                                        candidateCount,
                                        resultCount,
                                        degraded,
                                        skipped,
                                        "core")));
    }

    private RetrievalStageTrace temporalStageTrace(
            TemporalItemChannelResult result,
            String stage,
            String tier,
            String method,
            Integer inputCount,
            java.time.Instant startedAt,
            long durationMillis,
            RetrievalTraceOptions options) {
        List<ScoredResult> results = result == null ? List.of() : result.items();
        return new RetrievalStageTrace(
                stage,
                tier,
                method,
                result != null && result.degraded() ? "degraded" : "success",
                inputCount,
                result == null ? 0 : result.candidateCount(),
                results == null ? 0 : results.size(),
                result != null && result.degraded(),
                result == null || !result.enabled(),
                startedAt,
                durationMillis,
                result == null ? Map.of() : Map.of("constraintPresent", result.constraintPresent()),
                RetrievalTraceSupport.candidates(
                        results, options.maxCandidatesPerStage(), options.maxTextLength()));
    }
}
