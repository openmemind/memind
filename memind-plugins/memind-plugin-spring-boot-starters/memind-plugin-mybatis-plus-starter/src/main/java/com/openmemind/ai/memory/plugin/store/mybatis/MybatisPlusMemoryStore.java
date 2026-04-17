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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.MemoryResource;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.NoOpGraphOperations;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.store.resource.ResourceOperations;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.InsightConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.InsightTypeConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.ItemConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.RawDataConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.ResourceConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightTypeDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryRawDataDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryResourceDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightTypeMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryRawDataMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryResourceMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

public class MybatisPlusMemoryStore
        implements MemoryStore,
                RawDataOperations,
                ItemOperations,
                InsightOperations,
                ResourceOperations {

    private final MemoryRawDataMapper rawDataMapper;
    private final MemoryItemMapper itemMapper;
    private final MemoryInsightTypeMapper insightTypeMapper;
    private final MemoryInsightMapper insightMapper;
    private final MemoryResourceMapper resourceMapper;
    private final ResourceStore resourceStore;
    private final GraphOperations graphOperations;

    public MybatisPlusMemoryStore(
            MemoryRawDataMapper rawDataMapper,
            MemoryItemMapper itemMapper,
            MemoryInsightTypeMapper insightTypeMapper,
            MemoryInsightMapper insightMapper) {
        this(rawDataMapper, itemMapper, insightTypeMapper, insightMapper, null, null, null);
    }

    public MybatisPlusMemoryStore(
            MemoryRawDataMapper rawDataMapper,
            MemoryItemMapper itemMapper,
            MemoryInsightTypeMapper insightTypeMapper,
            MemoryInsightMapper insightMapper,
            MemoryResourceMapper resourceMapper,
            ResourceStore resourceStore) {
        this(
                rawDataMapper,
                itemMapper,
                insightTypeMapper,
                insightMapper,
                resourceMapper,
                resourceStore,
                null);
    }

    public MybatisPlusMemoryStore(
            MemoryRawDataMapper rawDataMapper,
            MemoryItemMapper itemMapper,
            MemoryInsightTypeMapper insightTypeMapper,
            MemoryInsightMapper insightMapper,
            MemoryResourceMapper resourceMapper,
            ResourceStore resourceStore,
            GraphOperations graphOperations) {
        this.rawDataMapper = rawDataMapper;
        this.itemMapper = itemMapper;
        this.insightTypeMapper = insightTypeMapper;
        this.insightMapper = insightMapper;
        this.resourceMapper = resourceMapper;
        this.resourceStore = resourceStore;
        this.graphOperations =
                graphOperations != null ? graphOperations : NoOpGraphOperations.INSTANCE;
    }

    @Override
    public RawDataOperations rawDataOperations() {
        return this;
    }

    @Override
    public ItemOperations itemOperations() {
        return this;
    }

    @Override
    public InsightOperations insightOperations() {
        return this;
    }

    @Override
    public ResourceOperations resourceOperations() {
        return this;
    }

    @Override
    public ResourceStore resourceStore() {
        return resourceStore;
    }

    @Override
    public GraphOperations graphOperations() {
        return graphOperations;
    }

    // ===== MemoryRawData =====

    @Override
    public void upsertRawData(MemoryId id, List<MemoryRawData> rawDataList) {
        upsertRawDataWithResources(id, List.of(), rawDataList);
    }

    @Override
    @Transactional
    public void upsertRawDataWithResources(
            MemoryId id, List<MemoryResource> resources, List<MemoryRawData> rawDataList) {
        if (resources != null && !resources.isEmpty()) {
            upsertResources(id, resources);
        }
        if (rawDataList == null || rawDataList.isEmpty()) {
            return;
        }

        Map<String, MemoryRawDataDO> existingByBizId =
                rawDataMapper
                        .selectList(
                                memoryQuery(id, MemoryRawDataDO.class)
                                        .in(
                                                "biz_id",
                                                rawDataList.stream()
                                                        .map(MemoryRawData::id)
                                                        .toList()))
                        .stream()
                        .collect(Collectors.toMap(MemoryRawDataDO::getBizId, Function.identity()));

        rawDataList.forEach(
                rawData -> {
                    MemoryRawDataDO dataObject = RawDataConverter.toDO(id, rawData);
                    MemoryRawDataDO existing = existingByBizId.get(rawData.id());
                    if (existing != null) {
                        dataObject.setId(existing.getId());
                        rawDataMapper.updateById(dataObject);
                    } else {
                        rawDataMapper.insert(dataObject);
                    }
                });
    }

    @Override
    @Transactional
    public void upsertResources(MemoryId id, List<MemoryResource> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        if (resourceMapper == null) {
            throw new IllegalStateException("MemoryResourceMapper is required");
        }

        Map<String, MemoryResourceDO> existingByBizId =
                resourceMapper
                        .selectList(
                                memoryQuery(id, MemoryResourceDO.class)
                                        .in(
                                                "biz_id",
                                                resources.stream()
                                                        .map(MemoryResource::id)
                                                        .toList()))
                        .stream()
                        .collect(Collectors.toMap(MemoryResourceDO::getBizId, Function.identity()));

        resources.forEach(
                resource -> {
                    MemoryResourceDO dataObject = ResourceConverter.toDO(id, resource);
                    MemoryResourceDO existing = existingByBizId.get(resource.id());
                    if (existing != null) {
                        dataObject.setId(existing.getId());
                        resourceMapper.updateById(dataObject);
                    } else {
                        resourceMapper.insert(dataObject);
                    }
                });
    }

    @Override
    public Optional<MemoryResource> getResource(MemoryId id, String resourceId) {
        if (resourceMapper == null) {
            return Optional.empty();
        }
        MemoryResourceDO dataObject =
                resourceMapper.selectOne(
                        memoryQuery(id, MemoryResourceDO.class).eq("biz_id", resourceId));
        return Optional.ofNullable(dataObject).map(ResourceConverter::toRecord);
    }

    @Override
    public List<MemoryResource> listResources(MemoryId id) {
        if (resourceMapper == null) {
            return List.of();
        }
        return resourceMapper.selectList(memoryQuery(id, MemoryResourceDO.class)).stream()
                .map(ResourceConverter::toRecord)
                .toList();
    }

    @Override
    public Optional<MemoryRawData> getRawData(MemoryId id, String rawDataId) {
        MemoryRawDataDO dataObject =
                rawDataMapper.selectOne(
                        memoryQuery(id, MemoryRawDataDO.class).eq("biz_id", rawDataId));
        return Optional.ofNullable(dataObject).map(RawDataConverter::toRecord);
    }

    @Override
    public List<MemoryRawData> listRawData(MemoryId id) {
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
    @Transactional
    public void updateRawDataVectorIds(
            MemoryId id, Map<String, String> vectorIds, Map<String, Object> metadataPatch) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }

        Map<String, MemoryRawDataDO> existingByBizId =
                rawDataMapper
                        .selectList(
                                memoryQuery(id, MemoryRawDataDO.class)
                                        .in("biz_id", vectorIds.keySet()))
                        .stream()
                        .collect(Collectors.toMap(MemoryRawDataDO::getBizId, Function.identity()));

        vectorIds.forEach(
                (bizId, vectorId) -> {
                    MemoryRawDataDO existing = existingByBizId.get(bizId);
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
    @Transactional
    public void insertItems(MemoryId id, List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        items.forEach(item -> itemMapper.insert(ItemConverter.toDO(id, item)));
    }

    @Override
    public List<MemoryItem> getItemsByContentHashes(MemoryId id, Collection<String> contentHashes) {
        if (contentHashes == null || contentHashes.isEmpty()) {
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
        if (vectorIds == null || vectorIds.isEmpty()) {
            return List.of();
        }
        return itemMapper
                .selectList(memoryQuery(id, MemoryItemDO.class).in("vector_id", vectorIds))
                .stream()
                .map(ItemConverter::toRecord)
                .toList();
    }

    @Override
    public List<MemoryItem> getItemsByIds(MemoryId id, Collection<Long> itemIds) {
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
    public List<MemoryItem> listItems(MemoryId id) {
        return itemMapper.selectList(memoryQuery(id, MemoryItemDO.class)).stream()
                .map(ItemConverter::toRecord)
                .toList();
    }

    @Override
    public boolean hasItems(MemoryId id) {
        return itemMapper.selectCount(memoryQuery(id, MemoryItemDO.class)) > 0;
    }

    @Override
    public void deleteItems(MemoryId id, Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        itemMapper.delete(memoryQuery(id, MemoryItemDO.class).in("biz_id", itemIds));
    }

    // ===== MemoryInsightType =====

    @Override
    @Transactional
    public void upsertInsightTypes(List<MemoryInsightType> insightTypes) {
        if (insightTypes == null || insightTypes.isEmpty()) {
            return;
        }

        Map<String, MemoryInsightTypeDO> existingByName =
                insightTypeMapper
                        .selectList(
                                appQuery(MemoryInsightTypeDO.class)
                                        .in(
                                                "name",
                                                insightTypes.stream()
                                                        .map(MemoryInsightType::name)
                                                        .toList()))
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        MemoryInsightTypeDO::getName, Function.identity()));

        insightTypes.forEach(
                insightType -> {
                    MemoryInsightTypeDO dataObject = InsightTypeConverter.toDO(insightType);
                    MemoryInsightTypeDO existing = existingByName.get(insightType.name());
                    if (existing != null) {
                        dataObject.setId(existing.getId());
                        insightTypeMapper.updateById(dataObject);
                    } else {
                        insightTypeMapper.insert(dataObject);
                    }
                });
    }

    @Override
    public Optional<MemoryInsightType> getInsightType(String insightType) {
        MemoryInsightTypeDO dataObject =
                insightTypeMapper.selectOne(
                        appQuery(MemoryInsightTypeDO.class).eq("name", insightType));
        if (dataObject == null) {
            return Optional.empty();
        }
        return Optional.of(InsightTypeConverter.toRecord(dataObject));
    }

    @Override
    public List<MemoryInsightType> listInsightTypes() {
        return insightTypeMapper.selectList(appQuery(MemoryInsightTypeDO.class)).stream()
                .map(InsightTypeConverter::toRecord)
                .toList();
    }

    // ===== MemoryInsight =====

    @Override
    @Transactional
    public void upsertInsights(MemoryId id, List<MemoryInsight> insights) {
        if (insights == null || insights.isEmpty()) {
            return;
        }

        Map<Long, MemoryInsightDO> existingByBizId =
                insightMapper
                        .selectList(
                                memoryQuery(id, MemoryInsightDO.class)
                                        .in(
                                                "biz_id",
                                                insights.stream().map(MemoryInsight::id).toList()))
                        .stream()
                        .collect(Collectors.toMap(MemoryInsightDO::getBizId, Function.identity()));

        insights.forEach(
                insight -> {
                    MemoryInsightDO dataObject = InsightConverter.toDO(id, insight);
                    MemoryInsightDO existing = existingByBizId.get(insight.id());
                    if (existing != null) {
                        dataObject.setId(existing.getId());
                        insightMapper.updateById(dataObject);
                    } else {
                        insightMapper.insert(dataObject);
                    }
                });
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
    public List<MemoryInsight> listInsights(MemoryId id) {
        return insightMapper.selectList(memoryQuery(id, MemoryInsightDO.class)).stream()
                .map(InsightConverter::toRecord)
                .toList();
    }

    @Override
    public List<MemoryInsight> getInsightsByType(MemoryId id, String insightType) {
        return insightMapper
                .selectList(memoryQuery(id, MemoryInsightDO.class).eq("type", insightType))
                .stream()
                .map(InsightConverter::toRecord)
                .toList();
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
    public List<MemoryInsight> getInsightsByTier(MemoryId id, InsightTier tier) {
        return insightMapper
                .selectList(memoryQuery(id, MemoryInsightDO.class).eq("tier", tier.name()))
                .stream()
                .map(InsightConverter::toRecord)
                .toList();
    }

    @Override
    public void deleteInsights(MemoryId id, Collection<Long> insightIds) {
        if (insightIds == null || insightIds.isEmpty()) {
            return;
        }
        insightMapper.delete(memoryQuery(id, MemoryInsightDO.class).in("biz_id", insightIds));
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
    private <T> QueryWrapper<T> appQuery(Class<T> clazz) {
        return new QueryWrapper<>();
    }
}
