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
package com.openmemind.ai.memory.core.extraction.rawdata;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.MemoryResource;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import com.openmemind.ai.memory.core.extraction.step.SegmentProcessor;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.utils.HashUtils;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RawData layer processing entry.
 *
 * <p>Routes chunking and caption generation to registered {@link RawContentProcessor} instances,
 * then vectorizes and stores the results.
 */
public class RawDataLayer implements RawDataExtractStep, SegmentProcessor {

    private final Map<Class<?>, RawContentProcessor<?>> processors;
    private final CaptionGenerator defaultCaptionGenerator;
    private final MemoryStore memoryStore;
    private final MemoryVector vector;
    private final int vectorBatchSize;

    /**
     * Creates a RawDataLayer with a list of content processors.
     *
     * @param processorList list of processors (each keyed by its contentClass())
     * @param defaultCaptionGenerator fallback caption generator when processor provides none
     * @param memoryStore memory store for persistence
     * @param vector memory vector for vectorization
     */
    public RawDataLayer(
            List<RawContentProcessor<?>> processorList,
            CaptionGenerator defaultCaptionGenerator,
            MemoryStore memoryStore,
            MemoryVector vector) {
        this(
                processorList,
                defaultCaptionGenerator,
                memoryStore,
                vector,
                com.openmemind.ai.memory.core.builder.RawDataExtractionOptions
                        .DEFAULT_VECTOR_BATCH_SIZE);
    }

    public RawDataLayer(
            List<RawContentProcessor<?>> processorList,
            CaptionGenerator defaultCaptionGenerator,
            MemoryStore memoryStore,
            MemoryVector vector,
            int vectorBatchSize) {
        this.processors = new HashMap<>();
        for (var processor : processorList) {
            this.processors.put(processor.contentClass(), processor);
        }
        this.defaultCaptionGenerator = defaultCaptionGenerator;
        this.memoryStore = memoryStore;
        this.vector = vector;
        if (vectorBatchSize <= 0) {
            throw new IllegalArgumentException("vectorBatchSize must be > 0");
        }
        this.vectorBatchSize = vectorBatchSize;
    }

    @Override
    public Mono<RawDataResult> extract(
            MemoryId memoryId,
            RawContent content,
            String contentType,
            Map<String, Object> metadata) {
        return extract(memoryId, content, contentType, metadata, null);
    }

    @Override
    public Mono<RawDataResult> extract(
            MemoryId memoryId,
            RawContent content,
            String contentType,
            Map<String, Object> metadata,
            String language) {
        RawDataInput input = new RawDataInput(memoryId, content, contentType, metadata);
        return process(input, language)
                .map(
                        result ->
                                new RawDataResult(
                                        result.rawDataList(), result.segments(), result.existed()));
    }

    /**
     * Process raw data.
     *
     * @param input Input
     * @return Processing result
     */
    public Mono<RawDataProcessResult> process(RawDataInput input) {
        return process(input, null);
    }

    private Mono<RawDataProcessResult> process(RawDataInput input, String language) {
        String contentId = resolveRawDataContentId(input);
        MemoryId memoryId = input.memoryId();

        // Idempotency check
        Optional<MemoryRawData> existing =
                memoryStore.rawDataOperations().getRawDataByContentId(memoryId, contentId);
        return existing.map(
                        memoryRawData -> Mono.just(RawDataProcessResult.existing(memoryRawData)))
                .orElseGet(() -> doProcess(input, memoryId, contentId, language));
    }

    @Override
    public Mono<RawDataResult> processSegment(
            MemoryId memoryId,
            Segment segment,
            String type,
            String contentId,
            Map<String, Object> metadata) {
        return processSegment(memoryId, segment, type, contentId, metadata, null);
    }

