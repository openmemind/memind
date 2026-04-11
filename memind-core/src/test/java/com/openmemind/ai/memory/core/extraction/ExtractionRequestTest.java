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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.DocumentContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ImageContent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtractionRequestTest {

    @Test
    void ofShouldNormalizeDirectDocumentMetadata() {
        var content = DocumentContent.of("Notes", "text/markdown", "# title");

        var request = ExtractionRequest.of(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.contentType()).isEqualTo(ContentTypes.DOCUMENT);
        assertThat(request.metadata())
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("parserId", "direct")
                .containsEntry("contentProfile", "document.markdown")
                .containsEntry("governanceType", ContentGovernanceType.DOCUMENT_TEXT_LIKE.name())
                .containsEntry("mimeType", "text/markdown");
    }

    @Test
    void documentFactoryShouldNotOverrideExplicitParserMetadata() {
        var content =
                new DocumentContent(
                        "Binary Export",
                        "application/pdf",
                        "hello",
                        java.util.List.of(),
                        null,
                        Map.of("parserId", "document-tika", "contentProfile", "document.binary"));

        var request = ExtractionRequest.document(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.metadata())
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("parserId", "document-tika")
                .containsEntry("contentProfile", "document.binary")
                .containsEntry("governanceType", ContentGovernanceType.DOCUMENT_BINARY.name())
                .containsEntry("mimeType", "application/pdf");
    }

    @Test
    void documentFactoryShouldNormalizeMetadata() {
        var content =
                new DocumentContent(
                        "Report",
                        "application/pdf",
                        "revenue grew",
                        java.util.List.of(),
                        "file:///tmp/report.pdf",
                        Map.of("author", "Alice"));

        var request = ExtractionRequest.document(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.contentType()).isEqualTo(ContentTypes.DOCUMENT);
        assertThat(request.metadata())
                .containsEntry("author", "Alice")
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("parserId", "direct")
                .containsEntry("contentProfile", "document.binary")
                .containsEntry("governanceType", ContentGovernanceType.DOCUMENT_BINARY.name())
                .containsEntry("mimeType", "application/pdf")
                .containsEntry("sourceUri", "file:///tmp/report.pdf");
    }

    @Test
    void documentFactoryShouldPreserveCustomProfileWithinDerivedGovernanceFamily() {
        var content =
                new DocumentContent(
                        "Guide",
                        "text/markdown",
                        "# title",
                        java.util.List.of(),
                        null,
                        Map.of("contentProfile", "document.custom.markdown"));

        var request = ExtractionRequest.document(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.metadata())
                .containsEntry("contentProfile", "document.custom.markdown")
                .containsEntry("governanceType", ContentGovernanceType.DOCUMENT_TEXT_LIKE.name());
    }

    @Test
    void documentFactoryShouldRejectConflictingExplicitGovernanceType() {
        var content =
                new DocumentContent(
                        "Guide",
                        "text/markdown",
                        "# title",
                        java.util.List.of(),
                        null,
                        Map.of("governanceType", ContentGovernanceType.DOCUMENT_BINARY.name()));

        assertThatThrownBy(
                        () ->
                                ExtractionRequest.document(
                                        DefaultMemoryId.of("user-1", "agent-1"), content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("governanceType")
                .hasMessageContaining(ContentGovernanceType.DOCUMENT_TEXT_LIKE.name());
    }

    @Test
    void documentFactoryShouldRejectBuiltinProfileThatConflictsWithDerivedGovernance() {
        var content =
                new DocumentContent(
                        "Guide",
                        "text/markdown",
                        "# title",
                        java.util.List.of(),
                        null,
                        Map.of("contentProfile", "document.binary"));

        assertThatThrownBy(
                        () ->
                                ExtractionRequest.document(
                                        DefaultMemoryId.of("user-1", "agent-1"), content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentProfile")
                .hasMessageContaining("document.binary");
    }

    @Test
    void imageFactoryShouldNormalizeMimeTypeAndSourceUri() {
        var content =
                new ImageContent(
                        "image/png",
                        "dashboard screenshot",
                        "total revenue 30%",
                        "file:///tmp/dashboard.png",
                        Map.of("width", 1280));

        var request = ExtractionRequest.image(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.contentType()).isEqualTo(ContentTypes.IMAGE);
        assertThat(request.metadata())
                .containsEntry("width", 1280)
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("parserId", "direct")
                .containsEntry("contentProfile", "image.caption-ocr")
                .containsEntry("governanceType", ContentGovernanceType.IMAGE_CAPTION_OCR.name())
                .containsEntry("mimeType", "image/png")
                .containsEntry("sourceUri", "file:///tmp/dashboard.png");
    }

    @Test
    void audioFactoryShouldNormalizeMimeTypeAndKeepEmptyMetadataSafe() {
        var content =
                new AudioContent(
                        "audio/mpeg",
                        "hello world",
                        java.util.List.of(),
                        "file:///tmp/audio.mp3",
                        Map.of());

        var request = ExtractionRequest.audio(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.contentType()).isEqualTo(ContentTypes.AUDIO);
        assertThat(request.metadata())
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("parserId", "direct")
                .containsEntry("contentProfile", "audio.transcript")
                .containsEntry("governanceType", ContentGovernanceType.AUDIO_TRANSCRIPT.name())
                .containsEntry("mimeType", "audio/mpeg")
                .containsEntry("sourceUri", "file:///tmp/audio.mp3");
    }

    @Test
    void fileFactoryShouldPopulateRawFileInputAndDefensivelyCopyBytes() {
        var bytes = new byte[] {1, 2, 3};

        var request =
                ExtractionRequest.file(
                        DefaultMemoryId.of("user-1", "agent-1"),
                        "report.pdf",
                        bytes,
                        "application/pdf");
        bytes[0] = 9;

        assertThat(request.content()).isNull();
        assertThat(request.fileInput()).isNotNull();
        assertThat(request.fileInput().fileName()).isEqualTo("report.pdf");
        assertThat(request.fileInput().mimeType()).isEqualTo("application/pdf");
        assertThat(request.fileInput().data()).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void urlFactoryShouldPopulateRawUrlInputAndOptionalOverrides() {
        var request =
                ExtractionRequest.url(
                        DefaultMemoryId.of("user-1", "agent-1"),
                        "https://example.com/report.pdf",
                        "report.pdf",
                        "application/pdf");

        assertThat(request.content()).isNull();
        assertThat(request.fileInput()).isNull();
        assertThat(request.urlInput()).isNotNull();
        assertThat(request.urlInput().sourceUrl()).isEqualTo("https://example.com/report.pdf");
        assertThat(request.urlInput().fileName()).isEqualTo("report.pdf");
        assertThat(request.urlInput().mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void urlFactoryShouldRejectUnsupportedSourceUrlScheme() {
        assertThatThrownBy(
                        () ->
                                ExtractionRequest.url(
                                        DefaultMemoryId.of("user-1", "agent-1"),
                                        "ftp://example.com/report.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only http/https sourceUrl is supported");
    }

    @Test
    void withConfigAndWithMetadataShouldPreserveRawFileInput() {
        var request =
                ExtractionRequest.file(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                "report.pdf",
                                new byte[] {1, 2, 3},
                                "application/pdf")
                        .withMetadata("source", "upload")
                        .withConfig(ExtractionConfig.agentOnly());

        assertThat(request.fileInput()).isNotNull();
        assertThat(request.fileInput().fileName()).isEqualTo("report.pdf");
        assertThat(request.metadata()).containsEntry("source", "upload");
        assertThat(request.config()).isEqualTo(ExtractionConfig.agentOnly());
    }

    @Test
    void withConfigAndWithMetadataShouldPreserveRawUrlInput() {
        var request =
                ExtractionRequest.url(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                "https://example.com/report.pdf",
                                "report.pdf",
                                "application/pdf")
                        .withMetadata("source", "link")
                        .withConfig(ExtractionConfig.agentOnly());

        assertThat(request.urlInput()).isNotNull();
        assertThat(request.urlInput().sourceUrl()).isEqualTo("https://example.com/report.pdf");
        assertThat(request.urlInput().fileName()).isEqualTo("report.pdf");
        assertThat(request.urlInput().mimeType()).isEqualTo("application/pdf");
        assertThat(request.metadata()).containsEntry("source", "link");
        assertThat(request.config()).isEqualTo(ExtractionConfig.agentOnly());
    }
}
