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
package com.openmemind.ai.memory.core.extraction;

import com.openmemind.ai.memory.core.buffer.ConversationBufferLocks;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.context.CommitDecision;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectionContext;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectionInput;
import com.openmemind.ai.memory.core.extraction.context.ContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.SegmentBudgetEnforcer;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.core.extraction.result.InsightResult;
import com.openmemind.ai.memory.core.extraction.result.MemoryItemResult;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import com.openmemind.ai.memory.core.extraction.step.SegmentProcessor;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicyRegistry;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.resource.ResourceRef;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.utils.HashUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Memory Extractor
 *
 * <p>Executes a three-stage memory extraction process: RawData → MemoryItem → Insight
 *
 */
public class DefaultMemoryExtractor implements MemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryExtractor.class);

    private final RawDataExtractStep rawDataStep;
    private final MemoryItemExtractStep memoryItemStep;
    private final InsightExtractStep insightStep;
    private final SegmentProcessor segmentProcessor;
    private final ContextCommitDetector contextCommitDetector;
    private final PendingConversationBuffer pendingConversationBuffer;
    private final RecentConversationBuffer recentConversationBuffer;
    private final ContentParserRegistry contentParserRegistry;
    private final ResourceStore resourceStore;
    private final ResourceFetcher resourceFetcher;
    private final RawDataIngestionPolicyRegistry ingestionPolicyRegistry;
    private final ExtractionRequestResolver requestResolver;
    private final RawDataExtractionOptions rawDataExtractionOptions;
    private final ItemExtractionOptions itemExtractionOptions;
    private final RawContentProcessorRegistry rawContentProcessorRegistry;
    private final SegmentBudgetEnforcer segmentBudgetEnforcer;

    public DefaultMemoryExtractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            InsightExtractStep insightStep,
            SegmentProcessor segmentProcessor,
            ContextCommitDetector contextCommitDetector,
            PendingConversationBuffer pendingConversationBuffer,
            RecentConversationBuffer recentConversationBuffer) {
        this(
                rawDataStep,
                memoryItemStep,
                insightStep,
                segmentProcessor,
                contextCommitDetector,
                pendingConversationBuffer,
                recentConversationBuffer,
                null,
                null,
                null,
                null,
                null,
                RawDataIngestionPolicyRegistry.empty(),
                RawDataExtractionOptions.defaults(),
                ItemExtractionOptions.defaults());
    }

    public DefaultMemoryExtractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            InsightExtractStep insightStep,
            SegmentProcessor segmentProcessor,
            ContextCommitDetector contextCommitDetector,
            PendingConversationBuffer pendingConversationBuffer,
            RecentConversationBuffer recentConversationBuffer,
            RawContentProcessorRegistry rawContentProcessorRegistry,
            ContentParserRegistry contentParserRegistry,
            ResourceStore resourceStore,
            ResourceFetcher resourceFetcher,
            RawDataIngestionPolicyRegistry ingestionPolicyRegistry,
            RawDataExtractionOptions rawDataExtractionOptions,
            ItemExtractionOptions itemExtractionOptions) {
        this(
                rawDataStep,
                memoryItemStep,
                insightStep,
                segmentProcessor,
                contextCommitDetector,
                pendingConversationBuffer,
                recentConversationBuffer,
                rawContentProcessorRegistry,
                contentParserRegistry,
                resourceStore,
                resourceFetcher,
                null,
                ingestionPolicyRegistry,
                rawDataExtractionOptions,
                itemExtractionOptions);
    }

    DefaultMemoryExtractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            InsightExtractStep insightStep,
            SegmentProcessor segmentProcessor,
            ContextCommitDetector contextCommitDetector,
            PendingConversationBuffer pendingConversationBuffer,
            RecentConversationBuffer recentConversationBuffer,
            ExtractionRequestResolver requestResolver,
            RawDataExtractionOptions rawDataExtractionOptions,
            ItemExtractionOptions itemExtractionOptions) {
        this(
                rawDataStep,
                memoryItemStep,
                insightStep,
                segmentProcessor,
                contextCommitDetector,
                pendingConversationBuffer,
                recentConversationBuffer,
                null,
                null,
                null,
                null,
                requestResolver,
                RawDataIngestionPolicyRegistry.empty(),
                rawDataExtractionOptions,
                itemExtractionOptions);
    }

    private DefaultMemoryExtractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            InsightExtractStep insightStep,
            SegmentProcessor segmentProcessor,
            ContextCommitDetector contextCommitDetector,
            PendingConversationBuffer pendingConversationBuffer,
            RecentConversationBuffer recentConversationBuffer,
            RawContentProcessorRegistry rawContentProcessorRegistry,
            ContentParserRegistry contentParserRegistry,
            ResourceStore resourceStore,
            ResourceFetcher resourceFetcher,
            ExtractionRequestResolver requestResolver,
            RawDataIngestionPolicyRegistry ingestionPolicyRegistry,
            RawDataExtractionOptions rawDataExtractionOptions,
            ItemExtractionOptions itemExtractionOptions) {
        this.rawDataStep = Objects.requireNonNull(rawDataStep, "rawDataStep is required");
        this.memoryItemStep = Objects.requireNonNull(memoryItemStep, "memoryItemStep is required");
        this.insightStep = Objects.requireNonNull(insightStep, "insightStep is required");
        this.segmentProcessor = segmentProcessor;
        this.contextCommitDetector = contextCommitDetector;
        this.pendingConversationBuffer = pendingConversationBuffer;
        this.recentConversationBuffer = recentConversationBuffer;
        this.contentParserRegistry = contentParserRegistry;
        this.resourceStore = resourceStore;
        this.resourceFetcher = resourceFetcher;
        this.ingestionPolicyRegistry =
                Objects.requireNonNull(ingestionPolicyRegistry, "ingestionPolicyRegistry");
        this.rawDataExtractionOptions =
                Objects.requireNonNull(rawDataExtractionOptions, "rawDataExtractionOptions");
        this.itemExtractionOptions =
                Objects.requireNonNull(itemExtractionOptions, "itemExtractionOptions");
        this.rawContentProcessorRegistry = rawContentProcessorRegistry;
        this.requestResolver =
                requestResolver != null
                        ? requestResolver
                        : new DefaultExtractionRequestResolver(
                                rawContentProcessorRegistry,
                                this.contentParserRegistry,
                                resourceStore,
                                resourceFetcher,
                                this.ingestionPolicyRegistry);
        this.segmentBudgetEnforcer = new SegmentBudgetEnforcer();
    }

    /**
     * Execute the complete memory extraction process
     *
     * @param request Extraction request
     * @return Extraction result
     */
    public Mono<ExtractionResult> extract(ExtractionRequest request) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(request.memoryId(), "memoryId is required");

        var startTime = Instant.now();

        return Mono.defer(() -> requestResolver.resolve(request))
                .flatMap(resolved -> executeResolvedRequest(resolved, startTime))
                .onErrorResume(e -> toErrorResult(request.memoryId(), e, startTime));
    }

    /**
     * Extract memory from archived conversation segments (internal context entry)
     *
     * <p>Skip normalize + chunk, directly execute: build Segment → caption → vectorize → persist → Item → Insight
     */
    private Mono<ExtractionResult> extractConversationSegment(
            MemoryId memoryId, List<Message> messages, ExtractionConfig config) {
        return extractConversationSegment(memoryId, messages, config, Map.of());
    }

    /**
     * Extract memory from archived conversation segments (internal context entry, supports cross seal context)
     */
    private Mono<ExtractionResult> extractConversationSegment(
            MemoryId memoryId,
            List<Message> messages,
            ExtractionConfig config,
            Map<String, Object> sealMetadata) {
        Objects.requireNonNull(memoryId, "memoryId is required");
        Objects.requireNonNull(messages, "messages is required");
        Objects.requireNonNull(config, "config is required");
        Objects.requireNonNull(sealMetadata, "sealMetadata is required");

        if (segmentProcessor == null) {
            return Mono.error(
                    new IllegalStateException(
                            "segmentProcessor is required for extractConversationSegment"));
        }

        var startTime = Instant.now();
        Map<String, Object> effectiveMetadata =
                mergeSourceClientMetadata(sealMetadata, sourceClientFromMessages(messages));

        // Build Segment, merge sealMetadata into segment.metadata()
        String content =
                messages.stream()
                        .map(ConversationContent::formatMessage)
                        .collect(Collectors.joining("\n"));
        int startMessage = 0;
        int endMessage = messages.size();
        Segment segment =
                new Segment(
                        content,
                        null,
                        new MessageBoundary(startMessage, endMessage),
                        effectiveMetadata,
                        SegmentRuntimeContext.fromConversationMessages(messages));
        String contentId = HashUtils.sampledSha256(content);

        var request =
                resolvedRequest(
                        memoryId,
                        new ConversationContent(messages),
                        effectiveMetadata,
                        config,
                        null);

        return segmentProcessor
                .processSegment(
                        memoryId,
                        segment,
                        ConversationContent.TYPE,
                        contentId,
                        sealMetadata,
                        config.language())
                .flatMap(rawResult -> extractMemoryItem(request, rawResult))
                .flatMap(pair -> extractInsight(request, pair))
                .map(triple -> toSuccessResult(memoryId, triple, startTime))
                .timeout(config.timeout())
                .onErrorResume(e -> toErrorResult(memoryId, e, startTime));
    }

    /**
     * Streaming single message extraction entry
     *
     * <p>Use memoryId as buffer key, determine whether to trigger extraction through boundary detection.
     *
     * @param memoryId Memory identifier (as buffer key)
     * @param message Single message
     * @param config Extraction configuration
     * @return Emits an {@link ExtractionResult} when extraction is triggered; emits an empty signal ({@code Mono.empty()}) when not triggered,
     *         the caller should handle the no result case using {@code switchIfEmpty} or {@code defaultIfEmpty}
     */
    public Mono<ExtractionResult> addMessage(
            MemoryId memoryId, Message message, ExtractionConfig config) {
        Objects.requireNonNull(memoryId, "memoryId is required");
        Objects.requireNonNull(message, "message is required");
        Objects.requireNonNull(config, "config is required");

        if (contextCommitDetector == null
                || pendingConversationBuffer == null
                || recentConversationBuffer == null) {
            return Mono.error(
                    new IllegalStateException(
                            "contextCommitDetector, pendingConversationBuffer, and"
                                    + " recentConversationBuffer are required for addMessage"));
        }

        var bufferKey = memoryId.toIdentifier();

        return Mono.fromCallable(
                        () ->
                                ConversationBufferLocks.withLock(
                                        bufferKey,
                                        () -> {
                                            recentConversationBuffer.append(bufferKey, message);
                                            if (message.role() == Message.Role.ASSISTANT) {
                                                appendToPendingBuffer(bufferKey, message);
                                                return Optional.<PendingExtraction>empty();
                                            }

                                            var snapshot =
                                                    pendingConversationBuffer.load(bufferKey);
                                            if (snapshot.isEmpty()) {
                                                appendToPendingBuffer(bufferKey, message);
                                                return Optional.<PendingExtraction>empty();
                                            }

                                            var detectionInput =
                                                    new CommitDetectionInput(
                                                            snapshot,
                                                            List.of(message),
                                                            CommitDetectionContext.empty());
                                            var decision =
                                                    contextCommitDetector
                                                            .shouldCommit(detectionInput)
                                                            .defaultIfEmpty(CommitDecision.hold())
                                                            .block();

                                            if (decision == null || !decision.shouldSeal()) {
                                                appendToPendingBuffer(bufferKey, message);
                                                return Optional.<PendingExtraction>empty();
                                            }

                                            log.debug(
                                                    "Boundary detection triggered sealing:"
                                                        + " memoryId={}, reason={}, confidence={}",
                                                    bufferKey,
                                                    decision.reason(),
                                                    decision.confidence());

                                            var sealedMessages = List.copyOf(snapshot);
                                            pendingConversationBuffer.clear(bufferKey);
                                            pendingConversationBuffer.append(bufferKey, message);

                                            return Optional.of(
                                                    new PendingExtraction(
                                                            sealedMessages, new HashMap<>()));
                                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(
                        pending ->
                                pending.map(
                                                extraction ->
                                                        extractConversationSegment(
                                                                memoryId,
                                                                extraction.messages(),
                                                                config,
                                                                extraction.sealMetadata()))
                                        .orElseGet(Mono::empty));
    }

    private void appendToPendingBuffer(String bufferKey, Message message) {
        pendingConversationBuffer.append(bufferKey, message);
    }

    private record PendingExtraction(List<Message> messages, Map<String, Object> sealMetadata) {}

    private static Map<String, Object> mergeSourceClientMetadata(
            Map<String, Object> metadata, String sourceClient) {
        if (sourceClient == null || sourceClient.isBlank()) {
            return metadata == null ? Map.of() : Map.copyOf(metadata);
        }
        Map<String, Object> merged = new HashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        merged.putIfAbsent("sourceClient", sourceClient);
        return Map.copyOf(merged);
    }

    private static String sourceClientFromMessages(List<Message> messages) {
        if (messages == null) {
            return null;
        }
        return messages.stream()
                .map(Message::sourceClient)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private Mono<ExtractionResult> executeResolvedRequest(
            ResolvedExtractionRequest resolved, Instant startTime) {
        var rawDataPersisted = new AtomicBoolean(false);
        return extractRawData(resolved)
                .doOnNext(
                        r -> {
                            rawDataPersisted.set(true);
                            log.info("RawData completed: segments={}", r.segments().size());
                        })
                .flatMap(rawResult -> extractMemoryItem(resolved, rawResult))
                .doOnNext(
                        p ->
                                log.info(
                                        "MemoryItem completed: newItems={}",
                                        p.itemResult().newItems().size()))
                .flatMap(pair -> extractInsight(resolved, pair))
                .doOnNext(
                        t ->
                                log.info(
                                        "Insight completed: count={}",
                                        t.insightResult().totalCount()))
                .map(triple -> toSuccessResult(resolved.memoryId(), triple, startTime))
                .timeout(resolved.config().timeout())
                .onErrorResume(
                        e ->
                                cleanupStoredResourceIfNeeded(
                                                resolved.cleanupRef(), rawDataPersisted)
                                        .then(toErrorResult(resolved.memoryId(), e, startTime)));
    }

    private ResolvedExtractionRequest resolvedRequest(
            MemoryId memoryId,
            RawContent content,
            Map<String, Object> metadata,
            ExtractionConfig config,
            ResourceRef cleanupRef) {
        return new ResolvedExtractionRequest(
                memoryId, content, content.contentType(), metadata, config, cleanupRef);
    }

    private <T extends RawContent> RawContentProcessor<T> resolveRequiredProcessor(T content) {
        if (rawContentProcessorRegistry == null) {
            throw new IllegalStateException(
                    "RawContentProcessorRegistry is required for raw content type: "
                            + content.contentType());
        }
        try {
            return rawContentProcessorRegistry.resolve(content);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "No processor registered for raw content type: " + content.contentType(), e);
        }
    }

    private Mono<Void> cleanupStoredResourceIfNeeded(
            ResourceRef cleanupRef, AtomicBoolean rawDataPersisted) {
        if (cleanupRef == null || rawDataPersisted.get() || resourceStore == null) {
            return Mono.empty();
        }
        return resourceStore
                .delete(cleanupRef)
                .onErrorResume(
                        cleanupError -> {
                            log.warn(
                                    "Best-effort resource cleanup failed for {}",
                                    cleanupRef.storageUri(),
                                    cleanupError);
                            return Mono.empty();
                        });
    }

    private Mono<RawDataResult> extractRawData(ResolvedExtractionRequest request) {
        return rawDataStep.extract(
                request.memoryId(),
                request.content(),
                request.contentType(),
                request.metadata(),
                request.config().language());
    }

    private Mono<StepPair> extractMemoryItem(
            ResolvedExtractionRequest request, RawDataResult rawResult) {
        if (rawResult.isEmpty()) {
            return Mono.just(new StepPair(rawResult, MemoryItemResult.empty()));
        }

        var processor = resolveRequiredProcessor(request.content());
        ItemExtractionConfig itemConfig =
                ItemExtractionConfig.from(
                        request.config(),
                        request.contentType(),
                        itemExtractionOptions,
                        processor.allowedCategories());
        RawDataResult normalized =
                processor.normalizeForItemBudget(request.content(), rawResult, itemConfig);
        RawDataResult budgeted = segmentBudgetEnforcer.enforce(normalized, itemConfig);
        return memoryItemStep
                .extract(request.memoryId(), budgeted, itemConfig)
                .map(itemResult -> new StepPair(budgeted, itemResult));
    }

    private Mono<StepTriple> extractInsight(ResolvedExtractionRequest request, StepPair pair) {
        if (!request.config().enableInsight()) {
            log.debug("Insight skipped: enableInsight=false");
            return Mono.just(
                    new StepTriple(pair.rawResult(), pair.itemResult(), InsightResult.empty()));
        }
        if (pair.itemResult() == null || pair.itemResult().isEmpty()) {
            log.debug(
                    "Insight skipped: itemResult is empty (itemResult={})",
                    pair.itemResult() == null ? "null" : "empty");
            return Mono.just(
                    new StepTriple(pair.rawResult(), pair.itemResult(), InsightResult.empty()));
        }

        return insightStep
                .extract(request.memoryId(), pair.itemResult(), request.config().language())
                .map(
                        insightResult ->
                                new StepTriple(pair.rawResult(), pair.itemResult(), insightResult));
    }

    private ExtractionResult toSuccessResult(
            MemoryId memoryId, StepTriple triple, Instant startTime) {
        return ExtractionResult.success(
                memoryId,
                triple.rawResult(),
                triple.itemResult(),
                triple.insightResult(),
                Duration.between(startTime, Instant.now()));
    }

    private Mono<ExtractionResult> toErrorResult(
            MemoryId memoryId, Throwable error, Instant startTime) {
        return Mono.just(
                ExtractionResult.failed(
                        memoryId, Duration.between(startTime, Instant.now()), error.getMessage()));
    }

    // ===== Internal Data Classes =====

    private record StepPair(RawDataResult rawResult, MemoryItemResult itemResult) {}

    private record StepTriple(
            RawDataResult rawResult, MemoryItemResult itemResult, InsightResult insightResult) {}
}
