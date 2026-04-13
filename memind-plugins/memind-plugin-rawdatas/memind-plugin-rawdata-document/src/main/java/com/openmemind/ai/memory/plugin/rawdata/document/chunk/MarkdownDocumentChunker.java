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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown-aware chunker that preserves heading block boundaries before token assembly.
 */
final class MarkdownDocumentChunker {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+.+$");

    private final TokenAwareSegmentAssembler tokenAwareSegmentAssembler;

    MarkdownDocumentChunker(TokenAwareSegmentAssembler tokenAwareSegmentAssembler) {
        this.tokenAwareSegmentAssembler =
                Objects.requireNonNull(tokenAwareSegmentAssembler, "tokenAwareSegmentAssembler");
    }

    List<Segment> chunk(String text, TokenChunkingOptions options) {
        Objects.requireNonNull(options, "options");
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<Segment> blocks = headingBlocks(text);
        if (blocks.isEmpty()) {
            return tokenAwareSegmentAssembler.assemble(
                    tokenAwareSegmentAssembler.paragraphCandidates(text), options);
        }

        List<Segment> segments = new ArrayList<>();
        for (Segment block : blocks) {
            List<Segment> shiftedCandidates =
                    tokenAwareSegmentAssembler.paragraphCandidates(block.content()).stream()
                            .map(candidate -> shift(block, candidate))
                            .toList();
            segments.addAll(tokenAwareSegmentAssembler.assemble(shiftedCandidates, options));
        }
        return segments;
    }

    private List<Segment> headingBlocks(String text) {
        Matcher matcher = MARKDOWN_HEADING.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
        }
        if (starts.isEmpty()) {
            return List.of();
        }

        List<Segment> blocks = new ArrayList<>();
        if (starts.getFirst() > 0) {
            addBlock(blocks, text, 0, starts.getFirst());
        }
        for (int index = 0; index < starts.size(); index++) {
            int start = starts.get(index);
            int end = index + 1 < starts.size() ? starts.get(index + 1) : text.length();
            addBlock(blocks, text, start, end);
        }
        return blocks;
    }

    private void addBlock(List<Segment> blocks, String text, int rawStart, int rawEnd) {
        int start = skipWhitespaceForward(text, rawStart);
        int end = skipWhitespaceBackward(text, rawEnd);
        if (start >= end) {
            return;
        }
        blocks.add(
                new Segment(
                        text.substring(start, end),
                        null,
                        new CharBoundary(start, end),
                        java.util.Map.of()));
    }

    private Segment shift(Segment parent, Segment child) {
        CharBoundary parentBoundary = charBoundary(parent);
        CharBoundary childBoundary = charBoundary(child);
        return new Segment(
                child.content(),
                child.caption(),
                new CharBoundary(
                        parentBoundary.startChar() + childBoundary.startChar(),
                        parentBoundary.startChar() + childBoundary.endChar()),
                child.metadata(),
                parent.runtimeContext());
    }

    private CharBoundary charBoundary(Segment segment) {
        if (segment.boundary() instanceof CharBoundary charBoundary) {
            return charBoundary;
        }
        throw new IllegalArgumentException("MarkdownDocumentChunker requires CharBoundary");
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
}