    @Override
    public Mono<RawDataResult> processSegment(
            MemoryId memoryId,
            Segment segment,
            String type,
            String contentId,
            Map<String, Object> metadata,
            String language) {

        // Idempotency check
        Optional<MemoryRawData> existing =
                memoryStore.rawDataOperations().getRawDataByContentId(memoryId, contentId);
        if (existing.isPresent()) {
            return Mono.just(RawDataResult.existing(existing.get()));
        }

        String contentType = (type != null && !type.isBlank()) ? type : ContentTypes.CONVERSATION;

        // Skip chunk, start directly from caption
        return defaultCaptionGenerator
                .generateForSegments(List.of(segment), language)
                .flatMap(segments -> vectorize(memoryId, segments))
                .flatMap(
                        vectorization ->
                                persistWithCleanup(
                                                memoryId,
                                                new RawDataInput(
                                                        memoryId, null, contentType, metadata),
                                                contentId,
                                                vectorization)
                                        .map(
                                                result ->
                                                        new RawDataResult(
                                                                result.rawDataList(),
                                                                result.segments(),
                                                                result.existed())));
    }

    private Mono<RawDataProcessResult> doProcess(
            RawDataInput input, MemoryId memoryId, String contentHash, String language) {

        // 1. chunk -- Route via processor registry
        return chunkContent(input.content())
                // 2. caption -- Route via processor registry
                .flatMap(
                        segments ->
                                getCaptionGenerator(input.content())
                                        .generateForSegments(segments, language))
                // 3. Vectorization
                .flatMap(segments -> vectorize(memoryId, segments))
                // 4. Persistence
                .flatMap(
                        vectorization ->
                                persistWithCleanup(memoryId, input, contentHash, vectorization));
    }

    @SuppressWarnings("unchecked")
    private <T extends RawContent> Mono<List<Segment>> chunkContent(T content) {
        var processor = (RawContentProcessor<T>) getProcessor(content);
        return processor.chunk(content);
    }

    private RawContentProcessor<?> getProcessor(RawContent content) {
        Class<?> cls = content.getClass();
        while (cls != null && cls != Object.class) {
            var proc = processors.get(cls);
            if (proc != null) {
                return proc;
            }
            cls = cls.getSuperclass();
        }
        throw new IllegalArgumentException(
                "No processor registered for: " + content.getClass().getName());
    }

    private CaptionGenerator getCaptionGenerator(RawContent content) {
        var processor = getProcessor(content);
        var gen = processor.captionGenerator();
        if (gen != null) {
            return gen;
        }
        return defaultCaptionGenerator;
    }

    private Mono<VectorizationResult> vectorize(MemoryId memoryId, List<Segment> segments) {
        if (segments.isEmpty()) {
            return Mono.just(new VectorizationResult(List.of(), List.of()));
        }

        List<VectorizationCandidate> candidates =
                IntStream.range(0, segments.size())
                        .mapToObj(i -> toVectorizationCandidate(i, segments.get(i)))
                        .filter(candidate -> candidate != null)
                        .toList();
        if (candidates.isEmpty()) {
            return Mono.just(new VectorizationResult(segments, List.of()));
        }

        List<VectorizedSegment> storedVectors = new ArrayList<>();
        List<String> storedVectorIds = new ArrayList<>();
        return Flux.fromIterable(candidates)
                .buffer(vectorBatchSize)
                .concatMap(
                        batch ->
                                storeVectorBatch(memoryId, batch)
                                        .doOnNext(
                                                batchWrites -> {
                                                    storedVectors.addAll(batchWrites);
                                                    batchWrites.forEach(
                                                            write ->
                                                                    storedVectorIds.add(
                                                                            write.vectorId()));
                                                }))
                .then(
                        Mono.fromSupplier(
                                () ->
                                        new VectorizationResult(
                                                applyVectorIds(segments, storedVectors),
                                                List.copyOf(storedVectorIds))))
                .onErrorResume(
                        error ->
                                cleanupVectorIds(memoryId, storedVectorIds, error)
                                        .then(Mono.error(error)));
    }

    private Mono<RawDataProcessResult> persistWithCleanup(
            MemoryId memoryId,
            RawDataInput input,
            String contentId,
            VectorizationResult vectorization) {
        return Mono.fromCallable(
                        () -> buildAndPersist(memoryId, input, contentId, vectorization.segments()))
                .onErrorResume(
                        error ->
                                cleanupVectorIds(memoryId, vectorization.vectorIds(), error)
                                        .then(Mono.error(error)));
    }

