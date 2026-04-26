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
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link GraphOperations}.
 */
public class InMemoryGraphOperations implements GraphOperations {

    private final Map<String, Map<String, GraphEntity>> entities = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ItemEntityMention>> mentions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ItemLink>> itemLinks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, GraphEntityAlias>> entityAliases =
            new ConcurrentHashMap<>();
    private final Map<String, Map<String, EntityCooccurrence>> entityCooccurrences =
            new ConcurrentHashMap<>();
    private final Map<String, Map<ExtractionBatchId, PendingGraphBatch>> pendingBatches =
            new ConcurrentHashMap<>();
    private final Map<String, Set<String>> aliasBatchReceipts = new ConcurrentHashMap<>();

    @Override
    public void applyGraphWritePlan(
            MemoryId memoryId, ExtractionBatchId extractionBatchId, ItemGraphWritePlan writePlan) {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(extractionBatchId, "extractionBatchId");
        Objects.requireNonNull(writePlan, "writePlan");
        pendingBatches
                .computeIfAbsent(memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>())
                .put(extractionBatchId, PendingGraphBatch.from(memoryId, writePlan));
    }

    @Override
    public CommittedGraphView previewPromotedBatch(
            MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        String memoryKey = memoryId.toIdentifier();
        PendingGraphBatch pendingBatch =
                pendingBatches.getOrDefault(memoryKey, Map.of()).get(extractionBatchId);
        if (pendingBatch == null) {
            throw new IllegalStateException(
                    "no pending graph batch for extractionBatchId=" + extractionBatchId);
        }

        var nextEntities = new LinkedHashMap<>(entities.getOrDefault(memoryKey, Map.of()));
        pendingBatch
                .writePlan()
                .entities()
                .forEach(entity -> nextEntities.put(entity.entityKey(), entity));

        var nextMentions = new LinkedHashMap<>(mentions.getOrDefault(memoryKey, Map.of()));
        pendingBatch
                .writePlan()
                .mentions()
                .forEach(
                        mention ->
                                nextMentions.put(
                                        mentionIdentity(mention.itemId(), mention.entityKey()),
                                        mention));

        var nextLinks = new LinkedHashMap<>(itemLinks.getOrDefault(memoryKey, Map.of()));
        pendingBatch
                .persistedLinks()
                .forEach(
                        link ->
                                nextLinks.put(
                                        linkIdentity(
                                                link.sourceItemId(),
                                                link.targetItemId(),
                                                link.linkType()),
                                        link));

        var nextAliases = new LinkedHashMap<>(entityAliases.getOrDefault(memoryKey, Map.of()));
        var nextAliasReceipts =
                new LinkedHashSet<>(aliasBatchReceipts.getOrDefault(memoryKey, Set.of()));
        upsertAliasesWithBatchReceipts(
                extractionBatchId,
                pendingBatch.writePlan().aliases(),
                nextAliases,
                nextAliasReceipts);

        return new CommittedGraphView(
                nextEntities,
                nextMentions,
                nextAliases,
                nextLinks,
                entityCooccurrences.getOrDefault(memoryKey, Map.of()),
                nextAliasReceipts);
    }

    @Override
    public void installCommittedBatch(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            CommittedGraphView committedGraphView) {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(extractionBatchId, "extractionBatchId");
        Objects.requireNonNull(committedGraphView, "committedGraphView");

        String memoryKey = memoryId.toIdentifier();
        entities.put(memoryKey, new ConcurrentHashMap<>(committedGraphView.entitiesByKey()));
        mentions.put(memoryKey, new ConcurrentHashMap<>(committedGraphView.mentionsByIdentity()));
        entityAliases.put(
                memoryKey, new ConcurrentHashMap<>(committedGraphView.aliasesByIdentity()));
        itemLinks.put(memoryKey, new ConcurrentHashMap<>(committedGraphView.itemLinksByIdentity()));
        entityCooccurrences.put(
                memoryKey,
                new ConcurrentHashMap<>(committedGraphView.entityCooccurrencesByIdentity()));
        Set<String> installedReceipts = ConcurrentHashMap.newKeySet();
        installedReceipts.addAll(committedGraphView.aliasBatchReceipts());
        aliasBatchReceipts.put(memoryKey, installedReceipts);
        removePendingBatch(memoryKey, extractionBatchId);
    }

