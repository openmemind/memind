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
package com.openmemind.ai.memory.plugin.rawdata.document.caption;

import java.util.Map;

/**
 * Deterministic fallback captions for document chunks.
 */
public final class DocumentCaptionFallbacks {

    private DocumentCaptionFallbacks() {}

    public static String build(String content, Map<String, Object> metadata, int maxLength) {
        String subject =
                firstNonBlank(
                        metadataText(metadata, "headingTitle"),
                        metadataText(metadata, "sectionTitle"),
                        rangeSubject(metadata));
        String excerpt = firstSentence(normalizeWhitespace(content));
        String caption;
        if (subject != null && excerpt != null && !excerpt.isBlank()) {
            caption = subject + ": " + excerpt;
        } else if (subject != null) {
            caption = subject;
        } else {
            caption = excerpt == null ? "" : excerpt;
        }
        return truncate(caption, maxLength);
    }

    private static String metadataText(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String normalizeWhitespace(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.replaceAll("\\s+", " ").trim();
    }

    private static String firstSentence(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int boundary = findSentenceBoundary(content);
        return boundary < 0 ? content : content.substring(0, boundary).trim();
    }

    private static int findSentenceBoundary(String content) {
        for (int index = 0; index < content.length(); index++) {
            char ch = content.charAt(index);
            if (ch == '.' || ch == '!' || ch == '?') {
                return index + 1;
            }
        }
        return -1;
    }

    private static String truncate(String caption, int maxLength) {
        if (caption == null || caption.isBlank() || maxLength <= 0) {
            return "";
        }
        if (caption.length() <= maxLength) {
            return caption;
        }
        if (maxLength <= 3) {
            return ".".repeat(maxLength);
        }
        return caption.substring(0, maxLength - 3).trim() + "...";
    }

    private static String rangeSubject(Map<String, Object> metadata) {
        String page =
                rangeLabel(
                        "Page",
                        metadata == null ? null : metadata.get("pageNumber"),
                        metadata == null ? null : metadata.get("pageStart"),
                        metadata == null ? null : metadata.get("pageEnd"));
        if (page != null) {
            return page;
        }
        return rangeLabel(
                "Row",
                null,
                metadata == null ? null : metadata.get("rowStart"),
                metadata == null ? null : metadata.get("rowEnd"));
    }

    private static String rangeLabel(
            String singular, Object singleValue, Object startValue, Object endValue) {
        Integer single = positiveInt(singleValue);
        if (single != null) {
            return singular + " " + single;
        }
        Integer start = positiveInt(startValue);
        Integer end = positiveInt(endValue);
        if (start == null && end == null) {
            return null;
        }
        if (start != null && end != null) {
            return start.equals(end) ? singular + " " + start : singular + "s " + start + "-" + end;
        }
        Integer resolved = start != null ? start : end;
        return singular + " " + resolved;
    }

    private static Integer positiveInt(Object value) {
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return null;
    }
}
