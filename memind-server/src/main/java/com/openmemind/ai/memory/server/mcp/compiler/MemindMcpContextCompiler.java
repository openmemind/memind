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
package com.openmemind.ai.memory.server.mcp.compiler;

import com.openmemind.ai.memory.server.domain.memory.response.RetrieveMemoryResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindCompiledContextResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MemindMcpContextCompiler {

    private static final List<String> SECTION_ORDER =
            List.of(
                    "Must Follow",
                    "Resolved Problems",
                    "Playbooks",
                    "Tool Notes",
                    "Insights",
                    "Recent Context",
                    "Other Memory");

    public MemindCompiledContextResponse compile(
            RetrieveMemoryResponse response,
            int maxItems,
            int tokenBudget,
            boolean includeSources) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        SECTION_ORDER.forEach(section -> grouped.put(section, new ArrayList<>()));

        List<RetrieveMemoryResponse.RetrievedItemView> selectedItems =
                response.items().stream().limit(Math.max(0, maxItems)).toList();
        Set<String> selectedItemIds = new LinkedHashSet<>();
        for (var item : selectedItems) {
            if (hasText(item.id())) {
                selectedItemIds.add(item.id());
            }
            if (hasText(item.text())) {
                grouped.get(sectionForCategory(item.category())).add(item.text().trim());
            }
        }

        for (var insight : response.insights()) {
            if (hasText(insight.text())) {
                grouped.get("Insights").add(insight.text().trim());
            }
        }

        for (var rawData : response.rawData()) {
            if (shouldRenderRawDataCaption(rawData, selectedItemIds)
                    && hasText(rawData.caption())) {
                grouped.get("Recent Context").add(rawData.caption().trim());
            }
        }

        List<MemindCompiledContextResponse.Section> sections =
                SECTION_ORDER.stream()
                        .map(
                                name ->
                                        new MemindCompiledContextResponse.Section(
                                                name, grouped.get(name)))
                        .filter(section -> !section.items().isEmpty())
                        .toList();
        String rendered = render(sections);
        TruncatedText truncated = applyTokenBudget(rendered, tokenBudget);

        return new MemindCompiledContextResponse(
                response.status(),
                response.query(),
                response.strategy(),
                truncated.text(),
                sections,
                includeSources ? sources(response.rawData()) : List.of(),
                truncated.truncated());
    }

    private static boolean shouldRenderRawDataCaption(
            RetrieveMemoryResponse.RetrievedRawDataView rawData, Set<String> selectedItemIds) {
        if (rawData.itemIds().isEmpty()) {
            return true;
        }
        return rawData.itemIds().stream().anyMatch(selectedItemIds::contains);
    }

    private static List<MemindCompiledContextResponse.Source> sources(
            List<RetrieveMemoryResponse.RetrievedRawDataView> rawData) {
        return rawData.stream()
                .map(
                        source ->
                                new MemindCompiledContextResponse.Source(
                                        source.rawDataId(),
                                        source.type(),
                                        source.caption(),
                                        source.sourceClient(),
                                        source.itemIds(),
                                        source.metadata()))
                .toList();
    }

    private static String render(List<MemindCompiledContextResponse.Section> sections) {
        StringBuilder builder = new StringBuilder();
        for (var section : sections) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("## ").append(section.name());
            for (String item : section.items()) {
                builder.append("\n- ").append(item);
            }
        }
        return builder.toString();
    }

    private static TruncatedText applyTokenBudget(String text, int tokenBudget) {
        int maxChars = Math.max(1, tokenBudget) * 4;
        if (text.length() <= maxChars) {
            return new TruncatedText(text, false);
        }
        return new TruncatedText(text.substring(0, maxChars) + "\n...[truncated]", true);
    }

    private static String sectionForCategory(String category) {
        if (!hasText(category)) {
            return "Other Memory";
        }
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "directive" -> "Must Follow";
            case "resolution" -> "Resolved Problems";
            case "playbook" -> "Playbooks";
            case "tool" -> "Tool Notes";
            case "insight" -> "Insights";
            default -> "Other Memory";
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record TruncatedText(String text, boolean truncated) {}
}
