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
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParagraphWindowDocumentChunkerTest {

    @Test
    void chunkMergesShortNeighboringParagraphsUntilTargetBudget() {
        var chunker =
                new ParagraphWindowDocumentChunker(
                        new TokenAwareSegmentAssembler(), new DocumentChunkSupport());
        String text = "Alpha\n\nBeta\n\nGamma\n\nDelta";

        List<Segment> segments = chunker.chunk(text, new TokenChunkingOptions(3, 6), 2);

        assertThat(segments).hasSize(2);
        assertThat(segments.getFirst().content()).isEqualTo("Alpha\n\nBeta\n\nGamma");
        assertThat(segments.getFirst().metadata())
                .containsEntry("chunkStrategy", "paragraph-window")
                .containsEntry("structureType", "paragraphBlock");
    }

    @Test
    void chunkSplitsOversizedParagraphWithoutThrowingOnIrregularSpacing() {
        var chunker =
                new ParagraphWindowDocumentChunker(
                        new TokenAwareSegmentAssembler(), new DocumentChunkSupport());
        String text = "   word ".repeat(2_200);

        List<Segment> segments = chunker.chunk(text, new TokenChunkingOptions(1_200, 1_800), 300);

        assertThat(segments).isNotEmpty();
        assertThat(segments)
                .allSatisfy(
                        segment ->
                                assertThat(TokenUtils.countTokens(segment.content()))
                                        .isLessThanOrEqualTo(1_800));
    }

    @Test
    void chunkReturnsEmptyListForBlankInput() {
        var chunker =
                new ParagraphWindowDocumentChunker(
                        new TokenAwareSegmentAssembler(), new DocumentChunkSupport());

        assertThat(chunker.chunk(" \n\t", new TokenChunkingOptions(1_200, 1_800), 300)).isEmpty();
    }
}
