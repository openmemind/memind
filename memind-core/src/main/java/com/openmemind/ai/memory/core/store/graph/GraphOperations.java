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

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Optional graph storage domain for item graph primitives.
 */
public interface GraphOperations {

    default void applyGraphWritePlan(
            MemoryId memoryId, ExtractionBatchId extractionBatchId, ItemGraphWritePlan writePlan) {
        throw new UnsupportedOperationException("graph write-plan apply not supported");
    }

    default CommittedGraphView previewPromotedBatch(
            MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        throw new UnsupportedOperationException("graph batch preview not supported");
    }

    default void installCommittedBatch(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            CommittedGraphView committedGraphView) {
        throw new UnsupportedOperationException("graph batch install not supported");
    }

    default void discardPendingBatch(MemoryId memoryId, ExtractionBatchId extractionBatchId) {}

    default boolean supportsTransactionalBatchCommit() {
        return false;
    }

    void upsertEntities(MemoryId memoryId, List<GraphEntity> entities);

    void upsertItemEntityMentions(MemoryId memoryId, List<ItemEntityMention> mentions);

    void rebuildEntityCooccurrences(MemoryId memoryId, Collection<String> entityKeys);

    void upsertItemLinks(MemoryId memoryId, List<ItemLink> links);

    void upsertEntityAliases(MemoryId memoryId, List<GraphEntityAlias> aliases);

    List<GraphEntity> listEntities(MemoryId memoryId);

    default List<GraphEntityAlias> listEntityAliases(MemoryId memoryId) {
        return List.of();
    }

    default List<GraphEntityAlias> listEntityAliasesByNormalizedAlias(
            MemoryId memoryId, GraphEntityType entityType, String normalizedAlias) {
        if (entityType == null || normalizedAlias == null || normalizedAlias.isBlank()) {
            return List.of();
        }
        return listEntityAliases(memoryId).stream()
                .filter(alias -> alias.entityType() == entityType)
                .filter(alias -> normalizedAlias.equals(alias.normalizedAlias()))
                .sorted(Comparator.comparing(GraphEntityAlias::entityKey))
                .toList();
    }

    default List<GraphEntity> listEntitiesByEntityKeys(
            MemoryId memoryId, Collection<String> entityKeys) {
        if (entityKeys == null || entityKeys.isEmpty()) {
            return List.of();
        }
        TreeSet<String> requested =
                entityKeys.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(TreeSet::new));
        if (requested.isEmpty()) {
            return List.of();
        }
        return listEntities(memoryId).stream()
                .filter(entity -> requested.contains(entity.entityKey()))
                .toList();
    }

    List<ItemEntityMention> listItemEntityMentions(MemoryId memoryId);

    default List<ItemEntityMention> listItemEntityMentions(
            MemoryId memoryId, Collection<Long> itemIds) {
        var itemIdSet = Set.copyOf(itemIds);
        return listItemEntityMentions(memoryId).stream()
                .filter(mention -> itemIdSet.contains(mention.itemId()))
                .toList();
    }

    default List<ItemEntityMention> listItemEntityMentionsByEntityKeys(
            MemoryId memoryId, Collection<String> entityKeys, int perEntityLimitPlusOne) {
        if (entityKeys == null || entityKeys.isEmpty() || perEntityLimitPlusOne <= 0) {
            return List.of();
        }
        var entityKeySet =
                entityKeys.stream()
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());
        if (entityKeySet.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> seen = new HashMap<>();
        return listItemEntityMentions(memoryId).stream()
                .filter(mention -> entityKeySet.contains(mention.entityKey()))
                .sorted(
                        Comparator.comparing(ItemEntityMention::entityKey)
                                .thenComparing(ItemEntityMention::itemId))
                .filter(
                        mention -> {
                            int next = seen.merge(mention.entityKey(), 1, Integer::sum);
                            return next <= perEntityLimitPlusOne;
                        })
                .toList();
    }

    List<EntityCooccurrence> listEntityCooccurrences(MemoryId memoryId);

    List<ItemLink> listItemLinks(MemoryId memoryId);

    default List<ItemLink> listItemLinks(
            MemoryId memoryId, Collection<Long> itemIds, Collection<ItemLinkType> linkTypes) {
        var itemIdSet = Set.copyOf(itemIds);
        var typeSet = Set.copyOf(linkTypes);
        return listItemLinks(memoryId).stream()
                .filter(
                        link ->
                                itemIdSet.contains(link.sourceItemId())
                                        && itemIdSet.contains(link.targetItemId()))
                .filter(link -> typeSet.isEmpty() || typeSet.contains(link.linkType()))
                .toList();
    }

    default List<ItemLink> listAdjacentItemLinks(
            MemoryId memoryId, Collection<Long> seedItemIds, Collection<ItemLinkType> linkTypes) {
        if (seedItemIds == null || seedItemIds.isEmpty()) {
            return List.of();
        }
        var seedIdSet =
                seedItemIds.stream()
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());
        if (seedIdSet.isEmpty()) {
            return List.of();
        }
        var typeSet =
                linkTypes == null
                        ? Set.<ItemLinkType>of()
                        : linkTypes.stream()
                                .filter(Objects::nonNull)
                                .collect(java.util.stream.Collectors.toSet());
        return listItemLinks(memoryId).stream()
                .filter(
                        link ->
                                seedIdSet.contains(link.sourceItemId())
                                        || seedIdSet.contains(link.targetItemId()))
                .filter(link -> typeSet.isEmpty() || typeSet.contains(link.linkType()))
                .toList();
    }
}
