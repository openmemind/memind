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
package com.openmemind.ai.memory.plugin.rawdata.document.processor;

import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.exception.ParsedContentTooLargeException;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import com.openmemind.ai.memory.plugin.rawdata.document.DocumentSemantics;
import com.openmemind.ai.memory.plugin.rawdata.document.caption.DocumentCaptionFallbacks;
import com.openmemind.ai.memory.plugin.rawdata.document.chunk.ProfileAwareDocumentChunker;
import com.openmemind.ai.memory.plugin.rawdata.document.config.DocumentExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
import com.openmemind.ai.memory.plugin.rawdata.document.content.document.DocumentSection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import reactor.core.publisher.Mono;

/**
 * Processor for parsed document content.
 */
public final class DocumentContentProcessor implements RawContentProcessor<DocumentContent> {

    private static final String WHOLE_DOCUMENT_CHUNK_STRATEGY = "document-whole";

    private final ProfileAwareDocumentChunker profileAwareDocumentChunker;
    private final DocumentExtractionOptions options;
    private final DocumentSectionBoundaryResolver sectionBoundaryResolver;
    private final CaptionGenerator captionGenerator;

    public DocumentContentProcessor(
            ProfileAwareDocumentChunker profileAwareDocumentChunker,
            DocumentExtractionOptions options) {
        this(profileAwareDocumentChunker, options, fallbackCaptionGenerator(options));
    }