    @Override
    public void discardPendingBatch(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        removePendingBatch(memoryId.toIdentifier(), extractionBatchId);
    }

    @Override
    public void upsertEntities(MemoryId memoryId, List<GraphEntity> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, GraphEntity> scoped =
                entities.computeIfAbsent(
                        memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>());
        records.forEach(entity -> scoped.put(entity.entityKey(), entity));
    }

    @Override
    public void upsertItemEntityMentions(MemoryId memoryId, List<ItemEntityMention> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, ItemEntityMention> scoped =
                mentions.computeIfAbsent(
                        memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>());
        records.forEach(
                mention ->
                        scoped.put(
                                mentionIdentity(mention.itemId(), mention.entityKey()), mention));
    }

    @Override
    public void rebuildEntityCooccurrences(MemoryId memoryId, Collection<String> entityKeys) {
        Set<String> affectedEntityKeys = normalizeEntityKeys(entityKeys);
        if (affectedEntityKeys.isEmpty()) {
            return;
        }

        String memoryKey = memoryId.toIdentifier();
        Map<String, EntityCooccurrence> scoped =
                entityCooccurrences.computeIfAbsent(
                        memoryKey, ignored -> new ConcurrentHashMap<>());
        scoped.values()
                .removeIf(
                        cooccurrence ->
                                affectedEntityKeys.contains(cooccurrence.leftEntityKey())
                                        || affectedEntityKeys.contains(
                                                cooccurrence.rightEntityKey()));

        Map<Long, Set<String>> mentionsByItem = new HashMap<>();
        mentions.getOrDefault(memoryKey, Map.of())
                .values()
                .forEach(
                        mention ->
                                mentionsByItem
                                        .computeIfAbsent(
                                                mention.itemId(), ignored -> new HashSet<>())
                                        .add(mention.entityKey()));

        Map<CooccurrenceKey, Integer> counts = new HashMap<>();
        mentionsByItem
                .values()
                .forEach(entitySet -> accumulateCounts(entitySet, affectedEntityKeys, counts));

        Instant rebuiltAt = Instant.now();
        counts.forEach(
                (key, count) ->
                        scoped.put(
                                cooccurrenceIdentity(key.leftEntityKey(), key.rightEntityKey()),
                                new EntityCooccurrence(
                                        memoryKey,
                                        key.leftEntityKey(),
                                        key.rightEntityKey(),
                                        count,
                                        Map.of(),
                                        rebuiltAt)));
    }

    @Override
    public void upsertItemLinks(MemoryId memoryId, List<ItemLink> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, ItemLink> scoped =
                itemLinks.computeIfAbsent(
                        memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>());
        records.forEach(
                link ->
                        scoped.put(
                                linkIdentity(
                                        link.sourceItemId(), link.targetItemId(), link.linkType()),
                                link));
    }

    @Override
    public void upsertEntityAliases(MemoryId memoryId, List<GraphEntityAlias> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, GraphEntityAlias> scoped =
                entityAliases.computeIfAbsent(
                        memoryId.toIdentifier(), ignored -> new ConcurrentHashMap<>());
        records.forEach(
                alias ->
                        scoped.merge(
                                aliasIdentity(
                                        alias.entityKey(),
                                        alias.entityType(),
                                        alias.normalizedAlias()),
                                alias,
                                InMemoryGraphOperations::mergeAlias));
    }

    @Override
    public List<GraphEntity> listEntities(MemoryId memoryId) {
        return entities.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .sorted(Comparator.comparing(GraphEntity::entityKey))
                .toList();
    }

    @Override
    public List<GraphEntityAlias> listEntityAliases(MemoryId memoryId) {
        return entityAliases.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .sorted(
                        Comparator.comparing(GraphEntityAlias::entityType)
                                .thenComparing(GraphEntityAlias::normalizedAlias)
                                .thenComparing(GraphEntityAlias::entityKey))
                .toList();
    }

    @Override
    public List<GraphEntity> listEntitiesByEntityKeys(
            MemoryId memoryId, Collection<String> entityKeys) {
        List<String> normalizedKeys = normalizeEntityKeys(entityKeys).stream().sorted().toList();
        if (normalizedKeys.isEmpty()) {
            return List.of();
        }
        Map<String, GraphEntity> scoped = entities.getOrDefault(memoryId.toIdentifier(), Map.of());
        return normalizedKeys.stream().map(scoped::get).filter(Objects::nonNull).toList();
    }

