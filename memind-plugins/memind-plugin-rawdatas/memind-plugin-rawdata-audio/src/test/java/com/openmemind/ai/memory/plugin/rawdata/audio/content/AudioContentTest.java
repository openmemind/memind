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
package com.openmemind.ai.memory.plugin.rawdata.audio.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.utils.HashUtils;
import com.openmemind.ai.memory.plugin.rawdata.audio.AudioSemantics;
import com.openmemind.ai.memory.plugin.rawdata.audio.content.audio.TranscriptSegment;
import com.openmemind.ai.memory.plugin.rawdata.audio.plugin.AudioRawContentTypeRegistrar;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AudioContentTest {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RawContentJackson.registerAll(mapper, List.of(new AudioRawContentTypeRegistrar()));
        return mapper;
    }

    @Test
    void contentTypeToContentStringAndContentIdMatchTranscript() {
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "Hello everyone, thanks for joining.",
                        List.of(),
                        "file:///tmp/meeting.mp3",
                        Map.of("durationSeconds", 3));

        assertThat(content.contentType()).isEqualTo(AudioContent.TYPE);
        assertThat(content.toContentString()).isEqualTo("Hello everyone, thanks for joining.");
        assertThat(content.getContentId())
                .isEqualTo(HashUtils.sampledSha256("Hello everyone, thanks for joining."));
    }

    @Test
    void defaultsAreEmptyAndNullableWhereExpected() {
        var content = AudioContent.of("Hello everyone, thanks for joining.");

        assertThat(content.mimeType()).isNull();
        assertThat(content.segments()).isEmpty();
        assertThat(content.sourceUri()).isNull();
        assertThat(content.metadata()).isEmpty();
    }

    @Test
    void contentExposesMetadataGovernanceProfileAndCopy() {
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "hello world",
                        List.of(),
                        "file:///tmp/audio.mp3",
                        Map.of("durationSeconds", 12));

        assertThat(content.contentMetadata()).containsEntry("durationSeconds", 12);
        assertThat(content.directGovernanceType()).isEqualTo(AudioSemantics.GOVERNANCE_TRANSCRIPT);
        assertThat(content.directContentProfile()).isEqualTo(AudioSemantics.PROFILE_TRANSCRIPT);
        assertThat(content.withMetadata(Map.of("parserId", "whisper")))
                .isInstanceOf(AudioContent.class)
                .extracting(value -> ((AudioContent) value).metadata().get("parserId"))
                .isEqualTo("whisper");
    }

    @Test
    void jacksonRoundTripPreservesSubtypeAndSegments() throws Exception {
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "Hello everyone, thanks for joining.",
                        List.of(
                                new TranscriptSegment(
                                        "Hello everyone",
                                        Duration.ZERO,
                                        Duration.ofSeconds(2),
                                        "Alice")),
                        "file:///tmp/meeting.mp3",
                        Map.of("durationSeconds", 3));

        String json = OBJECT_MAPPER.writeValueAsString(content);
        RawContent decoded = OBJECT_MAPPER.readValue(json, RawContent.class);

        assertThat(json).contains("\"type\":\"audio\"");
        assertThat(decoded).isInstanceOf(AudioContent.class);
        assertThat((AudioContent) decoded)
                .extracting(
                        AudioContent::mimeType, AudioContent::transcript, AudioContent::sourceUri)
                .containsExactly(
                        "audio/mpeg",
                        "Hello everyone, thanks for joining.",
                        "file:///tmp/meeting.mp3");
        assertThat(((AudioContent) decoded).segments()).hasSize(1);
    }
}
