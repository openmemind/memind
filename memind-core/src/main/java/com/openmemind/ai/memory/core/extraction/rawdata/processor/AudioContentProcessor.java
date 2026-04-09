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

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TextChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TextChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.audio.TranscriptSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Processor for parsed audio content.
 */
public class AudioContentProcessor implements RawContentProcessor<AudioContent> {

    private final TextChunker textChunker;
    private final TextChunkingConfig chunkingConfig;

    public AudioContentProcessor(TextChunker textChunker, TextChunkingConfig chunkingConfig) {
        this.textChunker = Objects.requireNonNull(textChunker, "textChunker must not be null");
        this.chunkingConfig =
                Objects.requireNonNull(chunkingConfig, "chunkingConfig must not be null");
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
        if (content.segments().isEmpty()) {
            return Mono.just(textChunker.chunk(content.toContentString(), chunkingConfig));
        }

        List<Segment> chunks = new ArrayList<>();
        List<TranscriptSegment> current = new ArrayList<>();
        int currentLength = 0;
        int chunkStart = 0;
        int cursor = 0;
        for (TranscriptSegment segment : content.segments()) {
            String text = segment.text() == null ? "" : segment.text();
            if (text.isBlank()) {
                continue;
            }
            int additional = current.isEmpty() ? text.length() : text.length() + 1;
            if (!current.isEmpty() && currentLength + additional > chunkingConfig.chunkSize()) {
                chunks.add(buildTranscriptChunk(current, chunkStart, cursor));
                chunkStart = cursor + 1;
                current = new ArrayList<>();
                currentLength = 0;
            }
            current.add(segment);
            cursor += additional;
            currentLength += additional;
        }
        if (!current.isEmpty()) {
            chunks.add(buildTranscriptChunk(current, chunkStart, cursor));
        }
        return Mono.just(chunks);
    }

    private Segment buildTranscriptChunk(
            List<TranscriptSegment> transcriptSegments, int startOffset, int endOffset) {
        String content =
                transcriptSegments.stream()
                        .map(TranscriptSegment::text)
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
        TranscriptSegment first = transcriptSegments.getFirst();
        TranscriptSegment last = transcriptSegments.getLast();
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (first.startTime() != null) {
            metadata.put("startTime", first.startTime());
        }
        if (last.endTime() != null) {
            metadata.put("endTime", last.endTime());
        }
        String speaker = singleSpeaker(transcriptSegments);
        if (speaker != null) {
            metadata.put("speaker", speaker);
        }
        return new Segment(
                content,
                null,
                new CharBoundary(startOffset, endOffset),
                metadata.isEmpty() ? Map.of() : Map.copyOf(metadata));
    }

    private String singleSpeaker(List<TranscriptSegment> segments) {
        String speaker = null;
        for (TranscriptSegment segment : segments) {
            if (segment.speaker() == null || segment.speaker().isBlank()) {
                return null;
            }
            if (speaker == null) {
                speaker = segment.speaker();
                continue;
            }
            if (!speaker.equals(segment.speaker())) {
                return null;
            }
        }
        return speaker;
    }
}
