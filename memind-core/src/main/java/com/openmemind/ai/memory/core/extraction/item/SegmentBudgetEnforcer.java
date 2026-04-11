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
package com.openmemind.ai.memory.core.extraction.item;

import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TokenAwareSegmentAssembler;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Enforces the final prompt budget before raw-data segments enter item extraction.
 */
public final class SegmentBudgetEnforcer {

    private final TokenAwareSegmentAssembler tokenAwareSegmentAssembler;

    public SegmentBudgetEnforcer() {
        this(new TokenAwareSegmentAssembler());
    }

    public SegmentBudgetEnforcer(TokenAwareSegmentAssembler tokenAwareSegmentAssembler) {
        this.tokenAwareSegmentAssembler =
                Objects.requireNonNull(tokenAwareSegmentAssembler, "tokenAwareSegmentAssembler");
    }

    public RawDataResult enforce(RawDataResult rawDataResult, ItemExtractionConfig config) {
        Objects.requireNonNull(rawDataResult, "rawDataResult");
        Objects.requireNonNull(config, "config");
        if (rawDataResult.segments().isEmpty()) {
            return rawDataResult;
        }

        int effectiveBudget = effectiveBudget(config);
        List<ParsedSegment> adjusted = new ArrayList<>();
        for (ParsedSegment segment : rawDataResult.segments()) {
            for (ParsedSegment splitSegment : splitSegment(segment, effectiveBudget)) {
                adjusted.add(enforceHardLimit(splitSegment, effectiveBudget));
            }
        }
        return rawDataResult.withSegments(adjusted);
    }

    private int effectiveBudget(ItemExtractionConfig config) {
        var budget = config.promptBudget();
        return budget.maxInputTokens()
                - budget.reservedPromptOverhead()
                - budget.reservedOutputTokens()
                - budget.safetyMargin();
    }

    private List<ParsedSegment> splitSegment(ParsedSegment segment, int effectiveBudget) {
        if (TokenUtils.countTokens(segment.text()) <= effectiveBudget) {
            return List.of(segment);
        }

        Segment base = toSegment(segment);
        List<Segment> structuredCandidates = structuredCandidates(base);
        if (structuredCandidates.size() <= 1) {
            return List.of(segment);
        }
        List<Segment> assembled =
                tokenAwareSegmentAssembler.assemble(
                        structuredCandidates,
                        new TokenChunkingOptions(effectiveBudget, effectiveBudget));
        return assembled.stream().map(child -> toParsedSegment(segment, child)).toList();
    }

    private List<Segment> structuredCandidates(Segment base) {
        String profile = String.valueOf(base.metadata().getOrDefault("contentProfile", "")).trim();
        List<Segment> candidates =
                switch (profile) {
                    case "document.markdown" ->
                            tokenAwareSegmentAssembler.markdownCandidates(
                                    base.content(), base.metadata());
                    default ->
                            tokenAwareSegmentAssembler.paragraphCandidates(
                                    base.content(), base.metadata());
                };
        if (candidates.isEmpty()) {
            return List.of(base);
        }
        return candidates.stream().map(candidate -> shift(base, candidate)).toList();
    }

    private ParsedSegment enforceHardLimit(ParsedSegment segment, int effectiveBudget) {
        int tokens = TokenUtils.countTokens(segment.text());
        if (tokens <= effectiveBudget) {
            return segment;
        }

        String truncatedText = truncateToBudget(segment.text(), effectiveBudget);
        var metadata =
                segment.metadata() == null
                        ? new LinkedHashMap<String, Object>()
                        : new LinkedHashMap<String, Object>(segment.metadata());
        metadata.put("budgetTruncated", true);
        metadata.put("originalTokenCount", tokens);
        metadata.put("retainedTokenCount", TokenUtils.countTokens(truncatedText));
        metadata.put("truncationReason", "prompt_budget");

        int endIndex =
                Math.max(
                        segment.startIndex(),
                        segment.startIndex()
                                + (truncatedText == null ? 0 : truncatedText.length()));
        return new ParsedSegment(
                truncatedText,
                segment.caption(),
                segment.startIndex(),
                endIndex,
                segment.rawDataId(),
                Map.copyOf(metadata),
                segment.runtimeContext());
    }

    private String truncateToBudget(String text, int effectiveBudget) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String[] words = text.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            String candidate = builder.isEmpty() ? word : builder + " " + word;
            if (TokenUtils.countTokens(candidate) > effectiveBudget) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(word);
        }
        if (!builder.isEmpty()) {
            return builder.toString();
        }

        int low = 1;
        int high = text.length();
        int best = 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String candidate = text.substring(0, mid).trim();
            if (candidate.isEmpty()) {
                low = mid + 1;
                continue;
            }
            if (TokenUtils.countTokens(candidate) <= effectiveBudget) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, best).trim();
    }

    private Segment toSegment(ParsedSegment segment) {
        int start = Math.max(0, segment.startIndex());
        int end =
                segment.endIndex() > start
                        ? segment.endIndex()
                        : start + (segment.text() == null ? 0 : segment.text().length());
        return new Segment(
                segment.text(),
                segment.caption(),
                new CharBoundary(start, end),
                segment.metadata() == null ? Map.of() : segment.metadata(),
                segment.runtimeContext());
    }

    private Segment shift(Segment parent, Segment child) {
        CharBoundary parentBoundary = (CharBoundary) parent.boundary();
        CharBoundary childBoundary = (CharBoundary) child.boundary();
        return new Segment(
                child.content(),
                parent.caption(),
                new CharBoundary(
                        parentBoundary.startChar() + childBoundary.startChar(),
                        parentBoundary.startChar() + childBoundary.endChar()),
                child.metadata(),
                parent.runtimeContext());
    }

    private ParsedSegment toParsedSegment(ParsedSegment original, Segment child) {
        CharBoundary boundary = (CharBoundary) child.boundary();
        return new ParsedSegment(
                child.content(),
                child.caption() != null ? child.caption() : original.caption(),
                boundary.startChar(),
                boundary.endChar(),
                original.rawDataId(),
                child.metadata(),
                original.runtimeContext());
    }
}