    public DocumentContentProcessor(
            ProfileAwareDocumentChunker profileAwareDocumentChunker,
            DocumentExtractionOptions options,
            CaptionGenerator captionGenerator) {
        this.profileAwareDocumentChunker =
                Objects.requireNonNull(
                        profileAwareDocumentChunker,
                        "profileAwareDocumentChunker must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.captionGenerator = Objects.requireNonNull(captionGenerator, "captionGenerator");
        this.sectionBoundaryResolver = new DocumentSectionBoundaryResolver();
    }

    @Override
    public Class<DocumentContent> contentClass() {
        return DocumentContent.class;
    }

    @Override
    public String contentType() {
        return DocumentContent.TYPE;
    }

    @Override
    public boolean usesSourceIdentity() {
        return true;
    }

    @Override
    public CaptionGenerator captionGenerator() {
        return captionGenerator;
    }

    @Override
    public void validateParsedContent(DocumentContent content) {
        String profile = resolveContentProfile(content);
        ParsedContentLimitOptions limits =
                DocumentSemantics.isBinaryGovernance(resolveGovernanceType(content, profile))
                        ? options.binaryParsedLimit()
                        : options.textLikeParsedLimit();
        int tokenCount = TokenUtils.countTokens(content.toContentString());
        if (tokenCount > limits.maxTokens()) {
            throw new ParsedContentTooLargeException(
                    "Parsed content exceeds token limit: profile=%s tokens=%d max=%d"
                            .formatted(profile, tokenCount, limits.maxTokens()));
        }
        validateDocumentStructure(profile, content, limits);
    }

    @Override
    public Mono<List<Segment>> chunk(DocumentContent content) {
        String text = content.toContentString();
        if (text == null || text.isBlank()) {
            return Mono.just(List.of());
        }
        String profile = resolveContentProfile(content);
        String governanceType = resolveGovernanceType(content, profile);
        if (TokenUtils.countTokens(text) <= options.wholeDocumentMaxTokens()) {
            return Mono.just(finalizeSegments(List.of(wholeDocumentSegment(content))));
        }
        if (content.sections().isEmpty()) {
            return Mono.just(
                    finalizeSegments(chunkStructuredDocument(content, governanceType, profile)));
        }

        return Mono.just(
                sectionBoundaryResolver
                        .resolve(text, content.sections())
                        .filter(spans -> !spans.isEmpty())
                        .map(
                                spans ->
                                        finalizeSegments(
                                                chunkResolvedSections(
                                                        spans, content, profile, governanceType)))
                        .orElseGet(
                                () ->
                                        finalizeSegments(
                                                chunkStructuredDocument(
                                                        content, governanceType, profile))));
    }

    @Override
    public RawDataResult normalizeForItemBudget(
            DocumentContent content, RawDataResult rawDataResult, ItemExtractionConfig config) {
        if (rawDataResult.segments().isEmpty()) {
            return rawDataResult;
        }
        int effectiveBudget = effectiveBudget(config);
        String profile = resolveContentProfile(content);
        String governanceType = resolveGovernanceType(content, profile);
        DocumentExtractionOptions budgetOptions = budgetOptions(effectiveBudget, governanceType);

        List<ParsedSegment> normalized = new ArrayList<>();
        for (ParsedSegment segment : rawDataResult.segments()) {
            if (TokenUtils.countTokens(segment.text()) <= effectiveBudget) {
                normalized.add(segment);
                continue;
            }
            normalized.addAll(splitForItemBudget(segment, budgetOptions, governanceType, profile));
        }
        return rawDataResult.withSegments(normalized);
    }

    private String resolveContentProfile(DocumentContent content) {
        Object value = content.metadata().get("contentProfile");
        if (value != null && !value.toString().isBlank()) {
            return value.toString();
        }
        if (content.directContentProfile() != null && !content.directContentProfile().isBlank()) {
            return content.directContentProfile();
        }
        return DocumentSemantics.PROFILE_TEXT;
    }

    private String resolveGovernanceType(DocumentContent content, String contentProfile) {
        if (content.metadata().containsKey("governanceType")) {
            Object governanceType = content.metadata().get("governanceType");
            if (governanceType != null && !governanceType.toString().isBlank()) {
                return governanceType.toString();
            }
            throw new IllegalArgumentException(
                    "Missing governanceType for contentProfile=" + contentProfile);
        }
        if (content.directGovernanceType() != null && !content.directGovernanceType().isBlank()) {
            return content.directGovernanceType();
        }
        return DocumentSemantics.isBinaryProfile(contentProfile)
                ? DocumentSemantics.GOVERNANCE_BINARY
                : DocumentSemantics.GOVERNANCE_TEXT_LIKE;
    }

    private List<Segment> enrichWithContentMetadata(
            List<Segment> segments, Map<String, Object> contentMetadata) {
        if (contentMetadata == null || contentMetadata.isEmpty()) {
            return segments;
        }
        return segments.stream().map(segment -> mergeMetadata(segment, contentMetadata)).toList();
    }

    private void validateDocumentStructure(
            String profile, DocumentContent content, ParsedContentLimitOptions limits) {
        if (limits.maxSections() != null && content.sections().size() > limits.maxSections()) {
            throw new ParsedContentTooLargeException(
                    "Parsed content exceeds section limit: profile=%s sections=%d max=%d"
                            .formatted(profile, content.sections().size(), limits.maxSections()));
        }

        if (limits.maxPages() == null) {
            return;
        }

        OptionalInt pageCount = resolvePageCount(content);
        if (pageCount.isPresent() && pageCount.getAsInt() > limits.maxPages()) {
            throw new ParsedContentTooLargeException(
                    "Parsed content exceeds page limit: profile=%s pages=%d max=%d"
                            .formatted(profile, pageCount.getAsInt(), limits.maxPages()));
        }
    }

    private OptionalInt resolvePageCount(DocumentContent content) {
        Object pageCount = content.metadata().get("pageCount");
        if (pageCount instanceof Number number && number.intValue() > 0) {
            return OptionalInt.of(number.intValue());
        }

        int distinctPages =
                content.sections().stream()
                        .map(DocumentSection::metadata)
                        .map(this::resolveSectionPage)
                        .flatMapToInt(
                                page ->
                                        page.isPresent()
                                                ? IntStream.of(page.getAsInt())
                                                : IntStream.empty())
                        .distinct()
                        .toArray()
                        .length;
        return distinctPages > 0 ? OptionalInt.of(distinctPages) : OptionalInt.empty();
    }

    private OptionalInt resolveSectionPage(Map<String, Object> metadata) {
        Object page = metadata.get("page");
        if (page instanceof Number number && number.intValue() > 0) {
            return OptionalInt.of(number.intValue());
        }
        Object pageNumber = metadata.get("pageNumber");
        if (pageNumber instanceof Number number && number.intValue() > 0) {
            return OptionalInt.of(number.intValue());
        }
        return OptionalInt.empty();
    }

    private Segment mergeMetadata(Segment segment, Map<String, Object> contentMetadata) {
        Map<String, Object> merged = new LinkedHashMap<>(contentMetadata);
        merged.putAll(segment.metadata());
        return new Segment(
                segment.content(),
                segment.caption(),
                segment.boundary(),
                Map.copyOf(merged),
                segment.runtimeContext());
    }

    private int effectiveBudget(ItemExtractionConfig config) {
        var budget = config.promptBudget();
        return budget.maxInputTokens()
                - budget.reservedPromptOverhead()
                - budget.reservedOutputTokens()
                - budget.safetyMargin();
    }

    private List<Segment> chunkStructuredDocument(
            DocumentContent content, String governanceType, String profile) {
        return enrichWithContentMetadata(
                profileAwareDocumentChunker.chunk(
                        content.toContentString(), options, governanceType, profile),
                content.metadata());
    }

    private Segment wholeDocumentSegment(DocumentContent content) {
        String text = content.toContentString();
        Map<String, Object> metadata = new LinkedHashMap<>(content.metadata());
        metadata.put("chunkStrategy", WHOLE_DOCUMENT_CHUNK_STRATEGY);
        metadata.put("structureType", "document");
        if (!content.sections().isEmpty()) {
            metadata.put("sectionCount", content.sections().size());
        }
        return new Segment(text, null, new CharBoundary(0, text.length()), Map.copyOf(metadata));
    }

    private List<Segment> chunkResolvedSections(
            List<DocumentSectionBoundaryResolver.ResolvedSectionSpan> spans,
            DocumentContent content,
            String profile,
            String governanceType) {
        List<Segment> segments = new ArrayList<>();
        for (DocumentSectionBoundaryResolver.ResolvedSectionSpan span : spans) {
            DocumentSection section = span.section();
            List<Segment> localSegments =
                    profileAwareDocumentChunker.chunk(
                            section.content(), options, governanceType, profile);
            for (Segment local : localSegments) {
                CharBoundary localBoundary = charBoundary(local);
                Map<String, Object> metadata = new LinkedHashMap<>(content.metadata());
                metadata.putAll(local.metadata());
                metadata.putAll(section.metadata());
                metadata.put("sectionIndex", section.index());
                if (section.title() != null && !section.title().isBlank()) {
                    metadata.put("sectionTitle", section.title());
                }
                segments.add(
                        new Segment(
                                local.content(),
                                local.caption(),
                                new CharBoundary(
                                        span.start() + localBoundary.startChar(),
                                        span.start() + localBoundary.endChar()),
                                Map.copyOf(metadata),
                                local.runtimeContext()));
            }
        }
        return segments;
    }

    private List<Segment> finalizeSegments(List<Segment> segments) {
        List<Segment> finalized = new ArrayList<>(segments.size());
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            Map<String, Object> metadata = new LinkedHashMap<>(segment.metadata());
            metadata.put("chunkIndex", index);
            finalized.add(
                    new Segment(
                            segment.content(),
                            segment.caption(),
                            segment.boundary(),
                            Map.copyOf(metadata),
                            segment.runtimeContext()));
        }
        return List.copyOf(finalized);
    }

