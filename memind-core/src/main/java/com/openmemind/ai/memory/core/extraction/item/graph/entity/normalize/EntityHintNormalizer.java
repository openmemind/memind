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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Normalizes extracted entity hints into bounded mention candidates plus diagnostics.
 */
public final class EntityHintNormalizer {

    private final EntityTypeMapper typeMapper;
    private final EntityNameNormalizer nameNormalizer;
    private final SpecialEntityKeyResolver specialEntityKeyResolver;
    private final EntityNoiseFilter noiseFilter;
    private final EntityKeyCanonicalizer entityKeyCanonicalizer;

    public EntityHintNormalizer() {
        this(
                new LocalizedEntityTypeMapper(),
                new LanguageAwareEntityNameNormalizer(),
                new SpecialEntityKeyResolver(),
                new LanguageAwareEntityNoiseFilter(),
                new EntityKeyCanonicalizer());
    }

    EntityHintNormalizer(
            EntityTypeMapper typeMapper,
            EntityNameNormalizer nameNormalizer,
            SpecialEntityKeyResolver specialEntityKeyResolver,
            EntityNoiseFilter noiseFilter,
            EntityKeyCanonicalizer entityKeyCanonicalizer) {
        this.typeMapper = Objects.requireNonNull(typeMapper, "typeMapper");
        this.nameNormalizer = Objects.requireNonNull(nameNormalizer, "nameNormalizer");
        this.specialEntityKeyResolver =
                Objects.requireNonNull(specialEntityKeyResolver, "specialEntityKeyResolver");
        this.noiseFilter = Objects.requireNonNull(noiseFilter, "noiseFilter");
        this.entityKeyCanonicalizer =
                Objects.requireNonNull(entityKeyCanonicalizer, "entityKeyCanonicalizer");
    }

    public EntityHintNormalizationResult normalize(
            MemoryId memoryId,
            List<MemoryItem> persistedItems,
            List<ExtractedMemoryEntry> sourceEntries,
            ItemGraphOptions options) {
        if (options == null
                || !options.enabled()
                || persistedItems == null
                || persistedItems.isEmpty()
                || sourceEntries == null
                || sourceEntries.isEmpty()) {
            return EntityHintNormalizationResult.empty();
        }

        int entryCount = Math.min(persistedItems.size(), sourceEntries.size());
        List<NormalizedEntityMentionCandidate> candidates = new ArrayList<>();
        DiagnosticsAccumulator diagnostics = new DiagnosticsAccumulator();

        for (int index = 0; index < entryCount; index++) {
            collectEntityMentions(
                    memoryId,
                    persistedItems.get(index),
                    sourceEntries.get(index),
                    options,
                    candidates,
                    diagnostics);
        }

        return new EntityHintNormalizationResult(
                List.copyOf(candidates), diagnostics.toDiagnostics());
    }

    private void collectEntityMentions(
            MemoryId memoryId,
            MemoryItem item,
            ExtractedMemoryEntry entry,
            ItemGraphOptions options,
            List<NormalizedEntityMentionCandidate> candidates,
            DiagnosticsAccumulator diagnostics) {
        List<ExtractedGraphHints.ExtractedEntityHint> retained =
                entry.graphHints().entities().stream()
                        .filter(Objects::nonNull)
                        .filter(hint -> hint.name() != null && !hint.name().isBlank())
                        .sorted(
                                Comparator.comparing(
                                                ExtractedGraphHints.ExtractedEntityHint::salience,
                                                Comparator.nullsLast(Comparator.reverseOrder()))
                                        .thenComparing(
                                                ExtractedGraphHints.ExtractedEntityHint::name,
                                                String.CASE_INSENSITIVE_ORDER))
                        .toList();

        Map<String, NormalizedEntity> normalized = new LinkedHashMap<>();
        for (ExtractedGraphHints.ExtractedEntityHint hint : retained) {
            if (normalized.size() >= options.maxEntitiesPerItem()) {
                break;
            }
            GraphEntityType entityType = typeMapper.map(hint.entityType());
            if (entityType == GraphEntityType.OTHER
                    && hint.entityType() != null
                    && !hint.entityType().isBlank()) {
                diagnostics.recordTypeFallback(hint.entityType());
            }

            String displayName = nameNormalizer.normalizeDisplay(hint.name());
            String normalizedName = nameNormalizer.normalizeCanonical(hint.name());
            Optional<String> reservedSpecialKey =
                    specialEntityKeyResolver.mapReservedKey(normalizedName, entityType);
            Optional<EntityDropReason> dropReason =
                    noiseFilter.dropReason(normalizedName, entityType);
            // Invariant: once a reserved special anchor is mapped under SPECIAL, this
            // second-level guard ensures the mention still survives even if the mapper and
            // filter contracts drift out of sync later.
            if (reservedSpecialKey.isEmpty() && dropReason.isPresent()) {
                diagnostics.recordDrop(dropReason.get());
                continue;
            }

            String entityKey =
                    entityKeyCanonicalizer.canonicalize(
                            entityType, normalizedName, reservedSpecialKey);
            if (entityKey.isBlank() || normalized.containsKey(entityKey)) {
                continue;
            }
            normalized.put(
                    entityKey,
                    new NormalizedEntity(
                            entityKey,
                            displayName,
                            entityType,
                            clampScore(hint.salience()),
                            hint.name(),
                            hint.entityType() == null ? "" : hint.entityType(),
                            normalizedName,
                            hint.aliasObservations()));
        }

        Instant createdAt = resolveGraphTimestamp(item);
        String memoryKey = memoryId.toIdentifier();
        normalized
                .values()
                .forEach(
                        normalizedEntity ->
                                candidates.add(
                                        new NormalizedEntityMentionCandidate(
                                                item.id(),
                                                memoryKey,
                                                normalizedEntity.rawName(),
                                                normalizedEntity.rawTypeLabel(),
                                                normalizedEntity.normalizedName(),
                                                normalizedEntity.displayName(),
                                                normalizedEntity.entityType(),
                                                normalizedEntity.entityKey(),
                                                normalizedEntity.salience(),
                                                normalizedEntity.aliasObservations(),
                                                createdAt)));
    }

