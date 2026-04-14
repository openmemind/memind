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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class ParagraphWindowDocumentChunker {

    private static final Map<String, Object> PARAGRAPH_METADATA =
            Map.of("chunkStrategy", "paragraph-window", "structureType", "paragraphBlock");

    private final TokenAwareSegmentAssembler assembler;
    private final DocumentChunkSupport support;

    ParagraphWindowDocumentChunker(
            TokenAwareSegmentAssembler assembler, DocumentChunkSupport support) {
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.support = Objects.requireNonNull(support, "support");
    }

    List<Segment> chunk(String text, TokenChunkingOptions options, int minChunkTokens) {
        Objects.requireNonNull(options, "options");
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<Segment> blocks = assembler.paragraphCandidates(text, PARAGRAPH_METADATA);
        if (blocks.isEmpty()) {
            return List.of();
        }

        List<Segment> merged = new ArrayList<>();
        List<Segment> current = new ArrayList<>();
        for (Segment block : blocks) {
            List<Segment> normalized = assembler.splitOversized(block, options.hardMaxTokens());
            if (normalized.size() > 1) {
                if (!current.isEmpty()) {
                    merged.add(mergeWindow(current));
                    current = new ArrayList<>();
                }
                merged.addAll(normalized);
                continue;
            }
            Segment candidate = normalized.isEmpty() ? block : normalized.getFirst();
            if (current.isEmpty()) {
                current.add(candidate);
                continue;
            }
            boolean canMerge =
                    support.isUndersized(current.getLast(), minChunkTokens)
                            || support.isUndersized(candidate, minChunkTokens);
            boolean withinTarget =
                    support.mergedTokenCount(current, candidate) <= options.targetTokens();
            boolean withinHard =
                    TokenUtils.countTokens(joinContents(current, candidate))
                            <= options.hardMaxTokens();
            if (canMerge && withinTarget && withinHard) {
                current.add(candidate);
                continue;
            }
            merged.add(mergeWindow(current));
            current = new ArrayList<>();
            current.add(candidate);
        }
        if (!current.isEmpty()) {
            merged.add(mergeWindow(current));
        }
        return support.withChunkIndexes(merged);
    }

    private Segment mergeWindow(List<Segment> segments) {
        Segment first = segments.getFirst();
        Segment last = segments.getLast();
        CharBoundary start = support.boundaryOf(first);
        CharBoundary end = support.boundaryOf(last);
        return new Segment(
                joinContents(segments),
                null,
                new CharBoundary(start.startChar(), end.endChar()),
                first.metadata(),
                first.runtimeContext());
    }

    private String joinContents(List<Segment> segments) {
        return segments.stream().map(Segment::content).collect(Collectors.joining("\n\n"));
    }

    private String joinContents(List<Segment> segments, Segment next) {
        List<Segment> merged = new ArrayList<>(segments);
        merged.add(next);
        return joinContents(merged);
    }
}
