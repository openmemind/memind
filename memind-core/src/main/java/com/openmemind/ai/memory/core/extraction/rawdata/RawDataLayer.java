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
import com.openmemind.ai.memory.core.vector.MemoryVector;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
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
        this.processors = new HashMap<>();
        for (var processor : processorList) {
            this.processors.put(processor.contentClass(), processor);
        }
        this.defaultCaptionGenerator = defaultCaptionGenerator;
        this.memoryStore = memoryStore;
        this.vector = vector;
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
        return doProcess(input, memoryId, content.getContentId(), language)
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
        String contentId = input.content().getContentId();
        MemoryId memoryId = input.memoryId();

        // Idempotency check
        Optional<MemoryRawData> existing =
                memoryStore.rawDataOperations().getRawDataByContentId(memoryId, contentId);
        return existing.map(
                        memoryRawData -> Mono.just(RawDataProcessResult.existing(memoryRawData)))
                .orElseGet(() -> doProcess(input, memoryId, contentId, null));
    }

    @Override
    public Mono<RawDataResult> processSegment(
            MemoryId memoryId,
            Segment segment,
            String type,
            String contentId,
            Map<String, Object> metadata) {

        // Idempotency check
        Optional<MemoryRawData> existing =
                memoryStore.rawDataOperations().getRawDataByContentId(memoryId, contentId);
        if (existing.isPresent()) {
            return Mono.just(RawDataResult.existing(existing.get()));
        }

        String contentType = (type != null && !type.isBlank()) ? type : ContentTypes.CONVERSATION;

        // Skip chunk, start directly from caption
        return defaultCaptionGenerator
                .generateForSegments(List.of(segment))
                .flatMap(segments -> vectorize(memoryId, segments))
                .map(
                        segments -> {
                            RawDataInput input =
                                    new RawDataInput(memoryId, null, contentType, metadata);
                            RawDataProcessResult result =
                                    buildAndPersist(memoryId, input, contentId, segments);
                            return new RawDataResult(
                                    result.rawDataList(), result.segments(), result.existed());
                        });
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
                .map(segments -> buildAndPersist(memoryId, input, contentHash, segments));
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
        var processor = processors.get(content.getClass());
        if (processor != null) {
            var gen = processor.captionGenerator();
            if (gen != null) {
                return gen;
            }
        }
        return defaultCaptionGenerator;
    }

    private Mono<List<Segment>> vectorize(MemoryId memoryId, List<Segment> segments) {
        if (segments.isEmpty()) {
            return Mono.just(List.of());
        }

        List<String> captions =
                segments.stream().map(s -> s.caption() != null ? s.caption() : "").toList();

        List<Map<String, Object>> metadataList =
                segments.stream().map(s -> Map.<String, Object>of()).toList();

        return vector.storeBatch(memoryId, captions, metadataList)
                .map(
                        vectorIds ->
                                IntStream.range(0, segments.size())
                                        .mapToObj(
                                                i -> {
                                                    Segment segment = segments.get(i);
                                                    Map<String, Object> newMetadata =
                                                            new HashMap<>(segment.metadata());
                                                    newMetadata.put("vectorId", vectorIds.get(i));
                                                    return new Segment(
                                                            segment.content(),
                                                            segment.caption(),
                                                            segment.boundary(),
                                                            newMetadata);
                                                })
                                        .toList());
    }

    private RawDataProcessResult buildAndPersist(
            MemoryId memoryId, RawDataInput input, String contentId, List<Segment> segments) {

        Instant now = Instant.now();
        List<Message> messages = extractMessages(input);

        List<MemoryRawData> rawDataList =
                segments.stream()
                        .map(
                                segment ->
                                        new MemoryRawData(
                                                UUID.randomUUID().toString(),
                                                memoryId.toIdentifier(),
                                                input.contentType(),
                                                contentId,
                                                segment,
                                                segment.caption(),
                                                (String) segment.metadata().get("vectorId"),
                                                segment.metadata(),
                                                now,
                                                resolveStartTime(segment, messages, now),
                                                resolveEndTime(segment, messages, now)))
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
                                            segment.metadata());
                                })
                        .toList();

        // Persistence
        memoryStore.rawDataOperations().upsertRawData(memoryId, rawDataList);

        return new RawDataProcessResult(rawDataList, parsedSegments, false);
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
        if (segment.boundary() instanceof MessageBoundary mb && !messages.isEmpty()) {
            int idx = mb.endMessage() - 1;
            if (idx >= 0 && idx < messages.size()) {
                Instant ts = messages.get(idx).timestamp();
                return ts != null ? ts : fallback;
            }
        }
        return fallback;
    }
}