    private static Float clampScore(Float score) {
        if (score == null) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, score));
    }

    private static Instant resolveGraphTimestamp(MemoryItem item) {
        return firstNonNull(
                item.createdAt(),
                item.observedAt(),
                item.occurredAt(),
                item.occurredStart(),
                Instant.EPOCH);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record NormalizedEntity(
            String entityKey,
            String displayName,
            GraphEntityType entityType,
            Float salience,
            String rawName,
            String rawTypeLabel,
            String normalizedName,
            List<EntityAliasObservation> aliasObservations) {}

    public record EntityHintNormalizationResult(
            List<NormalizedEntityMentionCandidate> candidates,
            GraphEntityNormalizationDiagnostics diagnostics) {

        public EntityHintNormalizationResult {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            diagnostics =
                    diagnostics == null ? GraphEntityNormalizationDiagnostics.empty() : diagnostics;
        }

        public static EntityHintNormalizationResult empty() {
            return new EntityHintNormalizationResult(
                    List.of(), GraphEntityNormalizationDiagnostics.empty());
        }
    }

    private static final class DiagnosticsAccumulator {

        private static final int MAX_TRACKED_UNRESOLVED_TYPE_LABELS = 16;

        private int typeFallbackToOtherCount;
        private final Map<EntityDropReason, Integer> droppedEntityCounts =
                new EnumMap<>(EntityDropReason.class);
        private final BoundedHeavyHitterCounter unresolvedTypeLabels =
                new BoundedHeavyHitterCounter(MAX_TRACKED_UNRESOLVED_TYPE_LABELS);

        private void recordTypeFallback(String rawTypeLabel) {
            typeFallbackToOtherCount++;
            if (rawTypeLabel != null && !rawTypeLabel.isBlank()) {
                unresolvedTypeLabels.add(rawTypeLabel.strip());
            }
        }

        private void recordDrop(EntityDropReason reason) {
            droppedEntityCounts.merge(reason, 1, Integer::sum);
        }

        private GraphEntityNormalizationDiagnostics toDiagnostics() {
            return new GraphEntityNormalizationDiagnostics(
                    typeFallbackToOtherCount,
                    Map.copyOf(droppedEntityCounts),
                    unresolvedTypeLabels.snapshot());
        }
    }

    private static final class BoundedHeavyHitterCounter {

        private final int capacity;
        private final Map<String, Integer> estimatedCounts = new LinkedHashMap<>();

        private BoundedHeavyHitterCounter(int capacity) {
            this.capacity = capacity;
        }

        private void add(String label) {
            Integer current = estimatedCounts.get(label);
            if (current != null) {
                estimatedCounts.put(label, current + 1);
                return;
            }
            if (estimatedCounts.size() < capacity) {
                estimatedCounts.put(label, 1);
                return;
            }

            Map.Entry<String, Integer> replacement =
                    estimatedCounts.entrySet().stream()
                            .min(
                                    Comparator.<Map.Entry<String, Integer>>comparingInt(
                                                    Map.Entry::getValue)
                                            .thenComparing(Map.Entry::getKey))
                            .orElseThrow();
            int minCount = replacement.getValue();
            estimatedCounts.remove(replacement.getKey());
            estimatedCounts.put(label, minCount + 1);
        }

        private Map<String, Integer> snapshot() {
            return estimatedCounts.entrySet().stream()
                    .sorted(
                            Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                                    .reversed()
                                    .thenComparing(Map.Entry::getKey))
                    .collect(
                            Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (left, right) -> left,
                                    LinkedHashMap::new));
        }
    }
}
