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
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.AudioCaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TranscriptSegmentChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
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
