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
package com.openmemind.ai.memory.core.store.graph;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Typed directed item-to-item relation.
 */
public record ItemLink(
        String memoryId,
        Long sourceItemId,
        Long targetItemId,
        ItemLinkType linkType,
        String relationCode,
        String evidenceSource,
        Double strength,
        Map<String, Object> metadata,
        Instant createdAt) {

    private static final Set<String> TEMPORAL_RELATION_CODES =
            Set.of("before", "overlap", "nearby");
    private static final Set<String> CAUSAL_RELATION_CODES =
            Set.of("caused_by", "enabled_by", "motivated_by");
    private static final Set<String> SEMANTIC_EVIDENCE_SOURCES =
            Set.of(
                    "vector_search",
                    "same_batch_vector",
                    "vector_search_fallback",
                    "entity_overlap");

    public ItemLink {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(sourceItemId, "sourceItemId");
        Objects.requireNonNull(targetItemId, "targetItemId");
        Objects.requireNonNull(linkType, "linkType");
        if (Objects.equals(sourceItemId, targetItemId)) {
            throw new IllegalArgumentException("source and target item ids must differ");
        }

        relationCode =
                normalizeOptionalValue(
                        relationCode != null
                                ? relationCode
                                : metadataValue(metadata, "relationType"));
        evidenceSource =
                normalizeOptionalValue(
                        evidenceSource != null
                                ? evidenceSource
                                : metadataValue(metadata, "source"));
        strength = normalizeStrength(strength);

        validateFamilyFields(linkType, relationCode, evidenceSource);

        var normalizedMetadata = new LinkedHashMap<String, Object>();
        if (metadata != null) {
            normalizedMetadata.putAll(metadata);
        }
        if (relationCode != null) {
            normalizedMetadata.put("relationType", relationCode);
        }
        if (evidenceSource != null) {
            normalizedMetadata.put("source", evidenceSource);
        }
        metadata = Map.copyOf(normalizedMetadata);
    }

    public ItemLink(
            String memoryId,
            Long sourceItemId,
            Long targetItemId,
            ItemLinkType linkType,
            Double strength,
            Map<String, Object> metadata,
            Instant createdAt) {
        this(
                memoryId,
                sourceItemId,
                targetItemId,
                linkType,
                null,
                null,
                strength,
                metadata,
                createdAt);
    }

    private static void validateFamilyFields(
            ItemLinkType linkType, String relationCode, String evidenceSource) {
        switch (linkType) {
            case SEMANTIC -> {
                if (relationCode != null) {
                    throw new IllegalArgumentException(
                            "semantic item link must not carry relationCode");
                }
                if (evidenceSource != null && !SEMANTIC_EVIDENCE_SOURCES.contains(evidenceSource)) {
                    throw new IllegalArgumentException(
                            "unsupported semantic evidence source: " + evidenceSource);
                }
            }
            case TEMPORAL -> {
                if (evidenceSource != null) {
                    throw new IllegalArgumentException(
                            "temporal item link must not carry semantic evidenceSource");
                }
                if (relationCode == null || !TEMPORAL_RELATION_CODES.contains(relationCode)) {
                    throw new IllegalArgumentException(
                            "temporal item link requires supported relationCode");
                }
            }
            case CAUSAL -> {
                if (evidenceSource != null) {
                    throw new IllegalArgumentException(
                            "causal item link must not carry semantic evidenceSource");
                }
                if (relationCode == null || !CAUSAL_RELATION_CODES.contains(relationCode)) {
                    throw new IllegalArgumentException(
                            "causal item link requires supported relationCode");
                }
            }
        }
    }

    private static Double normalizeStrength(Double strength) {
        double resolved = strength == null ? 1.0d : strength;
        return Math.max(0.0d, Math.min(1.0d, resolved));
    }

    private static String metadataValue(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
