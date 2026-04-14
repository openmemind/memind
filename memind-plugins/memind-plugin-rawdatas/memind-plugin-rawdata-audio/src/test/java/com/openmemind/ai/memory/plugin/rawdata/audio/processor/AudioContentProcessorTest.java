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
package com.openmemind.ai.memory.plugin.rawdata.audio.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import com.openmemind.ai.memory.plugin.rawdata.audio.chunk.TranscriptSegmentChunker;
import com.openmemind.ai.memory.plugin.rawdata.audio.config.AudioExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.AudioContent;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.audio.TranscriptSegment;
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

        assertThat(processor.contentType()).isEqualTo(AudioContent.TYPE);
        assertThat(processor.supportsInsight()).isTrue();
    }

    @Test
    void shouldChunkTranscriptWhenTimestampedSegmentsAreMissing() {
        var options =
                new AudioExtractionOptions(
                        new SourceLimitOptions(1024),
                        new ParsedContentLimitOptions(2048, null, null, Duration.ofMinutes(30)),
                        64,
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
    void wholeTranscriptWithinBudgetStaysSingleSegmentEvenWithMultipleSpans() {
        var options =
                new AudioExtractionOptions(
                        new SourceLimitOptions(1024),
                        new ParsedContentLimitOptions(2048, null, null, Duration.ofMinutes(30)),
                        64,
                        new TokenChunkingOptions(16, 24));
        var processor = new AudioContentProcessor(new TranscriptSegmentChunker(), options);
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "alpha beta gamma delta epsilon zeta eta theta",
                        List.of(
                                new TranscriptSegment(
                                        "alpha beta gamma delta",
                                        Duration.ZERO,
                                        Duration.ofSeconds(1),
                                        "Alice"),
                                new TranscriptSegment(
                                        "epsilon zeta eta theta",
                                        Duration.ofSeconds(1),
                                        Duration.ofSeconds(2),
                                        "Bob")),
                        null,
                        Map.of());

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSize(1);
                            assertThat(segments.getFirst().content())
                                    .isEqualTo("alpha beta gamma delta epsilon zeta eta theta");
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("startTime", Duration.ZERO)
                                    .containsEntry("endTime", Duration.ofSeconds(2))
                                    .containsEntry("speakers", List.of("Alice", "Bob"));
                        })
                .verifyComplete();
    }

    @Test
    void wholeTranscriptOverBudgetChunksInsteadOfStayingSingleSegment() {
        var options =
                new AudioExtractionOptions(
                        new SourceLimitOptions(1024),
                        new ParsedContentLimitOptions(4096, null, null, Duration.ofMinutes(30)),
                        20,
                        new TokenChunkingOptions(8, 12));
        var processor = new AudioContentProcessor(new TranscriptSegmentChunker(), options);
        var transcript = "word ".repeat(40).trim();
        var content =
                new AudioContent(
                        "audio/mpeg",
                        transcript,
                        List.of(
                                new TranscriptSegment(
                                        "word ".repeat(10).trim(),
                                        Duration.ZERO,
                                        Duration.ofSeconds(10),
                                        "Alice"),
                                new TranscriptSegment(
                                        "word ".repeat(10).trim(),
                                        Duration.ofSeconds(10),
                                        Duration.ofSeconds(20),
                                        "Alice"),
                                new TranscriptSegment(
                                        "word ".repeat(10).trim(),
                                        Duration.ofSeconds(20),
                                        Duration.ofSeconds(30),
                                        "Alice"),
                                new TranscriptSegment(
                                        "word ".repeat(10).trim(),
                                        Duration.ofSeconds(30),
                                        Duration.ofSeconds(40),
                                        "Alice")),
                        null,
                        Map.of());

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSizeGreaterThan(1);
                            assertThat(segments)
                                    .allSatisfy(
                                            segment ->
                                                    assertThat(
                                                                    TokenUtils.countTokens(
                                                                            segment.content()))
                                                            .isLessThanOrEqualTo(
                                                                    options.chunking()
                                                                            .hardMaxTokens()));
                        })
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
                            assertThat(segments.getFirst().content()).isEqualTo("hello world");
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("speaker", "Alice")
                                    .containsEntry("startTime", Duration.ZERO)
                                    .containsEntry("endTime", Duration.ofSeconds(2));
                        })
                .verifyComplete();
    }

    @Test
    void audioProcessorSplitsOversizedSingleTranscriptSegmentWithinHardTokenBudget() {
        var options =
                new AudioExtractionOptions(
                        new SourceLimitOptions(25L * 1024 * 1024),
                        new ParsedContentLimitOptions(18_000, null, null, Duration.ofMinutes(30)),
                        400,
                        new TokenChunkingOptions(800, 1000));
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
                                        .extracting(Segment::caption)
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
                                        .extracting(Segment::caption)
                                        .isEqualTo("Speakers (Alice, Bob): hello world"))
                .verifyComplete();
    }
}
