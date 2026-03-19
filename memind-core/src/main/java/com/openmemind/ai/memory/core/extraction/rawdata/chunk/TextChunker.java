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
package com.openmemind.ai.memory.core.extraction.rawdata.chunk;

import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Text Chunker
 *
 * <p>Chunks the text content by character count.
 *
 */
public class TextChunker {

    /**
     * Chunk the text
     *
     * @param text Text content
     * @param config Chunking configuration
     * @return List of segments
     */
    public List<Segment> chunk(String text, TextChunkingConfig config) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return switch (config.boundary()) {
            case CHARACTER -> chunkByCharacter(text, config.chunkSize());
            case LINE -> chunkByLine(text, config.chunkSize());
            case PARAGRAPH -> chunkByParagraph(text, config.chunkSize());
        };
    }

    private List<Segment> chunkByLine(String text, int chunkSize) {
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();

        if (lines.isEmpty()) {
            return List.of();
        }

        List<Segment> segments = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int startLine = 0;
        int currentLine = 0;

        for (String line : lines) {
            if (!buffer.isEmpty() && buffer.length() + line.length() + 1 > chunkSize) {
                segments.add(createSegment(buffer.toString().trim(), startLine, currentLine));
                buffer = new StringBuilder();
                startLine = currentLine;
            }

            if (!buffer.isEmpty()) {
                buffer.append("\n");
            }
            buffer.append(line);
            currentLine++;
        }

        if (!buffer.isEmpty()) {
            segments.add(createSegment(buffer.toString().trim(), startLine, currentLine));
        }

        return segments;
    }

    private List<Segment> chunkByCharacter(String text, int chunkSize) {
        List<Segment> segments = new ArrayList<>();

        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            segments.add(createSegment(text.substring(i, end), i, end));
        }

        return segments;
    }

    private List<Segment> chunkByParagraph(String text, int chunkSize) {
        String[] paragraphs = text.split("\n\n+");
        List<Segment> segments = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int startIndex = 0;
        int currentIndex = 0;

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (!buffer.isEmpty() && buffer.length() + trimmed.length() + 2 > chunkSize) {
                segments.add(createSegment(buffer.toString().trim(), startIndex, currentIndex));
                buffer = new StringBuilder();
                startIndex = currentIndex;
            }

            if (!buffer.isEmpty()) {
                buffer.append("\n\n");
            }
            buffer.append(trimmed);
            currentIndex++;
        }

        if (!buffer.isEmpty()) {
            segments.add(createSegment(buffer.toString().trim(), startIndex, currentIndex));
        }

        return segments;
    }

    private Segment createSegment(String content, int start, int end) {
        return new Segment(content, null, new CharBoundary(start, end), Map.of());
    }
}
