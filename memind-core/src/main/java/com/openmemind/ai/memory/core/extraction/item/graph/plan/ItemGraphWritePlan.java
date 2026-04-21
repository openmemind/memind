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
package com.openmemind.ai.memory.core.extraction.item.graph.plan;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.causal.CausalItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticEvidenceSource;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticItemRelation;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.temporal.TemporalItemRelation;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical source-of-truth write plan for item graph commits.
 */
public record ItemGraphWritePlan(
        List<GraphEntity> entities,
        List<ItemEntityMention> mentions,
        List<GraphEntityAlias> aliases,
        List<SemanticItemRelation> semanticRelations,
        List<TemporalItemRelation> temporalRelations,
        List<CausalItemRelation> causalRelations,
        Set<String> affectedEntityKeys,
        Map<String, Object> diagnostics) {

    private static final Comparator<GraphEntity> ENTITY_ORDER =
            Comparator.comparing(GraphEntity::entityKey);
    private static final Comparator<ItemEntityMention> MENTION_ORDER =
            Comparator.comparing(ItemEntityMention::itemId)
                    .thenComparing(ItemEntityMention::entityKey);
    private static final Comparator<GraphEntityAlias> ALIAS_ORDER =
            Comparator.comparing(GraphEntityAlias::entityType)
                    .thenComparing(GraphEntityAlias::normalizedAlias)
                    .thenComparing(GraphEntityAlias::entityKey);
    private static final Comparator<SemanticItemRelation> SEMANTIC_ORDER =
            Comparator.comparingLong(SemanticItemRelation::sourceItemId)
                    .thenComparingLong(SemanticItemRelation::targetItemId);
    private static final Comparator<TemporalItemRelation> TEMPORAL_ORDER =
            Comparator.comparingLong(TemporalItemRelation::sourceItemId)
                    .thenComparingLong(TemporalItemRelation::targetItemId);
    private static final Comparator<CausalItemRelation> CAUSAL_ORDER =
            Comparator.comparingLong(CausalItemRelation::sourceItemId)
                    .thenComparingLong(CausalItemRelation::targetItemId);
    private static final Comparator<ItemLink> ITEM_LINK_ORDER =
            Comparator.comparing(ItemLink::sourceItemId)
                    .thenComparing(ItemLink::targetItemId)
                    .thenComparing(ItemLink::linkType);

    public ItemGraphWritePlan {
        entities = List.copyOf(defaultList(entities));
        mentions = List.copyOf(defaultList(mentions));
        aliases = List.copyOf(defaultList(aliases));
        semanticRelations = List.copyOf(defaultList(semanticRelations));
        temporalRelations = List.copyOf(defaultList(temporalRelations));
        causalRelations = List.copyOf(defaultList(causalRelations));
        affectedEntityKeys = immutableOrderedSet(affectedEntityKeys);
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ItemGraphWritePlan normalized() {
        return builder()
                .entities(entities)
                .mentions(mentions)
                .aliases(aliases)
                .semanticRelations(semanticRelations)
                .temporalRelations(temporalRelations)
                .causalRelations(causalRelations)
                .affectedEntityKeys(affectedEntityKeys)
                .diagnostics(diagnostics)
                .build();
    }

    public List<ItemLink> toPersistedLinks(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        String memoryKey = memoryId.toIdentifier();
        List<ItemLink> flattened = new ArrayList<>();
        semanticRelations.forEach(
                relation ->
                        flattened.add(
                                new ItemLink(
                                        memoryKey,
                                        relation.sourceItemId(),
                                        relation.targetItemId(),
                                        ItemLinkType.SEMANTIC,
                                        null,
                                        relation.evidenceSource().code(),
                                        relation.strength(),
                                        Map.of(),
                                        null)));
        temporalRelations.forEach(
                relation ->
                        flattened.add(
                                new ItemLink(
                                        memoryKey,
                                        relation.sourceItemId(),
                                        relation.targetItemId(),
                                        ItemLinkType.TEMPORAL,
                                        relation.relationCode().code(),
                                        null,
                                        relation.strength(),
                                        Map.of(),
                                        null)));
        causalRelations.forEach(
                relation ->
                        flattened.add(
                                new ItemLink(
                                        memoryKey,
                                        relation.sourceItemId(),
                                        relation.targetItemId(),
                                        ItemLinkType.CAUSAL,
                                        relation.relationCode().code(),
                                        null,
                                        relation.strength(),
                                        Map.of(),
                                        null)));
        return flattened.stream().sorted(ITEM_LINK_ORDER).toList();
    }

    private static <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static Set<String> immutableOrderedSet(Collection<String> values) {
        var normalized = new LinkedHashSet<String>();
        if (values != null) {
            values.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .sorted()
                    .forEach(normalized::add);
        }
        return Set.copyOf(normalized);
    }

    public static final class Builder {

        private List<GraphEntity> entities = List.of();
        private List<ItemEntityMention> mentions = List.of();
        private List<GraphEntityAlias> aliases = List.of();
        private List<SemanticItemRelation> semanticRelations = List.of();
        private List<TemporalItemRelation> temporalRelations = List.of();
        private List<CausalItemRelation> causalRelations = List.of();
        private Set<String> affectedEntityKeys = Set.of();
        private Map<String, Object> diagnostics = Map.of();

        public Builder entities(Collection<GraphEntity> entities) {
            this.entities = copyList(entities);
            return this;
        }

        public Builder mentions(Collection<ItemEntityMention> mentions) {
            this.mentions = copyList(mentions);
            return this;
        }

        public Builder aliases(Collection<GraphEntityAlias> aliases) {
            this.aliases = copyList(aliases);
            return this;
        }

        public Builder semanticRelations(Collection<SemanticItemRelation> semanticRelations) {
            this.semanticRelations = copyList(semanticRelations);
            return this;
        }

        public Builder temporalRelations(Collection<TemporalItemRelation> temporalRelations) {
            this.temporalRelations = copyList(temporalRelations);
            return this;
        }

        public Builder causalRelations(Collection<CausalItemRelation> causalRelations) {
            this.causalRelations = copyList(causalRelations);
            return this;
        }

        public Builder affectedEntityKeys(Collection<String> affectedEntityKeys) {
            this.affectedEntityKeys =
                    new LinkedHashSet<>(defaultList(copyList(affectedEntityKeys)));
            return this;
        }

        public Builder diagnostics(Map<String, Object> diagnostics) {
            this.diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
            return this;
        }

        public ItemGraphWritePlan build() {
            List<GraphEntity> normalizedEntities = normalizeEntities(entities);
            List<ItemEntityMention> normalizedMentions = normalizeMentions(mentions);
            List<GraphEntityAlias> normalizedAliases = normalizeAliases(aliases);
            List<SemanticItemRelation> normalizedSemanticRelations =
                    normalizeSemanticRelations(semanticRelations);
            List<TemporalItemRelation> normalizedTemporalRelations =
                    normalizeTemporalRelations(temporalRelations);
            List<CausalItemRelation> normalizedCausalRelations =
                    normalizeCausalRelations(causalRelations);
            Set<String> normalizedAffectedKeys =
                    collectAffectedEntityKeys(
                            affectedEntityKeys,
                            normalizedEntities,
                            normalizedMentions,
                            normalizedAliases);
            return new ItemGraphWritePlan(
                    normalizedEntities,
                    normalizedMentions,
                    normalizedAliases,
                    normalizedSemanticRelations,
                    normalizedTemporalRelations,
                    normalizedCausalRelations,
                    normalizedAffectedKeys,
                    diagnostics);
        }

        private static List<GraphEntity> normalizeEntities(Collection<GraphEntity> entities) {
            Map<String, GraphEntity> normalized = new LinkedHashMap<>();
            for (var entity : copyList(entities)) {
                normalized.merge(entity.entityKey(), entity, Builder::mergeEntity);
            }
            return normalized.values().stream().sorted(ENTITY_ORDER).toList();
        }

        private static List<ItemEntityMention> normalizeMentions(
                Collection<ItemEntityMention> mentions) {
            Map<MentionIdentity, ItemEntityMention> normalized = new LinkedHashMap<>();
            for (var mention : copyList(mentions)) {
                normalized.merge(
                        new MentionIdentity(mention.itemId(), mention.entityKey()),
                        mention,
                        Builder::mergeMention);
            }
            return normalized.values().stream().sorted(MENTION_ORDER).toList();
        }

        private static List<GraphEntityAlias> normalizeAliases(
                Collection<GraphEntityAlias> aliases) {
            Map<AliasIdentity, GraphEntityAlias> normalized = new LinkedHashMap<>();
            for (var alias : copyList(aliases)) {
                normalized.merge(
                        new AliasIdentity(
                                alias.entityKey(), alias.entityType(), alias.normalizedAlias()),
                        alias,
                        Builder::mergeAlias);
            }
            return normalized.values().stream().sorted(ALIAS_ORDER).toList();
        }

        private static List<SemanticItemRelation> normalizeSemanticRelations(
                Collection<SemanticItemRelation> relations) {
            Map<RelationIdentity, SemanticItemRelation> normalized = new LinkedHashMap<>();
            for (var relation : copyList(relations)) {
                normalized.merge(
                        new RelationIdentity(
                                relation.sourceItemId(),
                                relation.targetItemId(),
                                relation.linkType()),
                        relation,
                        Builder::mergeSemanticRelation);
            }
            return normalized.values().stream().sorted(SEMANTIC_ORDER).toList();
        }

        private static List<TemporalItemRelation> normalizeTemporalRelations(
                Collection<TemporalItemRelation> relations) {
            Map<RelationIdentity, TemporalItemRelation> normalized = new LinkedHashMap<>();
            for (var relation : copyList(relations)) {
                normalized.merge(
                        new RelationIdentity(
                                relation.sourceItemId(),
                                relation.targetItemId(),
                                relation.linkType()),
                        relation,
                        Builder::mergeTemporalRelation);
            }
            return normalized.values().stream().sorted(TEMPORAL_ORDER).toList();
        }

        private static List<CausalItemRelation> normalizeCausalRelations(
                Collection<CausalItemRelation> relations) {
            Map<RelationIdentity, CausalItemRelation> normalized = new LinkedHashMap<>();
            for (var relation : copyList(relations)) {
                normalized.merge(
                        new RelationIdentity(
                                relation.sourceItemId(),
                                relation.targetItemId(),
                                relation.linkType()),
                        relation,
                        Builder::mergeCausalRelation);
            }
            return normalized.values().stream().sorted(CAUSAL_ORDER).toList();
        }

        private static Set<String> collectAffectedEntityKeys(
                Collection<String> explicitKeys,
                List<GraphEntity> entities,
                List<ItemEntityMention> mentions,
                List<GraphEntityAlias> aliases) {
            var affected = new LinkedHashSet<String>();
            if (explicitKeys != null) {
                explicitKeys.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .forEach(affected::add);
            }
            entities.stream().map(GraphEntity::entityKey).forEach(affected::add);
            mentions.stream().map(ItemEntityMention::entityKey).forEach(affected::add);
            aliases.stream().map(GraphEntityAlias::entityKey).forEach(affected::add);
            return Set.copyOf(affected.stream().sorted().toList());
        }

        private static GraphEntity mergeEntity(GraphEntity left, GraphEntity right) {
            if (left.entityType() != right.entityType()) {
                throw new IllegalArgumentException(
                        "conflicting entity type for entity key " + left.entityKey());
            }
            GraphEntity preferred = preferEntity(left, right);
            return new GraphEntity(
                    left.entityKey(),
                    preferred.memoryId(),
                    preferred.displayName(),
                    preferred.entityType(),
                    mergeMetadata(left.metadata(), right.metadata()),
                    earliest(left.createdAt(), right.createdAt()),
                    latest(left.updatedAt(), right.updatedAt()));
        }

        private static GraphEntity preferEntity(GraphEntity left, GraphEntity right) {
            return Comparator.comparing(
                                            GraphEntity::updatedAt,
                                            Comparator.nullsLast(Comparator.naturalOrder()))
                                    .thenComparing(
                                            GraphEntity::createdAt,
                                            Comparator.nullsLast(Comparator.naturalOrder()))
                                    .thenComparing(GraphEntity::displayName)
                                    .compare(left, right)
                            >= 0
                    ? left
                    : right;
        }

        private static ItemEntityMention mergeMention(
                ItemEntityMention left, ItemEntityMention right) {
            return new ItemEntityMention(
                    left.memoryId(),
                    left.itemId(),
                    left.entityKey(),
                    Math.max(defaultScore(left.confidence()), defaultScore(right.confidence())),
                    mergeMetadata(left.metadata(), right.metadata()),
                    earliest(left.createdAt(), right.createdAt()));
        }

        private static GraphEntityAlias mergeAlias(GraphEntityAlias left, GraphEntityAlias right) {
            validateAliasType(left.entityKey(), left.entityType(), right.entityType());
            return new GraphEntityAlias(
                    left.memoryId(),
                    left.entityKey(),
                    left.entityType(),
                    left.normalizedAlias(),
                    left.evidenceCount() + right.evidenceCount(),
                    mergeMetadata(left.metadata(), right.metadata()),
                    earliest(left.createdAt(), right.createdAt()),
                    latest(left.updatedAt(), right.updatedAt()));
        }

        private static void validateAliasType(
                String entityKey, GraphEntityType leftType, GraphEntityType rightType) {
            if (leftType != rightType) {
                throw new IllegalArgumentException(
                        "conflicting alias entity type for entity key " + entityKey);
            }
        }

        private static SemanticItemRelation mergeSemanticRelation(
                SemanticItemRelation left, SemanticItemRelation right) {
            return new SemanticItemRelation(
                    left.sourceItemId(),
                    left.targetItemId(),
                    preferEvidence(left.evidenceSource(), right.evidenceSource()),
                    Math.max(left.strength(), right.strength()));
        }

        private static SemanticEvidenceSource preferEvidence(
                SemanticEvidenceSource left, SemanticEvidenceSource right) {
            return left.precedence() >= right.precedence() ? left : right;
        }

        private static TemporalItemRelation mergeTemporalRelation(
                TemporalItemRelation left, TemporalItemRelation right) {
            if (left.relationCode() != right.relationCode()) {
                throw new IllegalArgumentException(
                        "conflicting temporal relation for identity "
                                + left.sourceItemId()
                                + "->"
                                + left.targetItemId());
            }
            return new TemporalItemRelation(
                    left.sourceItemId(),
                    left.targetItemId(),
                    left.relationCode(),
                    Math.max(left.strength(), right.strength()));
        }

        private static CausalItemRelation mergeCausalRelation(
                CausalItemRelation left, CausalItemRelation right) {
            if (left.relationCode() != right.relationCode()) {
                throw new IllegalArgumentException(
                        "conflicting causal relation for identity "
                                + left.sourceItemId()
                                + "->"
                                + left.targetItemId());
            }
            return new CausalItemRelation(
                    left.sourceItemId(),
                    left.targetItemId(),
                    left.relationCode(),
                    Math.max(left.strength(), right.strength()));
        }

        private static Map<String, Object> mergeMetadata(
                Map<String, Object> left, Map<String, Object> right) {
            var merged = new LinkedHashMap<String, Object>();
            if (left != null) {
                merged.putAll(left);
            }
            if (right != null) {
                merged.putAll(right);
            }
            return Map.copyOf(merged);
        }

        private static Instant earliest(Instant left, Instant right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left.isBefore(right) ? left : right;
        }

        private static Instant latest(Instant left, Instant right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return left.isAfter(right) ? left : right;
        }

        private static float defaultScore(Float score) {
            return score == null ? 0.0f : score;
        }

        private static <T> List<T> copyList(Collection<T> values) {
            return values == null ? List.of() : List.copyOf(values);
        }

        private record MentionIdentity(Long itemId, String entityKey) {}

        private record AliasIdentity(
                String entityKey, GraphEntityType entityType, String normalizedAlias) {}

        private record RelationIdentity(
                Long sourceItemId, Long targetItemId, ItemLinkType linkType) {}
    }
}
