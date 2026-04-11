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
package com.openmemind.ai.memory.core.extraction.rawdata.caption;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Deterministic caption generator for parsed image segments.
 */
public final class ImageCaptionGenerator implements CaptionGenerator {

    private static final String OCR_PREFIX = "Image text: ";

    private final int maxLength;

    public ImageCaptionGenerator() {
        this(200);
    }

    public ImageCaptionGenerator(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public Mono<String> generate(String content, Map<String, Object> metadata) {
        String segmentRole = metadata == null ? null : asText(metadata.get("segmentRole"));
        String caption =
                switch (segmentRole) {
                    case "ocr" -> buildOcrCaption(content);
                    case "caption_ocr" -> buildMergedCaption(content);
                    default -> buildDescriptionCaption(content);
                };
        return Mono.just(caption);
    }

    private String buildMergedCaption(String content) {
        String description = normalizeWhitespace(textBeforeFirstLineBreak(content));
        if (!description.isBlank()) {
            return truncate(description);
        }
        return buildOcrCaption(content);
    }

    private String buildDescriptionCaption(String content) {
        String normalized = normalizeWhitespace(content);
        if (normalized.isBlank()) {
            return "";
        }
        return truncate(normalized);
    }

    private String buildOcrCaption(String content) {
        String normalized = normalizeWhitespace(content);
        if (normalized.isBlank()) {
            return "";
        }
        int available = Math.max(0, maxLength - OCR_PREFIX.length());
        return OCR_PREFIX + truncate(normalized, available);
    }

    private String textBeforeFirstLineBreak(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int lineBreakIndex = content.indexOf('\n');
        return lineBreakIndex >= 0 ? content.substring(0, lineBreakIndex) : content;
    }

    private String normalizeWhitespace(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String content) {
        return truncate(content, maxLength);
    }

    private String truncate(String content, int limit) {
        if (limit <= 0 || content.isBlank()) {
            return "";
        }
        return content.length() <= limit ? content : content.substring(0, limit - 3) + "...";
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
