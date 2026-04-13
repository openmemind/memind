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

final class CsvRowWindowDocumentChunker {

    private static final Pattern ROW_MARKER = Pattern.compile("(?m)^Row\\s+(\\d+):\\s*$");

    private final TokenAwareSegmentAssembler assembler;
    private final ParagraphWindowDocumentChunker paragraphChunker;
    private final DocumentChunkSupport support;

    CsvRowWindowDocumentChunker(
            TokenAwareSegmentAssembler assembler,
            ParagraphWindowDocumentChunker paragraphChunker,
            DocumentChunkSupport support) {
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.paragraphChunker = Objects.requireNonNull(paragraphChunker, "paragraphChunker");
        this.support = Objects.requireNonNull(support, "support");
    }

    List<Segment> chunk(String text, TokenChunkingOptions options, int minChunkTokens) {
        Objects.requireNonNull(options, "options");
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<RowBlock> rows = rowBlocks(text);
        if (rows.isEmpty()) {
            return paragraphChunker.chunk(text, options, minChunkTokens);
        }

        List<Segment> segments = new ArrayList<>();
        List<RowBlock> current = new ArrayList<>();
        for (RowBlock row : rows) {
            if (TokenUtils.countTokens(row.segment().content()) > options.hardMaxTokens()) {
                flushRows(current, segments);
                segments.addAll(splitOversizedRow(row, options));
                continue;
            }
            if (current.isEmpty()) {
                current.add(row);
                continue;
            }

            boolean withinTarget =
                    TokenUtils.countTokens(joinRowContents(current, row)) <= options.targetTokens();
            boolean withinHard =
                    TokenUtils.countTokens(joinRowContents(current, row))
                            <= options.hardMaxTokens();
            if (withinTarget && withinHard) {
                current.add(row);
                continue;
            }

            flushRows(current, segments);
            current.add(row);
        }
        flushRows(current, segments);
        return support.withChunkIndexes(segments);
    }

    private void flushRows(List<RowBlock> current, List<Segment> segments) {
        if (current.isEmpty()) {
            return;
        }
        segments.add(mergeRows(current));
        current.clear();
    }

    private Segment mergeRows(List<RowBlock> rows) {
        if (rows.size() == 1) {
            return rows.getFirst().segment();
        }

        Segment first = rows.getFirst().segment();
        Segment last = rows.getLast().segment();
        CharBoundary start = support.boundaryOf(first);
        CharBoundary end = support.boundaryOf(last);
        return new Segment(
                rows.stream()
                        .map(RowBlock::segment)
                        .map(Segment::content)
                        .collect(Collectors.joining("\n\n")),
                null,
                new CharBoundary(start.startChar(), end.endChar()),
                Map.of(
                        "chunkStrategy",
                        "csv-row-window",
                        "structureType",
                        "rowBlock",
                        "rowStart",
                        rows.getFirst().rowNumber(),
                        "rowEnd",
                        rows.getLast().rowNumber()),
                first.runtimeContext());
    }

    private List<Segment> splitOversizedRow(RowBlock row, TokenChunkingOptions options) {
        String prefix = "Row " + row.rowNumber() + ":";
        String displayPrefix = prefix + "\n";
        int prefixTokens = TokenUtils.countTokens(displayPrefix);
        int bodyHardMaxTokens = Math.max(1, options.hardMaxTokens() - prefixTokens);
        List<Segment> bodySlices =
                row.bodySegment() == null
                        ? List.of()
                        : assembler.splitOversized(row.bodySegment(), bodyHardMaxTokens);
        if (bodySlices.size() <= 1) {
            return List.of(row.segment());
        }

        List<Segment> split = new ArrayList<>();
        CharBoundary rowBoundary = support.boundaryOf(row.segment());
        for (int index = 0; index < bodySlices.size(); index++) {
            Segment bodySlice = bodySlices.get(index);
            CharBoundary bodyBoundary = support.boundaryOf(bodySlice);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("chunkStrategy", "csv-row-window");
            metadata.put("structureType", "rowBlock");
            metadata.put("rowStart", row.rowNumber());
            metadata.put("rowEnd", row.rowNumber());
            if (index > 0) {
                metadata.put("syntheticRowPrefix", true);
            }
            split.add(
                    new Segment(
                            displayPrefix + bodySlice.content(),
                            bodySlice.caption(),
                            index == 0
                                    ? new CharBoundary(
                                            rowBoundary.startChar(), bodyBoundary.endChar())
                                    : bodyBoundary,
                            Map.copyOf(metadata),
                            bodySlice.runtimeContext()));
        }
        return split;
    }

    private List<RowBlock> rowBlocks(String text) {
        Matcher matcher = ROW_MARKER.matcher(text);
        List<RowMarker> markers = new ArrayList<>();
        while (matcher.find()) {
            markers.add(new RowMarker(matcher.start(), Integer.parseInt(matcher.group(1))));
        }
        if (markers.isEmpty()) {
            return List.of();
        }
        if (containsNonWhitespace(text, 0, markers.getFirst().start())) {
            return List.of();
        }

        List<RowBlock> rows = new ArrayList<>();
        for (int index = 0; index < markers.size(); index++) {
            RowMarker marker = markers.get(index);
            int end = index + 1 < markers.size() ? markers.get(index + 1).start() : text.length();
            RowBlock row = createRowBlock(text, marker, end);
            if (row == null) {
                return List.of();
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private RowBlock createRowBlock(String text, RowMarker marker, int rawEnd) {
        int start = skipWhitespaceForward(text, marker.start());
        int end = skipWhitespaceBackward(text, rawEnd);
        if (start >= end) {
            return null;
        }

        int lineEnd = text.indexOf('\n', start);
        int bodyStart =
                lineEnd < 0 || lineEnd >= end ? end : skipWhitespaceForward(text, lineEnd + 1);
        Segment bodySegment =
                bodyStart < end
                        ? new Segment(
                                text.substring(bodyStart, end),
                                null,
                                new CharBoundary(bodyStart, end),
                                Map.of(
                                        "chunkStrategy",
                                        "csv-row-window",
                                        "structureType",
                                        "rowBlock",
                                        "rowStart",
                                        marker.rowNumber(),
                                        "rowEnd",
                                        marker.rowNumber()))
                        : null;
        Segment rowSegment =
                new Segment(
                        text.substring(start, end),
                        null,
                        new CharBoundary(start, end),
                        Map.of(
                                "chunkStrategy",
                                "csv-row-window",
                                "structureType",
                                "rowBlock",
                                "rowStart",
                                marker.rowNumber(),
                                "rowEnd",
                                marker.rowNumber()));
        return new RowBlock(marker.rowNumber(), rowSegment, bodySegment);
    }

    private String joinRowContents(List<RowBlock> current, RowBlock next) {
        List<String> contents =
                new ArrayList<>(
                        current.stream().map(RowBlock::segment).map(Segment::content).toList());
        contents.add(next.segment().content());
        return String.join("\n\n", contents);
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

    private record RowMarker(int start, int rowNumber) {}

    private record RowBlock(int rowNumber, Segment segment, Segment bodySegment) {}
}
