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

import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Token-first segment assembler with structure-aware fallback splitting.
 */
public final class TokenAwareSegmentAssembler {

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\R\\s*\\R+");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+.+$");
    private static final Pattern WORD = Pattern.compile("\\S+");
    private static final Pattern LEADING_MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+");

    public List<Segment> paragraphCandidates(String text) {
        return paragraphCandidates(text, Map.of());
    }

    public List<Segment> paragraphCandidates(String text, Map<String, Object> metadata) {
        return splitCandidates(text, PARAGRAPH_BREAK, metadata, false);
    }

    public List<Segment> markdownCandidates(String text) {
        return markdownCandidates(text, Map.of());
    }

    public List<Segment> markdownCandidates(String text, Map<String, Object> metadata) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        Matcher matcher = MARKDOWN_HEADING.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
        }
        if (starts.isEmpty()) {
            return paragraphCandidates(text, metadata);
        }

        List<Segment> candidates = new ArrayList<>();
        if (starts.getFirst() > 0) {
            addCandidate(candidates, text, 0, starts.getFirst(), metadata);
        }
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            addCandidate(candidates, text, start, end, metadata);
        }
        return candidates;
    }

    public List<Segment> assemble(List<Segment> candidates, TokenChunkingOptions options) {
        Objects.requireNonNull(options, "options");
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<Segment> normalized = new ArrayList<>();
        for (Segment candidate : candidates) {
            normalized.addAll(splitOversized(candidate, options.hardMaxTokens()));
        }
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<Segment> segments = new ArrayList<>();
        List<Segment> current = new ArrayList<>();
        for (Segment candidate : normalized) {
            boolean metadataChanged =
                    !current.isEmpty()
                            && (!Objects.equals(current.getFirst().metadata(), candidate.metadata())
                                    || !Objects.equals(
                                            current.getFirst().runtimeContext(),
                                            candidate.runtimeContext())
                                    || !Objects.equals(
                                            current.getFirst().caption(), candidate.caption()));
            boolean exceedsTarget =
                    !current.isEmpty()
                            && mergedTokenCount(current, candidate) > options.targetTokens();
            boolean exceedsHard =
                    !current.isEmpty()
                            && mergedTokenCount(current, candidate) > options.hardMaxTokens();
            boolean startsNewHeadingBlock =
                    !current.isEmpty()
                            && LEADING_MARKDOWN_HEADING.matcher(candidate.content()).find();
            if (metadataChanged || exceedsTarget || exceedsHard || startsNewHeadingBlock) {
                segments.add(merge(current));
                current = new ArrayList<>();
            }
            current.add(candidate);
        }
        if (!current.isEmpty()) {
            segments.add(merge(current));
        }
        return segments;
    }

    public List<Segment> splitOversized(Segment candidate, int hardMaxTokens) {
        if (candidate == null || candidate.content() == null || candidate.content().isBlank()) {
            return List.of();
        }
        if (TokenUtils.countTokens(candidate.content()) <= hardMaxTokens) {
            return List.of(candidate);
        }

        List<Segment> paragraphs =
                paragraphCandidates(candidate.content(), candidate.metadata()).stream()
                        .map(child -> shift(candidate, child))
                        .toList();
        if (paragraphs.size() > 1) {
            return paragraphs.stream()
                    .flatMap(segment -> splitOversized(segment, hardMaxTokens).stream())
                    .toList();
        }

        List<Segment> lines = lineCandidates(candidate.content(), candidate.metadata());
        if (lines.size() > 1) {
            return lines.stream()
                    .map(child -> shift(candidate, child))
                    .flatMap(segment -> splitOversized(segment, hardMaxTokens).stream())
                    .toList();
        }

        return tokenSlice(candidate, hardMaxTokens);
    }

    private List<Segment> splitCandidates(
            String text,
            Pattern delimiter,
            Map<String, Object> metadata,
            boolean includeDelimiters) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Segment> candidates = new ArrayList<>();
        Matcher matcher = delimiter.matcher(text);
        int start = 0;
        while (matcher.find()) {
            int end = includeDelimiters ? matcher.end() : matcher.start();
            addCandidate(candidates, text, start, end, metadata);
            start = matcher.end();
        }
        addCandidate(candidates, text, start, text.length(), metadata);
        return candidates;
    }

    private List<Segment> lineCandidates(String text, Map<String, Object> metadata) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Segment> candidates = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                addCandidate(candidates, text, start, index, metadata);
                start = index + 1;
            }
        }
        addCandidate(candidates, text, start, text.length(), metadata);
        return candidates;
    }

    private int mergedTokenCount(List<Segment> current, Segment candidate) {
        List<String> contents = new ArrayList<>(current.stream().map(Segment::content).toList());
        contents.add(candidate.content());
        return TokenUtils.countTokens(String.join("\n\n", contents));
    }

    private List<Segment> tokenSlice(Segment candidate, int hardMaxTokens) {
        Matcher matcher = WORD.matcher(candidate.content());
        List<int[]> spans = new ArrayList<>();
        while (matcher.find()) {
            spans.add(new int[] {matcher.start(), matcher.end()});
        }
        if (spans.isEmpty()) {
            return characterSlice(candidate, hardMaxTokens);
        }

        List<Segment> slices = new ArrayList<>();
        int sliceStart = spans.getFirst()[0];
        int sliceEnd = spans.getFirst()[1];

        for (int index = 1; index < spans.size(); index++) {
            int wordStart = spans.get(index)[0];
            int wordEnd = spans.get(index)[1];
            String expanded = candidate.content().substring(sliceStart, wordEnd).trim();
            if (TokenUtils.countTokens(expanded) > hardMaxTokens) {
                Segment slice = createShiftedSegment(candidate, sliceStart, sliceEnd);
                if (slice == null) {
                    return characterSlice(candidate, hardMaxTokens);
                }
                slices.add(slice);
                sliceStart = wordStart;
            }
            sliceEnd = wordEnd;
        }

        Segment lastSlice = createShiftedSegment(candidate, sliceStart, sliceEnd);
        if (lastSlice != null) {
            slices.add(lastSlice);
        }

        if (slices.isEmpty()
                || slices.stream()
                        .anyMatch(s -> TokenUtils.countTokens(s.content()) > hardMaxTokens)) {
            return characterSlice(candidate, hardMaxTokens);
        }
        return slices;
    }

    private List<Segment> characterSlice(Segment candidate, int hardMaxTokens) {
        List<Segment> slices = new ArrayList<>();
        String text = candidate.content();
        int start = skipWhitespaceForward(text, 0);
        while (start < text.length()) {
            int low = start + 1;
            int high = text.length();
            int best = -1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                String sample = text.substring(start, mid).trim();
                if (sample.isEmpty()) {
                    low = mid + 1;
                    continue;
                }
                if (TokenUtils.countTokens(sample) <= hardMaxTokens) {
                    best = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }

            if (best < 0) {
                best = start + 1;
            }

            Segment slice = createShiftedSegment(candidate, start, best);
            if (slice == null) {
                start = skipWhitespaceForward(text, best);
                continue;
            }
            slices.add(slice);
            start = skipWhitespaceForward(text, best);
        }
        return slices;
    }

    private Segment merge(List<Segment> candidates) {
        Segment first = candidates.getFirst();
        Segment last = candidates.getLast();
        CharBoundary firstBoundary = charBoundary(first);
        CharBoundary lastBoundary = charBoundary(last);
        String content =
                candidates.stream().map(Segment::content).collect(Collectors.joining("\n\n"));
        return new Segment(
                content,
                first.caption(),
                new CharBoundary(firstBoundary.startChar(), lastBoundary.endChar()),
                first.metadata(),
                first.runtimeContext());
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

    private Segment createShiftedSegment(Segment parent, int rawStart, int rawEnd) {
        String text = parent.content();
        int start = skipWhitespaceForward(text, rawStart);
        int end = skipWhitespaceBackward(text, rawEnd);
        if (start >= end) {
            return null;
        }

        CharBoundary boundary = charBoundary(parent);
        return new Segment(
                text.substring(start, end),
                parent.caption(),
                new CharBoundary(boundary.startChar() + start, boundary.startChar() + end),
                parent.metadata(),
                parent.runtimeContext());
    }

    private void addCandidate(
            List<Segment> candidates,
            String text,
            int rawStart,
            int rawEnd,
            Map<String, Object> metadata) {
        int start = skipWhitespaceForward(text, rawStart);
        int end = skipWhitespaceBackward(text, rawEnd);
        if (start >= end) {
            return;
        }
        candidates.add(
                new Segment(
                        text.substring(start, end),
                        null,
                        new CharBoundary(start, end),
                        metadata == null ? Map.of() : Map.copyOf(metadata)));
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

    private CharBoundary charBoundary(Segment segment) {
        if (segment.boundary() instanceof CharBoundary charBoundary) {
            return charBoundary;
        }
        throw new IllegalArgumentException("TokenAwareSegmentAssembler requires CharBoundary");
    }
}
