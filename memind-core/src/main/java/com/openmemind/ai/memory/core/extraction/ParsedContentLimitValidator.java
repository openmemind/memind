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

import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ImageContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.audio.TranscriptSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.content.document.DocumentSection;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * Validates parsed multimodal content before chunking and raw-data persistence.
 */
public final class ParsedContentLimitValidator {

    private final RawDataExtractionOptions options;

    public ParsedContentLimitValidator(RawDataExtractionOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public void validate(RawContent content) {
        validate(content, MultimodalMetadataNormalizer.snapshot(content));
    }

    public void validate(RawContent content, Map<String, Object> metadata) {
        Objects.requireNonNull(content, "content");
        if (!(content instanceof DocumentContent
                || content instanceof ImageContent
                || content instanceof AudioContent)) {
            return;
        }

        Map<String, Object> normalizedMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        String profile = resolveProfile(content, normalizedMetadata);
        ParsedContentLimitOptions limits =
                resolveLimits(ContentGovernanceResolver.resolveRequired(normalizedMetadata));
        int tokenCount = TokenUtils.countTokens(content.toContentString());
        if (tokenCount > limits.maxTokens()) {
            throw new ParsedContentTooLargeException(
                    "Parsed content exceeds token limit: profile=%s tokens=%d max=%d"
                            .formatted(profile, tokenCount, limits.maxTokens()));
        }

        if (content instanceof DocumentContent documentContent) {
            validateDocumentStructure(profile, documentContent, limits);
        }
        if (content instanceof AudioContent audioContent) {
            validateAudioDuration(profile, audioContent, limits);
        }
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

    private void validateAudioDuration(
            String profile, AudioContent content, ParsedContentLimitOptions limits) {
        if (limits.maxDuration() == null) {
            return;
        }

        Duration duration = resolveAudioDuration(content);
        if (duration != null && duration.compareTo(limits.maxDuration()) > 0) {
            throw new ParsedContentTooLargeException(
                    "Parsed content exceeds duration limit: profile=%s duration=%s max=%s"
                            .formatted(profile, duration, limits.maxDuration()));
        }
    }

    private String resolveProfile(RawContent content, Map<String, Object> metadata) {
        Object value = metadata.get("contentProfile");
        return value == null
                ? content.contentType().toLowerCase(java.util.Locale.ROOT)
                : value.toString();
    }

    private ParsedContentLimitOptions resolveLimits(ContentGovernanceType governanceType) {
        return switch (governanceType) {
            case DOCUMENT_TEXT_LIKE -> options.document().textLikeParsedLimit();
            case DOCUMENT_BINARY -> options.document().binaryParsedLimit();
            case IMAGE_CAPTION_OCR -> options.image().parsedLimit();
            case AUDIO_TRANSCRIPT -> options.audio().parsedLimit();
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported governanceType: " + governanceType);
        };
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

    private Duration resolveAudioDuration(AudioContent content) {
        Object durationSeconds = content.metadata().get("durationSeconds");
        if (durationSeconds instanceof Number number && number.longValue() > 0) {
            return Duration.ofSeconds(number.longValue());
        }

        Object duration = content.metadata().get("duration");
        if (duration instanceof Duration metadataDuration
                && !metadataDuration.isNegative()
                && !metadataDuration.isZero()) {
            return metadataDuration;
        }

        if (content.segments().isEmpty()) {
            return null;
        }

        Duration firstStart = null;
        Duration lastEnd = null;
        for (TranscriptSegment transcriptSegment : content.segments()) {
            if (transcriptSegment.startTime() != null
                    && (firstStart == null
                            || transcriptSegment.startTime().compareTo(firstStart) < 0)) {
                firstStart = transcriptSegment.startTime();
            }
            if (transcriptSegment.endTime() != null
                    && (lastEnd == null || transcriptSegment.endTime().compareTo(lastEnd) > 0)) {
                lastEnd = transcriptSegment.endTime();
            }
        }

        if (firstStart == null || lastEnd == null || lastEnd.compareTo(firstStart) < 0) {
            return null;
        }
        return lastEnd.minus(firstStart);
    }
}
