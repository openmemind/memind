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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.builder.AudioExtractionOptions;
import com.openmemind.ai.memory.core.builder.DocumentExtractionOptions;
import com.openmemind.ai.memory.core.builder.ImageExtractionOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.builder.SourceLimitOptions;
import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.builder.ToolCallChunkingOptions;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.context.CommitDetectorConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.ConversationChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ImageContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.audio.TranscriptSegment;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParsedContentLimitValidatorTest {

    @Test
    void rejectsDocumentWhenParsedTokensExceedBinaryLimit() {
        var validator = new ParsedContentLimitValidator(restrictiveOptions());
        var content =
                new DocumentContent(
                        "Manual",
                        "application/pdf",
                        "word ".repeat(200),
                        List.of(),
                        null,
                        Map.of("contentProfile", "document.binary"));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ParsedContentTooLargeException.class)
                .hasMessageContaining("document.binary");
    }

    @Test
    void rejectsAudioWhenTranscriptDurationExceedsLimit() {
        var validator = new ParsedContentLimitValidator(restrictiveOptions());
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "short transcript",
                        List.of(
                                new TranscriptSegment(
                                        "short transcript",
                                        Duration.ZERO,
                                        Duration.ofMinutes(6),
                                        "speaker-1")),
                        null,
                        Map.of("contentProfile", "audio.transcript"));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ParsedContentTooLargeException.class)
                .hasMessageContaining("audio.transcript")
                .hasMessageContaining("duration");
    }

    @Test
    void acceptsImageWithinConfiguredParsedLimit() {
        var validator = new ParsedContentLimitValidator(restrictiveOptions());
        var content =
                new ImageContent(
                        "image/png",
                        "dashboard screenshot",
                        "Q1 revenue up",
                        null,
                        Map.of("contentProfile", "image.caption-ocr"));

        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @Test
    void acceptsCustomDocumentProfileWhenGovernanceTypeIsProvided() {
        var validator = new ParsedContentLimitValidator(restrictiveOptions());
        var content =
                new DocumentContent(
                        "Manual",
                        "application/pdf",
                        "word ".repeat(100),
                        List.of(),
                        null,
                        Map.of(
                                "contentProfile",
                                "document.pdf.tika",
                                "governanceType",
                                ContentGovernanceType.DOCUMENT_BINARY.name()));

        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownProfileWithoutGovernanceType() {
        var validator = new ParsedContentLimitValidator(restrictiveOptions());
        var content =
                new DocumentContent(
                        "Manual",
                        "application/pdf",
                        "word ".repeat(100),
                        List.of(),
                        null,
                        Map.of("contentProfile", "document.pdf.tika"));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document.pdf.tika")
                .hasMessageContaining("governanceType");
    }

    private RawDataExtractionOptions restrictiveOptions() {
        return new RawDataExtractionOptions(
                ConversationChunkingConfig.DEFAULT,
                new DocumentExtractionOptions(
                        new SourceLimitOptions(1024),
                        new SourceLimitOptions(1024),
                        new com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions(
                                256, 10, 10, null),
                        new com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions(
                                128, 10, 10, null),
                        new TokenChunkingOptions(64, 96),
                        new TokenChunkingOptions(64, 96)),
                new ImageExtractionOptions(
                        new SourceLimitOptions(1024),
                        new com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions(
                                64, null, null, null),
                        new TokenChunkingOptions(32, 48),
                        16),
                new AudioExtractionOptions(
                        new SourceLimitOptions(1024),
                        new com.openmemind.ai.memory.core.builder.ParsedContentLimitOptions(
                                128, null, null, Duration.ofMinutes(5)),
                        new TokenChunkingOptions(64, 96)),
                ToolCallChunkingOptions.defaults(),
                CommitDetectorConfig.defaults(),
                64);
    }
}
