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
package com.openmemind.ai.memory.plugin.rawdata.image.chunk;

import com.openmemind.ai.memory.core.extraction.rawdata.chunk.TokenAwareSegmentAssembler;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import com.openmemind.ai.memory.plugin.rawdata.image.config.ImageExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes image caption and OCR into token-bounded segments.
 */
public final class ImageSegmentComposer {

    private final TokenAwareSegmentAssembler tokenAwareSegmentAssembler;

    public ImageSegmentComposer() {
        this(new TokenAwareSegmentAssembler());
    }

    public ImageSegmentComposer(TokenAwareSegmentAssembler tokenAwareSegmentAssembler) {
        this.tokenAwareSegmentAssembler =
                Objects.requireNonNull(tokenAwareSegmentAssembler, "tokenAwareSegmentAssembler");
    }

    public List<Segment> compose(ImageContent content, ImageExtractionOptions options) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(options, "options");

        String caption = content.description() == null ? "" : content.description().trim();
        String ocrText = content.ocrText() == null ? "" : content.ocrText().trim();
        if (caption.isBlank() && ocrText.isBlank()) {
            return List.of();
        }

        if (!caption.isBlank()
                && !ocrText.isBlank()
                && TokenUtils.countTokens(ocrText) <= options.captionOcrMergeMaxTokens()) {
            String merged = caption + "\n" + ocrText;
            return tokenAwareSegmentAssembler.assemble(
                    List.of(
                            new Segment(
                                    merged,
                                    null,
                                    new CharBoundary(0, merged.length()),
                                    Map.of("segmentRole", "caption_ocr"))),
                    options.chunking());
        }

        List<Segment> segments = new ArrayList<>();
        if (!caption.isBlank()) {
            segments.addAll(
                    tokenAwareSegmentAssembler.assemble(
                            List.of(
                                    new Segment(
                                            caption,
                                            null,
                                            new CharBoundary(0, caption.length()),
                                            Map.of("segmentRole", "caption"))),
                            options.chunking()));
        }

        if (!ocrText.isBlank()) {
            int ocrOffset = caption.isBlank() ? 0 : caption.length() + 1;
            List<Segment> ocrCandidates =
                    tokenAwareSegmentAssembler.paragraphCandidates(
                            ocrText, Map.of("segmentRole", "ocr"));
            List<Segment> shifted =
                    ocrCandidates.stream().map(candidate -> shift(candidate, ocrOffset)).toList();
            segments.addAll(tokenAwareSegmentAssembler.assemble(shifted, options.chunking()));
        }
        return segments;
    }

    private Segment shift(Segment segment, int offset) {
        CharBoundary boundary = (CharBoundary) segment.boundary();
        return new Segment(
                segment.content(),
                segment.caption(),
                new CharBoundary(offset + boundary.startChar(), offset + boundary.endChar()),
                segment.metadata(),
                segment.runtimeContext());
    }
}
