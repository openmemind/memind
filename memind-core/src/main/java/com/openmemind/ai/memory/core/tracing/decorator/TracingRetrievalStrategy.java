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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.metrics.MemoryMetricsRecorder;
import com.openmemind.ai.memory.core.metrics.NoopMemoryMetricsRecorder;
import com.openmemind.ai.memory.core.metrics.RetrievalMetricsSupport;
import com.openmemind.ai.memory.core.metrics.RetrievalSummaryMetrics;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.strategy.RetrievalStrategy;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalFinalTrace;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceCollector;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceContext;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link RetrievalStrategy}.
 *
 * <p>{@link #retrieve} wraps with observer, recording span and attributes;
 * {@link #name()} and {@link #onDataChanged} delegate directly, no tracking needed.
 */
public class TracingRetrievalStrategy extends TracingSupport implements RetrievalStrategy {

    private final RetrievalStrategy delegate;
    private final MemoryMetricsRecorder metricsRecorder;

    public TracingRetrievalStrategy(RetrievalStrategy delegate, MemoryObserver observer) {
        this(delegate, observer, NoopMemoryMetricsRecorder.INSTANCE);
    }

    public TracingRetrievalStrategy(
            RetrievalStrategy delegate,
            MemoryObserver observer,
            MemoryMetricsRecorder metricsRecorder) {
        super(observer);
        this.delegate = delegate;
        this.metricsRecorder =
                metricsRecorder == null ? NoopMemoryMetricsRecorder.INSTANCE : metricsRecorder;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public Mono<RetrievalResult> retrieve(QueryContext context, RetrievalConfig config) {
        return trace(
                MemorySpanNames.RETRIEVAL_STRATEGY,
                Map.of(
                        MEMORY_ID,
                        context.memoryId().toIdentifier(),
                        MemoryAttributes.RETRIEVAL_STRATEGY,
                        delegate.name()),
                r -> Map.of(RETRIEVAL_RESULT_COUNT, r.items().size()),
                () ->
                        Mono.deferContextual(
                                reactorContext -> {
                                    RetrievalTraceCollector collector =
                                            RetrievalTraceContext.collector(reactorContext);
                                    return delegate.retrieve(context, config)
                                            .doOnNext(result -> recordSummary(collector, result))
                                            .doOnError(ignored -> recordSummaryError(collector));
                                }));
    }

    @Override
    public void onDataChanged(MemoryId memoryId) {
        delegate.onDataChanged(memoryId);
    }

    private void recordSummary(RetrievalTraceCollector collector, RetrievalResult result) {
        RetrievalSummaryMetrics metrics =
                RetrievalMetricsSupport.summary(delegate.name(), result, "core");
        RetrievalMetricsSupport.safeRecord(() -> metricsRecorder.recordRetrievalSummary(metrics));
        RetrievalMetricsSupport.safeRecord(
                () ->
                        collector.finalResults(
                                new RetrievalFinalTrace(
                                        metrics.strategy(),
                                        metrics.status(),
                                        metrics.itemCount(),
                                        metrics.insightCount(),
                                        metrics.rawDataCount(),
                                        metrics.evidenceCount())));
    }

    private void recordSummaryError(RetrievalTraceCollector collector) {
        RetrievalSummaryMetrics metrics =
                new RetrievalSummaryMetrics(delegate.name(), "error", 0, 0, 0, 0, "core");
        RetrievalMetricsSupport.safeRecord(() -> metricsRecorder.recordRetrievalSummary(metrics));
        RetrievalMetricsSupport.safeRecord(
                () ->
                        collector.finalResults(
                                new RetrievalFinalTrace(metrics.strategy(), "error", 0, 0, 0, 0)));
    }
}
