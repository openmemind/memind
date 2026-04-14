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

class CsvRowWindowDocumentChunkerTest {

    @Test
    void chunkMergesMultipleShortRowsAndPublishesRowRangeMetadata() {
        var chunker =
                new CsvRowWindowDocumentChunker(
                        new TokenAwareSegmentAssembler(),
                        new ParagraphWindowDocumentChunker(
                                new TokenAwareSegmentAssembler(), new DocumentChunkSupport()),
                        new DocumentChunkSupport());
        String text =
                """
                Row 1:
                name: Alice, team: Core

                Row 2:
                name: Bob, team: AI
                """;

        List<Segment> segments = chunker.chunk(text, new TokenChunkingOptions(1_200, 1_800), 300);

        assertThat(segments)
                .singleElement()
                .satisfies(
                        segment -> {
                            assertThat(segment.content()).contains("Row 1:").contains("Row 2:");
                            assertThat(segment.metadata())
                                    .containsEntry("chunkStrategy", "csv-row-window")
                                    .containsEntry("rowStart", 1)
                                    .containsEntry("rowEnd", 2);
                        });
    }

    @Test
    void chunkRepeatsStableRowPrefixForOversizedSingleRowSplits() {
        var chunker =
                new CsvRowWindowDocumentChunker(
                        new TokenAwareSegmentAssembler(),
                        new ParagraphWindowDocumentChunker(
                                new TokenAwareSegmentAssembler(), new DocumentChunkSupport()),
                        new DocumentChunkSupport());
        String text = "Row 9:\n" + "column1: word, ".repeat(2_600);

        List<Segment> segments = chunker.chunk(text, new TokenChunkingOptions(1_200, 1_800), 300);

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(segments)
                .allSatisfy(
                        segment -> {
                            assertThat(segment.content()).startsWith("Row 9:");
                            assertThat(TokenUtils.countTokens(segment.content()))
                                    .isLessThanOrEqualTo(1_800);
                            assertThat(segment.metadata())
                                    .containsEntry("rowStart", 9)
                                    .containsEntry("rowEnd", 9);
                        });
        assertThat(segments.stream().skip(1))
                .allSatisfy(
                        segment ->
                                assertThat(segment.metadata())
                                        .containsEntry("syntheticRowPrefix", true));
    }

    @Test
    void chunkFallsBackSafelyWhenRowMarkersAreMissing() {
        var chunker =
                new CsvRowWindowDocumentChunker(
                        new TokenAwareSegmentAssembler(),
                        new ParagraphWindowDocumentChunker(
                                new TokenAwareSegmentAssembler(), new DocumentChunkSupport()),
                        new DocumentChunkSupport());

        List<Segment> segments =
                chunker.chunk("name: Alice\n\nteam: Core", new TokenChunkingOptions(16, 24), 6);

        assertThat(segments)
                .singleElement()
                .satisfies(
                        segment ->
                                assertThat(segment.metadata())
                                        .containsEntry("chunkStrategy", "paragraph-window"));
    }
}
