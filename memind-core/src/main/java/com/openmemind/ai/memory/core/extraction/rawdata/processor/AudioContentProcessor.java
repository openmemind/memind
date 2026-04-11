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
package com.openmemind.ai.memory.core.extraction.rawdata.processor;

import com.openmemind.ai.memory.core.builder.AudioExtractionOptions;
import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.BuiltinContentProfiles;
import com.openmemind.ai.memory.core.extraction.ParsedContentTooLargeException;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.AudioCaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TranscriptSegmentChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.audio.TranscriptSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Processor for parsed audio content.
 */
public class AudioContentProcessor implements RawContentProcessor<AudioContent> {

    private final TranscriptSegmentChunker transcriptSegmentChunker;
    private final AudioExtractionOptions options;
    private final CaptionGenerator captionGenerator;

    public AudioContentProcessor(
            TranscriptSegmentChunker transcriptSegmentChunker, AudioExtractionOptions options) {
        this.transcriptSegmentChunker =
                Objects.requireNonNull(
                        transcriptSegmentChunker, "transcriptSegmentChunker must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.captionGenerator = new AudioCaptionGenerator();
    }

    @Override
    public Class<AudioContent> contentClass() {
        return AudioContent.class;
    }

    @Override
    public String contentType() {
        return ContentTypes.AUDIO;
    }

    @Override
    public boolean usesSourceIdentity() {
        return true;
    }

    @Override
    public void validateParsedContent(AudioContent content) {
        int tokenCount = TokenUtils.countTokens(content.toContentString());
        var limit = options.parsedLimit();
        if (tokenCount > limit.maxTokens()) {
            throw new ParsedContentTooLargeException(
                    "Parsed content exceeds token limit: profile=%s tokens=%d max=%d"
                            .formatted(resolveProfile(content), tokenCount, limit.maxTokens()));
        }

        Duration duration = resolveAudioDuration(content);
        if (limit.maxDuration() != null
                && duration != null
                && duration.compareTo(limit.maxDuration()) > 0) {
            throw new ParsedContentTooLargeException(
                    "Parsed content exceeds duration limit: profile=%s duration=%s max=%s"
                            .formatted(resolveProfile(content), duration, limit.maxDuration()));
        }
    }

    @Override
    public Mono<List<Segment>> chunk(AudioContent content) {
        return Mono.just(
                transcriptSegmentChunker.chunk(content, options).stream()
                        .map(segment -> mergeMetadata(segment, content.metadata()))
                        .toList());
    }

    @Override
    public CaptionGenerator captionGenerator() {
        return captionGenerator;
    }

    private String resolveProfile(AudioContent content) {
        Object profile = content.metadata().get("contentProfile");
        if (profile != null && !profile.toString().isBlank()) {
            return profile.toString();
        }
        return content.directContentProfile() != null
                ? content.directContentProfile()
                : BuiltinContentProfiles.AUDIO_TRANSCRIPT;
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

    private Segment mergeMetadata(Segment segment, Map<String, Object> contentMetadata) {
        if (contentMetadata == null || contentMetadata.isEmpty()) {
            return segment;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(contentMetadata);
        metadata.putAll(segment.metadata());
        return new Segment(
                segment.content(),
                segment.caption(),
                segment.boundary(),
                Map.copyOf(metadata),
                segment.runtimeContext());
    }
}