    @Override
    public List<ItemEntityMention> listItemEntityMentions(MemoryId memoryId) {
        return mentions.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .sorted(
                        Comparator.comparing(ItemEntityMention::itemId)
                                .thenComparing(ItemEntityMention::entityKey))
                .toList();
    }

    @Override
    public List<ItemEntityMention> listItemEntityMentions(
            MemoryId memoryId, Collection<Long> itemIds) {
        var itemIdSet = normalizeItemIds(itemIds);
        if (itemIdSet.isEmpty()) {
            return List.of();
        }
        return mentions.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .filter(mention -> itemIdSet.contains(mention.itemId()))
                .sorted(
                        Comparator.comparing(ItemEntityMention::itemId)
                                .thenComparing(ItemEntityMention::entityKey))
                .toList();
    }

    @Override
    public List<ItemEntityMention> listItemEntityMentionsByEntityKeys(
            MemoryId memoryId, Collection<String> entityKeys, int perEntityLimitPlusOne) {
        var entityKeySet = normalizeEntityKeys(entityKeys);
        if (entityKeySet.isEmpty() || perEntityLimitPlusOne <= 0) {
            return List.of();
        }
        Map<String, Integer> seen = new HashMap<>();
        return mentions.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
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

    @Override
    public List<EntityCooccurrence> listEntityCooccurrences(MemoryId memoryId) {
        return entityCooccurrences.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .sorted(
                        Comparator.comparing(EntityCooccurrence::leftEntityKey)
                                .thenComparing(EntityCooccurrence::rightEntityKey))
                .toList();
    }

    @Override
    public List<ItemLink> listItemLinks(MemoryId memoryId) {
        return itemLinks.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .sorted(
                        Comparator.comparing(ItemLink::sourceItemId)
                                .thenComparing(ItemLink::targetItemId)
                                .thenComparing(ItemLink::linkType))
                .toList();
    }

    @Override
    public List<ItemLink> listItemLinks(
            MemoryId memoryId, Collection<Long> itemIds, Collection<ItemLinkType> linkTypes) {
        var itemIdSet = normalizeItemIds(itemIds);
        if (itemIdSet.isEmpty()) {
            return List.of();
        }
        var typeSet = normalizeLinkTypes(linkTypes);
        return itemLinks.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .filter(
                        link ->
                                itemIdSet.contains(link.sourceItemId())
                                        && itemIdSet.contains(link.targetItemId()))
                .filter(link -> typeSet.isEmpty() || typeSet.contains(link.linkType()))
                .sorted(
                        Comparator.comparing(ItemLink::sourceItemId)
                                .thenComparing(ItemLink::targetItemId)
                                .thenComparing(ItemLink::linkType))
                .toList();
    }

    @Override
    public List<ItemLink> listAdjacentItemLinks(
            MemoryId memoryId, Collection<Long> seedItemIds, Collection<ItemLinkType> linkTypes) {
        var seedIdSet = normalizeItemIds(seedItemIds);
        if (seedIdSet.isEmpty()) {
            return List.of();
        }
        var typeSet = normalizeLinkTypes(linkTypes);
        return itemLinks.getOrDefault(memoryId.toIdentifier(), Map.of()).values().stream()
                .filter(
                        link ->
                                seedIdSet.contains(link.sourceItemId())
                                        || seedIdSet.contains(link.targetItemId()))
                .filter(link -> typeSet.isEmpty() || typeSet.contains(link.linkType()))
                .sorted(
                        Comparator.comparing(ItemLink::sourceItemId)
                                .thenComparing(ItemLink::targetItemId)
                                .thenComparing(ItemLink::linkType))
                .toList();
    }

    private static void accumulateCounts(
            Set<String> entitySet,
            Set<String> affectedEntityKeys,
            Map<CooccurrenceKey, Integer> counts) {
        List<String> sortedKeys = entitySet.stream().filter(Objects::nonNull).sorted().toList();
        for (int i = 0; i < sortedKeys.size(); i++) {
            for (int j = i + 1; j < sortedKeys.size(); j++) {
                String leftEntityKey = sortedKeys.get(i);
                String rightEntityKey = sortedKeys.get(j);
                if (!affectedEntityKeys.contains(leftEntityKey)
                        && !affectedEntityKeys.contains(rightEntityKey)) {
                    continue;
                }
                counts.merge(new CooccurrenceKey(leftEntityKey, rightEntityKey), 1, Integer::sum);
            }
        }
    }

    private static GraphEntityAlias mergeAlias(
            GraphEntityAlias existing, GraphEntityAlias incoming) {
        return new GraphEntityAlias(
                existing.memoryId(),
                existing.entityKey(),
                existing.entityType(),
                existing.normalizedAlias(),
                existing.evidenceCount() + incoming.evidenceCount(),
                existing.metadata().isEmpty() ? incoming.metadata() : existing.metadata(),
                earlierOf(existing.createdAt(), incoming.createdAt()),
                laterOf(existing.updatedAt(), incoming.updatedAt()));
    }

    private void upsertAliasesWithBatchReceipts(
            ExtractionBatchId extractionBatchId,
            List<GraphEntityAlias> aliases,
            Map<String, GraphEntityAlias> scopedAliases,
            Set<String> receipts) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }
        for (var alias : aliases) {
            String aliasIdentity =
                    aliasIdentity(alias.entityKey(), alias.entityType(), alias.normalizedAlias());
            String receiptKey = aliasBatchReceiptIdentity(aliasIdentity, extractionBatchId);
            if (!receipts.add(receiptKey)) {
                continue;
            }
            scopedAliases.merge(aliasIdentity, alias, InMemoryGraphOperations::mergeAlias);
        }
    }

    private void removePendingBatch(String memoryKey, ExtractionBatchId extractionBatchId) {
        var scopedPending = pendingBatches.get(memoryKey);
        if (scopedPending == null) {
            return;
        }
        scopedPending.remove(extractionBatchId);
        if (scopedPending.isEmpty()) {
            pendingBatches.remove(memoryKey);
        }
    }

    private static String mentionIdentity(Long itemId, String entityKey) {
        return itemId + "::" + entityKey;
    }

    private static String linkIdentity(
            Long sourceItemId, Long targetItemId, ItemLinkType linkType) {
        return sourceItemId + "::" + targetItemId + "::" + linkType.name();
    }

    private static String cooccurrenceIdentity(String leftEntityKey, String rightEntityKey) {
        return leftEntityKey + "::" + rightEntityKey;
    }

    private static String aliasIdentity(
            String entityKey, GraphEntityType entityType, String normalizedAlias) {
        return entityKey + "::" + entityType.name() + "::" + normalizedAlias;
    }

    private static String aliasBatchReceiptIdentity(
            String aliasIdentity, ExtractionBatchId extractionBatchId) {
        return aliasIdentity + "::" + extractionBatchId.value();
    }

    private static Set<String> normalizeEntityKeys(Collection<String> entityKeys) {
        if (entityKeys == null || entityKeys.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        entityKeys.stream().filter(Objects::nonNull).forEach(normalized::add);
        return normalized;
    }

    private static Set<Long> normalizeItemIds(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> normalized = new HashSet<>();
        itemIds.stream().filter(Objects::nonNull).forEach(normalized::add);
        return normalized;
    }

    private static Set<ItemLinkType> normalizeLinkTypes(Collection<ItemLinkType> linkTypes) {
        if (linkTypes == null || linkTypes.isEmpty()) {
            return Set.of();
        }
        Set<ItemLinkType> normalized = new HashSet<>();
        linkTypes.stream().filter(Objects::nonNull).forEach(normalized::add);
        return normalized;
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

    private static Instant laterOf(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null || left.isAfter(right)) {
            return left;
        }
        return right;
    }

    private record CooccurrenceKey(String leftEntityKey, String rightEntityKey) {}

    private record PendingGraphBatch(ItemGraphWritePlan writePlan, List<ItemLink> persistedLinks) {

        private static PendingGraphBatch from(MemoryId memoryId, ItemGraphWritePlan writePlan) {
            ItemGraphWritePlan normalized = writePlan.normalized();
            return new PendingGraphBatch(normalized, normalized.toPersistedLinks(memoryId));
        }
    }
}
