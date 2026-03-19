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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heading aware text chunker
 *
 * <p>Prioritize splitting at Markdown headings to avoid cutting off cross-paragraph semantics.
 *
 * <p>Scoring mechanism for split points:
 *
 * <ul>
 *   <li>H1 (#) → 100 points
 *   <li>H2 (##) → 90 points
 *   <li>H3 (###) → 80 points
 *   <li>H4+ (####...) → 70 points
 *   <li>Paragraph boundary (\n\n) → 20 points
 * </ul>
 *
 */
public class HeadingAwareTextChunker {

    private static final Pattern HEADING_PATTERN =
            Pattern.compile("^(#{1,6})\\s+", Pattern.MULTILINE);

    private static final int SCORE_H1 = 100;
    private static final int SCORE_H2 = 90;
    private static final int SCORE_H3 = 80;
    private static final int SCORE_H4_PLUS = 70;
    private static final int SCORE_PARAGRAPH = 20;

    /**
     * Perform heading-aware chunking on unstructured content
     *
     * @param text Text content
     * @param config Chunking configuration
     * @return List of segments
     */
    public List<Segment> chunk(String text, TextChunkingConfig config) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<BreakPoint> breakPoints = findBreakPoints(text);

        if (breakPoints.isEmpty()) {
            return List.of(createSegment(text.trim(), 0, text.length()));
        }

        return splitByBreakPoints(text, breakPoints, config.chunkSize());
    }

    /**
     * Check if the text contains Markdown headings
     *
     * @param text Text content
     * @return Whether it contains headings
     */
    public static boolean hasHeadings(String text) {
        if (text == null) {
            return false;
        }
        return HEADING_PATTERN.matcher(text).find();
    }

    /**
     * Find all candidate split points
     */
    private List<BreakPoint> findBreakPoints(String text) {
        List<BreakPoint> breakPoints = new ArrayList<>();

        // Find headings
        Matcher matcher = HEADING_PATTERN.matcher(text);
        while (matcher.find()) {
            int headingLevel = matcher.group(1).length();
            int score = getHeadingScore(headingLevel);
            // Split point at the beginning of the heading line
            int lineStart = findLineStart(text, matcher.start());
            breakPoints.add(new BreakPoint(lineStart, score));
        }

        // Find paragraph boundaries (double newlines)
        int idx = 0;
        while (idx < text.length() - 1) {
            if (text.charAt(idx) == '\n'
                    && idx + 1 < text.length()
                    && text.charAt(idx + 1) == '\n') {
                // Skip consecutive newlines, find the start position of the next paragraph
                int nextContentStart = idx + 2;
                while (nextContentStart < text.length() && text.charAt(nextContentStart) == '\n') {
                    nextContentStart++;
                }
                if (nextContentStart < text.length()) {
                    breakPoints.add(new BreakPoint(nextContentStart, SCORE_PARAGRAPH));
                }
                idx = nextContentStart;
            } else {
                idx++;
            }
        }

        // Sort by position, deduplicate (keep the highest score at the same position)
        return deduplicateAndSort(breakPoints);
    }

    /**
     * Chunk the text based on split points
     */
    private List<Segment> splitByBreakPoints(
            String text, List<BreakPoint> breakPoints, int chunkSize) {
        List<Segment> segments = new ArrayList<>();
        int currentStart = 0;

        for (int i = 0; i < breakPoints.size(); i++) {
            BreakPoint bp = breakPoints.get(i);

            // If the distance from the current position to the split point exceeds chunkSize, split
            // at the best previous split point
            if (bp.position() - currentStart >= chunkSize && currentStart < bp.position()) {
                // Find the highest scoring split point between currentStart and the current
                // position
                int bestBreakIdx =
                        findBestBreakInRange(breakPoints, currentStart, bp.position(), i);

                if (bestBreakIdx >= 0) {
                    int breakPos = breakPoints.get(bestBreakIdx).position();
                    String chunk = text.substring(currentStart, breakPos).trim();
                    if (!chunk.isEmpty()) {
                        segments.add(createSegment(chunk, currentStart, breakPos));
                    }
                    currentStart = breakPos;
                }
            }

            // If we are before the last split point and have accumulated enough content, split at
            // the heading
            if (bp.score() >= SCORE_H4_PLUS && bp.position() > currentStart) {
                int contentLength = bp.position() - currentStart;
                if (contentLength >= chunkSize / 2) {
                    String chunk = text.substring(currentStart, bp.position()).trim();
                    if (!chunk.isEmpty()) {
                        segments.add(createSegment(chunk, currentStart, bp.position()));
                    }
                    currentStart = bp.position();
                }
            }
        }

        // Handle the last segment
        if (currentStart < text.length()) {
            String lastChunk = text.substring(currentStart).trim();
            if (!lastChunk.isEmpty()) {
                segments.add(createSegment(lastChunk, currentStart, text.length()));
            }
        }

        // If there are no valid segments, return the whole as one segment
        if (segments.isEmpty()) {
            return List.of(createSegment(text.trim(), 0, text.length()));
        }

        return segments;
    }

    /**
     * Find the highest scoring split point within the specified range
     */
    private int findBestBreakInRange(
            List<BreakPoint> breakPoints, int rangeStart, int rangeEnd, int endIdx) {
        int bestIdx = -1;
        int bestScore = -1;

        for (int i = 0; i < endIdx; i++) {
            BreakPoint bp = breakPoints.get(i);
            if (bp.position() > rangeStart && bp.position() < rangeEnd && bp.score() > bestScore) {
                bestScore = bp.score();
                bestIdx = i;
            }
        }

        return bestIdx;
    }

    private int getHeadingScore(int level) {
        return switch (level) {
            case 1 -> SCORE_H1;
            case 2 -> SCORE_H2;
            case 3 -> SCORE_H3;
            default -> SCORE_H4_PLUS;
        };
    }

    private int findLineStart(String text, int position) {
        int lineStart = text.lastIndexOf('\n', position - 1);
        return lineStart >= 0 ? lineStart + 1 : 0;
    }

    private List<BreakPoint> deduplicateAndSort(List<BreakPoint> breakPoints) {
        return breakPoints.stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                BreakPoint::position,
                                bp -> bp,
                                (a, b) -> a.score() >= b.score() ? a : b))
                .values()
                .stream()
                .sorted(java.util.Comparator.comparingInt(BreakPoint::position))
                .toList();
    }

    private Segment createSegment(String content, int start, int end) {
        return new Segment(content, null, new CharBoundary(start, end), Map.of());
    }

    private record BreakPoint(int position, int score) {}
}
