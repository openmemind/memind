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
package com.openmemind.ai.memory.core.rawdata.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.chunk.HeadingAwareTextChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TextChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HeadingAwareTextChunker Unit Test")
class HeadingAwareTextChunkerTest {

    private final HeadingAwareTextChunker chunker = new HeadingAwareTextChunker();

    @Nested
    @DisplayName("Heading Detection")
    class HeadingDetectionTests {

        @Test
        @DisplayName("Text containing Markdown headings should return true")
        void shouldDetectMarkdownHeadings() {
            assertThat(HeadingAwareTextChunker.hasHeadings("# Level 1 Heading\nContent")).isTrue();
            assertThat(HeadingAwareTextChunker.hasHeadings("## Level 2 Heading\nContent")).isTrue();
            assertThat(HeadingAwareTextChunker.hasHeadings("### Level 3 Heading\nContent"))
                    .isTrue();
            assertThat(HeadingAwareTextChunker.hasHeadings("#### Level 4 Heading\nContent"))
                    .isTrue();
        }

        @Test
        @DisplayName("Text with headings in the middle should return true")
        void shouldDetectHeadingInMiddleOfText() {
            String text =
                    """
                    Content before
                    Some description
                    ## Heading in the Middle
                    Content after\
                    """;
            assertThat(HeadingAwareTextChunker.hasHeadings(text)).isTrue();
        }

        @Test
        @DisplayName("Text without headings should return false")
        void shouldReturnFalseWhenNoHeadings() {
            assertThat(HeadingAwareTextChunker.hasHeadings("Normal text content")).isFalse();
            assertThat(HeadingAwareTextChunker.hasHeadings("First line\nSecond line\nThird line"))
                    .isFalse();
            assertThat(HeadingAwareTextChunker.hasHeadings("#No space is not a heading")).isFalse();
        }

        @Test
        @DisplayName("null text should return false")
        void shouldReturnFalseForNull() {
            assertThat(HeadingAwareTextChunker.hasHeadings(null)).isFalse();
        }

        @Test
        @DisplayName("Empty string should return false")
        void shouldReturnFalseForEmptyString() {
            assertThat(HeadingAwareTextChunker.hasHeadings("")).isFalse();
        }
    }

    @Nested
    @DisplayName("Heading Splitting")
    class HeadingSplitTests {

