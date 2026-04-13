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
package com.openmemind.ai.memory.plugin.rawdata.document.chunk;

import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TokenAwareSegmentAssembler;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.plugin.rawdata.document.DocumentSemantics;
import com.openmemind.ai.memory.plugin.rawdata.document.config.DocumentExtractionOptions;
import java.util.List;
import java.util.Objects;

/**
 * Chooses document chunking strategy by normalized content profile.
 */
public final class ProfileAwareDocumentChunker {

    private final ParagraphWindowDocumentChunker paragraphChunker;
    private final MarkdownDocumentChunker markdownChunker;
    private final PdfPageDocumentChunker pdfChunker;
    private final CsvRowWindowDocumentChunker csvChunker;

    public ProfileAwareDocumentChunker() {
        this(new TokenAwareSegmentAssembler());
    }

    public ProfileAwareDocumentChunker(TokenAwareSegmentAssembler assembler) {
        Objects.requireNonNull(assembler, "assembler");
        var support = new DocumentChunkSupport();
        this.paragraphChunker = new ParagraphWindowDocumentChunker(assembler, support);
        this.markdownChunker = new MarkdownDocumentChunker(assembler, paragraphChunker, support);
        this.pdfChunker = new PdfPageDocumentChunker(assembler, paragraphChunker, support);
        this.csvChunker = new CsvRowWindowDocumentChunker(assembler, paragraphChunker, support);
    }

    public List<Segment> chunk(
            String text,
            DocumentExtractionOptions options,
            String governanceType,
            String contentProfile) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(governanceType, "governanceType");
        String profile =
                contentProfile == null || contentProfile.isBlank()
                        ? DocumentSemantics.PROFILE_TEXT
                        : contentProfile;
        int minChunkTokens =
                DocumentSemantics.isBinaryGovernance(governanceType)
                        ? options.binaryMinChunkTokens()
                        : options.textLikeMinChunkTokens();
        var chunkingOptions =
                DocumentSemantics.isBinaryGovernance(governanceType)
                        ? options.binaryChunking()
                        : options.textLikeChunking();
        return switch (profile) {
            case DocumentSemantics.PROFILE_MARKDOWN ->
                    markdownChunker.chunk(text, chunkingOptions, minChunkTokens);
            case DocumentSemantics.PROFILE_PDF_TIKA ->
                    pdfChunker.chunk(
                            text, chunkingOptions, minChunkTokens, options.pdfMaxMergedPages());
            case DocumentSemantics.PROFILE_CSV ->
                    csvChunker.chunk(text, chunkingOptions, minChunkTokens);
            default -> paragraphChunker.chunk(text, chunkingOptions, minChunkTokens);
        };
    }
}
