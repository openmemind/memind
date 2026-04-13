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

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.extraction.source.DirectContentSource;
import com.openmemind.ai.memory.core.extraction.source.FileExtractionSource;
import com.openmemind.ai.memory.core.extraction.source.UrlExtractionSource;
import com.openmemind.ai.memory.core.support.TestDocumentContent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtractionRequestTest {

    @Test
    void ofShouldNormalizeAnyContentThatDeclaresDirectGovernanceType() {
        final class CustomContent extends RawContent {
            private final Map<String, Object> metadata;

            private CustomContent(Map<String, Object> metadata) {
                this.metadata = Map.copyOf(metadata);
            }

            @Override
            public String contentType() {
                return "CUSTOM";
            }

            @Override
            public String toContentString() {
                return "payload";
            }

            @Override
            public String getContentId() {
                return "custom-id";
            }

            @Override
            public Map<String, Object> contentMetadata() {
                return metadata;
            }

            @Override
            public RawContent withMetadata(Map<String, Object> metadata) {
                return new CustomContent(metadata);
            }

            @Override
            public String mimeType() {
                return "text/plain";
            }

            @Override
            public String directGovernanceType() {
                return TestDocumentContent.GOVERNANCE_TEXT_LIKE;
            }

            @Override
            public String directContentProfile() {
                return "custom.text";
            }
        }

        RawContent content = new CustomContent(Map.of("x", 1));

        var request = ExtractionRequest.of(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.source()).isInstanceOf(DirectContentSource.class);
        assertThat(request.content()).isNotNull();
        assertThat(request.content().contentType()).isEqualTo("CUSTOM");
        assertThat(request.metadata())
                .containsEntry("x", 1)
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("parserId", "direct")
                .containsEntry("contentProfile", "custom.text")
                .containsEntry("governanceType", TestDocumentContent.GOVERNANCE_TEXT_LIKE);
    }

    @Test
    void coreDocumentImageAndAudioConvenienceFactoriesAreRemoved() {
        assertThat(ExtractionRequest.class.getMethods())
                .noneMatch(
                        method ->
                                method.getName().equals("document")
                                        || method.getName().equals("image")
                                        || method.getName().equals("audio"));
    }

    @Test
    void ofShouldNormalizeDirectDocumentMetadata() {
        var content = TestDocumentContent.of("Notes", "text/markdown", "# title");

        var request = ExtractionRequest.of(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.source()).isInstanceOf(DirectContentSource.class);
        assertThat(request.content()).isSameAs(((DirectContentSource) request.source()).content());
        assertThat(request.content().contentType()).isEqualTo(TestDocumentContent.TYPE);
        assertThat(request.metadata())
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("parserId", "direct")
                .containsEntry("contentProfile", "document.markdown")
                .containsEntry("governanceType", TestDocumentContent.GOVERNANCE_TEXT_LIKE)
                .containsEntry("mimeType", "text/markdown");
    }

    @Test
    void documentFactoryShouldNotOverrideExplicitParserMetadata() {
        var content =
                new TestDocumentContent(
                        "Binary Export",
                        "application/pdf",
                        "hello",
                        null,
                        TestDocumentContent.GOVERNANCE_BINARY,
                        "document.binary",
                        Map.of("parserId", "document-tika", "contentProfile", "document.binary"));

        var request = ExtractionRequest.of(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.metadata())
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("parserId", "document-tika")
                .containsEntry("contentProfile", "document.binary")
                .containsEntry("governanceType", TestDocumentContent.GOVERNANCE_BINARY)
                .containsEntry("mimeType", "application/pdf");
    }

    @Test
    void documentFactoryShouldNormalizeMetadata() {
        var content =
                new TestDocumentContent(
                        "Report",
                        "application/pdf",
                        "revenue grew",
                        "file:///tmp/report.pdf",
                        TestDocumentContent.GOVERNANCE_BINARY,
                        "document.binary",
                        Map.of("author", "Alice"));

        var request = ExtractionRequest.of(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.source()).isInstanceOf(DirectContentSource.class);
        assertThat(request.content()).isNotNull();
        assertThat(request.content().contentType()).isEqualTo(TestDocumentContent.TYPE);
        assertThat(request.metadata())
                .containsEntry("author", "Alice")
                .containsEntry("sourceKind", "DIRECT")
                .containsEntry("parserId", "direct")
                .containsEntry("contentProfile", "document.binary")
                .containsEntry("governanceType", TestDocumentContent.GOVERNANCE_BINARY)
                .containsEntry("mimeType", "application/pdf")
                .containsEntry("sourceUri", "file:///tmp/report.pdf");
    }

    @Test
    void documentFactoryShouldPreserveCustomProfileWithinDerivedGovernanceFamily() {
        var content =
                new TestDocumentContent(
                        "Guide",
                        "text/markdown",
                        "# title",
                        null,
                        TestDocumentContent.GOVERNANCE_TEXT_LIKE,
                        "document.markdown",
                        Map.of("contentProfile", "document.custom.markdown"));

        var request = ExtractionRequest.of(DefaultMemoryId.of("user-1", "agent-1"), content);

        assertThat(request.metadata())
                .containsEntry("contentProfile", "document.custom.markdown")
                .containsEntry("governanceType", TestDocumentContent.GOVERNANCE_TEXT_LIKE);
    }

    @Test
    void documentFactoryShouldRejectConflictingExplicitGovernanceType() {
        var content =
                new TestDocumentContent(
                        "Guide",
                        "text/markdown",
                        "# title",
                        null,
                        TestDocumentContent.GOVERNANCE_TEXT_LIKE,
                        "document.markdown",
                        Map.of("governanceType", TestDocumentContent.GOVERNANCE_BINARY));

        assertThatThrownBy(
                        () ->
                                ExtractionRequest.of(
                                        DefaultMemoryId.of("user-1", "agent-1"), content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("governanceType")
                .hasMessageContaining(TestDocumentContent.GOVERNANCE_TEXT_LIKE);
    }

    @Test
    void fileFactoryShouldPopulateFileSourceAndDefensivelyCopyBytes() {
        var bytes = new byte[] {1, 2, 3};

        var request =
                ExtractionRequest.file(
                        DefaultMemoryId.of("user-1", "agent-1"),
                        "report.pdf",
                        bytes,
                        "application/pdf");
        bytes[0] = 9;

        assertThat(request.content()).isNull();
        assertThat(request.source()).isInstanceOf(FileExtractionSource.class);
        var fileSource = (FileExtractionSource) request.source();
        assertThat(fileSource.fileName()).isEqualTo("report.pdf");
        assertThat(fileSource.mimeType()).isEqualTo("application/pdf");
        assertThat(fileSource.data()).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void urlFactoryShouldPopulateUrlSourceAndOptionalOverrides() {
        var request =
                ExtractionRequest.url(
                        DefaultMemoryId.of("user-1", "agent-1"),
                        "https://example.com/report.pdf",
                        "report.pdf",
                        "application/pdf");

        assertThat(request.content()).isNull();
        assertThat(request.source()).isInstanceOf(UrlExtractionSource.class);
        var urlSource = (UrlExtractionSource) request.source();
        assertThat(urlSource.sourceUrl()).isEqualTo("https://example.com/report.pdf");
        assertThat(urlSource.fileName()).isEqualTo("report.pdf");
        assertThat(urlSource.mimeType()).isEqualTo("application/pdf");
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
    void withConfigAndWithMetadataShouldPreserveFileSource() {
        var request =
                ExtractionRequest.file(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                "report.pdf",
                                new byte[] {1, 2, 3},
                                "application/pdf")
                        .withMetadata("source", "upload")
                        .withConfig(ExtractionConfig.agentOnly());

        assertThat(request.source()).isInstanceOf(FileExtractionSource.class);
        assertThat(((FileExtractionSource) request.source()).fileName()).isEqualTo("report.pdf");
        assertThat(request.metadata()).containsEntry("source", "upload");
        assertThat(request.config()).isEqualTo(ExtractionConfig.agentOnly());
    }

    @Test
    void withConfigAndWithMetadataShouldPreserveUrlSource() {
        var request =
                ExtractionRequest.url(
                                DefaultMemoryId.of("user-1", "agent-1"),
                                "https://example.com/report.pdf",
                                "report.pdf",
                                "application/pdf")
                        .withMetadata("source", "link")
                        .withConfig(ExtractionConfig.agentOnly());

        assertThat(request.source()).isInstanceOf(UrlExtractionSource.class);
        var urlSource = (UrlExtractionSource) request.source();
        assertThat(urlSource.sourceUrl()).isEqualTo("https://example.com/report.pdf");
        assertThat(urlSource.fileName()).isEqualTo("report.pdf");
        assertThat(urlSource.mimeType()).isEqualTo("application/pdf");
        assertThat(request.metadata()).containsEntry("source", "link");
        assertThat(request.config()).isEqualTo(ExtractionConfig.agentOnly());
    }
}