    private DocumentExtractionOptions budgetOptions(int effectiveBudget, String governanceType) {
        int hardMaxTokens = Math.max(1, effectiveBudget);
        TokenChunkingOptions textLikeChunking =
                capChunking(options.textLikeChunking(), hardMaxTokens);
        TokenChunkingOptions binaryChunking = capChunking(options.binaryChunking(), hardMaxTokens);
        return new DocumentExtractionOptions(
                options.textLikeSourceLimit(),
                options.binarySourceLimit(),
                options.textLikeParsedLimit(),
                options.binaryParsedLimit(),
                options.wholeDocumentMaxTokens(),
                textLikeChunking,
                binaryChunking,
                options.textLikeMinChunkTokens(),
                options.binaryMinChunkTokens(),
                options.pdfMaxMergedPages(),
                options.llmCaptionEnabled(),
                options.captionConcurrency(),
                options.fallbackCaptionMaxLength());
    }

    private static CaptionGenerator fallbackCaptionGenerator(DocumentExtractionOptions options) {
        var resolvedOptions = Objects.requireNonNull(options, "options must not be null");
        return (content, metadata) ->
                Mono.just(
                        DocumentCaptionFallbacks.build(
                                content, metadata, resolvedOptions.fallbackCaptionMaxLength()));
    }

    private TokenChunkingOptions capChunking(TokenChunkingOptions base, int hardMaxTokens) {
        int targetTokens = Math.min(base.targetTokens(), hardMaxTokens);
        return new TokenChunkingOptions(targetTokens, hardMaxTokens);
    }

    private List<ParsedSegment> splitForItemBudget(
            ParsedSegment segment,
            DocumentExtractionOptions budgetOptions,
            String governanceType,
            String profile) {
        List<Segment> splitSegments =
                profileAwareDocumentChunker.chunk(
                        segment.text(), budgetOptions, governanceType, profile);
        if (splitSegments.size() <= 1) {
            return List.of(segment);
        }
        int startIndex = Math.max(0, segment.startIndex());
        return splitSegments.stream()
                .map(child -> toParsedSegment(segment, child, startIndex))
                .toList();
    }

    private ParsedSegment toParsedSegment(
            ParsedSegment original, Segment child, int baseStartIndex) {
        CharBoundary boundary = charBoundary(child);
        Map<String, Object> metadata = new LinkedHashMap<>(original.metadata());
        metadata.putAll(child.metadata());
        return new ParsedSegment(
                child.content(),
                child.caption() != null ? child.caption() : original.caption(),
                baseStartIndex + boundary.startChar(),
                baseStartIndex + boundary.endChar(),
                original.rawDataId(),
                Map.copyOf(metadata),
                original.runtimeContext());
    }

    private CharBoundary charBoundary(Segment segment) {
        if (segment.boundary() instanceof CharBoundary charBoundary) {
            return charBoundary;
        }
        throw new IllegalArgumentException(
                "DocumentContentProcessor requires CharBoundary segments");
    }
}
