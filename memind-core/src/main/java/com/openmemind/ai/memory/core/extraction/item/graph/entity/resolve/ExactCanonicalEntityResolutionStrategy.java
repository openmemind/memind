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

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasClass;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityResolutionMode;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.EntityNormalizationVersions;
import com.openmemind.ai.memory.core.extraction.item.graph.entity.normalize.NormalizedEntityMentionCandidate;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.NormalizedGraphBatch;
import com.openmemind.ai.memory.core.extraction.item.graph.pipeline.model.ResolvedGraphBatch;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exact-path resolver that preserves Stage 1A canonical keys while emitting Stage 2 provenance.
 */
public final class ExactCanonicalEntityResolutionStrategy implements EntityResolutionStrategy {

    @Override
    public ResolvedGraphBatch resolve(
            MemoryId memoryId, NormalizedGraphBatch batch, ItemGraphOptions options) {
        Map<String, GraphEntity> entities = new LinkedHashMap<>();
        List<ItemEntityMention> mentions = new ArrayList<>();
        int observedAliasEvidenceCount =
                batch.candidates().stream()
                        .mapToInt(candidate -> candidate.aliasObservations().size())
                        .sum();

        for (NormalizedEntityMentionCandidate candidate : batch.candidates()) {
            GraphEntity existing = entities.get(candidate.preResolutionEntityKey());
            GraphEntity entity =
                    existing == null
                            ? buildEntity(memoryId, candidate)
                            : mergeEntity(memoryId, existing, candidate);
            entities.put(entity.entityKey(), entity);
            mentions.add(
                    new ItemEntityMention(
                            memoryId.toIdentifier(),
                            candidate.itemId(),
                            candidate.preResolutionEntityKey(),
                            candidate.salience(),
                            mentionMetadata(
                                    candidate,
                                    EntityResolutionMode.EXACT,
                                    EntityResolutionSource.EXACT_CANONICAL_HIT,
                                    null),
                            candidate.createdAt()));
        }

        return new ResolvedGraphBatch(
                List.copyOf(entities.values()),
                List.of(),
                List.copyOf(mentions),
                batch.itemLinks(),
                batch.diagnostics(),
                EntityResolutionDiagnostics.exactFallback(
                        batch.candidates().size(), observedAliasEvidenceCount));
    }

    private GraphEntity buildEntity(MemoryId memoryId, NormalizedEntityMentionCandidate candidate) {
        return new GraphEntity(
                candidate.preResolutionEntityKey(),
                memoryId.toIdentifier(),
                candidate.displayName(),
                candidate.entityType(),
                EntityAliasMetadataMerger.merge(
                        baseEntityMetadata(), candidate.aliasObservations(), candidate.createdAt()),
                candidate.createdAt(),
                candidate.createdAt());
    }

    private GraphEntity mergeEntity(
            MemoryId memoryId, GraphEntity existing, NormalizedEntityMentionCandidate candidate) {
        return new GraphEntity(
                existing.entityKey(),
                memoryId.toIdentifier(),
                existing.displayName(),
                existing.entityType(),
                EntityAliasMetadataMerger.merge(
                        existing.metadata(), candidate.aliasObservations(), candidate.createdAt()),
                existing.createdAt(),
                laterOf(existing.updatedAt(), candidate.createdAt()));
    }

    private static Map<String, Object> baseEntityMetadata() {
        return Map.of(
                "source",
                "item_extraction",
                "normalizationVersion",
                EntityNormalizationVersions.STAGE1A_V1);
    }

    private static Map<String, Object> mentionMetadata(
            NormalizedEntityMentionCandidate candidate,
            EntityResolutionMode resolutionMode,
            EntityResolutionSource source,
            EntityAliasClass aliasClass) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "item_extraction");
        metadata.put("rawName", candidate.rawName());
        metadata.put("rawTypeLabel", candidate.rawTypeLabel());
        metadata.put("normalizedName", candidate.normalizedName());
        metadata.put("normalizationVersion", EntityNormalizationVersions.STAGE1A_V1);
        metadata.put("resolutionMode", resolutionMode.name().toLowerCase(Locale.ROOT));
        metadata.put("resolutionSource", source.name().toLowerCase(Locale.ROOT));
        if (aliasClass != null) {
            metadata.put("resolvedViaAliasClass", aliasClass.wireValue());
        }
        return Map.copyOf(metadata);
    }

    private static java.time.Instant laterOf(java.time.Instant left, java.time.Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null || left.isAfter(right)) {
            return left;
        }
        return right;
    }
}
