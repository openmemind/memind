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
package com.openmemind.ai.memory.plugin.rawdata.audio.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.AudioExtractionOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.AudioContent;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.audio.TranscriptSegment;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranscriptSegmentChunkerTest {

    private final TranscriptSegmentChunker chunker = new TranscriptSegmentChunker();

    @Test
    void normalizedWhitespaceMatchReturnsOriginalBoundaryWithoutApproximateFlag() {
        var transcript = "Hello   world\nNext line";
        var content =
                new AudioContent(
                        "audio/mpeg",
                        transcript,
                        List.of(
                                new TranscriptSegment(
                                        "Hello world",
                                        Duration.ZERO,
                                        Duration.ofSeconds(2),
                                        "Alice")),
                        null,
                        Map.of());

        var segments = chunker.chunk(content, AudioExtractionOptions.defaults());

        assertThat(segments).hasSize(1);
        assertThat(segments.getFirst().metadata()).doesNotContainKey("boundaryApproximate");
        assertThat((CharBoundary) segments.getFirst().boundary())
                .isEqualTo(new CharBoundary(0, "Hello   world".length()));
    }

    @Test
    void approximateFallbackMarksBoundaryAndStaysMonotonic() {
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "Alpha-beta",
                        List.of(
                                new TranscriptSegment(
                                        "Alpha beta",
                                        Duration.ZERO,
                                        Duration.ofSeconds(2),
                                        "Alice")),
                        null,
                        Map.of());

        var segments = chunker.chunk(content, AudioExtractionOptions.defaults());

        assertThat(segments).hasSize(1);
        assertThat(segments.getFirst().metadata()).containsEntry("boundaryApproximate", true);
        assertThat(((CharBoundary) segments.getFirst().boundary()).startChar()).isZero();
    }

    @Test
    void oversizedTranscriptChildrenReceiveProportionalTimeBounds() {
        var options = AudioExtractionOptions.defaults();
        var longText = "word ".repeat(options.chunking().hardMaxTokens() * 2);
        var content =
                new AudioContent(
                        "audio/mpeg",
                        longText,
                        List.of(
                                new TranscriptSegment(
                                        longText, Duration.ZERO, Duration.ofMinutes(1), "Alice")),
                        null,
                        Map.of());

        var segments = chunker.chunk(content, options);

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(segments.getFirst().metadata()).containsEntry("startTime", Duration.ZERO);
        assertThat(segments.getFirst().metadata().get("endTime"))
                .isNotEqualTo(Duration.ofMinutes(1));
        assertThat(segments.getLast().metadata()).containsEntry("endTime", Duration.ofMinutes(1));
    }

    @Test
    void mergedTranscriptChunkKeepsSpeakerListWhenSpeakersDiffer() {
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "hello\nworld",
                        List.of(
                                new TranscriptSegment(
                                        "hello", Duration.ZERO, Duration.ofSeconds(1), "Alice"),
                                new TranscriptSegment(
                                        "world",
                                        Duration.ofSeconds(1),
                                        Duration.ofSeconds(2),
                                        "Bob")),
                        null,
                        Map.of());

        var segments = chunker.chunk(content, AudioExtractionOptions.defaults());

        assertThat(segments).hasSize(1);
        assertThat(segments.getFirst().metadata())
                .doesNotContainKey("speaker")
                .containsEntry("speakers", List.of("Alice", "Bob"));
    }
}
