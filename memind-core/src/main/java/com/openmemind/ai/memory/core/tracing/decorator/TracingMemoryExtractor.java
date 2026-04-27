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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.MemoryExtractor;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.metrics.ExtractionMetrics;
import com.openmemind.ai.memory.core.metrics.ExtractionMetricsExtractor;
import com.openmemind.ai.memory.core.metrics.MemoryMetricsRecorder;
import com.openmemind.ai.memory.core.metrics.NoopMemoryMetricsRecorder;
import com.openmemind.ai.memory.core.metrics.RetrievalMetricsSupport;
import com.openmemind.ai.memory.core.tracing.MemoryAttributes;
import com.openmemind.ai.memory.core.tracing.MemoryObserver;
import com.openmemind.ai.memory.core.tracing.MemorySpanNames;
import com.openmemind.ai.memory.core.tracing.TracingSupport;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A decorator that adds observability to {@link MemoryExtractor}.
 *
 * <p>All extraction methods are wrapped by the observer to record spans and attributes.
 */
public class TracingMemoryExtractor extends TracingSupport implements MemoryExtractor {

    private final MemoryExtractor delegate;
    private final MemoryMetricsRecorder metricsRecorder;

    public TracingMemoryExtractor(MemoryExtractor delegate, MemoryObserver observer) {
        this(delegate, observer, NoopMemoryMetricsRecorder.INSTANCE);
    }

    public TracingMemoryExtractor(
            MemoryExtractor delegate,
            MemoryObserver observer,
            MemoryMetricsRecorder metricsRecorder) {
        super(observer);
        this.delegate = delegate;
        this.metricsRecorder =
                metricsRecorder == null ? NoopMemoryMetricsRecorder.INSTANCE : metricsRecorder;
    }

    @Override
    public Mono<ExtractionResult> extract(ExtractionRequest request) {
        return trace(
                MemorySpanNames.EXTRACTION,
                Map.of(MemoryAttributes.MEMORY_ID, request.memoryId().toIdentifier()),
                () -> recordExtraction(delegate.extract(request)));
    }

    @Override
    public Mono<ExtractionResult> addMessage(
            MemoryId memoryId, Message message, ExtractionConfig config) {
        return trace(
                MemorySpanNames.EXTRACTION,
                Map.of(MemoryAttributes.MEMORY_ID, memoryId.toIdentifier()),
                () -> recordExtraction(delegate.addMessage(memoryId, message, config)));
    }

    private Mono<ExtractionResult> recordExtraction(Mono<ExtractionResult> operation) {
        return operation
                .doOnNext(
                        result ->
                                RetrievalMetricsSupport.safeRecord(
                                        () ->
                                                metricsRecorder.recordExtractionSummary(
                                                        ExtractionMetricsExtractor.extract(
                                                                result, "core"))))
                .doOnError(
                        ignored ->
                                RetrievalMetricsSupport.safeRecord(
                                        () ->
                                                metricsRecorder.recordExtractionSummary(
                                                        new ExtractionMetrics(
                                                                "error", 0, null, 0, null, 0, null,
                                                                null, null, "core"))));
    }
}
