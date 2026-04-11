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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.AudioExtractionOptions;
import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TranscriptSegmentChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.audio.TranscriptSegment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class AudioContentProcessorTest {

    @Test
    void contentTypeShouldBeAudio() {
        var processor =
                new AudioContentProcessor(
                        new TranscriptSegmentChunker(), AudioExtractionOptions.defaults());

        assertThat(processor.contentType()).isEqualTo(ContentTypes.AUDIO);
        assertThat(processor.supportsInsight()).isTrue();
    }

    @Test
    void shouldChunkTranscriptWhenTimestampedSegmentsAreMissing() {
        var options =
                new AudioExtractionOptions(
                        new SourceLimitOptions(1024),
                        new ParsedContentLimitOptions(2048, null, null, Duration.ofMinutes(30)),
                        new TokenChunkingOptions(64, 96));
        var processor = new AudioContentProcessor(new TranscriptSegmentChunker(), options);
        var content = AudioContent.of("word ".repeat(120) + "\n\n" + "word ".repeat(120));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments ->
                                assertThat(segments)
                                        .allSatisfy(
                                                segment ->
                                                        assertThat(
                                                                        TokenUtils.countTokens(
                                                                                segment.content()))
                                                                .isLessThanOrEqualTo(
                                                                        options.chunking()
                                                                                .hardMaxTokens())))
                .verifyComplete();
    }

    @Test
    void shouldMergeTranscriptSegmentsAndProjectTimeMetadata() {
        var processor =
                new AudioContentProcessor(
                        new TranscriptSegmentChunker(), AudioExtractionOptions.defaults());
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "hello world",
                        List.of(
                                new TranscriptSegment(
                                        "hello", Duration.ZERO, Duration.ofSeconds(1), "Alice"),
                                new TranscriptSegment(
                                        "world",
                                        Duration.ofSeconds(1),
                                        Duration.ofSeconds(2),
                                        "Alice")),
                        "file:///tmp/meeting.mp3",
                        Map.of("durationSeconds", 2));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSize(1);
                            assertThat(segments.getFirst().content()).isEqualTo("hello\nworld");
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("speaker", "Alice")
                                    .containsEntry("startTime", Duration.ZERO)
                                    .containsEntry("endTime", Duration.ofSeconds(2));
                        })
                .verifyComplete();
    }

    @Test
    void audioProcessorSplitsOversizedSingleTranscriptSegmentWithinHardTokenBudget() {
        var options = AudioExtractionOptions.defaults();
        var processor = new AudioContentProcessor(new TranscriptSegmentChunker(), options);
        var longTranscript = "word ".repeat(options.chunking().hardMaxTokens() * 2);
        var content =
                new AudioContent(
                        "audio/mpeg",
                        longTranscript,
                        List.of(
                                new TranscriptSegment(
                                        longTranscript,
                                        Duration.ZERO,
                                        Duration.ofMinutes(1),
                                        "speaker-1")),
                        null,
                        Map.of("contentProfile", "audio.transcript"));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSizeGreaterThan(1);
                            assertThat(segments)
                                    .allSatisfy(
                                            segment -> {
                                                assertThat(
                                                                TokenUtils.countTokens(
                                                                        segment.content()))
                                                        .isLessThanOrEqualTo(
                                                                options.chunking().hardMaxTokens());
                                                assertThat(segment.metadata())
                                                        .containsEntry("speaker", "speaker-1");
                                            });
                        })
                .verifyComplete();
    }

    @Test
    void audioProcessorCaptionIncludesSingleSpeakerLabel() {
        var processor =
                new AudioContentProcessor(
                        new TranscriptSegmentChunker(), AudioExtractionOptions.defaults());
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "hello world",
                        List.of(
                                new TranscriptSegment(
                                        "hello", Duration.ZERO, Duration.ofSeconds(1), "Alice"),
                                new TranscriptSegment(
                                        "world",
                                        Duration.ofSeconds(1),
                                        Duration.ofSeconds(2),
                                        "Alice")),
                        null,
                        Map.of("contentProfile", "audio.transcript"));

        StepVerifier.create(
                        processor
                                .chunk(content)
                                .flatMap(processor.captionGenerator()::generateForSegments))
                .assertNext(
                        segments ->
                                assertThat(segments)
                                        .singleElement()
                                        .extracting(
                                                com.openmemind.ai.memory.core.extraction.rawdata
                                                                .segment.Segment
                                                        ::caption)
                                        .isEqualTo("Alice: hello world"))
                .verifyComplete();
    }

    @Test
    void audioProcessorCaptionIncludesMergedSpeakerList() {
        var processor =
                new AudioContentProcessor(
                        new TranscriptSegmentChunker(), AudioExtractionOptions.defaults());
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "hello world",
                        List.of(
                                new TranscriptSegment(
                                        "hello", Duration.ZERO, Duration.ofSeconds(1), "Alice"),
                                new TranscriptSegment(
                                        "world",
                                        Duration.ofSeconds(1),
                                        Duration.ofSeconds(2),
                                        "Bob")),
                        null,
                        Map.of("contentProfile", "audio.transcript"));

        StepVerifier.create(
                        processor
                                .chunk(content)
                                .flatMap(processor.captionGenerator()::generateForSegments))
                .assertNext(
                        segments ->
                                assertThat(segments)
                                        .singleElement()
                                        .extracting(
                                                com.openmemind.ai.memory.core.extraction.rawdata
                                                                .segment.Segment
                                                        ::caption)
                                        .isEqualTo("Speakers (Alice, Bob): hello world"))
                .verifyComplete();
    }
}
