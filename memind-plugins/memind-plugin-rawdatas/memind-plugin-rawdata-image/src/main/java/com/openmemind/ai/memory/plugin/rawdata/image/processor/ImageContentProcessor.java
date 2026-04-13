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
package com.openmemind.ai.memory.plugin.rawdata.image.processor;

import com.openmemind.ai.memory.core.extraction.ParsedContentTooLargeException;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.caption.CaptionGenerator;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import com.openmemind.ai.memory.plugin.rawdata.image.caption.ImageCaptionGenerator;
import com.openmemind.ai.memory.plugin.rawdata.image.chunk.ImageSegmentComposer;
import com.openmemind.ai.memory.plugin.rawdata.image.config.ImageExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.image.content.ImageContent;
import com.openmemind.ai.memory.plugin.rawdata.image.ImageSemantics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Processor for parsed image content.
 */
public class ImageContentProcessor implements RawContentProcessor<ImageContent> {

    private final ImageSegmentComposer imageSegmentComposer;
    private final ImageExtractionOptions options;
    private final CaptionGenerator captionGenerator;

    public ImageContentProcessor() {
        this(new ImageSegmentComposer(), ImageExtractionOptions.defaults());
    }

    public ImageContentProcessor(
            ImageSegmentComposer imageSegmentComposer, ImageExtractionOptions options) {
        this.imageSegmentComposer =
                Objects.requireNonNull(
                        imageSegmentComposer, "imageSegmentComposer must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.captionGenerator = new ImageCaptionGenerator();
    }

    @Override
    public Class<ImageContent> contentClass() {
        return ImageContent.class;
    }

    @Override
    public String contentType() {
        return ImageContent.TYPE;
    }

    @Override
    public boolean usesSourceIdentity() {
        return true;
    }

    @Override
    public void validateParsedContent(ImageContent content) {
        int tokenCount = TokenUtils.countTokens(content.toContentString());
        int maxTokens = options.parsedLimit().maxTokens();
        if (tokenCount > maxTokens) {
            throw new ParsedContentTooLargeException(
                    "Parsed content exceeds token limit: profile=%s tokens=%d max=%d"
                            .formatted(resolveProfile(content), tokenCount, maxTokens));
        }
    }

    @Override
    public Mono<List<Segment>> chunk(ImageContent content) {
        return Mono.just(
                imageSegmentComposer.compose(content, options).stream()
                        .map(segment -> mergeMetadata(segment, content.metadata()))
                        .toList());
    }

    @Override
    public CaptionGenerator captionGenerator() {
        return captionGenerator;
    }

    private String resolveProfile(ImageContent content) {
        Object profile = content.metadata().get("contentProfile");
        if (profile != null && !profile.toString().isBlank()) {
            return profile.toString();
        }
        return content.directContentProfile() != null
                ? content.directContentProfile()
                : ImageSemantics.PROFILE_CAPTION_OCR;
    }

    private Segment mergeMetadata(Segment segment, Map<String, Object> contentMetadata) {
        if (contentMetadata == null || contentMetadata.isEmpty()) {
            return segment;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(contentMetadata);
        metadata.putAll(segment.metadata());
        return new Segment(
                segment.content(),
                segment.caption(),
                segment.boundary(),
                Map.copyOf(metadata),
                segment.runtimeContext());
    }
}
