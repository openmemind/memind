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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Structured HTML text extractor used by the native text parser.
 */
public final class HtmlTextExtractor {

    private static final Set<String> IGNORED_TAGS =
            Set.of("script", "style", "head", "noscript", "template");
    private static final Set<String> TEXT_BLOCK_TAGS =
            Set.of("h1", "h2", "h3", "h4", "h5", "h6", "p", "pre", "blockquote");

    public String extract(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document document = Jsoup.parse(html);
        List<String> blocks = new ArrayList<>();
        appendElement(document.body(), blocks);
        return String.join("\n\n", blocks);
    }

    private void appendElement(Element element, List<String> blocks) {
        if (element == null) {
            return;
        }
        String tag = element.normalName();
        if (IGNORED_TAGS.contains(tag)) {
            return;
        }
        if ("ul".equals(tag) || "ol".equals(tag)) {
            addBlock(blocks, renderListBlock(element));
            return;
        }
        if ("table".equals(tag)) {
            addBlock(blocks, renderTableBlock(element));
            return;
        }
        if (TEXT_BLOCK_TAGS.contains(tag)) {
            addBlock(blocks, normalizeInline(element.text()));
            return;
        }
        StringBuilder inlineBuffer = new StringBuilder();
        for (Node child : element.childNodes()) {
            if (child instanceof TextNode textNode) {
                appendInline(inlineBuffer, textNode.text());
                continue;
            }
            if (!(child instanceof Element childElement)) {
                continue;
            }
            if (IGNORED_TAGS.contains(childElement.normalName())) {
                continue;
            }
            if (isRenderableElement(childElement)) {
                flushInlineBlock(blocks, inlineBuffer);
                appendElement(childElement, blocks);
                continue;
            }
            appendInline(inlineBuffer, childElement.text());
        }
        flushInlineBlock(blocks, inlineBuffer);
    }

    private boolean isRenderableElement(Element element) {
        String tag = element.normalName();
        if ("ul".equals(tag)
                || "ol".equals(tag)
                || "table".equals(tag)
                || TEXT_BLOCK_TAGS.contains(tag)) {
            return true;
        }
        for (Element child : element.children()) {
            if (isRenderableElement(child)) {
                return true;
            }
        }
        return false;
    }

    private String renderListBlock(Element list) {
        return list.children().stream()
                .filter(child -> "li".equals(child.normalName()))
                .map(item -> "- " + normalizeInline(item.text()))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String renderTableBlock(Element table) {
        return table.select("> tr, > thead > tr, > tbody > tr, > tfoot > tr").stream()
                .map(this::serializeRow)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String serializeRow(Element row) {
        return row.children().stream()
                .filter(cell -> "th".equals(cell.normalName()) || "td".equals(cell.normalName()))
                .map(cell -> normalizeInline(cell.text()))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" | "));
    }

    private void addBlock(List<String> blocks, String value) {
        if (value != null && !value.isBlank()) {
            blocks.add(value);
        }
    }

    private void appendInline(StringBuilder inlineBuffer, String value) {
        String normalized = normalizeInline(value);
        if (normalized.isBlank()) {
            return;
        }
        if (!inlineBuffer.isEmpty()) {
            inlineBuffer.append(' ');
        }
        inlineBuffer.append(normalized);
    }

    private void flushInlineBlock(List<String> blocks, StringBuilder inlineBuffer) {
        addBlock(blocks, inlineBuffer.toString());
        inlineBuffer.setLength(0);
    }

    private String normalizeInline(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").strip();
    }
}
