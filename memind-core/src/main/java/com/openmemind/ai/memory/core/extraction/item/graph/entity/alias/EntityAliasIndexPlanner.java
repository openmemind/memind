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
package com.openmemind.ai.memory.core.extraction.item.graph.entity.alias;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.resolve.EntityResolutionSource;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.ResolvedGraphBatch;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Plans deterministic alias-index upserts from resolved mentions.
 */
public final class EntityAliasIndexPlanner {

    public List<GraphEntityAlias> plan(MemoryId memoryId, ResolvedGraphBatch batch) {
        Map<String, GraphEntityType> typesByEntityKey =
                batch.entities().stream()
                        .collect(
                                Collectors.toMap(
                                        GraphEntity::entityKey,
                                        GraphEntity::entityType,
                                        (left, right) -> left,
                                        LinkedHashMap::new));

        Map<AliasIdentity, AliasAccumulator> planned = new LinkedHashMap<>();
        for (ItemEntityMention mention : batch.mentions()) {
            EntityResolutionSource source =
                    resolutionSource(mention.metadata().get("resolutionSource"));
            if (!eligibleForPersistence(source, mention.entityKey())) {
                continue;
            }

            GraphEntityType entityType = typesByEntityKey.get(mention.entityKey());
            String normalizedName = normalizedName(mention.metadata().get("normalizedName"));
            if (entityType == null
                    || entityType == GraphEntityType.SPECIAL
                    || normalizedName == null) {
                continue;
            }

            planned.computeIfAbsent(
                            new AliasIdentity(mention.entityKey(), entityType, normalizedName),
                            ignored ->
                                    new AliasAccumulator(
                                            mention.entityKey(),
                                            entityType,
                                            normalizedName,
                                            mention.createdAt(),
                                            mention.createdAt()))
                    .record(mention.createdAt());
        }
        return planned.values().stream()
                .map(accumulator -> accumulator.toAlias(memoryId.toIdentifier()))
                .toList();
    }

    private static boolean eligibleForPersistence(EntityResolutionSource source, String entityKey) {
        if (entityKey == null || entityKey.startsWith("special:")) {
            return false;
        }
        return source == EntityResolutionSource.EXPLICIT_ALIAS_EVIDENCE_HIT
                || source == EntityResolutionSource.HISTORICAL_ALIAS_HIT
                || source == EntityResolutionSource.SAFE_VARIANT_HIT
                || source == EntityResolutionSource.USER_DICTIONARY_HIT;
    }

    private static EntityResolutionSource resolutionSource(Object rawValue) {
        if (rawValue == null) {
            return EntityResolutionSource.EXACT_CANONICAL_HIT;
        }
        try {
            return EntityResolutionSource.valueOf(
                    String.valueOf(rawValue).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return EntityResolutionSource.EXACT_CANONICAL_HIT;
        }
    }

    private static String normalizedName(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = String.valueOf(rawValue);
        return value.isBlank() ? null : value;
    }

    private static Instant laterOf(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null || left.isAfter(right)) {
            return left;
        }
        return right;
    }

    private static Instant earlierOf(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null || left.isBefore(right)) {
            return left;
        }
        return right;
    }

    private record AliasIdentity(
            String entityKey, GraphEntityType entityType, String normalizedAlias) {}

    private static final class AliasAccumulator {

        private final String entityKey;
        private final GraphEntityType entityType;
        private final String normalizedAlias;
        private Instant createdAt;
        private Instant updatedAt;
        private int evidenceCount;

        private AliasAccumulator(
                String entityKey,
                GraphEntityType entityType,
                String normalizedAlias,
                Instant createdAt,
                Instant updatedAt) {
            this.entityKey = entityKey;
            this.entityType = entityType;
            this.normalizedAlias = normalizedAlias;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        private void record(Instant mentionCreatedAt) {
            evidenceCount++;
            createdAt = earlierOf(createdAt, mentionCreatedAt);
            updatedAt = laterOf(updatedAt, mentionCreatedAt);
        }

        private GraphEntityAlias toAlias(String memoryId) {
            return new GraphEntityAlias(
                    memoryId,
                    entityKey,
                    entityType,
                    normalizedAlias,
                    evidenceCount,
                    Map.of("source", "item_extraction"),
                    createdAt,
                    updatedAt);
        }
    }
}