    private VectorizationCandidate toVectorizationCandidate(int index, Segment segment) {
        String text = resolveVectorText(segment);
        if (text == null) {
            return null;
        }
        return new VectorizationCandidate(index, text);
    }

    private String resolveVectorText(Segment segment) {
        if (segment.caption() != null && !segment.caption().isBlank()) {
            return segment.caption();
        }
        if (segment.content() != null && !segment.content().isBlank()) {
            return segment.content();
        }
        return null;
    }

    private Mono<List<VectorizedSegment>> storeVectorBatch(
            MemoryId memoryId, List<VectorizationCandidate> batch) {
        List<String> texts = batch.stream().map(VectorizationCandidate::text).toList();
        List<Map<String, Object>> metadataList =
                batch.stream().map(candidate -> Map.<String, Object>of()).toList();
        return vector.storeBatch(memoryId, texts, metadataList)
                .map(
                        vectorIds -> {
                            if (vectorIds.size() != batch.size()) {
                                throw new IllegalStateException(
                                        "Vector store returned "
                                                + vectorIds.size()
                                                + " ids for "
                                                + batch.size()
                                                + " inputs");
                            }
                            return IntStream.range(0, batch.size())
                                    .mapToObj(
                                            i ->
                                                    new VectorizedSegment(
                                                            batch.get(i).index(), vectorIds.get(i)))
                                    .toList();
                        });
    }

    private List<Segment> applyVectorIds(
            List<Segment> segments, List<VectorizedSegment> vectorizedSegments) {
        Map<Integer, String> vectorIdsByIndex = new HashMap<>();
        vectorizedSegments.forEach(write -> vectorIdsByIndex.put(write.index(), write.vectorId()));
        return IntStream.range(0, segments.size())
                .mapToObj(
                        i -> {
                            Segment segment = segments.get(i);
                            String vectorId = vectorIdsByIndex.get(i);
                            if (vectorId == null) {
                                return segment;
                            }
                            Map<String, Object> newMetadata = new HashMap<>(segment.metadata());
                            newMetadata.put("vectorId", vectorId);
                            return new Segment(
                                    segment.content(),
                                    segment.caption(),
                                    segment.boundary(),
                                    newMetadata,
                                    segment.runtimeContext());
                        })
                .toList();
    }

    private Mono<Void> cleanupVectorIds(
            MemoryId memoryId, List<String> vectorIds, Throwable originalError) {
        if (vectorIds.isEmpty()) {
            return Mono.empty();
        }
        return vector.deleteBatch(memoryId, List.copyOf(vectorIds))
                .onErrorResume(
                        cleanupError -> {
                            originalError.addSuppressed(cleanupError);
                            return Mono.empty();
                        });
    }

