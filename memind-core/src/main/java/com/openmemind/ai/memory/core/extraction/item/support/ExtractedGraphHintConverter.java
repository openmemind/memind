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
package com.openmemind.ai.memory.core.extraction.item.support;

import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasClass;
import com.openmemind.ai.memory.core.extraction.item.graph.EntityAliasObservation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts structured item extraction graph fields into core graph hints.
 */
public final class ExtractedGraphHintConverter {

    private ExtractedGraphHintConverter() {}

    public static ExtractedGraphHints from(MemoryItemExtractionResponse.ExtractedItem item) {
        if (item == null) {
            return ExtractedGraphHints.empty();
        }
        return new ExtractedGraphHints(
                toEntityHints(item.entities()), toCausalHints(item.causalRelations()));
    }

    public static List<ExtractedGraphHints.ExtractedEntityHint> toEntityHints(
            List<MemoryItemExtractionResponse.ExtractedEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .filter(
                        entity ->
                                entity != null && entity.name() != null && !entity.name().isBlank())
                .map(
                        entity ->
                                new ExtractedGraphHints.ExtractedEntityHint(
                                        entity.name(),
                                        entity.entityType(),
                                        clampNullable(entity.salience()),
                                        toAliasObservations(entity.aliasObservations())))
                .toList();
    }

    public static List<ExtractedGraphHints.ExtractedCausalRelationHint> toCausalHints(
            List<MemoryItemExtractionResponse.ExtractedCausalRelation> causalRelations) {
        if (causalRelations == null || causalRelations.isEmpty()) {
            return List.of();
        }
        return causalRelations.stream()
                .filter(
                        relation ->
                                relation != null
                                        && relation.causeIndex() != null
                                        && relation.effectIndex() != null)
                .map(
                        relation ->
                                new ExtractedGraphHints.ExtractedCausalRelationHint(
                                        relation.causeIndex(),
                                        relation.effectIndex(),
                                        relation.relationType(),
                                        clampNullable(relation.strength())))
                .toList();
    }

    private static List<EntityAliasObservation> toAliasObservations(
            List<MemoryItemExtractionResponse.ExtractedAliasObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return List.of();
        }
        return observations.stream()
                .filter(Objects::nonNull)
                .map(
                        observation ->
                                EntityAliasClass.fromWireValue(observation.aliasClass())
                                        .map(
                                                aliasClass ->
                                                        new EntityAliasObservation(
                                                                observation.aliasSurface(),
                                                                aliasClass,
                                                                observation.evidenceSource(),
                                                                clampNullable(
                                                                        observation.confidence()))))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Float clampNullable(Float value) {
        return value == null ? null : Math.max(0.0f, Math.min(1.0f, value));
    }
}
