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

import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.MEMORY_ID;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_RESULT_COUNT;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_TIER_NAME;
import static com.openmemind.ai.memory.core.tracing.MemoryAttributes.RETRIEVAL_TOP_K;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.metrics.MemoryMetricsRecorder;
import com.openmemind.ai.memory.core.metrics.NoopMemoryMetricsRecorder;
import com.openmemind.ai.memory.core.metrics.RetrievalMetricsSupport;
import com.openmemind.ai.memory.core.metrics.RetrievalStageMetrics;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.tier.InsightTierSearch;
import com.openmemind.ai.memory.core.retrieval.tier.TierResult;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalStageTrace;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceOptions;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceSupport;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/** Tracing decorator for insight tier retrieval. */
public class TracingInsightTierRetriever extends TracingSupport implements InsightTierSearch {

    private final InsightTierSearch delegate;
    private final MemoryMetricsRecorder metricsRecorder;

    public TracingInsightTierRetriever(InsightTierSearch delegate, MemoryObserver observer) {
        this(delegate, observer, NoopMemoryMetricsRecorder.INSTANCE);
    }

    public TracingInsightTierRetriever(
            InsightTierSearch delegate,
            MemoryObserver observer,
            MemoryMetricsRecorder metricsRecorder) {
        super(observer);
        this.delegate = delegate;
        this.metricsRecorder =
                metricsRecorder == null ? NoopMemoryMetricsRecorder.INSTANCE : metricsRecorder;
    }

    @Override
    public Mono<TierResult> retrieve(QueryContext context, RetrievalConfig config) {
        return trace(
                MemorySpanNames.RETRIEVAL_TIER_INSIGHT,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        RETRIEVAL_TIER_NAME,
                        "insight",
                        RETRIEVAL_TOP_K,
                        config.tier1().topK()),
                result -> Map.of(RETRIEVAL_RESULT_COUNT, result.results().size()),
                () ->
                        RetrievalTraceSupport.traceStage(
                                        delegate.retrieve(context, config),
                                        "tier",
                                        "insight",
                                        "vector",
                                        null,
                                        this::tierStageTrace)
                                .doOnNext(
                                        result ->
                                                recordStage(
                                                        result == null || result.results() == null
                                                                ? 0
                                                                : result.results().size(),
                                                        "success"))
                                .doOnError(ignored -> recordStage(0, "error")));
    }

    @Override
    public void invalidateCache(MemoryId memoryId) {
        delegate.invalidateCache(memoryId);
    }

    private void recordStage(int resultCount, String status) {
        RetrievalMetricsSupport.safeRecord(
                () ->
                        metricsRecorder.recordRetrievalStage(
                                new RetrievalStageMetrics(
                                        null,
                                        "tier",
                                        "insight",
                                        "vector",
                                        status,
                                        null,
                                        null,
                                        resultCount,
                                        false,
                                        false,
                                        "core")));
    }

    private RetrievalStageTrace tierStageTrace(
            TierResult result,
            String stage,
            String tier,
            String method,
            Integer inputCount,
            java.time.Instant startedAt,
            long durationMillis,
            RetrievalTraceOptions options) {
        List<ScoredResult> results = result == null ? List.of() : result.results();
        return new RetrievalStageTrace(
                stage,
                tier,
                method,
                "success",
                inputCount,
                null,
                results == null ? 0 : results.size(),
                false,
                false,
                startedAt,
                durationMillis,
                Map.of(),
                RetrievalTraceSupport.candidates(
                        results, options.maxCandidatesPerStage(), options.maxTextLength()));
    }
}
