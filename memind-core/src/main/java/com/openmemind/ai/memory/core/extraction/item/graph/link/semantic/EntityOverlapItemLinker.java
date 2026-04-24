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
package com.openmemind.ai.memory.core.extraction.item.graph.link.semantic;

import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.relation.semantic.SemanticEvidenceSource;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Creates deterministic intra-batch semantic item links from shared graph entities.
 */
public final class EntityOverlapItemLinker {

    private static final Comparator<ItemLink> LINK_ORDER =
            Comparator.comparing(ItemLink::sourceItemId)
                    .thenComparing(Comparator.comparing(ItemLink::strength).reversed())
                    .thenComparing(ItemLink::targetItemId);

    public EntityOverlapLinkingPlan plan(
            MemoryId memoryId,
            List<MemoryItem> items,
            List<ItemEntityMention> mentions,
            ItemGraphOptions options) {
        if (memoryId == null
                || options == null
                || !options.enabled()
                || !options.entityOverlapSemanticLinksEnabled()
                || items == null
                || items.isEmpty()
                || mentions == null
                || mentions.isEmpty()) {
            return EntityOverlapLinkingPlan.empty();
        }

        Map<Long, MemoryItem> batchItems = new LinkedHashMap<>();
        for (MemoryItem item : items) {
            if (item != null && item.id() != null) {
                batchItems.put(item.id(), item);
            }
        }
        if (batchItems.isEmpty()) {
            return EntityOverlapLinkingPlan.empty();
        }

        Map<String, Set<Long>> itemIdsByEntity = new LinkedHashMap<>();
        for (ItemEntityMention mention : mentions) {
            if (mention == null || !batchItems.containsKey(mention.itemId())) {
                continue;
            }
            String entityKey = normalizeEntityKey(mention.entityKey());
            if (entityKey == null || entityKey.startsWith("special:")) {
                continue;
            }
            float confidence = mention.confidence() == null ? 1.0f : mention.confidence();
            if (confidence < options.entityOverlapMinMentionConfidence()) {
                continue;
            }
            itemIdsByEntity
                    .computeIfAbsent(entityKey, ignored -> new TreeSet<>())
                    .add(mention.itemId());
        }

        Map<ItemPair, Integer> sharedEntityCounts = new HashMap<>();
        int skippedFanoutEntityCount = 0;
        for (Set<Long> entityItemIds : itemIdsByEntity.values()) {
            if (entityItemIds.size() > options.maxItemsPerEntityForSemanticLink()) {
                skippedFanoutEntityCount++;
                continue;
            }
            var ids = new ArrayList<>(entityItemIds);
            for (int leftIndex = 0; leftIndex < ids.size(); leftIndex++) {
                for (int rightIndex = leftIndex + 1; rightIndex < ids.size(); rightIndex++) {
                    sharedEntityCounts.merge(
                            new ItemPair(ids.get(leftIndex), ids.get(rightIndex)), 1, Integer::sum);
                }
            }
        }

        List<ItemLink> candidates = new ArrayList<>();
        int duplicateSuppressedCount = 0;
        Set<ItemPair> emittedPairs = new HashSet<>();
        for (var entry : sharedEntityCounts.entrySet()) {
            int sharedCount = entry.getValue();
            if (sharedCount < options.minSharedEntitiesForSemanticLink()) {
                continue;
            }
            ItemPair pair = entry.getKey();
            if (!emittedPairs.add(pair)) {
                duplicateSuppressedCount++;
                continue;
            }
            MemoryItem sourceItem = batchItems.get(pair.sourceItemId());
            MemoryItem targetItem = batchItems.get(pair.targetItemId());
            candidates.add(
                    new ItemLink(
                            memoryId.toIdentifier(),
                            pair.sourceItemId(),
                            pair.targetItemId(),
                            ItemLinkType.SEMANTIC,
                            null,
                            SemanticEvidenceSource.ENTITY_OVERLAP.code(),
                            Math.min(1.0d, 0.5d + (0.1d * sharedCount)),
                            Map.of("sharedEntityCount", sharedCount),
                            resolveCreatedAt(sourceItem, targetItem)));
        }

        List<ItemLink> links = limitPerSource(candidates, options.maxEntityOverlapLinksPerItem());
        return new EntityOverlapLinkingPlan(
                links,
                new EntityOverlapLinkingStats(
                        sharedEntityCounts.size(),
                        links.size(),
                        skippedFanoutEntityCount,
                        duplicateSuppressedCount));
    }

    private static List<ItemLink> limitPerSource(List<ItemLink> candidates, int maxPerSource) {
        Map<Long, List<ItemLink>> bySource = new LinkedHashMap<>();
        candidates.stream()
                .sorted(LINK_ORDER)
                .forEach(
                        link ->
                                bySource.computeIfAbsent(
                                                link.sourceItemId(), ignored -> new ArrayList<>())
                                        .add(link));
        return bySource.values().stream()
                .flatMap(links -> links.stream().limit(maxPerSource))
                .sorted(LINK_ORDER)
                .toList();
    }

    private static Instant resolveCreatedAt(MemoryItem sourceItem, MemoryItem targetItem) {
        if (sourceItem != null && sourceItem.createdAt() != null) {
            return sourceItem.createdAt();
        }
        return targetItem == null ? null : targetItem.createdAt();
    }

    private static String normalizeEntityKey(String entityKey) {
        if (entityKey == null) {
            return null;
        }
        String normalized = entityKey.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private record ItemPair(Long sourceItemId, Long targetItemId) {
        private ItemPair {
            Objects.requireNonNull(sourceItemId, "sourceItemId");
            Objects.requireNonNull(targetItemId, "targetItemId");
            if (sourceItemId.equals(targetItemId)) {
                throw new IllegalArgumentException("source and target item ids must differ");
            }
            if (sourceItemId > targetItemId) {
                Long originalSource = sourceItemId;
                sourceItemId = targetItemId;
                targetItemId = originalSource;
            }
        }
    }

    public record EntityOverlapLinkingPlan(List<ItemLink> links, EntityOverlapLinkingStats stats) {

        public EntityOverlapLinkingPlan {
            links = links == null ? List.of() : List.copyOf(links);
            stats = stats == null ? EntityOverlapLinkingStats.empty() : stats;
        }

        public static EntityOverlapLinkingPlan empty() {
            return new EntityOverlapLinkingPlan(List.of(), EntityOverlapLinkingStats.empty());
        }
    }

    public record EntityOverlapLinkingStats(
            int candidatePairCount,
            int createdLinkCount,
            int skippedFanoutEntityCount,
            int duplicateSuppressedCount) {

        public static EntityOverlapLinkingStats empty() {
            return new EntityOverlapLinkingStats(0, 0, 0, 0);
        }
    }
}
