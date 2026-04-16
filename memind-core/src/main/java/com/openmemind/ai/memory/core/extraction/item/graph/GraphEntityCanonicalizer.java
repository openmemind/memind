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
package com.openmemind.ai.memory.core.extraction.item.graph;

import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Canonicalizes extraction-side entity hints into conservative graph entity keys.
 */
public final class GraphEntityCanonicalizer {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Set<String> RESERVED_SPECIAL_NAMES = Set.of("self", "user", "assistant");

    public GraphEntityType normalizeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return GraphEntityType.OTHER;
        }
        return switch (rawType.trim().toLowerCase(Locale.ROOT)) {
            case "person" -> GraphEntityType.PERSON;
            case "organization" -> GraphEntityType.ORGANIZATION;
            case "place" -> GraphEntityType.PLACE;
            case "object" -> GraphEntityType.OBJECT;
            case "concept" -> GraphEntityType.CONCEPT;
            case "special" -> GraphEntityType.SPECIAL;
            default -> GraphEntityType.OTHER;
        };
    }

    public String canonicalize(String rawName, GraphEntityType type) {
        String normalizedName = normalizeName(rawName);
        if (normalizedName.isBlank()) {
            return "";
        }
        if (type == GraphEntityType.SPECIAL && RESERVED_SPECIAL_NAMES.contains(normalizedName)) {
            return "special:" + normalizedName;
        }
        return type.name().toLowerCase(Locale.ROOT) + ":" + normalizedName;
    }

    public String normalizeDisplayName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return WHITESPACE_PATTERN
                .matcher(Normalizer.normalize(rawName, Normalizer.Form.NFKC).trim())
                .replaceAll(" ");
    }

    public String normalizeName(String rawName) {
        return normalizeDisplayName(rawName).toLowerCase(Locale.ROOT);
    }
}