    private RawDataProcessResult buildAndPersist(
            MemoryId memoryId, RawDataInput input, String contentId, List<Segment> segments) {

        Instant now = Instant.now();
        List<Message> messages = extractMessages(input);
        Map<String, Object> requestMetadata = new LinkedHashMap<>();
        if (input.metadata() != null) {
            requestMetadata.putAll(input.metadata());
        }

        Optional<MemoryResource> resolvedResource = resolveResource(memoryId, requestMetadata, now);
        resolvedResource.ifPresent(
                resource -> {
                    requestMetadata.put("resourceId", resource.id());
                    if (resource.mimeType() != null && !resource.mimeType().isBlank()) {
                        requestMetadata.put("mimeType", resource.mimeType());
                    }
                    if (resource.sourceUri() != null && !resource.sourceUri().isBlank()) {
                        requestMetadata.put("sourceUri", resource.sourceUri());
                    }
                    if (resource.storageUri() != null && !resource.storageUri().isBlank()) {
                        requestMetadata.put("storageUri", resource.storageUri());
                    }
                });

        List<MemoryRawData> rawDataList =
                segments.stream()
                        .map(
                                segment -> {
                                    Segment durableSegment = segment.withoutRuntimeContext();
                                    Map<String, Object> mergedMetadata =
                                            mergeSegmentMetadata(
                                                    requestMetadata, segment.metadata());
                                    String resourceId = resolveString(mergedMetadata, "resourceId");
                                    String mimeType = resolveString(mergedMetadata, "mimeType");
                                    return new MemoryRawData(
                                            UUID.randomUUID().toString(),
                                            memoryId.toIdentifier(),
                                            input.contentType(),
                                            contentId,
                                            durableSegment,
                                            segment.caption(),
                                            resolveString(mergedMetadata, "vectorId"),
                                            mergedMetadata,
                                            resourceId,
                                            mimeType,
                                            now,
                                            resolveStartTime(segment, messages, now),
                                            resolveEndTime(segment, messages, now));
                                })
                        .toList();

        List<ParsedSegment> parsedSegments =
                IntStream.range(0, segments.size())
                        .mapToObj(
                                i -> {
                                    Segment segment = segments.get(i);
                                    String rawDataBizId = rawDataList.get(i).id();
                                    return new ParsedSegment(
                                            segment.content(),
                                            segment.caption(),
                                            getBoundaryStart(segment),
                                            getBoundaryEnd(segment),
                                            rawDataBizId,
                                            rawDataList.get(i).metadata(),
                                            segment.runtimeContext());
                                })
                        .toList();

        // Persistence
        memoryStore.upsertRawDataWithResources(
                memoryId, resolvedResource.map(List::of).orElseGet(List::of), rawDataList);

        return new RawDataProcessResult(rawDataList, parsedSegments, false);
    }

    private String resolveRawDataContentId(RawDataInput input) {
        String textContentId = input.content().getContentId();
        String contentType = normalizeContentType(input.contentType());
        if (!isMultimodal(contentType)) {
            return textContentId;
        }
        String sourceIdentity = resolveSourceIdentity(input.memoryId(), input.metadata());
        return HashUtils.sampledSha256(
                contentType
                        + "|"
                        + textContentId
                        + "|"
                        + (sourceIdentity == null ? "" : sourceIdentity));
    }

    private String normalizeContentType(String contentType) {
        return (contentType == null || contentType.isBlank())
                ? ContentTypes.CONVERSATION
                : contentType;
    }

    private boolean isMultimodal(String contentType) {
        return ContentTypes.DOCUMENT.equals(contentType)
                || ContentTypes.IMAGE.equals(contentType)
                || ContentTypes.AUDIO.equals(contentType);
    }

    private String resolveSourceIdentity(MemoryId memoryId, Map<String, Object> metadata) {
        String resourceId = resolveResourceId(memoryId, metadata);
        if (resourceId != null) {
            return resourceId;
        }
        String sourceUri = resolveString(metadata, "sourceUri");
        if (sourceUri != null) {
            return sourceUri;
        }
        String storageUri = resolveString(metadata, "storageUri");
        if (storageUri != null) {
            return storageUri;
        }
        String fileName = resolveString(metadata, "fileName");
        String checksum = resolveString(metadata, "checksum");
        if (fileName != null && checksum != null) {
            return fileName + "|" + checksum;
        }
        return null;
    }

    private Optional<MemoryResource> resolveResource(
            MemoryId memoryId, Map<String, Object> requestMetadata, Instant now) {
        String resourceId = resolveResourceId(memoryId, requestMetadata);
        if (resourceId == null) {
            return Optional.empty();
        }

        String sourceUri = resolveString(requestMetadata, "sourceUri");
        String storageUri = resolveString(requestMetadata, "storageUri");
        String fileName = resolveString(requestMetadata, "fileName");
        String mimeType = resolveString(requestMetadata, "mimeType");
        String checksum = resolveString(requestMetadata, "checksum");
        Long sizeBytes = resolveLong(requestMetadata, "sizeBytes");
        Map<String, Object> resourceMetadata = new LinkedHashMap<>();
        requestMetadata.forEach(
                (key, value) -> {
                    if (!isProjectedResourceKey(key) && !isSegmentOnlyKey(key)) {
                        resourceMetadata.put(key, value);
                    }
                });

        return Optional.of(
                new MemoryResource(
                        resourceId,
                        memoryId.toIdentifier(),
                        sourceUri,
                        storageUri,
                        fileName,
                        mimeType,
                        checksum,
                        sizeBytes,
                        resourceMetadata,
                        now));
    }

