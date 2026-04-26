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
package com.openmemind.ai.memory.plugin.store.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.EntityCooccurrenceConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.GraphEntityAliasConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.GraphEntityConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.ItemEntityMentionConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.ItemLinkConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryEntityCooccurrenceDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryGraphAliasBatchReceiptDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryGraphEntityAliasDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryGraphEntityDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemEntityMentionDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemLinkDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryEntityCooccurrenceMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryGraphAliasBatchReceiptMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryGraphEntityAliasMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryGraphEntityMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemEntityMentionMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemLinkMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialect;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MybatisGraphOperations implements GraphOperations {

    private final MemoryGraphEntityMapper graphEntityMapper;
    private final MemoryItemEntityMentionMapper itemEntityMentionMapper;
    private final MemoryItemLinkMapper itemLinkMapper;
    private final MemoryEntityCooccurrenceMapper entityCooccurrenceMapper;
    private final MemoryGraphEntityAliasMapper entityAliasMapper;
    private final MemoryGraphAliasBatchReceiptMapper aliasBatchReceiptMapper;
    private final DatabaseDialect dialect;

    public MybatisGraphOperations(
            MemoryGraphEntityMapper graphEntityMapper,
            MemoryItemEntityMentionMapper itemEntityMentionMapper,
            MemoryItemLinkMapper itemLinkMapper,
            MemoryEntityCooccurrenceMapper entityCooccurrenceMapper,
            MemoryGraphEntityAliasMapper entityAliasMapper,
            MemoryGraphAliasBatchReceiptMapper aliasBatchReceiptMapper,
            DatabaseDialect dialect) {
        this.graphEntityMapper = Objects.requireNonNull(graphEntityMapper, "graphEntityMapper");
        this.itemEntityMentionMapper =
                Objects.requireNonNull(itemEntityMentionMapper, "itemEntityMentionMapper");
        this.itemLinkMapper = Objects.requireNonNull(itemLinkMapper, "itemLinkMapper");
        this.entityCooccurrenceMapper =
                Objects.requireNonNull(entityCooccurrenceMapper, "entityCooccurrenceMapper");
        this.entityAliasMapper = Objects.requireNonNull(entityAliasMapper, "entityAliasMapper");
        this.aliasBatchReceiptMapper =
                Objects.requireNonNull(aliasBatchReceiptMapper, "aliasBatchReceiptMapper");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    @Override
    public void applyGraphWritePlan(
            MemoryId memoryId, ExtractionBatchId extractionBatchId, ItemGraphWritePlan writePlan) {
        ItemGraphWritePlan normalizedPlan = writePlan.normalized();
        upsertEntities(memoryId, normalizedPlan.entities());
        upsertItemEntityMentions(memoryId, normalizedPlan.mentions());
        upsertPersistedLinks(memoryId, normalizedPlan.toPersistedLinks(memoryId));
        upsertAliasesWithBatchReceipts(memoryId, extractionBatchId, normalizedPlan.aliases());
        rebuildEntityCooccurrences(memoryId, normalizedPlan.affectedEntityKeys());
    }

    @Override
    public boolean supportsTransactionalBatchCommit() {
        return true;
    }

    @Override
    public void upsertEntities(MemoryId memoryId, List<GraphEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        Map<String, MemoryGraphEntityDO> existingByKey =
                graphEntityMapper
                        .selectList(
                                memoryQuery(memoryId, MemoryGraphEntityDO.class)
                                        .in(
                                                "entity_key",
                                                entities.stream()
                                                        .map(GraphEntity::entityKey)
                                                        .toList()))
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        MemoryGraphEntityDO::getEntityKey,
                                        Function.identity(),
                                        (left, right) -> left));
        List<MemoryGraphEntityDO> inserts = new ArrayList<>();
        List<MemoryGraphEntityDO> updates = new ArrayList<>();
        entities.forEach(
                entity -> {
                    MemoryGraphEntityDO dataObject = GraphEntityConverter.toDO(memoryId, entity);
                    MemoryGraphEntityDO existing = existingByKey.get(entity.entityKey());
                    if (existing != null) {
                        dataObject.setId(existing.getId());
                        updates.add(dataObject);
                    } else {
                        inserts.add(dataObject);
                    }
                });
        if (!inserts.isEmpty()) {
            graphEntityMapper.insertBatch(inserts);
        }
        updates.forEach(graphEntityMapper::updateById);
    }

    @Override
    public void upsertItemEntityMentions(MemoryId memoryId, List<ItemEntityMention> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return;
        }
        Map<MentionIdentity, MemoryItemEntityMentionDO> existingByIdentity =
                itemEntityMentionMapper
                        .selectList(
                                memoryQuery(memoryId, MemoryItemEntityMentionDO.class)
                                        .in(
                                                "entity_key",
                                                mentions.stream()
                                                        .map(ItemEntityMention::entityKey)
                                                        .toList())
                                        .in(
                                                "item_id",
                                                mentions.stream()
                                                        .map(ItemEntityMention::itemId)
                                                        .toList()))
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        dataObject ->
                                                new MentionIdentity(
                                                        dataObject.getItemId(),
                                                        dataObject.getEntityKey()),
                                        Function.identity(),
                                        (left, right) -> left));
        List<MemoryItemEntityMentionDO> inserts = new ArrayList<>();
        List<MemoryItemEntityMentionDO> updates = new ArrayList<>();
        mentions.forEach(
                mention -> {
                    MemoryItemEntityMentionDO dataObject =
                            ItemEntityMentionConverter.toDO(memoryId, mention);
                    MemoryItemEntityMentionDO existing =
                            existingByIdentity.get(
                                    new MentionIdentity(mention.itemId(), mention.entityKey()));
                    if (existing != null) {
                        dataObject.setId(existing.getId());
                        updates.add(dataObject);
                    } else {
                        inserts.add(dataObject);
                    }
                });
        if (!inserts.isEmpty()) {
            itemEntityMentionMapper.insertBatch(inserts);
        }
        updates.forEach(itemEntityMentionMapper::updateById);
    }

    @Override
    public void rebuildEntityCooccurrences(MemoryId memoryId, Collection<String> entityKeys) {
        Set<String> affectedEntityKeys = normalizeEntityKeys(entityKeys);
        if (affectedEntityKeys.isEmpty()) {
            return;
        }

        entityCooccurrenceMapper.deleteByEntityKeys(memoryId.toIdentifier(), affectedEntityKeys);

        Map<Long, Set<String>> mentionsByItem = new HashMap<>();
        listItemEntityMentions(memoryId)
                .forEach(
                        mention ->
                                mentionsByItem
                                        .computeIfAbsent(
                                                mention.itemId(), ignored -> new HashSet<>())
                                        .add(mention.entityKey()));

        Map<CooccurrenceIdentity, Integer> counts = new HashMap<>();
        mentionsByItem
                .values()
                .forEach(entitySet -> accumulateCounts(entitySet, affectedEntityKeys, counts));

        Instant rebuiltAt = Instant.now();
        counts.forEach(
                (identity, count) ->
                        entityCooccurrenceMapper.insert(
                                EntityCooccurrenceConverter.toDO(
                                        memoryId,
                                        new EntityCooccurrence(
                                                memoryId.toIdentifier(),
                                                identity.leftEntityKey(),
                                                identity.rightEntityKey(),
                                                count,
                                                Map.of(),
                                                rebuiltAt))));
    }

    @Override
    public void upsertItemLinks(MemoryId memoryId, List<ItemLink> links) {
        if (links == null || links.isEmpty()) {
            return;
        }
        Map<LinkIdentity, MemoryItemLinkDO> existingByIdentity =
                itemLinkMapper
                        .selectList(
                                memoryQuery(memoryId, MemoryItemLinkDO.class)
                                        .in(
                                                "source_item_id",
                                                links.stream().map(ItemLink::sourceItemId).toList())
                                        .in(
                                                "target_item_id",
                                                links.stream().map(ItemLink::targetItemId).toList())
                                        .in(
                                                "link_type",
                                                links.stream()
                                                        .map(link -> link.linkType().name())
                                                        .toList()))
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        dataObject ->
                                                new LinkIdentity(
                                                        dataObject.getSourceItemId(),
                                                        dataObject.getTargetItemId(),
                                                        dataObject.getLinkType()),
                                        Function.identity(),
                                        (left, right) -> left));
        List<MemoryItemLinkDO> inserts = new ArrayList<>();
        List<MemoryItemLinkDO> updates = new ArrayList<>();
        links.forEach(
                link -> {
                    MemoryItemLinkDO dataObject = ItemLinkConverter.toDO(memoryId, link);
                    MemoryItemLinkDO existing =
                            existingByIdentity.get(
                                    new LinkIdentity(
                                            link.sourceItemId(),
                                            link.targetItemId(),
                                            link.linkType().name()));
                    if (existing != null) {
                        dataObject.setId(existing.getId());
                        updates.add(dataObject);
                    } else {
                        inserts.add(dataObject);
                    }
                });
        if (!inserts.isEmpty()) {
            itemLinkMapper.insertBatch(inserts);
        }
        updates.forEach(itemLinkMapper::updateById);
    }

    @Override
    public void upsertEntityAliases(MemoryId memoryId, List<GraphEntityAlias> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }
        List<GraphEntityAlias> mergedAliases = mergeAliasBatch(aliases);
        Map<AliasIdentity, MemoryGraphEntityAliasDO> existingByIdentity =
                loadExistingAliases(memoryId, mergedAliases);
        List<MemoryGraphEntityAliasDO> inserts = new ArrayList<>();
        List<MemoryGraphEntityAliasDO> updates = new ArrayList<>();
        mergedAliases.forEach(
                alias -> {
                    MemoryGraphEntityAliasDO dataObject =
                            GraphEntityAliasConverter.toDO(memoryId, alias);
                    MemoryGraphEntityAliasDO existing =
                            existingByIdentity.get(
                                    new AliasIdentity(
                                            alias.entityType(),
                                            alias.entityKey(),
                                            alias.normalizedAlias()));
                    if (existing != null) {
                        dataObject.setId(existing.getId());
                        dataObject.setEvidenceCount(
                                existing.getEvidenceCount() + alias.evidenceCount());
                        dataObject.setCreatedAt(
                                earlierOf(existing.getCreatedAt(), alias.createdAt()));
                        dataObject.setUpdatedAt(
                                laterOf(existing.getUpdatedAt(), alias.updatedAt()));
                        updates.add(dataObject);
                    } else {
                        inserts.add(dataObject);
                    }
                });
        if (!inserts.isEmpty()) {
            entityAliasMapper.insertBatch(inserts);
        }
        updates.forEach(entityAliasMapper::updateById);
    }

    @Override
    public List<GraphEntity> listEntities(MemoryId memoryId) {
        return graphEntityMapper
                .selectList(
                        memoryQuery(memoryId, MemoryGraphEntityDO.class).orderByAsc("entity_key"))
                .stream()
                .map(GraphEntityConverter::toRecord)
                .toList();
    }

    @Override
    public List<GraphEntityAlias> listEntityAliases(MemoryId memoryId) {
        return entityAliasMapper
                .selectList(
                        memoryQuery(memoryId, MemoryGraphEntityAliasDO.class)
                                .orderByAsc("entity_type", "normalized_alias", "entity_key"))
                .stream()
                .map(GraphEntityAliasConverter::toRecord)
                .toList();
    }

    @Override
    public List<GraphEntity> listEntitiesByEntityKeys(
            MemoryId memoryId, Collection<String> entityKeys) {
        Set<String> normalizedKeys = normalizeEntityKeys(entityKeys);
        if (normalizedKeys.isEmpty()) {
            return List.of();
        }
        return graphEntityMapper
                .selectByEntityKeys(memoryId.toIdentifier(), normalizedKeys)
                .stream()
                .map(GraphEntityConverter::toRecord)
                .toList();
    }

    @Override
    public List<GraphEntityAlias> listEntityAliasesByNormalizedAlias(
            MemoryId memoryId, GraphEntityType entityType, String normalizedAlias) {
        if (entityType == null || normalizedAlias == null || normalizedAlias.isBlank()) {
            return List.of();
        }
        return entityAliasMapper
                .selectByNormalizedAlias(
                        memoryId.toIdentifier(), entityType.name(), normalizedAlias)
                .stream()
                .map(GraphEntityAliasConverter::toRecord)
                .toList();
    }

    @Override
    public List<ItemEntityMention> listItemEntityMentions(MemoryId memoryId) {
        return itemEntityMentionMapper
                .selectList(
                        memoryQuery(memoryId, MemoryItemEntityMentionDO.class)
                                .orderByAsc("item_id", "entity_key", "created_at"))
                .stream()
                .map(ItemEntityMentionConverter::toRecord)
                .toList();
    }

    @Override
    public List<ItemEntityMention> listItemEntityMentions(
            MemoryId memoryId, Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return itemEntityMentionMapper
                .selectByItemIds(memoryId.toIdentifier(), normalizeItemIds(itemIds))
                .stream()
                .map(ItemEntityMentionConverter::toRecord)
                .toList();
    }

    @Override
    public List<EntityCooccurrence> listEntityCooccurrences(MemoryId memoryId) {
        return entityCooccurrenceMapper
                .selectList(
                        memoryQuery(memoryId, MemoryEntityCooccurrenceDO.class)
                                .orderByAsc("left_entity_key", "right_entity_key"))
                .stream()
                .map(EntityCooccurrenceConverter::toRecord)
                .toList();
    }

    @Override
    public List<ItemLink> listItemLinks(MemoryId memoryId) {
        return itemLinkMapper
                .selectList(
                        memoryQuery(memoryId, MemoryItemLinkDO.class)
                                .orderByAsc("source_item_id", "target_item_id", "link_type"))
                .stream()
                .map(ItemLinkConverter::toRecord)
                .toList();
    }

    @Override
    public List<ItemLink> listItemLinks(
            MemoryId memoryId, Collection<Long> itemIds, Collection<ItemLinkType> linkTypes) {
        Set<Long> normalizedItemIds = normalizeItemIds(itemIds);
        if (normalizedItemIds.isEmpty()) {
            return List.of();
        }
        return itemLinkMapper
                .selectLocalSubgraphLinks(
                        memoryId.toIdentifier(), normalizedItemIds, normalizeLinkTypes(linkTypes))
                .stream()
                .map(ItemLinkConverter::toRecord)
                .toList();
    }

    @Override
    public List<ItemLink> listAdjacentItemLinks(
            MemoryId memoryId, Collection<Long> seedItemIds, Collection<ItemLinkType> linkTypes) {
        Set<Long> normalizedSeedIds = normalizeItemIds(seedItemIds);
        if (normalizedSeedIds.isEmpty()) {
            return List.of();
        }
        return itemLinkMapper
                .selectAdjacentLinks(
                        memoryId.toIdentifier(), normalizedSeedIds, normalizeLinkTypes(linkTypes))
                .stream()
                .map(ItemLinkConverter::toRecord)
                .toList();
    }

    @Override
    public List<ItemEntityMention> listItemEntityMentionsByEntityKeys(
            MemoryId memoryId, Collection<String> entityKeys, int perEntityLimitPlusOne) {
        Set<String> normalizedEntityKeys = normalizeEntityKeys(entityKeys);
        if (normalizedEntityKeys.isEmpty() || perEntityLimitPlusOne <= 0) {
            return List.of();
        }
        return itemEntityMentionMapper
                .selectByEntityKeysLimited(
                        memoryId.toIdentifier(),
                        normalizedEntityKeys,
                        perEntityLimitPlusOne,
                        dialect)
                .stream()
                .map(ItemEntityMentionConverter::toRecord)
                .toList();
    }

    private <T> QueryWrapper<T> memoryQuery(MemoryId memoryId, Class<T> clazz) {
        return new QueryWrapper<T>()
                .eq("user_id", memoryId.getAttribute("userId"))
                .eq("agent_id", memoryId.getAttribute("agentId"))
                .eq("memory_id", memoryId.toIdentifier());
    }

    private Map<AliasIdentity, MemoryGraphEntityAliasDO> loadExistingAliases(
            MemoryId memoryId, List<GraphEntityAlias> aliases) {
        Map<AliasIdentity, MemoryGraphEntityAliasDO> existingByIdentity = new HashMap<>();
        aliases.stream()
                .map(alias -> new AliasLookup(alias.entityType(), alias.normalizedAlias()))
                .distinct()
                .forEach(
                        lookup ->
                                entityAliasMapper
                                        .selectByNormalizedAlias(
                                                memoryId.toIdentifier(),
                                                lookup.entityType().name(),
                                                lookup.normalizedAlias())
                                        .forEach(
                                                dataObject ->
                                                        existingByIdentity.put(
                                                                new AliasIdentity(
                                                                        GraphEntityType.valueOf(
                                                                                dataObject
                                                                                        .getEntityType()),
                                                                        dataObject.getEntityKey(),
                                                                        dataObject
                                                                                .getNormalizedAlias()),
                                                                dataObject)));
        return existingByIdentity;
    }

    private static List<GraphEntityAlias> mergeAliasBatch(List<GraphEntityAlias> aliases) {
        Map<AliasIdentity, GraphEntityAlias> merged = new HashMap<>();
        aliases.forEach(
                alias ->
                        merged.merge(
                                new AliasIdentity(
                                        alias.entityType(),
                                        alias.entityKey(),
                                        alias.normalizedAlias()),
                                alias,
                                MybatisGraphOperations::mergeAlias));
        return List.copyOf(merged.values());
    }

    private void upsertPersistedLinks(MemoryId memoryId, List<ItemLink> links) {
        upsertItemLinks(memoryId, links);
    }

    private void upsertAliasesWithBatchReceipts(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            List<GraphEntityAlias> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }
        List<GraphEntityAlias> mergedAliases = mergeAliasBatch(aliases);
        List<GraphEntityAlias> aliasesToPersist = new ArrayList<>();
        for (GraphEntityAlias alias : mergedAliases) {
            boolean firstReceipt =
                    aliasBatchReceiptMapper.insertIfAbsent(
                                    MemoryGraphAliasBatchReceiptDO.from(
                                            memoryId, extractionBatchId, alias),
                                    dialect)
                            == 1;
            if (firstReceipt) {
                aliasesToPersist.add(alias);
            }
        }
        upsertEntityAliases(memoryId, aliasesToPersist);
    }

    private static void accumulateCounts(
            Set<String> entitySet,
            Set<String> affectedEntityKeys,
            Map<CooccurrenceIdentity, Integer> counts) {
        List<String> sortedKeys = entitySet.stream().filter(Objects::nonNull).sorted().toList();
        for (int i = 0; i < sortedKeys.size(); i++) {
            for (int j = i + 1; j < sortedKeys.size(); j++) {
                String leftEntityKey = sortedKeys.get(i);
                String rightEntityKey = sortedKeys.get(j);
                if (!affectedEntityKeys.contains(leftEntityKey)
                        && !affectedEntityKeys.contains(rightEntityKey)) {
                    continue;
                }
                counts.merge(
                        new CooccurrenceIdentity(leftEntityKey, rightEntityKey), 1, Integer::sum);
            }
        }
    }

    private static Set<String> normalizeEntityKeys(Collection<String> entityKeys) {
        if (entityKeys == null || entityKeys.isEmpty()) {
            return Set.of();
        }
        return entityKeys.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static Set<Long> normalizeItemIds(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Set.of();
        }
        return itemIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static List<String> normalizeLinkTypes(Collection<ItemLinkType> linkTypes) {
        if (linkTypes == null || linkTypes.isEmpty()) {
            return List.of();
        }
        return linkTypes.stream().filter(Objects::nonNull).map(Enum::name).distinct().toList();
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

    private record MentionIdentity(Long itemId, String entityKey) {}

    private record LinkIdentity(Long sourceItemId, Long targetItemId, String linkType) {}

    private record CooccurrenceIdentity(String leftEntityKey, String rightEntityKey) {}

    private record AliasLookup(GraphEntityType entityType, String normalizedAlias) {}

    private record AliasIdentity(
            GraphEntityType entityType, String entityKey, String normalizedAlias) {}
}
