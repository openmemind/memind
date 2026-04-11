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
import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.context.CommitDecision;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectionContext;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectionInput;
import com.openmemind.ai.memory.core.extraction.context.ContextCommitDetector;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.SegmentBudgetEnforcer;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ImageSegmentComposer;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ProfileAwareDocumentChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TranscriptSegmentChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.AudioContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.DocumentContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.processor.ImageContentProcessor;
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
import com.openmemind.ai.memory.core.resource.ContentCapability;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.core.resource.ContentParserRegistry;
import com.openmemind.ai.memory.core.resource.FetchSession;
import com.openmemind.ai.memory.core.resource.FetchedResource;
import com.openmemind.ai.memory.core.resource.ResourceFetchRequest;
import com.openmemind.ai.memory.core.resource.ResourceFetcher;
import com.openmemind.ai.memory.core.resource.ResourceRef;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.resource.SourceDescriptor;
import com.openmemind.ai.memory.core.resource.SourceKind;
import com.openmemind.ai.memory.core.resource.SourceTooLargeException;
import com.openmemind.ai.memory.core.resource.UnsupportedContentSourceException;
import com.openmemind.ai.memory.core.utils.HashUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
public class MemoryExtractor implements MemoryExtractionPipeline {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractor.class);

    private final RawDataExtractStep rawDataStep;
    private final MemoryItemExtractStep memoryItemStep;
    private final InsightExtractStep insightStep;
    private final SegmentProcessor segmentProcessor;
    private final ContextCommitDetector contextCommitDetector;
    private final PendingConversationBuffer pendingConversationBuffer;
    private final RecentConversationBuffer recentConversationBuffer;
    private final ContentParser contentParser;
    private final ContentParserRegistry contentParserRegistry;
    private final ResourceStore resourceStore;
    private final ResourceFetcher resourceFetcher;
    private final RawDataExtractionOptions rawDataExtractionOptions;
    private final ItemExtractionOptions itemExtractionOptions;
    private final RawContentProcessorRegistry rawContentProcessorRegistry;
    private final SegmentBudgetEnforcer segmentBudgetEnforcer;

    public MemoryExtractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            InsightExtractStep insightStep) {
        this(
                rawDataStep,
                memoryItemStep,
                insightStep,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                RawDataExtractionOptions.defaults(),
                ItemExtractionOptions.defaults());
    }

    public MemoryExtractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            InsightExtractStep insightStep,
            SegmentProcessor segmentProcessor) {
        this(
                rawDataStep,
                memoryItemStep,
                insightStep,
                segmentProcessor,
                null,
                null,
                null,
                null,
                (ContentParser) null,
                (ContentParserRegistry) null,
                null,
                null,
                RawDataExtractionOptions.defaults(),
                ItemExtractionOptions.defaults());
    }

    public MemoryExtractor(
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
                RawDataExtractionOptions.defaults(),
                ItemExtractionOptions.defaults());
    }

    public MemoryExtractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            InsightExtractStep insightStep,
            SegmentProcessor segmentProcessor,
            ContextCommitDetector contextCommitDetector,
            PendingConversationBuffer pendingConversationBuffer,
            RecentConversationBuffer recentConversationBuffer,
            ContentParser contentParser,
            ResourceStore resourceStore) {
        this(
                rawDataStep,
                memoryItemStep,
                insightStep,
                segmentProcessor,
                contextCommitDetector,
                pendingConversationBuffer,
                recentConversationBuffer,
                null,
                contentParser,
                null,
                resourceStore,
                null,
                RawDataExtractionOptions.defaults(),
                ItemExtractionOptions.defaults());
    }

    public MemoryExtractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            InsightExtractStep insightStep,
            SegmentProcessor segmentProcessor,
            ContextCommitDetector contextCommitDetector,
            PendingConversationBuffer pendingConversationBuffer,
            RecentConversationBuffer recentConversationBuffer,
            ContentParser contentParser,
            ResourceStore resourceStore,
            ResourceFetcher resourceFetcher) {
        this(
                rawDataStep,
                memoryItemStep,
                insightStep,
                segmentProcessor,
                contextCommitDetector,
                pendingConversationBuffer,
                recentConversationBuffer,
                null,
                contentParser,
                null,
                resourceStore,
                resourceFetcher,
                RawDataExtractionOptions.defaults(),
                ItemExtractionOptions.defaults());
    }

    public MemoryExtractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            InsightExtractStep insightStep,
            SegmentProcessor segmentProcessor,
            ContextCommitDetector contextCommitDetector,
            PendingConversationBuffer pendingConversationBuffer,
            RecentConversationBuffer recentConversationBuffer,
            ContentParserRegistry contentParserRegistry,
            ResourceStore resourceStore,
            ResourceFetcher resourceFetcher) {
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
                contentParserRegistry,
                resourceStore,
                resourceFetcher,
                RawDataExtractionOptions.defaults(),
                ItemExtractionOptions.defaults());
    }

    public MemoryExtractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            InsightExtractStep insightStep,
            SegmentProcessor segmentProcessor,
            ContextCommitDetector contextCommitDetector,
            PendingConversationBuffer pendingConversationBuffer,
            RecentConversationBuffer recentConversationBuffer,
            ContentParserRegistry contentParserRegistry,
            ResourceStore resourceStore,
            ResourceFetcher resourceFetcher,
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
                contentParserRegistry,
                resourceStore,
                resourceFetcher,
                rawDataExtractionOptions,
                itemExtractionOptions);
    }

    public MemoryExtractor(
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
                null,
                contentParserRegistry,
                resourceStore,
                resourceFetcher,
                rawDataExtractionOptions,
                itemExtractionOptions);
    }

    private MemoryExtractor(
            RawDataExtractStep rawDataStep,
            MemoryItemExtractStep memoryItemStep,
            InsightExtractStep insightStep,
            SegmentProcessor segmentProcessor,
            ContextCommitDetector contextCommitDetector,
            PendingConversationBuffer pendingConversationBuffer,
            RecentConversationBuffer recentConversationBuffer,
            RawContentProcessorRegistry rawContentProcessorRegistry,
            ContentParser contentParser,
            ContentParserRegistry contentParserRegistry,
            ResourceStore resourceStore,
            ResourceFetcher resourceFetcher,
            RawDataExtractionOptions rawDataExtractionOptions,
            ItemExtractionOptions itemExtractionOptions) {
        this.rawDataStep = Objects.requireNonNull(rawDataStep, "rawDataStep is required");
        this.memoryItemStep = Objects.requireNonNull(memoryItemStep, "memoryItemStep is required");
        this.insightStep = Objects.requireNonNull(insightStep, "insightStep is required");
        this.segmentProcessor = segmentProcessor;
        this.contextCommitDetector = contextCommitDetector;
        this.pendingConversationBuffer = pendingConversationBuffer;
        this.recentConversationBuffer = recentConversationBuffer;
        this.contentParser = contentParser;
        this.contentParserRegistry = contentParserRegistry;
        this.resourceStore = resourceStore;
        this.resourceFetcher = resourceFetcher;
        this.rawDataExtractionOptions =
                Objects.requireNonNull(rawDataExtractionOptions, "rawDataExtractionOptions");
        this.itemExtractionOptions =
                Objects.requireNonNull(itemExtractionOptions, "itemExtractionOptions");
        this.rawContentProcessorRegistry =
                rawContentProcessorRegistry != null
                        ? rawContentProcessorRegistry
                        : createBuiltinProcessorRegistry(this.rawDataExtractionOptions);
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

        return Mono.defer(() -> resolveExtractionRequest(request))
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
                        sealMetadata,
                        SegmentRuntimeContext.fromConversationMessages(messages));
        String contentId = HashUtils.sampledSha256(content);

        var request =
                new ExtractionRequest(
                        memoryId, null, null, null, ContentTypes.CONVERSATION, Map.of(), config);

        return segmentProcessor
                .processSegment(
                        memoryId,
                        segment,
                        "CONVERSATION",
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
                        () -> {
                            return ConversationBufferLocks.withLock(
                                    bufferKey,
                                    () -> {
                                        recentConversationBuffer.append(bufferKey, message);
                                        if (message.role() == Message.Role.ASSISTANT) {
                                            appendToPendingBuffer(bufferKey, message);
                                            return Optional.<PendingExtraction>empty();
                                        }

                                        var snapshot = pendingConversationBuffer.load(bufferKey);
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
                                                "Boundary detection triggered sealing: memoryId={},"
                                                        + " reason={}, confidence={}",
                                                bufferKey,
                                                decision.reason(),
                                                decision.confidence());

                                        var sealedMessages = List.copyOf(snapshot);
                                        pendingConversationBuffer.clear(bufferKey);
                                        pendingConversationBuffer.append(bufferKey, message);

                                        return Optional.of(
                                                new PendingExtraction(
                                                        sealedMessages, new HashMap<>()));
                                    });
                        })
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

    private record ResolvedExtractionRequest(ExtractionRequest request, ResourceRef cleanupRef) {}

    private Mono<ExtractionResult> executeResolvedRequest(
            ResolvedExtractionRequest resolved, Instant startTime) {
        var request = resolved.request();
        Objects.requireNonNull(request.content(), "content is required");

        var rawDataPersisted = new AtomicBoolean(false);
        return extractRawData(request)
                .doOnNext(
                        r -> {
                            rawDataPersisted.set(true);
                            log.info("RawData completed: segments={}", r.segments().size());
                        })
                .flatMap(rawResult -> extractMemoryItem(request, rawResult))
                .doOnNext(
                        p ->
                                log.info(
                                        "MemoryItem completed: newItems={}",
                                        p.itemResult().newItems().size()))
                .flatMap(pair -> extractInsight(request, pair))
                .doOnNext(
                        t ->
                                log.info(
                                        "Insight completed: count={}",
                                        t.insightResult().totalCount()))
                .map(triple -> toSuccessResult(request.memoryId(), triple, startTime))
                .timeout(request.config().timeout())
                .onErrorResume(
                        e ->
                                cleanupStoredResourceIfNeeded(
                                                resolved.cleanupRef(), rawDataPersisted)
                                        .then(toErrorResult(request.memoryId(), e, startTime)));
    }

    private Mono<ResolvedExtractionRequest> resolveExtractionRequest(ExtractionRequest request) {
        RawUrlInput urlInput = request.urlInput();
        RawFileInput fileInput = request.fileInput();
        if (fileInput != null && urlInput != null) {
            return Mono.error(
                    new IllegalArgumentException("fileInput and urlInput cannot both be provided"));
        }
        if (urlInput != null) {
            return resolveUrlExtractionRequest(request);
        }
        if (fileInput != null) {
            return resolveFileExtractionRequest(request);
        }
        return resolveDirectContentRequest(request);
    }

    private Mono<ResolvedExtractionRequest> resolveDirectContentRequest(ExtractionRequest request) {
        if (request.content() == null) {
            return Mono.error(
                    new IllegalArgumentException(
                            "Extraction request content, fileInput, or urlInput is required"));
        }
        if (request.content().directGovernanceType() == null) {
            return Mono.just(new ResolvedExtractionRequest(request, null));
        }

        RawContent normalizedContent =
                validateWithProcessor(
                        MultimodalMetadataNormalizer.normalizeDirectContent(
                                request.content(), request.metadata()));
        Map<String, Object> normalizedMetadata =
                ExtractionRequest.normalizeMultimodalMetadata(normalizedContent);
        ExtractionRequest normalizedRequest =
                new ExtractionRequest(
                        request.memoryId(),
                        normalizedContent,
                        null,
                        null,
                        request.contentType() == null || request.contentType().isBlank()
                                ? normalizedContent.contentType()
                                : request.contentType(),
                        normalizedMetadata,
                        request.config());
        return Mono.just(new ResolvedExtractionRequest(normalizedRequest, null));
    }

    private Mono<ResolvedExtractionRequest> resolveFileExtractionRequest(
            ExtractionRequest request) {
        if (contentParserRegistry == null) {
            return Mono.error(
                    new IllegalStateException(
                            "ContentParserRegistry is required for file extraction requests"));
        }

        RawFileInput fileInput = request.fileInput();
        String checksum = HashUtils.sha256(fileInput.data());
        long sizeBytes = fileInput.data().length;
        SourceDescriptor source =
                new SourceDescriptor(
                        SourceKind.FILE,
                        fileInput.fileName(),
                        fileInput.mimeType(),
                        sizeBytes,
                        null);
        return contentParserRegistry
                .resolve(source)
                .flatMap(
                        resolution -> {
                            long maxBytes = resolveSourceLimit(resolution.capability());
                            validateKnownSourceSize(source, maxBytes);
                            return contentParserRegistry
                                    .parse(fileInput.data(), source)
                                    .switchIfEmpty(
                                            Mono.error(
                                                    new IllegalStateException(
                                                            "ContentParserRegistry returned no"
                                                                    + " content for file"
                                                                    + " extraction")))
                                    .flatMap(
                                            parsedContent -> {
                                                RawContent normalizedContent =
                                                        validateWithProcessor(
                                                                MultimodalMetadataNormalizer
                                                                        .normalizeParsedContent(
                                                                                parsedContent,
                                                                                request.metadata(),
                                                                                resolution
                                                                                        .capability()));
                                                Map<String, Object> normalizedMetadata =
                                                        ExtractionRequest
                                                                .normalizeMultimodalMetadata(
                                                                        normalizedContent);
                                                if (resourceStore == null) {
                                                    return Mono.just(
                                                            new ResolvedExtractionRequest(
                                                                    buildResolvedFileRequest(
                                                                            request,
                                                                            normalizedContent,
                                                                            fileInput,
                                                                            checksum,
                                                                            sizeBytes,
                                                                            null),
                                                                    null));
                                                }
                                                return resourceStore
                                                        .store(
                                                                request.memoryId(),
                                                                fileInput.fileName(),
                                                                fileInput.data(),
                                                                fileInput.mimeType(),
                                                                Map.of(
                                                                        "checksum",
                                                                        checksum,
                                                                        "sizeBytes",
                                                                        sizeBytes))
                                                        .map(
                                                                storedResource ->
                                                                        new ResolvedExtractionRequest(
                                                                                buildResolvedFileRequest(
                                                                                        request,
                                                                                        normalizedContent,
                                                                                        fileInput,
                                                                                        checksum,
                                                                                        sizeBytes,
                                                                                        storedResource),
                                                                                storedResource));
                                            });
                        });
    }

    private Mono<ResolvedExtractionRequest> resolveUrlExtractionRequest(ExtractionRequest request) {
        if (contentParserRegistry == null) {
            return Mono.error(
                    new IllegalStateException(
                            "ContentParserRegistry is required for URL extraction requests"));
        }
        if (resourceFetcher == null) {
            return Mono.error(
                    new IllegalStateException(
                            "ResourceFetcher is required for URL extraction requests"));
        }

        RawUrlInput urlInput = request.urlInput();
        SourceDescriptor provisionalSource =
                new SourceDescriptor(
                        SourceKind.URL,
                        urlInput.fileName(),
                        urlInput.mimeType(),
                        null,
                        urlInput.sourceUrl());

        return contentParserRegistry
                .resolve(provisionalSource)
                .then()
                .onErrorResume(UnsupportedContentSourceException.class, ignored -> Mono.empty())
                .then(
                        Mono.defer(
                                () ->
                                        resourceFetcher
                                                .open(
                                                        new ResourceFetchRequest(
                                                                request.memoryId(),
                                                                urlInput.sourceUrl(),
                                                                urlInput.fileName(),
                                                                urlInput.mimeType()))
                                                .flatMap(
                                                        session ->
                                                                resolveFetchedUrlRequest(
                                                                        request, session))));
    }

    private ExtractionRequest buildResolvedFileRequest(
            ExtractionRequest request,
            RawContent parsedContent,
            RawFileInput fileInput,
            String checksum,
            long sizeBytes,
            ResourceRef storedResource) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (request.metadata() != null && !request.metadata().isEmpty()) {
            normalized.putAll(request.metadata());
        }
        normalized.putAll(ExtractionRequest.normalizeMultimodalMetadata(parsedContent));
        if (request.metadata() != null && !request.metadata().isEmpty()) {
            reapplyTransportContext(request.metadata(), normalized, "sourceKind");
            reapplyTransportContext(request.metadata(), normalized, "sourceUri");
        }
        normalized.putIfAbsent("sourceKind", SourceKind.FILE.name());

        String mimeType = resolveMimeType(parsedContent, fileInput.mimeType(), storedResource);
        if (mimeType != null) {
            normalized.put("mimeType", mimeType);
        }
        normalized.put("fileName", fileInput.fileName());
        normalized.put("checksum", checksum);
        normalized.put("sizeBytes", sizeBytes);

        if (storedResource != null) {
            normalized.put("resourceId", storedResource.id());
            if (storedResource.storageUri() != null && !storedResource.storageUri().isBlank()) {
                normalized.put("storageUri", storedResource.storageUri());
            }
        } else {
            normalized.put(
                    "resourceId",
                    HashUtils.sampledSha256(
                            request.memoryId().toIdentifier()
                                    + "|"
                                    + fileInput.fileName()
                                    + "|"
                                    + checksum));
        }

        return new ExtractionRequest(
                request.memoryId(),
                parsedContent,
                null,
                null,
                parsedContent.contentType(),
                normalized,
                request.config());
    }

    private Mono<ResolvedExtractionRequest> resolveFetchedUrlRequest(
            ExtractionRequest request, FetchSession session) {
        SourceDescriptor finalSource =
                new SourceDescriptor(
                        SourceKind.URL,
                        session.resolvedFileName(),
                        session.resolvedMimeType(),
                        session.declaredContentLength(),
                        session.finalUrl());

        return contentParserRegistry
                .resolve(finalSource)
                .flatMap(
                        finalResolution -> {
                            long maxBytes = resolveSourceLimit(finalResolution.capability());
                            validateKnownSourceSize(finalSource, maxBytes);
                            return session.readBody(maxBytes)
                                    .flatMap(
                                            fetched -> {
                                                SourceDescriptor fetchedSource =
                                                        new SourceDescriptor(
                                                                SourceKind.URL,
                                                                fetched.fileName(),
                                                                fetched.mimeType(),
                                                                fetched.sizeBytes(),
                                                                fetched.finalUrl());
                                                return contentParserRegistry
                                                        .parse(fetched.data(), fetchedSource)
                                                        .flatMap(
                                                                parsedContent -> {
                                                                    RawContent normalizedContent =
                                                                            validateWithProcessor(
                                                                                    MultimodalMetadataNormalizer
                                                                                            .normalizeParsedContent(
                                                                                                    parsedContent,
                                                                                                    request
                                                                                                            .metadata(),
                                                                                                    finalResolution
                                                                                                            .capability()));
                                                                    Map<String, Object>
                                                                            normalizedMetadata =
                                                                                    ExtractionRequest
                                                                                            .normalizeMultimodalMetadata(
                                                                                                    normalizedContent);
                                                                    return persistFetchedUrlRequest(
                                                                            request,
                                                                            normalizedContent,
                                                                            fetched);
                                                                });
                                            });
                        });
    }

    private long resolveSourceLimit(ContentCapability capability) {
        return switch (capability.governanceType()) {
            case DOCUMENT_TEXT_LIKE ->
                    rawDataExtractionOptions.document().textLikeSourceLimit().maxBytes();
            case DOCUMENT_BINARY ->
                    rawDataExtractionOptions.document().binarySourceLimit().maxBytes();
            case IMAGE_CAPTION_OCR -> rawDataExtractionOptions.image().sourceLimit().maxBytes();
            case AUDIO_TRANSCRIPT -> rawDataExtractionOptions.audio().sourceLimit().maxBytes();
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported governanceType: " + capability.governanceType());
        };
    }

    private void validateKnownSourceSize(SourceDescriptor source, long maxBytes) {
        if (source.sizeBytes() != null && source.sizeBytes() > maxBytes) {
            throw new SourceTooLargeException(
                    "Source exceeds byte limit: source=%s size=%d max=%d"
                            .formatted(source, source.sizeBytes(), maxBytes));
        }
    }

    private Mono<ResolvedExtractionRequest> persistFetchedUrlRequest(
            ExtractionRequest request, RawContent parsedContent, FetchedResource fetchedResource) {
        RawFileInput fetchedFileInput =
                new RawFileInput(
                        fetchedResource.fileName(),
                        fetchedResource.data(),
                        fetchedResource.mimeType());
        String checksum = HashUtils.sha256(fetchedResource.data());
        long sizeBytes = fetchedResource.sizeBytes();
        Map<String, Object> requestMetadata = new LinkedHashMap<>(request.metadata());
        requestMetadata.put("sourceUri", fetchedResource.finalUrl());
        requestMetadata.put("sourceKind", SourceKind.URL.name());
        ExtractionRequest normalizedRequest =
                new ExtractionRequest(
                        request.memoryId(),
                        null,
                        null,
                        request.urlInput(),
                        null,
                        Map.copyOf(requestMetadata),
                        request.config());
        if (resourceStore == null) {
            return Mono.just(
                    new ResolvedExtractionRequest(
                            buildResolvedFileRequest(
                                    normalizedRequest,
                                    parsedContent,
                                    fetchedFileInput,
                                    checksum,
                                    sizeBytes,
                                    null),
                            null));
        }
        return resourceStore
                .store(
                        request.memoryId(),
                        fetchedResource.fileName(),
                        fetchedResource.data(),
                        fetchedResource.mimeType(),
                        Map.of("checksum", checksum, "sizeBytes", sizeBytes))
                .map(
                        storedResource ->
                                new ResolvedExtractionRequest(
                                        buildResolvedFileRequest(
                                                normalizedRequest,
                                                parsedContent,
                                                fetchedFileInput,
                                                checksum,
                                                sizeBytes,
                                                storedResource),
                                        storedResource));
    }

    private String resolveMimeType(
            RawContent parsedContent, String fallbackMimeType, ResourceRef storedResource) {
        if (storedResource != null && storedResource.mimeType() != null) {
            return storedResource.mimeType();
        }
        Object value = ExtractionRequest.normalizeMultimodalMetadata(parsedContent).get("mimeType");
        if (value == null) {
            return fallbackMimeType;
        }
        String contentMimeType = value.toString();
        return contentMimeType.isBlank() ? fallbackMimeType : contentMimeType;
    }

    private void reapplyTransportContext(
            Map<String, Object> requestMetadata, Map<String, Object> normalized, String key) {
        Object value = requestMetadata.get(key);
        if (value != null) {
            normalized.put(key, value);
        }
    }

    private RawContentProcessorRegistry processorRegistryRequired() {
        if (rawContentProcessorRegistry == null) {
            throw new IllegalStateException(
                    "RawContentProcessorRegistry is required for multimodal validation");
        }
        return rawContentProcessorRegistry;
    }

    private <T extends RawContent> T validateWithProcessor(T content) {
        processorRegistryRequired().resolve(content).validateParsedContent(content);
        return content;
    }

    private RawContentProcessorRegistry createBuiltinProcessorRegistry(
            RawDataExtractionOptions options) {
        return new RawContentProcessorRegistry(
                List.of(
                        new DocumentContentProcessor(
                                new ProfileAwareDocumentChunker(), options.document()),
                        new ImageContentProcessor(new ImageSegmentComposer(), options.image()),
                        new AudioContentProcessor(
                                new TranscriptSegmentChunker(), options.audio())));
    }

    private Mono<Void> cleanupStoredResourceIfNeeded(
            ResourceRef cleanupRef, AtomicBoolean rawDataPersisted) {
        if (cleanupRef == null || rawDataPersisted.get()) {
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

    private Mono<RawDataResult> extractRawData(ExtractionRequest request) {
        return rawDataStep.extract(
                request.memoryId(),
                request.content(),
                request.contentType(),
                request.metadata(),
                request.config().language());
    }

    private Mono<StepPair> extractMemoryItem(ExtractionRequest request, RawDataResult rawResult) {
        if (rawResult.isEmpty()) {
            return Mono.just(new StepPair(rawResult, MemoryItemResult.empty()));
        }

        ItemExtractionConfig itemConfig =
                ItemExtractionConfig.from(
                        request.config(), request.contentType(), itemExtractionOptions);
        RawDataResult budgeted = segmentBudgetEnforcer.enforce(rawResult, itemConfig);
        return memoryItemStep
                .extract(request.memoryId(), budgeted, itemConfig)
                .map(itemResult -> new StepPair(budgeted, itemResult));
    }

    private Mono<StepTriple> extractInsight(ExtractionRequest request, StepPair pair) {
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
