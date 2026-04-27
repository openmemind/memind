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
import com.openmemind.ai.memory.core.retrieval.graph.GraphExpansionResult;
import com.openmemind.ai.memory.core.retrieval.graph.GraphItemChannel;
import com.openmemind.ai.memory.core.retrieval.graph.RetrievalGraphSettings;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
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
import reactor.core.publisher.Mono;

/** Tracing decorator for graph item channel retrieval. */
public final class TracingGraphItemChannel extends TracingSupport implements GraphItemChannel {

    private final GraphItemChannel delegate;
    private final MemoryMetricsRecorder metricsRecorder;

    public TracingGraphItemChannel(GraphItemChannel delegate, MemoryObserver observer) {
        this(delegate, observer, NoopMemoryMetricsRecorder.INSTANCE);
    }

    public TracingGraphItemChannel(
            GraphItemChannel delegate,
            MemoryObserver observer,
            MemoryMetricsRecorder metricsRecorder) {
        super(Objects.requireNonNull(observer, "observer"));
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.metricsRecorder =
                metricsRecorder == null ? NoopMemoryMetricsRecorder.INSTANCE : metricsRecorder;
    }

    @Override
    public Mono<GraphExpansionResult> retrieve(
            QueryContext context,
            RetrievalConfig config,
            RetrievalGraphSettings settings,
            List<ScoredResult> seeds) {
        return trace(
                MemorySpanNames.RETRIEVAL_GRAPH_CHANNEL,
                Map.of(
                        MemoryAttributes.MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        MemoryAttributes.RETRIEVAL_CHANNEL,
                        "graph",
                        MemoryAttributes.RETRIEVAL_GRAPH_ENABLED,
                        settings != null && settings.enabled(),
                        MemoryAttributes.RETRIEVAL_GRAPH_SEED_COUNT,
                        seeds == null ? 0 : seeds.size()),
                result ->
                        Map.of(
                                MemoryAttributes.RETRIEVAL_RESULT_COUNT,
                                result.graphItems().size(),
                                MemoryAttributes.RETRIEVAL_GRAPH_LINK_EXPANSION_COUNT,
                                result.linkExpansionCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_ENTITY_EXPANSION_COUNT,
                                result.entityExpansionCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_DEDUPED_CANDIDATE_COUNT,
                                result.dedupedCandidateCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_OVERLAP_COUNT,
                                result.overlapCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_SKIPPED_OVERFANOUT_ENTITY_COUNT,
                                result.skippedOverFanoutEntityCount(),
                                MemoryAttributes.RETRIEVAL_GRAPH_TIMEOUT,
                                result.timedOut(),
                                MemoryAttributes.RETRIEVAL_GRAPH_DEGRADED,
                                result.degraded()),
                () ->
                        RetrievalTraceSupport.traceStage(
                                        delegate.retrieve(context, config, settings, seeds),
                                        "channel",
                                        "item",
                                        "graph",
                                        seeds == null ? 0 : seeds.size(),
                                        this::graphStageTrace)
                                .doOnNext(
                                        result ->
                                                recordStage(
                                                        seeds,
                                                        result == null
                                                                ? 0
                                                                : result.dedupedCandidateCount(),
                                                        result == null
                                                                        || result.graphItems()
                                                                                == null
                                                                ? 0
                                                                : result.graphItems().size(),
                                                        result != null && result.degraded(),
                                                        result == null || !result.enabled(),
                                                        result != null && result.degraded()
                                                                ? "degraded"
                                                                : "success"))
                                .doOnError(
                                        ignored ->
                                                recordStage(seeds, 0, 0, false, false, "error")));
    }

    private void recordStage(
            List<ScoredResult> seeds,
            int candidateCount,
            int resultCount,
            boolean degraded,
            boolean skipped,
            String status) {
        RetrievalMetricsSupport.safeRecord(
                () ->
                        metricsRecorder.recordRetrievalStage(
                                new RetrievalStageMetrics(
                                        null,
                                        "channel",
                                        "item",
                                        "graph",
                                        status,
                                        seeds == null ? 0 : seeds.size(),
                                        candidateCount,
                                        resultCount,
                                        degraded,
                                        skipped,
                                        "core")));
    }

    private RetrievalStageTrace graphStageTrace(
            GraphExpansionResult result,
            String stage,
            String tier,
            String method,
            Integer inputCount,
            java.time.Instant startedAt,
            long durationMillis,
            RetrievalTraceOptions options) {
        List<ScoredResult> results = result == null ? List.of() : result.graphItems();
        return new RetrievalStageTrace(
                stage,
                tier,
                method,
                result != null && result.degraded() ? "degraded" : "success",
                inputCount,
                result == null ? 0 : result.dedupedCandidateCount(),
                results == null ? 0 : results.size(),
                result != null && result.degraded(),
                result == null || !result.enabled(),
                startedAt,
                durationMillis,
                result == null
                        ? Map.of()
                        : Map.of(
                                "linkExpansionCount",
                                result.linkExpansionCount(),
                                "entityExpansionCount",
                                result.entityExpansionCount(),
                                "overlapCount",
                                result.overlapCount(),
                                "timedOut",
                                result.timedOut()),
                RetrievalTraceSupport.candidates(
                        results, options.maxCandidatesPerStage(), options.maxTextLength()));
    }
}