    private String resolveResourceId(MemoryId memoryId, Map<String, Object> metadata) {
        String explicitResourceId = resolveString(metadata, "resourceId");
        if (explicitResourceId != null) {
            return explicitResourceId;
        }
        String sourceUri = resolveString(metadata, "sourceUri");
        if (sourceUri != null) {
            return HashUtils.sampledSha256(memoryId.toIdentifier() + "|" + sourceUri);
        }
        String storageUri = resolveString(metadata, "storageUri");
        if (storageUri != null) {
            return HashUtils.sampledSha256(memoryId.toIdentifier() + "|" + storageUri);
        }
        String fileName = resolveString(metadata, "fileName");
        String checksum = resolveString(metadata, "checksum");
        if (fileName != null && checksum != null) {
            return HashUtils.sampledSha256(
                    memoryId.toIdentifier() + "|" + fileName + "|" + checksum);
        }
        return null;
    }

    private Map<String, Object> mergeSegmentMetadata(
            Map<String, Object> requestMetadata, Map<String, Object> segmentMetadata) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (requestMetadata != null) {
            merged.putAll(requestMetadata);
        }
        if (segmentMetadata != null) {
            merged.putAll(segmentMetadata);
        }
        return Map.copyOf(merged);
    }

    private boolean isProjectedResourceKey(String key) {
        return "resourceId".equals(key)
                || "sourceUri".equals(key)
                || "storageUri".equals(key)
                || "fileName".equals(key)
                || "mimeType".equals(key)
                || "checksum".equals(key)
                || "sizeBytes".equals(key);
    }

    private boolean isSegmentOnlyKey(String key) {
        return "vectorId".equals(key)
                || "start_message".equals(key)
                || "end_message".equals(key)
                || "startChar".equals(key)
                || "endChar".equals(key)
                || "startTime".equals(key)
                || "endTime".equals(key)
                || "messages".equals(key);
    }

    private String resolveString(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private Long resolveLong(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private int getBoundaryStart(Segment segment) {
        return switch (segment.boundary()) {
            case MessageBoundary mb -> mb.startMessage();
            case CharBoundary cb -> cb.startChar();
        };
    }

    private int getBoundaryEnd(Segment segment) {
        return switch (segment.boundary()) {
            case MessageBoundary mb -> mb.endMessage();
            case CharBoundary cb -> cb.endChar();
        };
    }

    private List<Message> extractMessages(RawDataInput input) {
        if (input.content() instanceof ConversationContent cc) {
            return cc.getMessages();
        }
        return Collections.emptyList();
    }

    private Instant resolveStartTime(Segment segment, List<Message> messages, Instant fallback) {
        if (segment.runtimeContext() != null && segment.runtimeContext().startTime() != null) {
            return segment.runtimeContext().startTime();
        }
        if (segment.boundary() instanceof MessageBoundary mb && !messages.isEmpty()) {
            int idx = mb.startMessage();
            if (idx >= 0 && idx < messages.size()) {
                Instant ts = messages.get(idx).timestamp();
                return ts != null ? ts : fallback;
            }
        }
        return fallback;
    }

    private Instant resolveEndTime(Segment segment, List<Message> messages, Instant fallback) {
        if (segment.runtimeContext() != null && segment.runtimeContext().observedAt() != null) {
            return segment.runtimeContext().observedAt();
        }
        if (segment.boundary() instanceof MessageBoundary mb && !messages.isEmpty()) {
            int idx = mb.endMessage() - 1;
            if (idx >= 0 && idx < messages.size()) {
                Instant ts = messages.get(idx).timestamp();
                return ts != null ? ts : fallback;
            }
        }
        return fallback;
    }

    private record VectorizationResult(List<Segment> segments, List<String> vectorIds) {}

    private record VectorizationCandidate(int index, String text) {}

    private record VectorizedSegment(int index, String vectorId) {}
}
