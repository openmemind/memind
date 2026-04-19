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
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic merger for Stage 2 alias aggregates stored in {@link
 * com.openmemind.ai.memory.core.store.graph.GraphEntity#metadata()}.
 */
public final class EntityAliasMetadataMerger {

    private static final int MAX_TOP_ALIASES = 8;

    public static Map<String, Object> merge(
            Map<String, Object> existingMetadata,
            List<EntityAliasObservation> newObservations,
            Instant observedAt) {
        Map<String, Object> merged =
                existingMetadata == null || existingMetadata.isEmpty()
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(existingMetadata);
        List<EntityAliasObservation> observations =
                newObservations == null ? List.of() : List.copyOf(newObservations);
        if (observations.isEmpty()) {
            return merged.isEmpty() ? Map.of() : Map.copyOf(merged);
        }

        Instant effectiveObservedAt = observedAt == null ? Instant.EPOCH : observedAt;
        merged.put("aliasEvidenceCount", existingCount(existingMetadata) + observations.size());
        merged.put("firstAliasSeenAt", firstSeen(existingMetadata, effectiveObservedAt).toString());
        merged.put("lastAliasSeenAt", lastSeen(existingMetadata, effectiveObservedAt).toString());

        List<String> aliasClasses = mergedAliasClasses(existingMetadata, observations);
        if (!aliasClasses.isEmpty()) {
            merged.put("aliasClasses", aliasClasses);
        }

        List<String> topAliases = mergedTopAliases(existingMetadata, observations);
        if (!topAliases.isEmpty()) {
            merged.put("topAliases", topAliases);
        }

        String scriptSummary = mergedScriptSummary(existingMetadata, observations);
        if (!scriptSummary.isBlank()) {
            merged.put("scriptSummary", scriptSummary);
        }

        return Map.copyOf(merged);
    }

    private static int existingCount(Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get("aliasEvidenceCount");
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static Instant firstSeen(Map<String, Object> metadata, Instant observedAt) {
        return parseInstant(metadata == null ? null : metadata.get("firstAliasSeenAt"))
                .filter(existing -> existing.isBefore(observedAt))
                .orElse(observedAt);
    }

    private static Instant lastSeen(Map<String, Object> metadata, Instant observedAt) {
        return parseInstant(metadata == null ? null : metadata.get("lastAliasSeenAt"))
                .filter(existing -> existing.isAfter(observedAt))
                .orElse(observedAt);
    }

    private static List<String> mergedAliasClasses(
            Map<String, Object> metadata, List<EntityAliasObservation> observations) {
        LinkedHashSet<String> aliasClasses =
                new LinkedHashSet<>(stringList(metadata, "aliasClasses"));
        observations.stream()
                .map(EntityAliasObservation::aliasClass)
                .filter(java.util.Objects::nonNull)
                .map(EntityAliasClass::wireValue)
                .forEach(aliasClasses::add);
        return List.copyOf(aliasClasses);
    }

    private static List<String> mergedTopAliases(
            Map<String, Object> metadata, List<EntityAliasObservation> observations) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (String existingAlias : stringList(metadata, "topAliases")) {
            if (aliases.size() >= MAX_TOP_ALIASES) {
                break;
            }
            if (!existingAlias.isBlank()) {
                aliases.add(existingAlias);
            }
        }

        List<String> newAliases =
                observations.stream()
                        .map(EntityAliasObservation::aliasSurface)
                        .filter(alias -> alias != null && !alias.isBlank())
                        .distinct()
                        .sorted()
                        .toList();
        for (String newAlias : newAliases) {
            if (aliases.size() >= MAX_TOP_ALIASES) {
                break;
            }
            aliases.add(newAlias);
        }
        return List.copyOf(aliases);
    }

    private static String mergedScriptSummary(
            Map<String, Object> metadata, List<EntityAliasObservation> observations) {
        LinkedHashSet<String> scripts = new LinkedHashSet<>(scriptList(metadata));
        observations.stream()
                .map(EntityAliasObservation::aliasSurface)
                .filter(alias -> alias != null && !alias.isBlank())
                .forEach(alias -> scripts.addAll(scriptTags(alias)));
        return String.join(",", scripts.stream().sorted().toList());
    }

    private static List<String> stringList(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return List.of();
        }
        Object value = metadata.get(key);
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : values) {
            if (entry == null) {
                continue;
            }
            String stringValue = String.valueOf(entry).trim();
            if (!stringValue.isBlank()) {
                result.add(stringValue);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> scriptList(Map<String, Object> metadata) {
        if (metadata == null) {
            return List.of();
        }
        Object value = metadata.get("scriptSummary");
        if (!(value instanceof String summary) || summary.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(summary.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static Set<String> scriptTags(String alias) {
        Set<String> scripts = new LinkedHashSet<>();
        alias.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .forEach(
                        codePoint -> {
                            if (!Character.isLetter(codePoint)) {
                                return;
                            }
                            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
                            if (script == Character.UnicodeScript.HAN) {
                                scripts.add("han");
                            } else if ((codePoint >= 'a' && codePoint <= 'z')
                                    || (codePoint >= 'A' && codePoint <= 'Z')) {
                                scripts.add("latin");
                            } else {
                                scripts.add("other");
                            }
                        });
        if (scripts.isEmpty()) {
            scripts.add("other");
        }
        return scripts;
    }

    private static Optional<Instant> parseInstant(Object value) {
        if (!(value instanceof String instantValue) || instantValue.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(instantValue));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private EntityAliasMetadataMerger() {}
}
