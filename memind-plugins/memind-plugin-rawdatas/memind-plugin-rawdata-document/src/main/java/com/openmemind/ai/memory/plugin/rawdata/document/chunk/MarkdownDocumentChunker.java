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

import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TokenAwareSegmentAssembler;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown-aware chunker that preserves heading block boundaries before token assembly.
 */
final class MarkdownDocumentChunker {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^(#{1,6})\\s+(.+)$");

    private final TokenAwareSegmentAssembler assembler;
    private final ParagraphWindowDocumentChunker paragraphChunker;
    private final DocumentChunkSupport support;

    MarkdownDocumentChunker(TokenAwareSegmentAssembler assembler) {
        this(
                assembler,
                new ParagraphWindowDocumentChunker(assembler, new DocumentChunkSupport()),
                new DocumentChunkSupport());
    }

    MarkdownDocumentChunker(
            TokenAwareSegmentAssembler assembler,
            ParagraphWindowDocumentChunker paragraphChunker,
            DocumentChunkSupport support) {
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.paragraphChunker = Objects.requireNonNull(paragraphChunker, "paragraphChunker");
        this.support = Objects.requireNonNull(support, "support");
    }

    List<Segment> chunk(String text, TokenChunkingOptions options) {
        return chunk(text, options, 1);
    }

    List<Segment> chunk(String text, TokenChunkingOptions options, int minChunkTokens) {
        Objects.requireNonNull(options, "options");
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<HeadingMarker> headings = headingMarkers(text);
        if (headings.isEmpty()) {
            return paragraphChunker.chunk(text, options, minChunkTokens);
        }

        List<Segment> blocks = headingBlocks(text, headings);
        List<Segment> segments = new ArrayList<>();
        for (Segment block : blocks) {
            if (TokenUtils.countTokens(block.content()) > options.hardMaxTokens()) {
                segments.addAll(
                        paragraphChunker.chunk(block.content(), options, minChunkTokens).stream()
                                .map(child -> support.shift(block, child))
                                .map(child -> support.mergeMetadata(child, block.metadata()))
                                .toList());
                continue;
            }
            segments.add(block);
        }
        return support.withChunkIndexes(segments);
    }

    private List<HeadingMarker> headingMarkers(String text) {
        Matcher matcher = MARKDOWN_HEADING.matcher(text);
        List<HeadingMarker> markers = new ArrayList<>();
        while (matcher.find()) {
            markers.add(
                    new HeadingMarker(
                            matcher.start(), matcher.group(1).length(), matcher.group(2).trim()));
        }
        return List.copyOf(markers);
    }

    private List<Segment> headingBlocks(String text, List<HeadingMarker> headings) {
        List<Segment> blocks = new ArrayList<>();
        if (headings.getFirst().start() > 0) {
            addBlock(blocks, text, 0, headings.getFirst().start(), baseHeadingMetadata());
        }
        for (int index = 0; index < headings.size(); index++) {
            HeadingMarker heading = headings.get(index);
            int end = index + 1 < headings.size() ? headings.get(index + 1).start() : text.length();
            addBlock(blocks, text, heading.start(), end, headingMetadata(heading));
        }
        return List.copyOf(blocks);
    }

    private Map<String, Object> baseHeadingMetadata() {
        return Map.of("chunkStrategy", "markdown-heading", "structureType", "headingBlock");
    }

    private Map<String, Object> headingMetadata(HeadingMarker heading) {
        Map<String, Object> metadata = new LinkedHashMap<>(baseHeadingMetadata());
        metadata.put("headingLevel", heading.level());
        metadata.put("headingTitle", heading.title());
        return Map.copyOf(metadata);
    }

    private void addBlock(
            List<Segment> blocks,
            String text,
            int rawStart,
            int rawEnd,
            Map<String, Object> metadata) {
        int start = skipWhitespaceForward(text, rawStart);
        int end = skipWhitespaceBackward(text, rawEnd);
        if (start >= end) {
            return;
        }
        blocks.add(
                new Segment(
                        text.substring(start, end), null, new CharBoundary(start, end), metadata));
    }

    private int skipWhitespaceForward(String text, int index) {
        int cursor = Math.max(0, index);
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private int skipWhitespaceBackward(String text, int index) {
        int cursor = Math.min(text.length(), index);
        while (cursor > 0 && Character.isWhitespace(text.charAt(cursor - 1))) {
            cursor--;
        }
        return cursor;
    }

    private record HeadingMarker(int start, int level, String title) {}
}
