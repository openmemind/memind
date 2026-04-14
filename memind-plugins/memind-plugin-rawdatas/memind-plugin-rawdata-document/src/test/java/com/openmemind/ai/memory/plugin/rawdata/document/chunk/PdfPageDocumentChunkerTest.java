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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TokenAwareSegmentAssembler;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import org.junit.jupiter.api.Test;

class PdfPageDocumentChunkerTest {

    @Test
    void chunkMergesAdjacentSmallPagesUpToConfiguredPageLimit() {
        var chunker =
                new PdfPageDocumentChunker(
                        new TokenAwareSegmentAssembler(),
                        new ParagraphWindowDocumentChunker(
                                new TokenAwareSegmentAssembler(), new DocumentChunkSupport()),
                        new DocumentChunkSupport());
        String text = "Page 1:\nalpha\n\nPage 2:\nbeta\n\nPage 3:\n" + "word ".repeat(900);

        List<Segment> segments =
                chunker.chunk(text, new TokenChunkingOptions(1_200, 1_800), 300, 2);

        assertThat(segments).hasSize(2);
        assertThat(segments.getFirst().metadata())
                .containsEntry("chunkStrategy", "pdf-page")
                .containsEntry("pageStart", 1)
                .containsEntry("pageEnd", 2);
        assertThat(segments.get(1).metadata()).containsEntry("pageNumber", 3);
    }

    @Test
    void chunkFallsBackToParagraphWindowWhenPageMarkersCannotBeDerived() {
        var chunker =
                new PdfPageDocumentChunker(
                        new TokenAwareSegmentAssembler(),
                        new ParagraphWindowDocumentChunker(
                                new TokenAwareSegmentAssembler(), new DocumentChunkSupport()),
                        new DocumentChunkSupport());

        List<Segment> segments =
                chunker.chunk("Alpha\n\nBeta", new TokenChunkingOptions(16, 24), 6, 3);

        assertThat(segments).hasSize(1);
        assertThat(segments.getFirst().metadata())
                .containsEntry("chunkStrategy", "paragraph-window")
                .containsEntry("structureType", "paragraphBlock");
    }
}
