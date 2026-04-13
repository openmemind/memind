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
package com.openmemind.ai.memory.plugin.rawdata.document.processor;

import com.openmemind.ai.memory.plugin.rawdata.document.content.document.DocumentSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class DocumentSectionBoundaryResolver {

    Optional<List<ResolvedSectionSpan>> resolve(String fullText, List<DocumentSection> sections) {
        if (fullText == null || fullText.isBlank() || sections == null || sections.isEmpty()) {
            return Optional.of(List.of());
        }

        List<ResolvedSectionSpan> spans = new ArrayList<>();
        int cursor = 0;
        boolean matchedSection = false;
        for (DocumentSection section : sections) {
            String text = section.content();
            if (text == null || text.isBlank()) {
                continue;
            }

            int match = fullText.indexOf(text, cursor);
            if (match < 0 || containsNonWhitespace(fullText, cursor, match)) {
                return Optional.empty();
            }
            spans.add(new ResolvedSectionSpan(section, match, match + text.length()));
            cursor = match + text.length();
            matchedSection = true;
        }
        if (!matchedSection) {
            return Optional.of(List.of());
        }
        if (containsNonWhitespace(fullText, cursor, fullText.length())) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(spans));
    }

    private boolean containsNonWhitespace(String text, int start, int end) {
        for (int index = Math.max(0, start); index < Math.max(start, end); index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    record ResolvedSectionSpan(DocumentSection section, int start, int end) {}
}
