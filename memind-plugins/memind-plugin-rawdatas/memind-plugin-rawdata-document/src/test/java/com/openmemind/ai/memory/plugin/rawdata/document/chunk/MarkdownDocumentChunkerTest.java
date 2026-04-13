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

class MarkdownDocumentChunkerTest {

    @Test
    void chunkAddsHeadingMetadataAndNeverMergesAcrossHeadingBlocks() {
        var chunker =
                new MarkdownDocumentChunker(
                        new TokenAwareSegmentAssembler(),
                        new ParagraphWindowDocumentChunker(
                                new TokenAwareSegmentAssembler(), new DocumentChunkSupport()),
                        new DocumentChunkSupport());
        var text = "Lead in\n\n# Intro\nhello\n\n## Usage\nworld";

        List<Segment> segments = chunker.chunk(text, new TokenChunkingOptions(16, 24), 6);

        assertThat(segments).hasSize(3);
        assertThat(segments.get(1).metadata())
                .containsEntry("chunkStrategy", "markdown-heading")
                .containsEntry("structureType", "headingBlock")
                .containsEntry("headingLevel", 1)
                .containsEntry("headingTitle", "Intro");
        assertThat(segments.get(2).content()).isEqualTo("## Usage\nworld");
    }

    @Test
    void chunkSplitsOversizedHeadingOnlyInsideThatHeadingBlock() {
        var chunker =
                new MarkdownDocumentChunker(
                        new TokenAwareSegmentAssembler(),
                        new ParagraphWindowDocumentChunker(
                                new TokenAwareSegmentAssembler(), new DocumentChunkSupport()),
                        new DocumentChunkSupport());
        String text = "# Intro\n" + "word ".repeat(2_500) + "\n\n## Usage\nshort";

        List<Segment> segments = chunker.chunk(text, new TokenChunkingOptions(1_200, 1_800), 300);

        assertThat(segments).hasSizeGreaterThan(2);
        assertThat(segments.subList(0, segments.size() - 1))
                .allSatisfy(
                        segment ->
                                assertThat(segment.metadata())
                                        .containsEntry("headingTitle", "Intro"));
        assertThat(segments.getLast().metadata()).containsEntry("headingTitle", "Usage");
    }
}
