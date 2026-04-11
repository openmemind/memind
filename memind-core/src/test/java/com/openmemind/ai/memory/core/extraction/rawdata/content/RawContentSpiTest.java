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
package com.openmemind.ai.memory.core.extraction.rawdata.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.BuiltinContentProfiles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RawContentSpiTest {

    @Test
    void documentContentExposesMetadataGovernanceProfileAndCopy() {
        var content =
                new DocumentContent(
                        "Guide",
                        "text/markdown",
                        "# title",
                        List.of(),
                        "file:///tmp/guide.md",
                        Map.of("author", "alice"));

        assertThat(content.contentMetadata()).containsEntry("author", "alice");
        assertThat(content.directGovernanceType())
                .isEqualTo(ContentGovernanceType.DOCUMENT_TEXT_LIKE);
        assertThat(content.directContentProfile())
                .isEqualTo(BuiltinContentProfiles.DOCUMENT_MARKDOWN);
        assertThat(content.withMetadata(Map.of("parserId", "direct")))
                .isInstanceOf(DocumentContent.class)
                .extracting(value -> ((DocumentContent) value).metadata().get("parserId"))
                .isEqualTo("direct");
    }

    @Test
    void imageContentExposesMetadataGovernanceProfileAndCopy() {
        var content =
                new ImageContent(
                        "image/png",
                        "chart screenshot",
                        "Q1 revenue",
                        "file:///tmp/chart.png",
                        Map.of("width", 1280));

        assertThat(content.contentMetadata()).containsEntry("width", 1280);
        assertThat(content.directGovernanceType())
                .isEqualTo(ContentGovernanceType.IMAGE_CAPTION_OCR);
        assertThat(content.directContentProfile())
                .isEqualTo(BuiltinContentProfiles.IMAGE_CAPTION_OCR);
        assertThat(content.withMetadata(Map.of("parserId", "vision")))
                .isInstanceOf(ImageContent.class)
                .extracting(value -> ((ImageContent) value).metadata().get("parserId"))
                .isEqualTo("vision");
    }

    @Test
    void audioContentExposesMetadataGovernanceProfileAndCopy() {
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "hello world",
                        List.of(),
                        "file:///tmp/audio.mp3",
                        Map.of("durationSeconds", 12));

        assertThat(content.contentMetadata()).containsEntry("durationSeconds", 12);
        assertThat(content.directGovernanceType())
                .isEqualTo(ContentGovernanceType.AUDIO_TRANSCRIPT);
        assertThat(content.directContentProfile())
                .isEqualTo(BuiltinContentProfiles.AUDIO_TRANSCRIPT);
        assertThat(content.withMetadata(Map.of("parserId", "whisper")))
                .isInstanceOf(AudioContent.class)
                .extracting(value -> ((AudioContent) value).metadata().get("parserId"))
                .isEqualTo("whisper");
    }

    @Test
    void baseRawContentWithMetadataFailsFastWhenNotOverridden() {
        var content = ConversationContent.builder().addUserMessage("hello").build();

        assertThatThrownBy(() -> content.withMetadata(Map.of("x", 1)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ConversationContent");
    }
}
