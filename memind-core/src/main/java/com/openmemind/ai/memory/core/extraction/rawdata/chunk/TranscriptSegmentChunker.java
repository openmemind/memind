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

import com.openmemind.ai.memory.core.builder.AudioExtractionOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.content.AudioContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.audio.TranscriptSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token-aware chunker for transcript segments.
 */
public final class TranscriptSegmentChunker {

    private static final Logger log = LoggerFactory.getLogger(TranscriptSegmentChunker.class);

    private final TokenAwareSegmentAssembler tokenAwareSegmentAssembler;

    public TranscriptSegmentChunker() {
        this(new TokenAwareSegmentAssembler());
    }

    public TranscriptSegmentChunker(TokenAwareSegmentAssembler tokenAwareSegmentAssembler) {
        this.tokenAwareSegmentAssembler =
                Objects.requireNonNull(tokenAwareSegmentAssembler, "tokenAwareSegmentAssembler");
    }

    public List<Segment> chunk(AudioContent content, AudioExtractionOptions options) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(options, "options");

        if (content.segments().isEmpty()) {
            return tokenAwareSegmentAssembler.assemble(
                    tokenAwareSegmentAssembler.paragraphCandidates(content.toContentString()),
                    options.chunking());
        }

        String transcript = resolveTranscriptText(content);
        int searchFrom = 0;
        List<TranscriptSlice> current = new ArrayList<>();
        List<Segment> segments = new ArrayList<>();

        for (TranscriptSegment transcriptSegment : content.segments()) {
            TranscriptSlice atomic = toAtomicSegment(transcriptSegment, transcript, searchFrom);
            searchFrom = ((CharBoundary) atomic.segment().boundary()).endChar();
            if (atomic.segment().content().isBlank()) {
                continue;
            }

            int segmentTokens = TokenUtils.countTokens(atomic.segment().content());
            if (segmentTokens > options.chunking().hardMaxTokens()) {
                if (!current.isEmpty()) {
                    segments.add(toSegment(current));
                    current = new ArrayList<>();
                }
                segments.addAll(splitOversizedSegment(atomic, options.chunking().hardMaxTokens()));
                continue;
            }

            if (!current.isEmpty()
                    && mergedTokens(current, atomic.segment())
                            > options.chunking().targetTokens()) {
                segments.add(toSegment(current));
                current = new ArrayList<>();
            }
            current.add(atomic);
        }

