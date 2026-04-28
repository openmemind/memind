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
package com.openmemind.ai.memory.server.service.memory;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.trace.BoundedRetrievalTraceCollector;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalDebugTrace;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceContext;
import com.openmemind.ai.memory.core.retrieval.trace.RetrievalTraceOptions;
import com.openmemind.ai.memory.server.configuration.MemindServerObservabilityProperties;
import com.openmemind.ai.memory.server.domain.memory.request.AddMessageRequest;
import com.openmemind.ai.memory.server.domain.memory.request.CommitMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.ExtractMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.RetrieveMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.response.AddMessageResponse;
import com.openmemind.ai.memory.server.domain.memory.response.ExtractMemoryResponse;
import com.openmemind.ai.memory.server.domain.memory.response.RetrievalTraceView;
import com.openmemind.ai.memory.server.domain.memory.response.RetrieveMemoryResponse;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class OpenMemoryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OpenMemoryApplicationService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_SOURCE_CLIENT = "api";

    private final MemoryRuntimeManager runtimeManager;
    private final MemindServerObservabilityProperties observabilityProperties;

    public OpenMemoryApplicationService(MemoryRuntimeManager runtimeManager) {
        this(runtimeManager, new MemindServerObservabilityProperties());
    }

    @Autowired
    public OpenMemoryApplicationService(
            MemoryRuntimeManager runtimeManager,
            MemindServerObservabilityProperties observabilityProperties) {
        this.runtimeManager = runtimeManager;
        this.observabilityProperties = observabilityProperties;
    }

    public void extractAsync(ExtractMemoryRequest request) {
        MemoryId memoryId = DefaultMemoryId.of(request.userId(), request.agentId());
        dispatchAsync(
                "extract",
                memoryId,
                lease -> lease.handle().memory().extract(extractionRequest(memoryId, request)));
    }

    public ExtractMemoryResponse extract(ExtractMemoryRequest request) {
        try (var lease = runtimeManager.acquire()) {
            ExtractionResult result =
                    Objects.requireNonNull(
                            lease.handle()
                                    .memory()
                                    .extract(
                                            extractionRequest(
                                                    DefaultMemoryId.of(
                                                            request.userId(), request.agentId()),
                                                    request))
                                    .block(REQUEST_TIMEOUT),
                            "Memory extract returned no result");
            return toExtractResponse(result);
        }
    }

    public void addMessageAsync(AddMessageRequest request) {
        MemoryId memoryId = DefaultMemoryId.of(request.userId(), request.agentId());
        dispatchAsync(
                "add-message",
                memoryId,
                lease ->
                        lease.handle()
                                .memory()
                                .addMessage(
                                        memoryId,
                                        request.message()
                                                .withSourceClient(
                                                        normalizeSourceClient(
                                                                request.sourceClient()))));
    }

    public AddMessageResponse addMessage(AddMessageRequest request) {
        try (var lease = runtimeManager.acquire()) {
            var result =
                    lease.handle()
                            .memory()
                            .addMessage(
                                    DefaultMemoryId.of(request.userId(), request.agentId()),
                                    request.message()
                                            .withSourceClient(
                                                    normalizeSourceClient(request.sourceClient())))
                            .blockOptional(REQUEST_TIMEOUT);
            return result.map(value -> new AddMessageResponse(true, toExtractResponse(value)))
                    .orElseGet(() -> new AddMessageResponse(false, null));
        }
    }

    public void commitAsync(CommitMemoryRequest request) {
        MemoryId memoryId = DefaultMemoryId.of(request.userId(), request.agentId());
        dispatchAsync(
                "commit",
                memoryId,
                lease ->
                        lease.handle()
                                .memory()
                                .commit(memoryId, normalizeSourceClient(request.sourceClient())));
    }

    public ExtractMemoryResponse commit(CommitMemoryRequest request) {
        try (var lease = runtimeManager.acquire()) {
            ExtractionResult result =
                    Objects.requireNonNull(
                            lease.handle()
                                    .memory()
                                    .commit(
                                            DefaultMemoryId.of(request.userId(), request.agentId()),
                                            normalizeSourceClient(request.sourceClient()))
                                    .block(REQUEST_TIMEOUT),
                            "Memory commit returned no result");
            return toExtractResponse(result);
        }
    }

    public RetrieveMemoryResponse retrieve(RetrieveMemoryRequest request) {
        try (var lease = runtimeManager.acquire()) {
            BoundedRetrievalTraceCollector traceCollector = traceCollector(request);
            RetrievalResult result =
                    Objects.requireNonNull(
                            retrievalMono(lease.handle().memory(), request, traceCollector)
                                    .block(REQUEST_TIMEOUT),
                            "Memory retrieve returned no result");
            return toRetrieveResponse(result, traceCollector);
        }
    }

    private Mono<RetrievalResult> retrievalMono(
            Memory memory,
            RetrieveMemoryRequest request,
            BoundedRetrievalTraceCollector traceCollector) {
        Mono<RetrievalResult> operation =
                memory.retrieve(
                        DefaultMemoryId.of(request.userId(), request.agentId()),
                        request.query(),
                        request.strategy());
        if (traceCollector == null) {
            return operation;
        }
        return operation.contextWrite(
                context -> RetrievalTraceContext.withCollector(context, traceCollector));
    }

    private static ExtractionRequest extractionRequest(
            MemoryId memoryId, ExtractMemoryRequest request) {
        return ExtractionRequest.of(memoryId, request.rawContent())
                .withMetadata("sourceClient", normalizeSourceClient(request.sourceClient()));
    }

    private static String normalizeSourceClient(String sourceClient) {
        if (sourceClient == null || sourceClient.isBlank()) {
            return DEFAULT_SOURCE_CLIENT;
        }
        return sourceClient.trim();
    }

    private BoundedRetrievalTraceCollector traceCollector(RetrieveMemoryRequest request) {
        if (!Boolean.TRUE.equals(request.trace())
                || !observabilityProperties.getRetrievalTrace().isEnabled()) {
            return null;
        }
        var properties = observabilityProperties.getRetrievalTrace();
        return new BoundedRetrievalTraceCollector(
                new RetrievalTraceOptions(
                        properties.getMaxStages(),
                        properties.getMaxCandidatesPerStage(),
                        properties.getMaxTextLength()));
    }

    private void dispatchAsync(
            String operation,
            MemoryId memoryId,
            Function<com.openmemind.ai.memory.server.runtime.RuntimeLease, Mono<? extends Object>>
                    invocation) {
        var lease = runtimeManager.acquire();
        Mono.defer(() -> invocation.apply(lease))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(REQUEST_TIMEOUT)
                .doFinally(signal -> lease.close())
                .subscribe(
                        ignored -> {},
                        error ->
                                log.warn(
                                        "Open API {} failed asynchronously for memoryId={}",
                                        operation,
                                        memoryId.toIdentifier(),
                                        error));
    }

    private static ExtractMemoryResponse toExtractResponse(ExtractionResult result) {
        return new ExtractMemoryResponse(
                result.status().name(),
                rawDataIds(result),
                itemIds(result),
                insightIds(result),
                result.insightPending(),
                result.duration() != null ? result.duration().toMillis() : null,
                result.errorMessage());
    }

    private static List<String> rawDataIds(ExtractionResult result) {
        if (result.rawDataResult() == null || result.rawDataResult().rawDataList() == null) {
            return List.of();
        }
        return result.rawDataResult().rawDataList().stream().map(MemoryRawData::id).toList();
    }

    private static List<Long> itemIds(ExtractionResult result) {
        if (result.memoryItemResult() == null || result.memoryItemResult().newItems() == null) {
            return List.of();
        }
        return result.memoryItemResult().newItems().stream().map(MemoryItem::id).toList();
    }

    private static List<Long> insightIds(ExtractionResult result) {
        if (result.insightResult() == null || result.insightResult().insights() == null) {
            return List.of();
        }
        return result.insightResult().insights().stream().map(MemoryInsight::id).toList();
    }

    private static RetrieveMemoryResponse toRetrieveResponse(
            RetrievalResult result, BoundedRetrievalTraceCollector traceCollector) {
        return new RetrieveMemoryResponse(
                result.items() == null
                        ? List.of()
                        : result.items().stream()
                                .map(OpenMemoryApplicationService::toRetrievedItemView)
                                .toList(),
                result.insights() == null
                        ? List.of()
                        : result.insights().stream()
                                .map(
                                        insight ->
                                                new RetrieveMemoryResponse.RetrievedInsightView(
                                                        insight.id(),
                                                        insight.text(),
                                                        insight.tier() != null
                                                                ? insight.tier().name()
                                                                : null))
                                .toList(),
                result.rawData() == null
                        ? List.of()
                        : result.rawData().stream()
                                .map(
                                        rawData ->
                                                new RetrieveMemoryResponse.RetrievedRawDataView(
                                                        rawData.rawDataId(),
                                                        rawData.caption(),
                                                        rawData.maxScore(),
                                                        rawData.itemIds()))
                                .toList(),
                result.evidences() == null ? List.of() : result.evidences(),
                result.strategy(),
                result.query(),
                traceCollector == null
                        ? null
                        : traceCollector
                                .snapshot()
                                .map(OpenMemoryApplicationService::toTraceView)
                                .orElse(null));
    }

    private static RetrievalTraceView toTraceView(RetrievalDebugTrace trace) {
        return new RetrievalTraceView(
                trace.traceId(),
                trace.startedAt(),
                trace.completedAt(),
                trace.truncated(),
                trace.stages().stream()
                        .map(
                                stage ->
                                        new RetrievalTraceView.StageView(
                                                stage.stage(),
                                                stage.tier(),
                                                stage.method(),
                                                stage.status(),
                                                stage.inputCount(),
                                                stage.candidateCount(),
                                                stage.resultCount(),
                                                stage.degraded(),
                                                stage.skipped(),
                                                stage.startedAt(),
                                                stage.durationMillis(),
                                                stage.attributes(),
                                                stage.candidates()))
                        .toList(),
                RetrievalTraceView.MergeView.from(trace.merge()),
                RetrievalTraceView.FinalView.from(trace.finalResults()));
    }

    private static RetrieveMemoryResponse.RetrievedItemView toRetrievedItemView(ScoredResult item) {
        return new RetrieveMemoryResponse.RetrievedItemView(
                item.sourceId(),
                item.text(),
                item.vectorScore(),
                item.finalScore(),
                item.occurredAt());
    }
}