        @Test
        @DisplayName("Text with H1 headings should split at H1 boundaries")
        void shouldSplitAtH1Boundaries() {
            String text =
                    """
                    # Chapter One
                    Content of Chapter One, there is a lot of text here.
                    This is the second paragraph of Chapter One.

                    # Chapter Two
                    Content of Chapter Two, which also has a lot of text.
                    This is the second paragraph of Chapter Two.\
                    """;

            var config = new TextChunkingConfig(50, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(text, config);

            assertThat(segments).hasSizeGreaterThanOrEqualTo(2);
            assertThat(segments.getFirst().content()).contains("Chapter One");
        }

        @Test
        @DisplayName("Text with H2 headings should split at H2 boundaries")
        void shouldSplitAtH2Boundaries() {
            String text =
                    """
                    ## Introduction
                    This is the content of the introduction, describing the overall overview.
                    The introduction contains multiple lines to ensure sufficient length.

                    ## Detailed Explanation
                    This is the content of the detailed explanation, describing specific details.
                    The detailed explanation also has multiple lines to meet the splitting requirements.\
                    """;

            var config = new TextChunkingConfig(60, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(text, config);

            assertThat(segments).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Mixed H1/H2/H3 headings should respect heading hierarchy")
        void shouldRespectHeadingHierarchy() {
            String text =
                    """
                    # Main Title
                    Content under the main title, this paragraph is relatively long, used for testing splitting.
                    More content under the main title, ensuring sufficient length.

                    ## Subheading One
                    Content of Subheading One, describing the first subtopic.
                    More content of Subheading One, also ensuring sufficient length.

                    ### Details
                    Detail description, this is deeper content.
                    Supplementary explanations and detailed descriptions of the details.

                    ## Subheading Two
                    Content of Subheading Two, describing the second subtopic.
                    Supplementary content and summary of Subheading Two.\
                    """;

            var config = new TextChunkingConfig(80, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(text, config);

            assertThat(segments).hasSizeGreaterThanOrEqualTo(2);
            // The first segment should contain "Main Title"
            assertThat(segments.getFirst().content()).contains("Main Title");
        }
    }

    @Nested
    @DisplayName("Paragraph Splitting")
    class ParagraphSplitTests {

        @Test
        @DisplayName("Text with double line breaks should split at paragraph boundaries")
        void shouldSplitAtParagraphBoundaries() {
            String text =
                    """
                    This is the content of the first paragraph. The first paragraph is long, containing multiple sentences and detailed descriptions.
                    This is the second line of the first paragraph, used to increase sufficient length.

                    This is the content of the second paragraph. The second paragraph is also long, containing multiple sentences.
                    This is the second line of the second paragraph, also increasing length to make it sufficient.

                    This is the content of the third paragraph. As the last paragraph summarizes the entire text.
                    This is the second line of the third paragraph, used to meet the splitting conditions.\
                    """;

            var config = new TextChunkingConfig(80, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(text, config);

            assertThat(segments).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Short text with no splitting points should return a single segment")
        void shouldReturnSingleSegmentForShortText() {
            String text = "This is a very short piece of text.";
            var config = new TextChunkingConfig(2000, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(text, config);

            assertThat(segments).hasSize(1);
            assertThat(segments.getFirst().content())
                    .isEqualTo("This is a very short piece of text.");
        }
    }

    @Nested
    @DisplayName("Size Limitations")
    class ChunkSizeTests {

        @Test
        @DisplayName("Chunking should respect chunkSize configuration")
        void shouldRespectChunkSizeConfig() {
            // Build a longer text with headings
            var sb = new StringBuilder();
            for (int i = 1; i <= 10; i++) {
                sb.append("# Chapter ").append(i).append("\n");
                sb.append("This is the content of Chapter ").append(i).append(".");
                sb.append(" There are some detailed descriptions to fill the content length.");
                sb.append(" Ensure each chapter has enough text to test the splitting logic.\n\n");
            }
            String text = sb.toString();

            var config = new TextChunkingConfig(100, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(text, config);

            // Should produce multiple segments
            assertThat(segments).hasSizeGreaterThan(1);
        }

        @Test
        @DisplayName("Small chunkSize should produce more segments")
        void smallerChunkSizeShouldProduceMoreSegments() {
            String text =
                    """
                    # Heading One
                    Description of Heading One, containing detailed information and explanations.

                    # Heading Two
                    Description of Heading Two, containing detailed information and explanations.

                    # Heading Three
                    Description of Heading Three, containing detailed information and explanations.\
                    """;

            var largeConfig = new TextChunkingConfig(2000, TextChunkingConfig.ChunkBoundary.LINE);
            var smallConfig = new TextChunkingConfig(30, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> largeSegments = chunker.chunk(text, largeConfig);
            List<Segment> smallSegments = chunker.chunk(text, smallConfig);

            assertThat(smallSegments.size()).isGreaterThanOrEqualTo(largeSegments.size());
        }
    }

    @Nested
    @DisplayName("Degenerate Cases")
    class DegenerateCaseTests {

        @Test
        @DisplayName("Text without headings should produce segments")
        void shouldProduceSegmentsForTextWithoutHeadings() {
            String text =
                    "This is a piece of ordinary text without headings.\n"
                            + "It has multiple lines but no Markdown headings.\n"
                            + "Purely paragraph text.";
            var config = new TextChunkingConfig(2000, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(text, config);

            assertThat(segments).isNotEmpty();
            assertThat(segments.getFirst().content()).contains("without headings");
        }

        @Test
        @DisplayName("Empty text should return an empty list")
        void shouldReturnEmptyListForEmptyText() {
            var config = new TextChunkingConfig(2000, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk("", config);

            assertThat(segments).isEmpty();
        }

        @Test
        @DisplayName("Blank text should return an empty list")
        void shouldReturnEmptyListForBlankText() {
            var config = new TextChunkingConfig(2000, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk("   \n  \n  ", config);

            assertThat(segments).isEmpty();
        }

        @Test
        @DisplayName("null text should return an empty list")
        void shouldReturnEmptyListForNullText() {
            var config = new TextChunkingConfig(2000, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(null, config);

            assertThat(segments).isEmpty();
        }

        @Test
        @DisplayName("Text containing only headings without body should return segments")
        void shouldHandleHeadingsWithoutBody() {
            String text =
                    """
                    # Heading One
                    # Heading Two
                    # Heading Three\
                    """;
            var config = new TextChunkingConfig(2000, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(text, config);

            assertThat(segments).isNotEmpty();
        }

        @Test
        @DisplayName("Segments should contain valid boundary information")
        void segmentsShouldHaveValidBoundary() {
            String text =
                    """
                    # Section One
                    Content of Section One.

                    # Section Two
                    Content of Section Two.\
                    """;

            var config = new TextChunkingConfig(30, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(text, config);

            segments.forEach(
                    segment -> {
                        assertThat(segment.boundary()).isNotNull();
                        assertThat(segment.content()).isNotBlank();
                    });
        }
    }
}
