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
import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.BuiltinContentProfiles;
import com.openmemind.ai.memory.core.extraction.ContentGovernanceResolver;
import com.openmemind.ai.memory.core.extraction.ParsedContentTooLargeException;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
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

    private final ProfileAwareDocumentChunker profileAwareDocumentChunker;
    private final DocumentExtractionOptions options;

    public DocumentContentProcessor(
            ProfileAwareDocumentChunker profileAwareDocumentChunker,
            DocumentExtractionOptions options) {
        this.profileAwareDocumentChunker =
                Objects.requireNonNull(
                        profileAwareDocumentChunker,
                        "profileAwareDocumentChunker must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    @Override
    public Class<DocumentContent> contentClass() {
        return DocumentContent.class;
    }

    @Override
    public String contentType() {
        return ContentTypes.DOCUMENT;
    }

    @Override
    public boolean usesSourceIdentity() {
        return true;
    }

    @Override
    public void validateParsedContent(DocumentContent content) {
        String profile = resolveContentProfile(content);
        ParsedContentLimitOptions limits =
                resolveGovernanceType(content, profile) == ContentGovernanceType.DOCUMENT_TEXT_LIKE
                        ? options.textLikeParsedLimit()
                        : options.binaryParsedLimit();
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
        String profile = resolveContentProfile(content);
        ContentGovernanceType governanceType = resolveGovernanceType(content, profile);
        if (content.sections().isEmpty()) {
            return Mono.just(
                    enrichWithContentMetadata(
                            profileAwareDocumentChunker.chunk(
                                    content.toContentString(), options, governanceType, profile),
                            content.metadata()));
        }

        List<Segment> segments = new ArrayList<>();
        String fullText = content.toContentString();
        int searchFrom = 0;
        for (DocumentSection section : content.sections()) {
            String sectionText = section.content();
            if (sectionText == null || sectionText.isBlank()) {
                continue;
            }
            int sectionStart = fullText.indexOf(sectionText, searchFrom);
            if (sectionStart < 0) {
                sectionStart = Math.max(0, searchFrom);
            }
            searchFrom = sectionStart + sectionText.length();
            for (Segment local :
                    profileAwareDocumentChunker.chunk(
                            sectionText, options, governanceType, profile)) {
                CharBoundary localBoundary = (CharBoundary) local.boundary();
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
                                        sectionStart + localBoundary.startChar(),
                                        sectionStart + localBoundary.endChar()),
                                Map.copyOf(metadata),
                                local.runtimeContext()));
            }
        }
        return Mono.just(
                segments.isEmpty()
                        ? enrichWithContentMetadata(
                                profileAwareDocumentChunker.chunk(
                                        content.toContentString(),
                                        options,
                                        governanceType,
                                        profile),
                                content.metadata())
                        : segments);
    }

    private String resolveContentProfile(DocumentContent content) {
        Object value = content.metadata().get("contentProfile");
        if (value != null && !value.toString().isBlank()) {
            return value.toString();
        }
        return BuiltinContentProfiles.DOCUMENT_TEXT;
    }

    private ContentGovernanceType resolveGovernanceType(
            DocumentContent content, String contentProfile) {
        if (content.metadata().containsKey("governanceType")) {
            return ContentGovernanceResolver.resolveRequired(content.metadata());
        }
        return BuiltinContentProfiles.governanceTypeOf(contentProfile)
                .orElse(ContentGovernanceType.DOCUMENT_TEXT_LIKE);
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
}
