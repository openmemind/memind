package com.openmemind.ai.memory.core.rawdata.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TextChunker;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TextChunkingConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TextChunker Unit Test")
class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Nested
    @DisplayName("Chunk by Line")
    class ChunkByLineTests {

        @Test
        @DisplayName("Should chunk by line, not exceeding specified size")
        void shouldChunkByLineWithinSize() {
            String text =
                    "First line content\n"
                            + "Second line content\n"
                            + "Third line content\n"
                            + "Fourth line content";
            TextChunkingConfig config =
                    new TextChunkingConfig(20, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(text, config);

            assertThat(segments).hasSizeGreaterThan(1);
            segments.forEach(
                    s ->
                            assertThat(s.content().length())
                                    .isLessThanOrEqualTo(25)); // Allow slight overflow
        }

        @Test
        @DisplayName("Short text should return a single chunk")
        void shouldReturnSingleChunkForShortText() {
            List<Segment> segments = chunker.chunk("Short text", TextChunkingConfig.DEFAULT);

            assertThat(segments).hasSize(1);
            assertThat(segments.getFirst().content()).isEqualTo("Short text");
        }

        @Test
        @DisplayName("Empty text should return an empty list")
        void shouldReturnEmptyListForEmptyText() {
            List<Segment> segments = chunker.chunk("", TextChunkingConfig.DEFAULT);

            assertThat(segments).isEmpty();
        }

        @Test
        @DisplayName("Should skip blank lines")
        void shouldSkipBlankLines() {
            String text = "First line\n\n\nSecond line";

            List<Segment> segments = chunker.chunk(text, TextChunkingConfig.DEFAULT);

            assertThat(segments).hasSize(1);
            assertThat(segments.getFirst().content()).contains("First line");
            assertThat(segments.getFirst().content()).contains("Second line");
        }
    }

    @Nested
    @DisplayName("Boundary Information")
    class BoundaryTests {

        @Test
        @DisplayName("Should correctly record line boundaries")
        void shouldRecordLineBoundary() {
            String text = "Line 1\nLine 2\nLine 3\nLine 4";
            TextChunkingConfig config =
                    new TextChunkingConfig(5, TextChunkingConfig.ChunkBoundary.LINE);

            List<Segment> segments = chunker.chunk(text, config);

            assertThat(segments.getFirst().boundary()).isInstanceOf(CharBoundary.class);
        }
    }
}
