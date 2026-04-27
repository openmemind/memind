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

import com.openmemind.ai.memory.core.metrics.MemoryMetricsRecorder;
import com.openmemind.ai.memory.core.metrics.NoopMemoryMetricsRecorder;
import com.openmemind.ai.memory.core.metrics.RetrievalMetricsSupport;
import com.openmemind.ai.memory.core.metrics.RetrievalStageMetrics;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoringConfig;
import com.openmemind.ai.memory.core.retrieval.tier.ItemTierSearch;
import com.openmemind.ai.memory.core.retrieval.tier.TierResult;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalStageTrace;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceOptions;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceSupport;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/** Tracing decorator for item tier retrieval operations. */
public class TracingItemTierRetriever extends TracingSupport implements ItemTierSearch {

    private final ItemTierSearch delegate;
    private final MemoryMetricsRecorder metricsRecorder;

    public TracingItemTierRetriever(ItemTierSearch delegate, MemoryObserver observer) {
        this(delegate, observer, NoopMemoryMetricsRecorder.INSTANCE);
    }

    public TracingItemTierRetriever(
            ItemTierSearch delegate,
            MemoryObserver observer,
            MemoryMetricsRecorder metricsRecorder) {
        super(observer);
        this.delegate = delegate;
        this.metricsRecorder =
                metricsRecorder == null ? NoopMemoryMetricsRecorder.INSTANCE : metricsRecorder;
    }

    @Override
    public Mono<TierResult> searchByVector(QueryContext context, RetrievalConfig config) {
        return trace(
                MemorySpanNames.RETRIEVAL_VECTOR_SEARCH,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        RETRIEVAL_TIER_NAME,
                        "item",
                        RETRIEVAL_TOP_K,
                        config.tier2().topK()),
                result -> Map.of(RETRIEVAL_RESULT_COUNT, result.results().size()),
                () ->
                        RetrievalTraceSupport.traceStage(
                                        delegate.searchByVector(context, config),
                                        "tier",
                                        "item",
                                        "vector",
                                        null,
                                        this::tierStageTrace)
                                .doOnNext(
                                        result ->
                                                recordStage(
                                                        "tier",
                                                        "vector",
                                                        null,
                                                        result.results().size(),
                                                        false,
                                                        false,
                                                        "success"))
                                .doOnError(
                                        ignored ->
                                                recordStage(
                                                        "tier", "vector", null, 0, false, false,
                                                        "error")));
    }

    @Override
    public MemoryTextSearch textSearch() {
        return delegate.textSearch();
    }

    @Override
    public Mono<List<ScoredResult>> searchByVector(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring) {
        return trace(
                MemorySpanNames.RETRIEVAL_VECTOR_SEARCH,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        RETRIEVAL_TIER_NAME,
                        "item",
                        RETRIEVAL_TOP_K,
                        tier.topK()),
                result -> Map.of(RETRIEVAL_RESULT_COUNT, result.size()),
                () ->
                        recordListStage(
                                delegate.searchByVector(context, tier, scoring),
                                context,
                                "vector"));
    }

    @Override
    public Mono<List<ScoredResult>> searchByKeyword(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring) {
        return trace(
                MemorySpanNames.RETRIEVAL_KEYWORD_SEARCH,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        RETRIEVAL_TIER_NAME,
                        "item",
                        RETRIEVAL_TOP_K,
                        tier.topK()),
                result -> Map.of(RETRIEVAL_RESULT_COUNT, result.size()),
                () ->
                        recordListStage(
                                delegate.searchByKeyword(context, tier, scoring),
                                context,
                                "keyword"));
    }

    @Override
    public Mono<List<ScoredResult>> searchHybrid(
            QueryContext context, RetrievalConfig.TierConfig tier, ScoringConfig scoring) {
        return trace(
                MemorySpanNames.RETRIEVAL_TIER_ITEM,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        RETRIEVAL_TIER_NAME,
                        "item",
                        RETRIEVAL_TOP_K,
                        tier.topK()),
                result -> Map.of(RETRIEVAL_RESULT_COUNT, result.size()),
                () ->
                        recordListStage(
                                delegate.searchHybrid(context, tier, scoring), context, "hybrid"));
    }

    private Mono<List<ScoredResult>> recordListStage(
            Mono<List<ScoredResult>> operation, QueryContext context, String method) {
        return RetrievalTraceSupport.traceStage(
                        operation, "tier", "item", method, null, this::listStageTrace)
                .doOnNext(
                        result ->
                                recordStage(
                                        "tier",
                                        method,
                                        null,
                                        result == null ? 0 : result.size(),
                                        false,
                                        false,
                                        "success"))
                .doOnError(ignored -> recordStage("tier", method, null, 0, false, false, "error"));
    }

    private void recordStage(
            String stage,
            String method,
            Integer candidateCount,
            Integer resultCount,
            boolean degraded,
            boolean skipped,
            String status) {
        RetrievalMetricsSupport.safeRecord(
                () ->
                        metricsRecorder.recordRetrievalStage(
                                new RetrievalStageMetrics(
                                        null,
                                        stage,
                                        "item",
                                        method,
                                        status,
                                        null,
                                        candidateCount,
                                        resultCount,
                                        degraded,
                                        skipped,
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

    private RetrievalStageTrace listStageTrace(
            List<ScoredResult> result,
            String stage,
            String tier,
            String method,
            Integer inputCount,
            java.time.Instant startedAt,
            long durationMillis,
            RetrievalTraceOptions options) {
        return new RetrievalStageTrace(
                stage,
                tier,
                method,
                "success",
                inputCount,
                null,
                result == null ? 0 : result.size(),
                false,
                false,
                startedAt,
                durationMillis,
                Map.of(),
                RetrievalTraceSupport.candidates(
                        result, options.maxCandidatesPerStage(), options.maxTextLength()));
    }
}
