package com.openmemind.ai.memory.plugin.store.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.InsightConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.InsightTypeConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.ItemConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.RawDataConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightTypeDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryRawDataDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightTypeMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryRawDataMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class MybatisPlusMemoryStore implements MemoryStore {

    private final MemoryRawDataMapper rawDataMapper;
    private final MemoryItemMapper itemMapper;
    private final MemoryInsightTypeMapper insightTypeMapper;
    private final MemoryInsightMapper insightMapper;

    public MybatisPlusMemoryStore(
            MemoryRawDataMapper rawDataMapper,
            MemoryItemMapper itemMapper,
            MemoryInsightTypeMapper insightTypeMapper,
            MemoryInsightMapper insightMapper) {
        this.rawDataMapper = rawDataMapper;
        this.itemMapper = itemMapper;
        this.insightTypeMapper = insightTypeMapper;
        this.insightMapper = insightMapper;
    }

    // ===== MemoryRawData =====

    @Override
    public void saveRawData(MemoryId id, MemoryRawData rawData) {
        MemoryRawDataDO existing =
                rawDataMapper.selectOne(
                        memoryQuery(id, MemoryRawDataDO.class).eq("biz_id", rawData.id()));
        MemoryRawDataDO dataObject = RawDataConverter.toDO(id, rawData);
        if (existing != null) {
            dataObject.setId(existing.getId());
            rawDataMapper.updateById(dataObject);
        } else {
            rawDataMapper.insert(dataObject);
        }
    }

    @Override
    public void saveRawDataList(MemoryId id, List<MemoryRawData> rawDataList) {
        rawDataList.forEach(rawData -> saveRawData(id, rawData));
    }

    @Override
    public Optional<MemoryRawData> getRawData(MemoryId id, String rawDataId) {
        MemoryRawDataDO dataObject =
                rawDataMapper.selectOne(
                        memoryQuery(id, MemoryRawDataDO.class).eq("biz_id", rawDataId));
        return Optional.ofNullable(dataObject).map(RawDataConverter::toRecord);
    }

    @Override
    public List<MemoryRawData> getAllRawData(MemoryId id) {
        return rawDataMapper.selectList(memoryQuery(id, MemoryRawDataDO.class)).stream()
                .map(RawDataConverter::toRecord)
                .toList();
    }

    @Override
    public Optional<MemoryRawData> getRawDataByContentId(MemoryId id, String contentId) {
        MemoryRawDataDO dataObject =
                rawDataMapper.selectOne(
                        memoryQuery(id, MemoryRawDataDO.class)
                                .eq("content_id", contentId)
                                .last("LIMIT 1"));
        return Optional.ofNullable(dataObject).map(RawDataConverter::toRecord);
    }

    @Override
    public void deleteRawData(MemoryId id, String rawDataId) {
        rawDataMapper.delete(memoryQuery(id, MemoryRawDataDO.class).eq("biz_id", rawDataId));
    }

    @Override
    public List<MemoryRawData> pollRawDataWithoutVector(MemoryId id, int limit, Duration minAge) {
        if (limit <= 0) {
            return List.of();
        }
        Instant cutoff = Instant.now().minus(minAge);
        return rawDataMapper
                .selectList(
                        new LambdaQueryWrapper<MemoryRawDataDO>()
                                .eq(MemoryRawDataDO::getUserId, id.getAttribute("userId"))
                                .eq(MemoryRawDataDO::getAgentId, id.getAttribute("agentId"))
                                .isNull(MemoryRawDataDO::getCaptionVectorId)
                                .lt(MemoryRawDataDO::getCreatedAt, cutoff)
                                .orderByAsc(MemoryRawDataDO::getCreatedAt)
                                .last("LIMIT " + limit))
                .stream()
                .map(RawDataConverter::toRecord)
                .toList();
    }

    @Override
    public void updateRawDataVectorIds(
            MemoryId id, Map<String, String> vectorIds, Map<String, Object> metadataPatch) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        vectorIds.forEach(
                (bizId, vectorId) -> {
                    MemoryRawDataDO existing =
                            rawDataMapper.selectOne(
                                    memoryQuery(id, MemoryRawDataDO.class).eq("biz_id", bizId));
                    if (existing == null) {
                        return;
                    }
                    // Convert DO to domain object to reuse the merge logic of
                    // MemoryRawData.withVectorId
                    MemoryRawData updatedRecord =
                            RawDataConverter.toRecord(existing)
                                    .withVectorId(vectorId, metadataPatch);

                    MemoryRawDataDO updatedDo = RawDataConverter.toDO(id, updatedRecord);
                    // Retain ID to ensure updateById updates the original record
                    updatedDo.setId(existing.getId());

                    // If the original metadata is not empty and the patch is not empty, ensure the
                    // merged metadata is written back
                    Map<String, Object> mergedMetadata = new HashMap<>();
                    if (existing.getMetadata() != null) {
                        mergedMetadata.putAll(existing.getMetadata());
                    }
                    if (metadataPatch != null) {
                        mergedMetadata.putAll(metadataPatch);
                    }
                    updatedDo.setMetadata(mergedMetadata);

                    rawDataMapper.updateById(updatedDo);
                });
    }

    // ===== MemoryItem =====

    @Override
    public void addItem(MemoryId id, MemoryItem item) {
        MemoryItemDO dataObject = ItemConverter.toDO(id, item);
        itemMapper.insert(dataObject);
    }

    @Override
    public void addItems(MemoryId id, List<MemoryItem> items) {
        items.forEach(item -> addItem(id, item));
    }

    @Override
    public Optional<MemoryItem> getItem(MemoryId id, Long itemId) {
        MemoryItemDO dataObject =
                itemMapper.selectOne(memoryQuery(id, MemoryItemDO.class).eq("biz_id", itemId));
        return Optional.ofNullable(dataObject).map(ItemConverter::toRecord);
    }

    @Override
    public Optional<MemoryItem> getItemByContentHash(MemoryId id, String contentHash) {
        MemoryItemDO dataObject =
                itemMapper.selectOne(
                        memoryQuery(id, MemoryItemDO.class).eq("content_hash", contentHash));
        return Optional.ofNullable(dataObject).map(ItemConverter::toRecord);
    }

    @Override
    public List<MemoryItem> getItemsByContentHashes(MemoryId id, Collection<String> contentHashes) {
        if (contentHashes.isEmpty()) {
            return List.of();
        }
        return itemMapper
                .selectList(memoryQuery(id, MemoryItemDO.class).in("content_hash", contentHashes))
                .stream()
                .map(ItemConverter::toRecord)
                .toList();
    }

    @Override
    public List<MemoryItem> getItemsByVectorIds(MemoryId id, Collection<String> vectorIds) {
        if (vectorIds.isEmpty()) {
            return List.of();
        }
        return itemMapper
                .selectList(memoryQuery(id, MemoryItemDO.class).in("vector_id", vectorIds))
                .stream()
                .map(ItemConverter::toRecord)
                .toList();
    }

    @Override
    public List<MemoryItem> getItemsByIds(MemoryId id, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return itemMapper
                .selectList(memoryQuery(id, MemoryItemDO.class).in("biz_id", itemIds))
                .stream()
                .map(ItemConverter::toRecord)
                .toList();
    }

    @Override
    public List<MemoryItem> getAllItems(MemoryId id) {
        return itemMapper.selectList(memoryQuery(id, MemoryItemDO.class)).stream()
                .map(ItemConverter::toRecord)
                .toList();
    }

    @Override
    public List<MemoryItem> getRecentItems(MemoryId id, int limit) {
        return itemMapper
                .selectList(
                        memoryQuery(id, MemoryItemDO.class)
                                .orderByDesc("created_at")
                                .last("LIMIT " + limit))
                .stream()
                .map(ItemConverter::toRecord)
                .toList();
    }

    @Override
    public boolean hasItems(MemoryId id) {
        return itemMapper.selectCount(memoryQuery(id, MemoryItemDO.class)) > 0;
    }

    @Override
    public List<MemoryItem> getItemsByRawDataId(MemoryId id, String rawDataId) {
        return itemMapper
                .selectList(memoryQuery(id, MemoryItemDO.class).eq("raw_data_id", rawDataId))
                .stream()
                .map(ItemConverter::toRecord)
                .toList();
    }

    @Override
    public List<MemoryItem> getItemsByScope(MemoryId id, MemoryScope scope) {
        return itemMapper
                .selectList(memoryQuery(id, MemoryItemDO.class).eq("scope", scope.name()))
                .stream()
                .map(ItemConverter::toRecord)
                .toList();
    }

    @Override
    public void updateItems(MemoryId id, List<MemoryItem> items) {
        items.forEach(
                item -> {
                    MemoryItemDO existing =
                            itemMapper.selectOne(
                                    memoryQuery(id, MemoryItemDO.class).eq("biz_id", item.id()));
                    if (existing != null) {
                        MemoryItemDO dataObject = ItemConverter.toDO(id, item);
                        dataObject.setId(existing.getId());
                        itemMapper.updateById(dataObject);
                    }
                });
    }

    @Override
    public void deleteItem(MemoryId id, Long itemId) {
        itemMapper.delete(memoryQuery(id, MemoryItemDO.class).eq("biz_id", itemId));
    }

    // ===== MemoryInsightType =====

    @Override
    public void saveInsightType(MemoryId id, MemoryInsightType insightType) {
        MemoryInsightTypeDO existing =
                insightTypeMapper.selectOne(
                        appQuery(id, MemoryInsightTypeDO.class).eq("name", insightType.name()));
        MemoryInsightTypeDO dataObject = InsightTypeConverter.toDO(id, insightType);
        if (existing != null) {
            dataObject.setId(existing.getId());
            insightTypeMapper.updateById(dataObject);
        } else {
            insightTypeMapper.insert(dataObject);
        }
    }

    @Override
    public Optional<MemoryInsightType> getInsightType(MemoryId id, String insightType) {
        MemoryInsightTypeDO dataObject =
                insightTypeMapper.selectOne(
                        appQuery(id, MemoryInsightTypeDO.class).eq("name", insightType));
        if (dataObject == null) {
            return Optional.empty();
        }
        return Optional.of(InsightTypeConverter.toRecord(dataObject));
    }

    @Override
    public List<MemoryInsightType> getAllInsightTypes(MemoryId id) {
        return insightTypeMapper.selectList(appQuery(id, MemoryInsightTypeDO.class)).stream()
                .map(InsightTypeConverter::toRecord)
                .toList();
    }

    @Override
    public void deleteInsightType(MemoryId id, String insightType) {
        MemoryInsightTypeDO existing =
                insightTypeMapper.selectOne(
                        appQuery(id, MemoryInsightTypeDO.class).eq("name", insightType));
        if (existing != null) {
            insightTypeMapper.deleteById(existing.getId());
        }
    }

    // ===== MemoryInsight =====

    @Override
    public void saveInsight(MemoryId id, MemoryInsight insight) {
        MemoryInsightDO existing =
                insightMapper.selectOne(
                        memoryQuery(id, MemoryInsightDO.class).eq("biz_id", insight.id()));
        MemoryInsightDO dataObject = InsightConverter.toDO(id, insight);
        if (existing != null) {
            dataObject.setId(existing.getId());
            insightMapper.updateById(dataObject);
        } else {
            insightMapper.insert(dataObject);
        }
    }

    @Transactional
    @Override
    public void saveInsights(MemoryId id, List<MemoryInsight> insights) {
        insights.forEach(insight -> saveInsight(id, insight));
    }

    @Override
    public Optional<MemoryInsight> getInsight(MemoryId id, Long insightId) {
        MemoryInsightDO dataObject =
                insightMapper.selectOne(
                        memoryQuery(id, MemoryInsightDO.class).eq("biz_id", insightId));
        if (dataObject == null) {
            return Optional.empty();
        }
        return Optional.of(InsightConverter.toRecord(dataObject));
    }

    @Override
    public List<MemoryInsight> getAllInsights(MemoryId id) {
        return insightMapper.selectList(memoryQuery(id, MemoryInsightDO.class)).stream()
                .map(InsightConverter::toRecord)
                .toList();
    }

    @Override
    public List<MemoryInsight> getInsightsByTypeId(MemoryId id, String insightType) {
        return insightMapper
                .selectList(memoryQuery(id, MemoryInsightDO.class).eq("type", insightType))
                .stream()
                .map(InsightConverter::toRecord)
                .toList();
    }

    @Override
    public List<MemoryInsight> getInsightsByScope(MemoryId id, MemoryScope scope) {
        return insightMapper
                .selectList(memoryQuery(id, MemoryInsightDO.class).eq("scope", scope.name()))
                .stream()
                .map(InsightConverter::toRecord)
                .toList();
    }

    @Override
    public void deleteInsight(MemoryId id, Long insightId) {
        MemoryInsightDO existing =
                insightMapper.selectOne(
                        memoryQuery(id, MemoryInsightDO.class).eq("biz_id", insightId));
        if (existing != null) {
            insightMapper.deleteById(existing.getId());
        }
    }

    // ===== Shared tree query optimization =====

    @Override
    public Optional<MemoryInsight> getLeafByGroup(MemoryId id, String type, String group) {
        var dataObject =
                insightMapper.selectOne(
                        memoryQuery(id, MemoryInsightDO.class)
                                .eq("type", type)
                                .eq("tier", InsightTier.LEAF.name())
                                .eq("group_name", group)
                                .last("LIMIT 1"));
        return Optional.ofNullable(dataObject).map(InsightConverter::toRecord);
    }

    @Override
    public Optional<MemoryInsight> getBranchByType(MemoryId id, String type) {
        var dataObject =
                insightMapper.selectOne(
                        memoryQuery(id, MemoryInsightDO.class)
                                .eq("type", type)
                                .eq("tier", InsightTier.BRANCH.name())
                                .last("LIMIT 1"));
        return Optional.ofNullable(dataObject).map(InsightConverter::toRecord);
    }

    @Override
    public Optional<MemoryInsight> getRootByType(MemoryId id, String type) {
        var dataObject =
                insightMapper.selectOne(
                        memoryQuery(id, MemoryInsightDO.class)
                                .eq("type", type)
                                .eq("tier", InsightTier.ROOT.name())
                                .last("LIMIT 1"));
        return Optional.ofNullable(dataObject).map(InsightConverter::toRecord);
    }

    @Override
    public List<MemoryInsight> getAllInsightsByTier(MemoryId id, InsightTier tier) {
        return insightMapper
                .selectList(memoryQuery(id, MemoryInsightDO.class).eq("tier", tier.name()))
                .stream()
                .map(InsightConverter::toRecord)
                .toList();
    }

    // ===== Helper methods =====

    private <T> QueryWrapper<T> memoryQuery(MemoryId id, Class<T> clazz) {
        return new QueryWrapper<T>()
                .eq("user_id", id.getAttribute("userId"))
                .eq("agent_id", id.getAttribute("agentId"));
    }

    /**
     * InsightType is global (not scoped by userId/agentId), so no extra conditions needed.
     */
    private <T> QueryWrapper<T> appQuery(MemoryId id, Class<T> clazz) {
        return new QueryWrapper<>();
    }
}
