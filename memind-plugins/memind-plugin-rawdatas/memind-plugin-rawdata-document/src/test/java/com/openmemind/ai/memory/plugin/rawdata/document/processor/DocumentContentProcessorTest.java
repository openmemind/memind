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
package com.openmemind.ai.memory.plugin.rawdata.document.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import com.openmemind.ai.memory.plugin.rawdata.document.chunk.ProfileAwareDocumentChunker;
import com.openmemind.ai.memory.plugin.rawdata.document.config.DocumentExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
import com.openmemind.ai.memory.plugin.rawdata.document.content.document.DocumentSection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DocumentContentProcessorTest {

    @Test
    void pluginOwnsDocumentExtractionDefaults() {
        assertThat(DocumentExtractionOptions.defaults().binaryParsedLimit().maxTokens())
                .isEqualTo(30_000);
    }

    @Test
    void contentTypeShouldBeDocument() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());

        assertThat(processor.contentType()).isEqualTo(DocumentContent.TYPE);
        assertThat(processor.supportsInsight()).isTrue();
    }

    @Test
    void documentProcessorUsesHeadingAwareChunkingForMarkdownProfile() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());
        var content =
                new DocumentContent(
                        "Guide",
                        "text/markdown",
                        "# Intro\nhello\n\n## Usage\nworld",
                        List.of(),
                        null,
                        Map.of("contentProfile", "document.markdown"));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSize(2);
                            assertThat(segments.getFirst().content()).contains("# Intro");
                            assertThat(segments.get(1).content()).contains("## Usage");
                        })
                .verifyComplete();
    }

    @Test
    void documentProcessorKeepsChunksWithinHardTokenBudget() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());
        var content =
                new DocumentContent(
                        "Guide",
                        "text/markdown",
                        "# Intro\n"
                                + "word ".repeat(4_000)
                                + "\n\n## Usage\n"
                                + "word ".repeat(4_000),
                        List.of(),
                        null,
                        Map.of("contentProfile", "document.markdown"));

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
                                                                        DocumentExtractionOptions
                                                                                .defaults()
                                                                                .textLikeChunking()
                                                                                .hardMaxTokens())))
                .verifyComplete();
    }

    @Test
    void documentProcessorUsesBinaryChunkingForCustomBinaryProfile() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());
        String text = "word ".repeat(1_300);
        var content =
                new DocumentContent(
                        "Manual",
                        "application/pdf",
                        text,
                        List.of(),
                        null,
                        Map.of(
                                "contentProfile",
                                "document.pdf.tika",
                                "governanceType",
                                ContentGovernanceType.DOCUMENT_BINARY.name()));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSize(1);
                            assertThat(TokenUtils.countTokens(segments.getFirst().content()))
                                    .isGreaterThan(
                                            DocumentExtractionOptions.defaults()
                                                    .textLikeChunking()
                                                    .hardMaxTokens())
                                    .isLessThanOrEqualTo(
                                            DocumentExtractionOptions.defaults()
                                                    .binaryChunking()
                                                    .hardMaxTokens());
                        })
                .verifyComplete();
    }

    @Test
    void shouldPreserveSectionMetadataWhenSectionsExist() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());
        var content =
                new DocumentContent(
                        "Report",
                        "application/pdf",
                        "summary\n\nappendix",
                        List.of(
                                new DocumentSection("Summary", "summary", 0, Map.of("page", 1)),
                                new DocumentSection("Appendix", "appendix", 1, Map.of("page", 2))),
                        "file:///tmp/report.pdf",
                        Map.of("author", "Alice"));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSize(2);
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("sectionIndex", 0)
                                    .containsEntry("sectionTitle", "Summary")
                                    .containsEntry("page", 1);
                            assertThat(segments.get(1).metadata())
                                    .containsEntry("sectionIndex", 1)
                                    .containsEntry("sectionTitle", "Appendix")
                                    .containsEntry("page", 2);
                        })
                .verifyComplete();
    }
}
