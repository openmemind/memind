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

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TextChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TextChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.audio.TranscriptSegment;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class AudioContentProcessorTest {

    @Test
    void contentTypeShouldBeAudio() {
        var processor = new AudioContentProcessor(new TextChunker(), TextChunkingConfig.DEFAULT);

        assertThat(processor.contentType()).isEqualTo(ContentTypes.AUDIO);
        assertThat(processor.supportsInsight()).isTrue();
    }

    @Test
    void shouldChunkTranscriptWhenTimestampedSegmentsAreMissing() {
        var processor =
                new AudioContentProcessor(
                        new TextChunker(),
                        new TextChunkingConfig(12, TextChunkingConfig.ChunkBoundary.LINE));
        var content = AudioContent.of("alpha\nbeta\ngamma");

        StepVerifier.create(processor.chunk(content))
                .assertNext(segments -> assertThat(segments).hasSize(2))
                .verifyComplete();
    }

    @Test
    void shouldMergeTranscriptSegmentsAndProjectTimeMetadata() {
        var processor =
                new AudioContentProcessor(
                        new TextChunker(),
                        new TextChunkingConfig(64, TextChunkingConfig.ChunkBoundary.CHARACTER));
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
}