        if (!current.isEmpty()) {
            segments.add(toSegment(current));
        }
        return segments;
    }

    private String resolveTranscriptText(AudioContent content) {
        if (content.toContentString() != null && !content.toContentString().isBlank()) {
            return content.toContentString();
        }
        return content.segments().stream()
                .map(TranscriptSegment::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    private TranscriptSlice toAtomicSegment(
            TranscriptSegment transcriptSegment, String transcript, int searchFrom) {
        String text = transcriptSegment.text() == null ? "" : transcriptSegment.text().trim();
        if (text.isBlank()) {
            return new TranscriptSlice(
                    transcriptSegment,
                    new Segment("", null, new CharBoundary(searchFrom, searchFrom), Map.of()),
                    MatchKind.EXACT);
        }

        ResolvedBoundary resolvedBoundary = resolveBoundary(transcript, text, searchFrom);
        if (resolvedBoundary.matchKind() == MatchKind.APPROXIMATE) {
            logApproximateBoundary(transcript, text, resolvedBoundary.boundary());
        }

        Segment segment =
                new Segment(
                        text,
                        null,
                        resolvedBoundary.boundary(),
                        segmentMetadata(
                                transcriptSegment.startTime(),
                                transcriptSegment.endTime(),
                                transcriptSegment.speaker(),
                                List.of(),
                                resolvedBoundary.matchKind() == MatchKind.APPROXIMATE));
        return new TranscriptSlice(transcriptSegment, segment, resolvedBoundary.matchKind());
    }

    private ResolvedBoundary resolveBoundary(String transcript, String text, int searchFrom) {
        int exact = transcript.indexOf(text, searchFrom);
        if (exact >= 0) {
            return new ResolvedBoundary(
                    new CharBoundary(exact, exact + text.length()), MatchKind.EXACT);
        }

        CharBoundary normalized = findNormalizedWhitespaceBoundary(transcript, text, searchFrom);
        if (normalized != null) {
            return new ResolvedBoundary(normalized, MatchKind.NORMALIZED);
        }

        int start = Math.max(0, searchFrom);
        int end = Math.min(transcript.length(), start + text.length());
        return new ResolvedBoundary(new CharBoundary(start, end), MatchKind.APPROXIMATE);
    }

    private CharBoundary findNormalizedWhitespaceBoundary(
            String transcript, String text, int searchFrom) {
        if (searchFrom >= transcript.length()) {
            return null;
        }
        String normalizedText = collapseWhitespace(text);
        if (normalizedText.isBlank()) {
            return null;
        }
        CollapsedText collapsed =
                collapseWhitespaceWithIndexMap(transcript.substring(searchFrom), searchFrom);
        int collapsedStart = collapsed.text().indexOf(normalizedText);
        if (collapsedStart < 0) {
            return null;
        }
        int collapsedEnd = collapsedStart + normalizedText.length() - 1;
        int start = collapsed.originalIndices().get(collapsedStart);
        int end = collapsed.originalIndices().get(collapsedEnd) + 1;
        return new CharBoundary(start, end);
    }

    private CollapsedText collapseWhitespaceWithIndexMap(String text, int offset) {
        StringBuilder collapsed = new StringBuilder();
        List<Integer> originalIndices = new ArrayList<>();
        boolean previousWhitespace = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!previousWhitespace) {
                    collapsed.append(' ');
                    originalIndices.add(offset + i);
                    previousWhitespace = true;
                }
                continue;
            }
            collapsed.append(ch);
            originalIndices.add(offset + i);
            previousWhitespace = false;
        }
        return new CollapsedText(collapsed.toString(), List.copyOf(originalIndices));
    }

    private String collapseWhitespace(String text) {
        StringBuilder collapsed = new StringBuilder();
        boolean previousWhitespace = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!previousWhitespace) {
                    collapsed.append(' ');
                    previousWhitespace = true;
                }
                continue;
            }
            collapsed.append(ch);
            previousWhitespace = false;
        }
        return collapsed.toString().trim();
    }

    private List<Segment> splitOversizedSegment(TranscriptSlice atomic, int hardMaxTokens) {
        CharBoundary parentBoundary = (CharBoundary) atomic.segment().boundary();
        return tokenAwareSegmentAssembler.splitOversized(atomic.segment(), hardMaxTokens).stream()
                .map(
                        child ->
                                new Segment(
                                        child.content(),
                                        child.caption(),
                                        child.boundary(),
                                        splitMetadata(
                                                atomic.transcriptSegment(),
                                                parentBoundary,
                                                (CharBoundary) child.boundary(),
                                                atomic.matchKind())))
                .toList();
    }

    private Map<String, Object> splitMetadata(
            TranscriptSegment source,
            CharBoundary parentBoundary,
            CharBoundary childBoundary,
            MatchKind matchKind) {
        Duration startTime = source.startTime();
        Duration endTime = source.endTime();
        if (startTime != null && endTime != null) {
            Duration total = endTime.minus(startTime);
            int parentSpan = parentBoundary.endChar() - parentBoundary.startChar();
            if (!total.isNegative() && parentSpan > 0) {
                long totalNanos = total.toNanos();
                startTime =
                        source.startTime()
                                .plusNanos(
                                        proportionalNanos(
                                                totalNanos,
                                                childBoundary.startChar()
                                                        - parentBoundary.startChar(),
                                                parentSpan));
                endTime =
                        source.startTime()
                                .plusNanos(
                                        proportionalNanos(
                                                totalNanos,
                                                childBoundary.endChar()
                                                        - parentBoundary.startChar(),
                                                parentSpan));
            }
        }
        return segmentMetadata(
                startTime,
                endTime,
                source.speaker(),
                List.of(),
                matchKind == MatchKind.APPROXIMATE);
    }

    private long proportionalNanos(long totalNanos, int numerator, int denominator) {
        double ratio = Math.max(0D, Math.min(1D, numerator / (double) denominator));
        return Math.round(totalNanos * ratio);
    }

    private int mergedTokens(List<TranscriptSlice> current, Segment candidate) {
        List<String> contents =
                new ArrayList<>(current.stream().map(slice -> slice.segment().content()).toList());
        contents.add(candidate.content());
        return TokenUtils.countTokens(String.join("\n", contents));
    }

    private Segment toSegment(List<TranscriptSlice> slices) {
        TranscriptSegment first = slices.getFirst().transcriptSegment();
        TranscriptSegment last = slices.getLast().transcriptSegment();
        Segment firstSegment = slices.getFirst().segment();
        Segment lastSegment = slices.getLast().segment();
        CharBoundary start = (CharBoundary) firstSegment.boundary();
        CharBoundary end = (CharBoundary) lastSegment.boundary();
        String content =
                slices.stream()
                        .map(slice -> slice.segment().content())
                        .collect(Collectors.joining("\n"));

        String singleSpeaker = singleSpeaker(slices);
        return new Segment(
                content,
                null,
                new CharBoundary(start.startChar(), end.endChar()),
                segmentMetadata(
                        first.startTime(),
                        last.endTime(),
                        singleSpeaker,
                        singleSpeaker == null ? distinctSpeakers(slices) : List.of(),
                        slices.stream()
                                .anyMatch(slice -> slice.matchKind() == MatchKind.APPROXIMATE)));
    }

    private Map<String, Object> segmentMetadata(
            Duration startTime,
            Duration endTime,
            String speaker,
            List<String> speakers,
            boolean approximateBoundary) {
        var metadata = new LinkedHashMap<String, Object>();
        if (startTime != null) {
            metadata.put("startTime", startTime);
        }
        if (endTime != null) {
            metadata.put("endTime", endTime);
        }
        if (speaker != null && !speaker.isBlank()) {
            metadata.put("speaker", speaker);
        }
        if (!speakers.isEmpty()) {
            metadata.put("speakers", speakers);
        }
        if (approximateBoundary) {
            metadata.put("boundaryApproximate", true);
        }
        return metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
    }

    private String singleSpeaker(List<TranscriptSlice> slices) {
        String speaker = null;
        for (TranscriptSlice slice : slices) {
            String candidate = slice.transcriptSegment().speaker();
            if (candidate == null || candidate.isBlank()) {
                return null;
            }
            if (speaker == null) {
                speaker = candidate;
                continue;
            }
            if (!speaker.equals(candidate)) {
                return null;
            }
        }
        return speaker;
    }

    private List<String> distinctSpeakers(List<TranscriptSlice> slices) {
        Set<String> speakers = new LinkedHashSet<>();
        for (TranscriptSlice slice : slices) {
            String candidate = slice.transcriptSegment().speaker();
            if (candidate != null && !candidate.isBlank()) {
                speakers.add(candidate);
            }
        }
        return speakers.size() > 1 ? List.copyOf(speakers) : List.of();
    }

    private void logApproximateBoundary(
            String transcript, String text, CharBoundary approximateBoundary) {
        int start = approximateBoundary.startChar();
        int end = approximateBoundary.endChar();
        int excerptStart = Math.max(0, start - 24);
        int excerptEnd = Math.min(transcript.length(), end + 24);
        String excerpt =
                transcript.substring(excerptStart, excerptEnd).replaceAll("\\s+", " ").trim();
        log.warn(
                "Approximated transcript boundary for segment text='{}', excerpt='{}'",
                text,
                excerpt);
    }

    private enum MatchKind {
        EXACT,
        NORMALIZED,
        APPROXIMATE
    }

    private record ResolvedBoundary(CharBoundary boundary, MatchKind matchKind) {}

    private record CollapsedText(String text, List<Integer> originalIndices) {}

    private record TranscriptSlice(
            TranscriptSegment transcriptSegment, Segment segment, MatchKind matchKind) {}
}
