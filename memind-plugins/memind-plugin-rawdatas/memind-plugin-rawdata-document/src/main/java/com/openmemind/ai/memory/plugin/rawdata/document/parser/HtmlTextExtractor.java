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
package com.openmemind.ai.memory.plugin.rawdata.document.parser;

import java.util.Locale;
import java.util.Set;

/**
 * Lightweight, linear HTML text extractor used by the native text parser.
 */
public final class HtmlTextExtractor {

    private static final Set<String> BLOCK_TAGS =
            Set.of(
                    "article",
                    "aside",
                    "blockquote",
                    "br",
                    "div",
                    "footer",
                    "h1",
                    "h2",
                    "h3",
                    "h4",
                    "h5",
                    "h6",
                    "header",
                    "hr",
                    "li",
                    "main",
                    "nav",
                    "ol",
                    "p",
                    "pre",
                    "section",
                    "table",
                    "tbody",
                    "tfoot",
                    "thead",
                    "tr",
                    "ul");

    public String extract(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        boolean inScript = false;
        boolean inStyle = false;

        for (int i = 0; i < html.length(); i++) {
            char ch = html.charAt(i);
            if ((inScript || inStyle) && ch != '<') {
                continue;
            }
            if (ch == '<') {
                if (startsWith(html, i, "<!--")) {
                    int commentEnd = html.indexOf("-->", i + 4);
                    if (commentEnd < 0) {
                        break;
                    }
                    i = commentEnd + 2;
                    continue;
                }

                int tagEnd = html.indexOf('>', i + 1);
                if (tagEnd < 0) {
                    text.append(ch);
                    continue;
                }

                String tag = html.substring(i + 1, tagEnd);
                String tagName = tagName(tag);
                boolean closing = isClosingTag(tag);

                if (inScript) {
                    if (closing && "script".equals(tagName)) {
                        inScript = false;
                        appendLineBreak(text);
                    }
                    i = tagEnd;
                    continue;
                }
                if (inStyle) {
                    if (closing && "style".equals(tagName)) {
                        inStyle = false;
                        appendLineBreak(text);
                    }
                    i = tagEnd;
                    continue;
                }

                if (!closing && "script".equals(tagName)) {
                    inScript = true;
                    i = tagEnd;
                    continue;
                }
                if (!closing && "style".equals(tagName)) {
                    inStyle = true;
                    i = tagEnd;
                    continue;
                }
                if (BLOCK_TAGS.contains(tagName)) {
                    appendLineBreak(text);
                }
                i = tagEnd;
                continue;
            }

            if (ch == '&') {
                int entityEnd = html.indexOf(';', i + 1);
                if (entityEnd > i && entityEnd - i <= 10) {
                    String decoded = decodeEntity(html.substring(i, entityEnd + 1));
                    if (decoded != null) {
                        text.append(decoded);
                        i = entityEnd;
                        continue;
                    }
                }
            }
            text.append(ch);
        }

        return normalize(text.toString());
    }

    private boolean startsWith(String text, int offset, String prefix) {
        return text.regionMatches(offset, prefix, 0, prefix.length());
    }

    private boolean isClosingTag(String tag) {
        return !tag.isBlank() && tag.trim().startsWith("/");
    }

    private String tagName(String tag) {
        String trimmed = tag.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int start = trimmed.startsWith("/") ? 1 : 0;
        while (start < trimmed.length() && Character.isWhitespace(trimmed.charAt(start))) {
            start++;
        }
        if (start >= trimmed.length()) {
            return "";
        }
        if (trimmed.charAt(start) == '!' || trimmed.charAt(start) == '?') {
            return "";
        }
        int end = start;
        while (end < trimmed.length()) {
            char ch = trimmed.charAt(end);
            if (Character.isWhitespace(ch) || ch == '/' || ch == '>') {
                break;
            }
            end++;
        }
        return trimmed.substring(start, end).toLowerCase(Locale.ROOT);
    }

    private void appendLineBreak(StringBuilder text) {
        if (text.isEmpty() || text.charAt(text.length() - 1) == '\n') {
            return;
        }
        text.append('\n');
    }

    private String decodeEntity(String entity) {
        return switch (entity) {
            case "&amp;" -> "&";
            case "&lt;" -> "<";
            case "&gt;" -> ">";
            case "&quot;" -> "\"";
            case "&apos;", "&#39;" -> "'";
            case "&nbsp;" -> " ";
            default -> decodeNumericEntity(entity);
        };
    }

    private String decodeNumericEntity(String entity) {
        try {
            if (entity.startsWith("&#x") || entity.startsWith("&#X")) {
                return Character.toString(
                        Integer.parseInt(entity.substring(3, entity.length() - 1), 16));
            }
            if (entity.startsWith("&#")) {
                return Character.toString(
                        Integer.parseInt(entity.substring(2, entity.length() - 1)));
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private String normalize(String rawText) {
        String normalized = rawText.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder();
        boolean pendingSpace = false;
        boolean previousWasNewline = true;

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '\n') {
                trimTrailingSpaces(result);
                if (!result.isEmpty() && result.charAt(result.length() - 1) != '\n') {
                    result.append('\n');
                }
                pendingSpace = false;
                previousWasNewline = true;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (!previousWasNewline && !result.isEmpty()) {
                    pendingSpace = true;
                }
                continue;
            }
            if (pendingSpace && !result.isEmpty() && result.charAt(result.length() - 1) != '\n') {
                result.append(' ');
            }
            result.append(ch);
            pendingSpace = false;
            previousWasNewline = false;
        }

        trimTrailingSpaces(result);
        while (!result.isEmpty() && result.charAt(result.length() - 1) == '\n') {
            result.setLength(result.length() - 1);
        }
        return result.toString();
    }

    private void trimTrailingSpaces(StringBuilder text) {
        while (!text.isEmpty() && text.charAt(text.length() - 1) == ' ') {
            text.setLength(text.length() - 1);
        }
    }
}
