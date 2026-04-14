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
import java.util.stream.Collectors;

final class PdfPageDocumentChunker {

    private static final Pattern PAGE_MARKER = Pattern.compile("(?m)^Page\\s+(\\d+):\\s*$");

    private final TokenAwareSegmentAssembler assembler;
    private final ParagraphWindowDocumentChunker paragraphChunker;
    private final DocumentChunkSupport support;

    PdfPageDocumentChunker(
            TokenAwareSegmentAssembler assembler,
            ParagraphWindowDocumentChunker paragraphChunker,
            DocumentChunkSupport support) {
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.paragraphChunker = Objects.requireNonNull(paragraphChunker, "paragraphChunker");
        this.support = Objects.requireNonNull(support, "support");
    }

    List<Segment> chunk(
            String text, TokenChunkingOptions options, int minChunkTokens, int pdfMaxMergedPages) {
        Objects.requireNonNull(options, "options");
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (pdfMaxMergedPages <= 0) {
            throw new IllegalArgumentException("pdfMaxMergedPages must be positive");
        }

        List<PageBlock> pages = pageBlocks(text);
        if (pages.isEmpty()) {
            return paragraphChunker.chunk(text, options, minChunkTokens);
        }

        List<Segment> segments = new ArrayList<>();
        List<PageBlock> bufferedSmallPages = new ArrayList<>();
        for (PageBlock page : pages) {
            int pageTokens = TokenUtils.countTokens(page.segment().content());
            if (pageTokens > options.hardMaxTokens()) {
                flushMergedPages(bufferedSmallPages, segments);
                segments.addAll(splitOversizedPage(page, options, minChunkTokens));
                continue;
            }

            boolean undersized = pageTokens < minChunkTokens;
            if (!undersized) {
                flushMergedPages(bufferedSmallPages, segments);
                segments.add(page.segment());
                continue;
            }

            if (bufferedSmallPages.size() >= pdfMaxMergedPages
                    || wouldExceedTarget(bufferedSmallPages, page, options)) {
                flushMergedPages(bufferedSmallPages, segments);
            }
            bufferedSmallPages.add(page);
        }
        flushMergedPages(bufferedSmallPages, segments);
        return support.withChunkIndexes(segments);
    }

    private boolean wouldExceedTarget(
            List<PageBlock> bufferedSmallPages, PageBlock next, TokenChunkingOptions options) {
        if (bufferedSmallPages.isEmpty()) {
            return false;
        }
        List<String> contents =
                bufferedSmallPages.stream().map(PageBlock::segment).map(Segment::content).toList();
        String mergedContent =
                StreamBuilder.builder(contents).add(next.segment().content()).build();
        return TokenUtils.countTokens(mergedContent) > options.targetTokens();
    }

    private List<Segment> splitOversizedPage(
            PageBlock page, TokenChunkingOptions options, int minChunkTokens) {
        return paragraphChunker.chunk(page.segment().content(), options, minChunkTokens).stream()
                .map(child -> support.shift(page.segment(), child))
                .map(child -> support.mergeMetadata(child, page.segment().metadata()))
                .toList();
    }

    private void flushMergedPages(List<PageBlock> bufferedSmallPages, List<Segment> segments) {
        if (bufferedSmallPages.isEmpty()) {
            return;
        }
        segments.add(mergePages(bufferedSmallPages));
        bufferedSmallPages.clear();
    }

    private Segment mergePages(List<PageBlock> pages) {
        if (pages.size() == 1) {
            return pages.getFirst().segment();
        }

        Segment first = pages.getFirst().segment();
        Segment last = pages.getLast().segment();
        CharBoundary start = support.boundaryOf(first);
        CharBoundary end = support.boundaryOf(last);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("chunkStrategy", "pdf-page");
        metadata.put("structureType", "pageBlock");
        metadata.put("pageStart", pages.getFirst().pageNumber());
        metadata.put("pageEnd", pages.getLast().pageNumber());
        return new Segment(
                pages.stream()
                        .map(PageBlock::segment)
                        .map(Segment::content)
                        .collect(Collectors.joining("\n\n")),
                null,
                new CharBoundary(start.startChar(), end.endChar()),
                Map.copyOf(metadata),
                first.runtimeContext());
    }

    private List<PageBlock> pageBlocks(String text) {
        Matcher matcher = PAGE_MARKER.matcher(text);
        List<PageMarker> markers = new ArrayList<>();
        while (matcher.find()) {
            markers.add(new PageMarker(matcher.start(), Integer.parseInt(matcher.group(1))));
        }
        if (markers.isEmpty()) {
            return List.of();
        }
        if (containsNonWhitespace(text, 0, markers.getFirst().start())) {
            return List.of();
        }

        List<PageBlock> pages = new ArrayList<>();
        for (int index = 0; index < markers.size(); index++) {
            PageMarker marker = markers.get(index);
            int end = index + 1 < markers.size() ? markers.get(index + 1).start() : text.length();
            Segment segment = createPageSegment(text, marker, end);
            if (segment == null) {
                return List.of();
            }
            pages.add(new PageBlock(marker.pageNumber(), segment));
        }
        return List.copyOf(pages);
    }

    private Segment createPageSegment(String text, PageMarker marker, int rawEnd) {
        int start = skipWhitespaceForward(text, marker.start());
        int end = skipWhitespaceBackward(text, rawEnd);
        if (start >= end) {
            return null;
        }
        return new Segment(
                text.substring(start, end),
                null,
                new CharBoundary(start, end),
                Map.of(
                        "chunkStrategy", "pdf-page",
                        "structureType", "pageBlock",
                        "pageNumber", marker.pageNumber()));
    }

    private boolean containsNonWhitespace(String text, int start, int end) {
        for (int index = Math.max(0, start); index < Math.max(start, end); index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                return true;
            }
        }
        return false;
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

    private record PageMarker(int start, int pageNumber) {}

    private record PageBlock(int pageNumber, Segment segment) {}

    private static final class StreamBuilder {

        private final List<String> contents = new ArrayList<>();

        private StreamBuilder(List<String> contents) {
            this.contents.addAll(contents);
        }

        static StreamBuilder builder(List<String> contents) {
            return new StreamBuilder(contents);
        }

        StreamBuilder add(String content) {
            contents.add(content);
            return this;
        }

        String build() {
            return String.join("\n\n", contents);
        }
    }
}
