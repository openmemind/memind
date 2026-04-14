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
import static org.mockito.Mockito.mock;

import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import com.openmemind.ai.memory.plugin.rawdata.document.DocumentSemantics;
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
        assertThat(DocumentExtractionOptions.defaults().textLikeParsedLimit().maxTokens())
                .isEqualTo(100_000);
        assertThat(DocumentExtractionOptions.defaults().binaryParsedLimit().maxTokens())
                .isEqualTo(100_000);
        assertThat(DocumentExtractionOptions.defaults().wholeDocumentMaxTokens()).isEqualTo(12_000);
        assertThat(DocumentExtractionOptions.defaults().textLikeChunking().targetTokens())
                .isEqualTo(1_200);
        assertThat(DocumentExtractionOptions.defaults().textLikeChunking().hardMaxTokens())
                .isEqualTo(1_800);
        assertThat(DocumentExtractionOptions.defaults().textLikeMinChunkTokens()).isEqualTo(300);
        assertThat(DocumentExtractionOptions.defaults().binaryMinChunkTokens()).isEqualTo(300);
        assertThat(DocumentExtractionOptions.defaults().pdfMaxMergedPages()).isEqualTo(3);
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
    void processorUsesInjectedCaptionGenerator() {
        var captionGenerator = mock(CaptionGenerator.class);
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(),
                        DocumentExtractionOptions.defaults(),
                        captionGenerator);

        assertThat(processor.captionGenerator()).isSameAs(captionGenerator);
    }

    @Test
    void legacyConstructorRemainsAvailableForCompatibility() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());

        assertThat(processor.captionGenerator()).isNotNull();
    }

    @Test
    void documentProcessorKeepsSmallMarkdownAsWholeDocument() {
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
                            assertThat(segments).singleElement();
                            assertThat(segments.getFirst().content())
                                    .contains("# Intro")
                                    .contains("## Usage");
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("chunkStrategy", "document-whole")
                                    .containsEntry("structureType", "document")
                                    .containsEntry("chunkIndex", 0);
                        })
                .verifyComplete();
    }

    @Test
    void documentProcessorKeepsChunksWithinHardTokenBudget() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(),
                        optionsWithWholeDocumentMaxTokens(1_000));
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
    void documentProcessorKeepsSmallCsvAsWholeDocument() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());
        var content =
                new DocumentContent(
                        "People",
                        "text/csv",
                        String.join(
                                "\n",
                                "Row 1:",
                                "name: Alice, team: Core",
                                "",
                                "Row 2:",
                                "name: Bob, team: AI"),
                        List.of(),
                        null,
                        Map.of("contentProfile", DocumentSemantics.PROFILE_CSV));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).singleElement();
                            assertThat(segments.getFirst().content())
                                    .contains("Row 1:")
                                    .contains("Row 2:");
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("chunkStrategy", "document-whole")
                                    .containsEntry("structureType", "document")
                                    .containsEntry("chunkIndex", 0);
                        })
                .verifyComplete();
    }

    @Test
    void documentProcessorKeepsSmallPdfAsWholeDocument() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());
        var content =
                new DocumentContent(
                        "Manual",
                        "application/pdf",
                        "Page 1:\nalpha\n\nPage 2:\nbeta\n\nPage 3:\n" + "word ".repeat(900),
                        List.of(),
                        null,
                        Map.of("contentProfile", DocumentSemantics.PROFILE_PDF_TIKA));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).singleElement();
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("chunkStrategy", "document-whole")
                                    .containsEntry("structureType", "document")
                                    .containsEntry("chunkIndex", 0);
                        })
                .verifyComplete();
    }

    @Test
    void normalizeForItemBudgetUsesMarkdownAwareSecondarySplit() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());
        var content =
                new DocumentContent(
                        "Guide",
                        "text/markdown",
                        "# Intro\n"
                                + "word ".repeat(3_000)
                                + "\n\n## Usage\n"
                                + "word ".repeat(3_000),
                        List.of(),
                        null,
                        Map.of("contentProfile", DocumentSemantics.PROFILE_MARKDOWN));
        var rawDataResult =
                new com.openmemind.ai.memory.core.extraction.result.RawDataResult(
                        List.of(),
                        List.of(
                                new com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment(
                                        content.toContentString(),
                                        null,
                                        0,
                                        content.toContentString().length(),
                                        "raw-1",
                                        Map.of(
                                                "contentProfile",
                                                DocumentSemantics.PROFILE_MARKDOWN))),
                        false);
        var itemConfig =
                new com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig(
                        com.openmemind.ai.memory.core.data.enums.MemoryScope.USER,
                        DocumentContent.TYPE,
                        false,
                        "English",
                        new com.openmemind.ai.memory.core.builder.PromptBudgetOptions(
                                1_800, 200, 200, 200));

        var normalized = processor.normalizeForItemBudget(content, rawDataResult, itemConfig);

        assertThat(normalized.segments()).hasSizeGreaterThan(1);
        assertThat(normalized.segments())
                .allSatisfy(
                        segment ->
                                assertThat(TokenUtils.countTokens(segment.text()))
                                        .isLessThanOrEqualTo(1_200));
    }

    @Test
    void normalizeForItemBudgetReusesProfileSpecificChunking() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());
        var content =
                new DocumentContent(
                        "People",
                        "text/csv",
                        "Row 1:\n" + "column1: word, ".repeat(2_600),
                        List.of(),
                        null,
                        Map.of("contentProfile", DocumentSemantics.PROFILE_CSV));
        var rawDataResult =
                new com.openmemind.ai.memory.core.extraction.result.RawDataResult(
                        List.of(),
                        List.of(
                                new com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment(
                                        content.toContentString(),
                                        null,
                                        0,
                                        content.toContentString().length(),
                                        "raw-1",
                                        content.metadata())),
                        false);
        var itemConfig =
                new com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig(
                        com.openmemind.ai.memory.core.data.enums.MemoryScope.USER,
                        DocumentContent.TYPE,
                        false,
                        "English",
                        new com.openmemind.ai.memory.core.builder.PromptBudgetOptions(
                                1_800, 200, 200, 200));

        var normalized = processor.normalizeForItemBudget(content, rawDataResult, itemConfig);

        assertThat(normalized.segments()).hasSizeGreaterThan(1);
        assertThat(normalized.segments())
                .allSatisfy(segment -> assertThat(segment.text()).startsWith("Row 1:"));
    }

    @Test
    void documentProcessorKeepsSectionAsOuterBoundary() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), optionsWithWholeDocumentMaxTokens(5));
        var content =
                new DocumentContent(
                        "Report",
                        "application/pdf",
                        "Page 1:\nsummary\n\nPage 2:\nappendix",
                        List.of(
                                new DocumentSection(
                                        "Summary", "Page 1:\nsummary", 0, Map.of("page", 1)),
                                new DocumentSection(
                                        "Appendix", "Page 2:\nappendix", 1, Map.of("page", 2))),
                        null,
                        Map.of(
                                "author",
                                "Alice",
                                "contentProfile",
                                DocumentSemantics.PROFILE_PDF_TIKA));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).hasSize(2);
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("sectionIndex", 0)
                                    .containsEntry("sectionTitle", "Summary")
                                    .containsEntry("page", 1)
                                    .containsEntry("pageNumber", 1)
                                    .containsEntry("chunkIndex", 0);
                            assertThat(segments.get(1).metadata())
                                    .containsEntry("sectionIndex", 1)
                                    .containsEntry("sectionTitle", "Appendix")
                                    .containsEntry("page", 2)
                                    .containsEntry("pageNumber", 2)
                                    .containsEntry("chunkIndex", 1);
                        })
                .verifyComplete();
    }

    @Test
    void documentProcessorFallsBackToWholeDocumentChunkingWhenSectionSpansCannotBeProven() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());
        var content =
                new DocumentContent(
                        "Report",
                        "text/plain",
                        "Summary\n\nBody",
                        List.of(
                                new DocumentSection("Summary", "Summary", 0, Map.of()),
                                new DocumentSection("Body", "B ody", 1, Map.of())),
                        null,
                        Map.of("contentProfile", DocumentSemantics.PROFILE_TEXT));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).singleElement();
                            assertThat(segments.getFirst().metadata())
                                    .doesNotContainKeys("sectionIndex", "sectionTitle")
                                    .containsEntry("chunkStrategy", "document-whole")
                                    .containsEntry("structureType", "document")
                                    .containsEntry("chunkIndex", 0);
                        })
                .verifyComplete();
    }

    @Test
    void documentProcessorIgnoresSectionsWhenWholeDocumentThresholdIsNotExceeded() {
        var processor =
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), DocumentExtractionOptions.defaults());
        var content =
                new DocumentContent(
                        "Guide",
                        "text/markdown",
                        "# Intro\nhello\n\n## Usage\nworld",
                        List.of(
                                new DocumentSection("Intro", "# Intro\nhello", 0, Map.of()),
                                new DocumentSection("Usage", "## Usage\nworld", 1, Map.of())),
                        null,
                        Map.of("contentProfile", DocumentSemantics.PROFILE_MARKDOWN));

        StepVerifier.create(processor.chunk(content))
                .assertNext(
                        segments -> {
                            assertThat(segments).singleElement();
                            assertThat(segments.getFirst().metadata())
                                    .containsEntry("chunkStrategy", "document-whole")
                                    .containsEntry("structureType", "document")
                                    .containsEntry("sectionCount", 2)
                                    .doesNotContainKeys("sectionIndex", "sectionTitle");
                        })
                .verifyComplete();
    }

    private static DocumentExtractionOptions optionsWithWholeDocumentMaxTokens(
            int wholeDocumentMaxTokens) {
        var defaults = DocumentExtractionOptions.defaults();
        return new DocumentExtractionOptions(
                defaults.textLikeSourceLimit(),
                defaults.binarySourceLimit(),
                defaults.textLikeParsedLimit(),
                defaults.binaryParsedLimit(),
                wholeDocumentMaxTokens,
                defaults.textLikeChunking(),
                defaults.binaryChunking(),
                defaults.textLikeMinChunkTokens(),
                defaults.binaryMinChunkTokens(),
                defaults.pdfMaxMergedPages(),
                defaults.llmCaptionEnabled(),
                defaults.captionConcurrency(),
                defaults.fallbackCaptionMaxLength());
    }
}
