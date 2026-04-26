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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve;

import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasClass;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.NormalizedEntityMentionCandidate;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic safe-variant generator for conservative same-script resolution.
 */
public final class EntityVariantKeyGenerator {

    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{Punct}]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Map<String, String> ORG_SUFFIX_ABBREVIATIONS =
            Map.of(
                    "corporation", "corp",
                    "company", "co",
                    "incorporated", "inc",
                    "limited", "ltd");

    public List<VariantCandidate> generate(NormalizedEntityMentionCandidate candidate) {
        if (candidate == null) {
            return List.of();
        }

        String normalized = normalizeSpaces(candidate.normalizedName());
        if (normalized.isBlank()) {
            return List.of();
        }

        Map<String, VariantCandidate> variants = new LinkedHashMap<>();
        addVariant(
                variants,
                normalized.toLowerCase(Locale.ROOT),
                EntityAliasClass.CASE_ONLY,
                normalized);

        String punctuationToSpace =
                normalizeSpaces(PUNCTUATION.matcher(normalized).replaceAll(" "));
        addVariant(variants, punctuationToSpace, EntityAliasClass.PUNCTUATION, normalized);
        addVariant(
                variants,
                PUNCTUATION.matcher(normalized).replaceAll(""),
                EntityAliasClass.PUNCTUATION,
                normalized);
        addVariant(
                variants,
                normalizeSpaces(normalized).replace(" ", ""),
                EntityAliasClass.SPACING,
                normalized);

        if (candidate.entityType() == GraphEntityType.ORGANIZATION) {
            addVariant(
                    variants,
                    organizationSuffixVariant(normalized),
                    EntityAliasClass.ORG_SUFFIX,
                    normalized);
        }

        return List.copyOf(variants.values());
    }

    private static void addVariant(
            Map<String, VariantCandidate> variants,
            String normalizedName,
            EntityAliasClass aliasClass,
            String original) {
        String candidate = normalizeSpaces(normalizedName);
        if (candidate.isBlank() || candidate.equals(original)) {
            return;
        }
        variants.putIfAbsent(candidate, new VariantCandidate(candidate, aliasClass));
    }

    private static String organizationSuffixVariant(String normalized) {
        List<String> tokens = new ArrayList<>(List.of(normalizeSpaces(normalized).split(" ")));
        if (tokens.isEmpty()) {
            return "";
        }
        String last = tokens.getLast();
        String abbreviated = ORG_SUFFIX_ABBREVIATIONS.get(last);
        if (abbreviated == null) {
            return "";
        }
        tokens.set(tokens.size() - 1, abbreviated);
        return String.join(" ", tokens);
    }

    private static String normalizeSpaces(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE.matcher(value.trim()).replaceAll(" ");
    }

    public record VariantCandidate(String normalizedName, EntityAliasClass aliasClass) {}
}
