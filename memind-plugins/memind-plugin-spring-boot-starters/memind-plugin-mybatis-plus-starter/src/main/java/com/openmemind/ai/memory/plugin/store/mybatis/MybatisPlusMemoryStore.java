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
import com.openmemind.ai.memory.core.data.enums.MemoryThreadEventType;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadIntakeStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadLifecycleStatus;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadMembershipRole;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadObjectState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadProjectionState;
import com.openmemind.ai.memory.core.data.enums.MemoryThreadType;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEnrichmentInput;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadEvent;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeClaim;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadIntakeOutboxEntry;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadMembership;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadProjection;
import com.openmemind.ai.memory.core.data.thread.MemoryThreadRuntimeState;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.GraphOperationsCapabilities;
import com.openmemind.ai.memory.core.store.graph.ItemGraphCommitOperations;
import com.openmemind.ai.memory.core.store.graph.NoOpGraphOperations;
import com.openmemind.ai.memory.core.store.graph.NoOpItemGraphCommitOperations;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.item.TemporalCandidateMatch;
import com.openmemind.ai.memory.core.store.item.TemporalCandidateRequest;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.store.resource.ResourceOperations;
import com.openmemind.ai.memory.core.store.thread.NoOpThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.store.thread.NoOpThreadProjectionStore;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentAppendResult;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
import com.openmemind.ai.memory.core.utils.JsonUtils;
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
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadEnrichmentInputDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadEventDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadIntakeOutboxDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadMembershipDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadProjectionDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadRuntimeDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightTypeMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryRawDataMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryResourceMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadEnrichmentInputMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadEventMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadIntakeOutboxMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadMembershipMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadProjectionMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadRuntimeMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialect;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.SerializationFeature;

public class MybatisPlusMemoryStore
        implements MemoryStore,
                RawDataOperations,
                ItemOperations,
                InsightOperations,
                ResourceOperations,
                ThreadProjectionStore,
                ThreadEnrichmentInputStore {

    private final MemoryRawDataMapper rawDataMapper;
    private final MemoryItemMapper itemMapper;
    private final MemoryInsightTypeMapper insightTypeMapper;
    private final MemoryInsightMapper insightMapper;
    private final MemoryResourceMapper resourceMapper;
    private final ResourceStore resourceStore;
    private final GraphOperations graphOperations;
    private final GraphOperationsCapabilities graphOperationsCapabilities;
    private final ItemGraphCommitOperations itemGraphCommitOperations;
    private final DatabaseDialect dialect;
    private final MemoryThreadProjectionMapper threadProjectionMapper;
    private final MemoryThreadEventMapper threadEventMapper;
    private final MemoryThreadEnrichmentInputMapper threadEnrichmentInputMapper;
    private final MemoryThreadMembershipMapper threadMembershipMapper;
    private final MemoryThreadIntakeOutboxMapper threadIntakeOutboxMapper;
    private final MemoryThreadRuntimeMapper threadRuntimeMapper;

    public MybatisPlusMemoryStore(
            MemoryRawDataMapper rawDataMapper,
            MemoryItemMapper itemMapper,
            MemoryInsightTypeMapper insightTypeMapper,
            MemoryInsightMapper insightMapper) {
        this(
                rawDataMapper,
                itemMapper,
                insightTypeMapper,
                insightMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
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
        this(
                rawDataMapper,
                itemMapper,
                insightTypeMapper,
                insightMapper,
                resourceMapper,
                resourceStore,
                null,
                graphOperations,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public MybatisPlusMemoryStore(
            MemoryRawDataMapper rawDataMapper,
            MemoryItemMapper itemMapper,
            MemoryInsightTypeMapper insightTypeMapper,
            MemoryInsightMapper insightMapper,
            MemoryResourceMapper resourceMapper,
            ResourceStore resourceStore,
            DatabaseDialect dialect,
            GraphOperations graphOperations,
            GraphOperationsCapabilities graphOperationsCapabilities,
            MemoryThreadMapper threadMapper,
            MemoryThreadProjectionMapper threadProjectionMapper,
            MemoryThreadEventMapper threadEventMapper,
            MemoryThreadEnrichmentInputMapper threadEnrichmentInputMapper,
            MemoryThreadMembershipMapper threadMembershipMapper,
            MemoryThreadIntakeOutboxMapper threadIntakeOutboxMapper,
            MemoryThreadRuntimeMapper threadRuntimeMapper,
            ItemGraphCommitOperations itemGraphCommitOperations) {
        this.rawDataMapper = rawDataMapper;
        this.itemMapper = itemMapper;
        this.insightTypeMapper = insightTypeMapper;
        this.insightMapper = insightMapper;
        this.resourceMapper = resourceMapper;
        this.resourceStore = resourceStore;
        this.dialect = dialect;
        this.graphOperations =
                graphOperations != null ? graphOperations : NoOpGraphOperations.INSTANCE;
        this.graphOperationsCapabilities =
                graphOperationsCapabilities != null
                        ? graphOperationsCapabilities
                        : GraphOperationsCapabilities.NONE;
        this.itemGraphCommitOperations =
                itemGraphCommitOperations != null
                        ? itemGraphCommitOperations
                        : NoOpItemGraphCommitOperations.INSTANCE;
        this.threadProjectionMapper = threadProjectionMapper;
        this.threadEventMapper = threadEventMapper;
        this.threadEnrichmentInputMapper = threadEnrichmentInputMapper;
        this.threadMembershipMapper = threadMembershipMapper;
        this.threadIntakeOutboxMapper = threadIntakeOutboxMapper;
        this.threadRuntimeMapper = threadRuntimeMapper;
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

    @Override
    public GraphOperationsCapabilities graphOperationsCapabilities() {
        return graphOperationsCapabilities;
    }

    @Override
    public ItemGraphCommitOperations itemGraphCommitOperations() {
        return itemGraphCommitOperations;
    }

    @Override
    public ThreadProjectionStore threadOperations() {
        return threadProjectionMapper != null
                        && threadEventMapper != null
                        && threadMembershipMapper != null
                        && threadIntakeOutboxMapper != null
                        && threadRuntimeMapper != null
                ? this
                : NoOpThreadProjectionStore.INSTANCE;
    }

    @Override
    public ThreadEnrichmentInputStore threadEnrichmentInputStore() {
        return threadEnrichmentInputMapper != null && threadIntakeOutboxMapper != null
                ? this
                : NoOpThreadEnrichmentInputStore.INSTANCE;
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

    @Override
    public List<TemporalCandidateMatch> listTemporalCandidateMatches(
            MemoryId id, List<TemporalCandidateRequest> requests, Collection<Long> excludeItemIds) {
        if (dialect == null || requests == null || requests.isEmpty()) {
            return ItemOperations.super.listTemporalCandidateMatches(id, requests, excludeItemIds);
        }

        Collection<Long> effectiveExcludeIds = excludeItemIds != null ? excludeItemIds : List.of();
        var matches = new ArrayList<TemporalCandidateMatch>();
        for (var request : requests) {
            var seenCandidateIds = new LinkedHashSet<Long>();
            String categoryName = request.category() != null ? request.category().name() : null;
            appendNativeMatches(
                    matches,
                    seenCandidateIds,
                    request,
                    itemMapper.selectTemporalOverlapCandidates(
                            dialect,
                            id.toIdentifier(),
                            request.itemType().name(),
                            categoryName,
                            effectiveExcludeIds,
                            request.sourceStart(),
                            request.sourceEndOrAnchor(),
                            request.sourceAnchor(),
                            request.overlapLimit()));
            appendNativeMatches(
                    matches,
                    seenCandidateIds,
                    request,
                    itemMapper.selectTemporalBeforeCandidates(
                            dialect,
                            id.toIdentifier(),
                            request.itemType().name(),
                            categoryName,
                            effectiveExcludeIds,
                            request.sourceAnchor(),
                            request.beforeLimit()));
            appendNativeMatches(
                    matches,
                    seenCandidateIds,
                    request,
                    itemMapper.selectTemporalAfterCandidates(
                            dialect,
                            id.toIdentifier(),
                            request.itemType().name(),
                            categoryName,
                            effectiveExcludeIds,
                            request.sourceAnchor(),
                            request.afterLimit()));
        }
        return List.copyOf(matches);
    }

    private static void appendNativeMatches(
            List<TemporalCandidateMatch> matches,
            LinkedHashSet<Long> seenCandidateIds,
            TemporalCandidateRequest request,
            List<MemoryItemDO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (var candidate : candidates) {
            if (!seenCandidateIds.add(candidate.getBizId())) {
                continue;
            }
            matches.add(
                    new TemporalCandidateMatch(
                            request.sourceItemId(), ItemConverter.toRecord(candidate)));
        }
    }

    // ===== ThreadProjectionStore =====

    @Override
    @Transactional
    public void ensureRuntime(MemoryId memoryId, String materializationPolicyVersion) {
        if (threadRuntimeMapper == null) {
            return;
        }
        String memoryIdentifier = memoryId.toIdentifier();
        if (threadRuntimeMapper.selectById(memoryIdentifier) != null) {
            return;
        }
        upsertRuntime(
                new MemoryThreadRuntimeState(
                        memoryIdentifier,
                        MemoryThreadProjectionState.REBUILD_REQUIRED,
                        0,
                        0,
                        null,
                        null,
                        false,
                        null,
                        0L,
                        materializationPolicyVersion,
                        "runtime bootstrap",
                        Instant.now()));
    }

    @Override
    public Optional<MemoryThreadRuntimeState> getRuntime(MemoryId memoryId) {
        if (threadRuntimeMapper == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(threadRuntimeMapper.selectById(memoryId.toIdentifier()))
                .map(MybatisPlusMemoryStore::toRuntimeRecord);
    }

    @Override
    public List<MemoryThreadProjection> listThreads(MemoryId memoryId) {
        if (threadProjectionMapper == null) {
            return List.of();
        }
        return threadProjectionMapper
                .selectList(
                        threadMemoryQuery(memoryId.toIdentifier(), MemoryThreadProjectionDO.class)
                                .orderByAsc("thread_key"))
                .stream()
                .map(MybatisPlusMemoryStore::toProjectionRecord)
                .toList();
    }

    @Override
    public Optional<MemoryThreadProjection> getThread(MemoryId memoryId, String threadKey) {
        if (threadProjectionMapper == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                        threadProjectionMapper.selectOne(
                                threadMemoryQuery(
                                                memoryId.toIdentifier(),
                                                MemoryThreadProjectionDO.class)
                                        .eq("thread_key", threadKey)
                                        .last("LIMIT 1")))
                .map(MybatisPlusMemoryStore::toProjectionRecord);
    }

    @Override
    public List<MemoryThreadEvent> listEvents(MemoryId memoryId, String threadKey) {
        if (threadEventMapper == null) {
            return List.of();
        }
        return threadEventMapper
                .selectList(
                        threadMemoryQuery(memoryId.toIdentifier(), MemoryThreadEventDO.class)
                                .eq("thread_key", threadKey)
                                .orderByAsc("event_seq", "event_key"))
                .stream()
                .map(MybatisPlusMemoryStore::toEventRecord)
                .toList();
    }

    @Override
    public List<MemoryThreadMembership> listMemberships(MemoryId memoryId, String threadKey) {
        if (threadMembershipMapper == null) {
            return List.of();
        }
        return threadMembershipMapper
                .selectList(
                        threadMemoryQuery(memoryId.toIdentifier(), MemoryThreadMembershipDO.class)
                                .eq("thread_key", threadKey)
                                .orderByAsc("item_id", "role"))
                .stream()
                .map(MybatisPlusMemoryStore::toMembershipRecord)
                .toList();
    }

    @Override
    public List<MemoryThreadProjection> listThreadsByItemId(MemoryId memoryId, long itemId) {
        if (threadMembershipMapper == null || threadProjectionMapper == null) {
            return List.of();
        }
        List<String> threadKeys =
                threadMembershipMapper
                        .selectList(
                                threadMemoryQuery(
                                                memoryId.toIdentifier(),
                                                MemoryThreadMembershipDO.class)
                                        .eq("item_id", itemId)
                                        .orderByAsc("thread_key"))
                        .stream()
                        .map(MemoryThreadMembershipDO::getThreadKey)
                        .distinct()
                        .toList();
        if (threadKeys.isEmpty()) {
            return List.of();
        }
        return threadProjectionMapper
                .selectList(
                        threadMemoryQuery(memoryId.toIdentifier(), MemoryThreadProjectionDO.class)
                                .in("thread_key", threadKeys)
                                .orderByAsc("thread_key"))
                .stream()
                .map(MybatisPlusMemoryStore::toProjectionRecord)
                .toList();
    }

    @Override
    @Transactional
    public void enqueue(MemoryId memoryId, long triggerItemId) {
        enqueueInternal(memoryId, triggerItemId, false);
    }

    @Override
    @Transactional
    public void enqueueReplay(MemoryId memoryId, long replayCutoffItemId) {
        enqueueInternal(memoryId, replayCutoffItemId, true);
    }

    private void enqueueInternal(
            MemoryId memoryId, long triggerItemId, boolean replayableExisting) {
        if (threadIntakeOutboxMapper == null) {
            return;
        }
        String memoryIdentifier = memoryId.toIdentifier();
        Instant now = Instant.now();
        MemoryThreadIntakeOutboxDO existing = outboxRow(memoryIdentifier, triggerItemId);
        if (existing == null) {
            MemoryThreadIntakeOutboxDO row = new MemoryThreadIntakeOutboxDO();
            row.setMemoryId(memoryIdentifier);
            row.setTriggerItemId(triggerItemId);
            row.setEnqueueGeneration(1L);
            row.setStatus(MemoryThreadIntakeStatus.PENDING.name());
            row.setAttemptCount(0);
            row.setEnqueuedAt(now);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            threadIntakeOutboxMapper.insert(row);
        } else if (replayableExisting
                && !Objects.equals(existing.getStatus(), MemoryThreadIntakeStatus.PENDING.name())) {
            existing.setEnqueueGeneration(
                    existing.getEnqueueGeneration() != null
                            ? existing.getEnqueueGeneration() + 1L
                            : 1L);
            existing.setStatus(MemoryThreadIntakeStatus.PENDING.name());
            existing.setClaimedAt(null);
            existing.setLeaseExpiresAt(null);
            existing.setFailureReason(null);
            existing.setFinalizedAt(null);
            existing.setEnqueuedAt(now);
            existing.setUpdatedAt(now);
            threadIntakeOutboxMapper.updateById(existing);
        }

        MemoryThreadRuntimeDO runtime =
                threadRuntimeMapper != null
                        ? threadRuntimeMapper.selectById(memoryIdentifier)
                        : null;
        if (runtime != null) {
            runtime.setLastEnqueuedItemId(
                    runtime.getLastEnqueuedItemId() == null
                            ? triggerItemId
                            : Math.max(runtime.getLastEnqueuedItemId(), triggerItemId));
            runtime.setUpdatedAt(now);
            refreshRuntimeCounters(memoryIdentifier, runtime);
        }
    }

    @Override
    @Transactional
    public ThreadEnrichmentAppendResult appendRunAndEnqueueReplay(
            MemoryId memoryId,
            long replayCutoffItemId,
            List<MemoryThreadEnrichmentInput> runInputs) {
        Objects.requireNonNull(memoryId, "memoryId");
        if (replayCutoffItemId <= 0L) {
            throw new IllegalArgumentException("replayCutoffItemId must be positive");
        }
        if (runInputs == null || runInputs.isEmpty()) {
            throw new IllegalArgumentException("runInputs must not be empty");
        }
        if (threadEnrichmentInputMapper == null || threadIntakeOutboxMapper == null) {
            return ThreadEnrichmentAppendResult.DUPLICATE_EQUIVALENT;
        }

        String memoryIdentifier = memoryId.toIdentifier();
        LinkedHashSet<String> runKeys = new LinkedHashSet<>();
        for (MemoryThreadEnrichmentInput input : runInputs) {
            validateEnrichmentInput(memoryIdentifier, input);
            runKeys.add(input.inputRunKey());
        }

        Map<String, MemoryThreadEnrichmentInputDO> existingByKey =
                threadEnrichmentInputMapper
                        .selectList(
                                threadMemoryQuery(
                                                memoryIdentifier,
                                                MemoryThreadEnrichmentInputDO.class)
                                        .in("input_run_key", runKeys))
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        dataObject ->
                                                enrichmentInputKey(
                                                        dataObject.getInputRunKey(),
                                                        dataObject.getEntrySeq()),
                                        Function.identity(),
                                        (left, right) -> left));

        List<MemoryThreadEnrichmentInput> toInsert = new ArrayList<>();
        for (MemoryThreadEnrichmentInput input : runInputs) {
            MemoryThreadEnrichmentInputDO existing =
                    existingByKey.get(enrichmentInputKey(input.inputRunKey(), input.entrySeq()));
            if (existing == null) {
                toInsert.add(input);
                continue;
            }
            if (!equivalent(existing, input)) {
                throw new IllegalStateException("conflicting duplicate enrichment input");
            }
        }
        if (toInsert.isEmpty()) {
            return ThreadEnrichmentAppendResult.DUPLICATE_EQUIVALENT;
        }

        for (MemoryThreadEnrichmentInput input : toInsert) {
            threadEnrichmentInputMapper.insert(toEnrichmentInputDO(input));
        }
        enqueueInternal(memoryId, replayCutoffItemId, true);
        return ThreadEnrichmentAppendResult.INSERTED;
    }

    @Override
    public List<MemoryThreadEnrichmentInput> listReplayable(
            MemoryId memoryId, long cutoffItemId, String materializationPolicyVersion) {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(materializationPolicyVersion, "materializationPolicyVersion");
        if (cutoffItemId <= 0L || threadEnrichmentInputMapper == null) {
            return List.of();
        }
        return threadEnrichmentInputMapper
                .selectList(
                        threadMemoryQuery(
                                        memoryId.toIdentifier(),
                                        MemoryThreadEnrichmentInputDO.class)
                                .le("basis_cutoff_item_id", cutoffItemId)
                                .eq(
                                        "basis_materialization_policy_version",
                                        materializationPolicyVersion)
                                .orderByAsc(
                                        "basis_cutoff_item_id",
                                        "basis_meaningful_event_count",
                                        "input_run_key",
                                        "entry_seq"))
                .stream()
                .map(MybatisPlusMemoryStore::toEnrichmentInputRecord)
                .toList();
    }

    @Override
    public List<MemoryThreadIntakeOutboxEntry> listOutbox(MemoryId memoryId) {
        if (threadIntakeOutboxMapper == null) {
            return List.of();
        }
        return threadIntakeOutboxMapper
                .selectList(
                        threadMemoryQuery(memoryId.toIdentifier(), MemoryThreadIntakeOutboxDO.class)
                                .orderByAsc("trigger_item_id"))
                .stream()
                .map(MybatisPlusMemoryStore::toOutboxRecord)
                .toList();
    }

    @Override
    @Transactional
    public List<MemoryThreadIntakeClaim> claimPending(
            MemoryId memoryId, Instant claimedAt, Instant leaseExpiresAt, int batchSize) {
        if (threadIntakeOutboxMapper == null || batchSize <= 0) {
            return List.of();
        }
        String memoryIdentifier = memoryId.toIdentifier();
        List<MemoryThreadIntakeOutboxDO> claimedRows =
                threadIntakeOutboxMapper.selectList(
                        threadMemoryQuery(memoryIdentifier, MemoryThreadIntakeOutboxDO.class)
                                .eq("status", MemoryThreadIntakeStatus.PENDING.name())
                                .orderByAsc("trigger_item_id")
                                .last("LIMIT " + batchSize));
        if (claimedRows.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        for (MemoryThreadIntakeOutboxDO row : claimedRows) {
            row.setStatus(MemoryThreadIntakeStatus.PROCESSING.name());
            row.setClaimedAt(claimedAt);
            row.setLeaseExpiresAt(leaseExpiresAt);
            row.setUpdatedAt(now);
            threadIntakeOutboxMapper.updateById(row);
        }
        MemoryThreadRuntimeDO runtime =
                threadRuntimeMapper != null
                        ? threadRuntimeMapper.selectById(memoryIdentifier)
                        : null;
        if (runtime != null) {
            refreshRuntimeCounters(memoryIdentifier, runtime);
        }
        return claimedRows.stream().map(MybatisPlusMemoryStore::toClaimRecord).toList();
    }

    @Override
    @Transactional
    public int recoverAbandoned(MemoryId memoryId, Instant now, int maxAttempts) {
        if (threadIntakeOutboxMapper == null) {
            return 0;
        }
        String memoryIdentifier = memoryId.toIdentifier();
        List<MemoryThreadIntakeOutboxDO> abandonedRows =
                threadIntakeOutboxMapper.selectList(
                        threadMemoryQuery(memoryIdentifier, MemoryThreadIntakeOutboxDO.class)
                                .eq("status", MemoryThreadIntakeStatus.PROCESSING.name())
                                .isNotNull("lease_expires_at")
                                .lt("lease_expires_at", now));
        if (abandonedRows.isEmpty()) {
            return 0;
        }
        for (MemoryThreadIntakeOutboxDO row : abandonedRows) {
            int nextAttempt = (row.getAttemptCount() != null ? row.getAttemptCount() : 0) + 1;
            boolean failed = nextAttempt >= maxAttempts;
            row.setAttemptCount(nextAttempt);
            row.setStatus(
                    failed
                            ? MemoryThreadIntakeStatus.FAILED.name()
                            : MemoryThreadIntakeStatus.PENDING.name());
            row.setClaimedAt(null);
            row.setLeaseExpiresAt(null);
            row.setFailureReason(failed ? "lease expired" : null);
            row.setFinalizedAt(failed ? now : null);
            row.setUpdatedAt(now);
            threadIntakeOutboxMapper.updateById(row);
        }
        MemoryThreadRuntimeDO runtime =
                threadRuntimeMapper != null
                        ? threadRuntimeMapper.selectById(memoryIdentifier)
                        : null;
        if (runtime != null) {
            refreshRuntimeCounters(memoryIdentifier, runtime);
        }
        return abandonedRows.size();
    }

    @Override
    @Transactional
    public void finalizeOutboxSuccess(
            MemoryId memoryId, long triggerItemId, long lastProcessedItemId, Instant finalizedAt) {
        if (threadIntakeOutboxMapper == null) {
            return;
        }
        String memoryIdentifier = memoryId.toIdentifier();
        MemoryThreadIntakeOutboxDO row = outboxRow(memoryIdentifier, triggerItemId);
        if (row == null) {
            return;
        }
        row.setStatus(MemoryThreadIntakeStatus.COMPLETED.name());
        row.setFailureReason(null);
        row.setLastProcessedItemId(lastProcessedItemId);
        row.setFinalizedAt(finalizedAt);
        row.setUpdatedAt(finalizedAt);
        threadIntakeOutboxMapper.updateById(row);

        MemoryThreadRuntimeDO runtime =
                threadRuntimeMapper != null
                        ? threadRuntimeMapper.selectById(memoryIdentifier)
                        : null;
        if (runtime != null) {
            runtime.setLastProcessedItemId(lastProcessedItemId);
            runtime.setUpdatedAt(finalizedAt);
            refreshRuntimeCounters(memoryIdentifier, runtime);
        }
    }

    @Override
    @Transactional
    public void finalizeOutboxSkippedPrefix(
            MemoryId memoryId, long rebuildCutoffItemId, Instant finalizedAt) {
        if (threadIntakeOutboxMapper == null) {
            return;
        }
        String memoryIdentifier = memoryId.toIdentifier();
        List<MemoryThreadIntakeOutboxDO> rows =
                threadIntakeOutboxMapper.selectList(
                        threadMemoryQuery(memoryIdentifier, MemoryThreadIntakeOutboxDO.class)
                                .le("trigger_item_id", rebuildCutoffItemId));
        for (MemoryThreadIntakeOutboxDO row : rows) {
            row.setStatus(MemoryThreadIntakeStatus.SKIPPED.name());
            row.setFinalizedAt(finalizedAt);
            row.setUpdatedAt(finalizedAt);
            threadIntakeOutboxMapper.updateById(row);
        }
        MemoryThreadRuntimeDO runtime =
                threadRuntimeMapper != null
                        ? threadRuntimeMapper.selectById(memoryIdentifier)
                        : null;
        if (runtime != null) {
            refreshRuntimeCounters(memoryIdentifier, runtime);
        }
    }

    @Override
    @Transactional
    public void markRebuildRequired(MemoryId memoryId, String reason) {
        if (threadRuntimeMapper == null) {
            return;
        }
        String memoryIdentifier = memoryId.toIdentifier();
        MemoryThreadRuntimeDO runtime = threadRuntimeMapper.selectById(memoryIdentifier);
        Instant now = Instant.now();
        if (runtime == null) {
            upsertRuntime(
                    new MemoryThreadRuntimeState(
                            memoryIdentifier,
                            MemoryThreadProjectionState.REBUILD_REQUIRED,
                            outboxCount(memoryIdentifier, MemoryThreadIntakeStatus.PENDING),
                            outboxCount(memoryIdentifier, MemoryThreadIntakeStatus.FAILED),
                            null,
                            null,
                            false,
                            null,
                            0L,
                            "v1",
                            reason,
                            now));
            return;
        }
        runtime.setProjectionState(MemoryThreadProjectionState.REBUILD_REQUIRED.name());
        runtime.setInvalidationReason(reason);
        runtime.setUpdatedAt(now);
        refreshRuntimeCounters(memoryIdentifier, runtime);
    }

    @Override
    @Transactional
    public boolean beginRebuild(
            MemoryId memoryId, String materializationPolicyVersion, long rebuildCutoffItemId) {
        if (threadRuntimeMapper == null) {
            return false;
        }
        String memoryIdentifier = memoryId.toIdentifier();
        MemoryThreadRuntimeDO runtime = threadRuntimeMapper.selectById(memoryIdentifier);
        if (runtime != null && Boolean.TRUE.equals(runtime.getRebuildInProgress())) {
            return false;
        }
        Instant now = Instant.now();
        if (runtime == null) {
            upsertRuntime(
                    new MemoryThreadRuntimeState(
                            memoryIdentifier,
                            MemoryThreadProjectionState.REBUILD_REQUIRED,
                            outboxCount(memoryIdentifier, MemoryThreadIntakeStatus.PENDING),
                            outboxCount(memoryIdentifier, MemoryThreadIntakeStatus.FAILED),
                            null,
                            null,
                            true,
                            rebuildCutoffItemId,
                            1L,
                            materializationPolicyVersion,
                            "rebuild bootstrap",
                            now));
            return true;
        }
        runtime.setPendingCount(outboxCount(memoryIdentifier, MemoryThreadIntakeStatus.PENDING));
        runtime.setFailedCount(outboxCount(memoryIdentifier, MemoryThreadIntakeStatus.FAILED));
        runtime.setRebuildInProgress(true);
        runtime.setRebuildCutoffItemId(rebuildCutoffItemId);
        runtime.setRebuildEpoch(
                runtime.getRebuildEpoch() != null ? runtime.getRebuildEpoch() + 1L : 1L);
        runtime.setMaterializationPolicyVersion(materializationPolicyVersion);
        runtime.setUpdatedAt(now);
        threadRuntimeMapper.updateById(runtime);
        return true;
    }

    @Override
    @Transactional
    public boolean commitClaimedIntakeReplaySuccess(
            MemoryId memoryId,
            List<MemoryThreadIntakeClaim> claimedEntries,
            long replayCutoffItemId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt) {
        if (threadIntakeOutboxMapper == null || threadRuntimeMapper == null) {
            return false;
        }
        String memoryIdentifier = memoryId.toIdentifier();
        MemoryThreadRuntimeDO runtime = threadRuntimeMapper.selectById(memoryIdentifier);
        if (runtime == null
                || runtimeState == null
                || !Objects.equals(runtime.getRebuildEpoch(), runtimeState.rebuildEpoch())) {
            return false;
        }
        for (MemoryThreadIntakeClaim claim : claimedEntries) {
            MemoryThreadIntakeOutboxDO row = outboxRow(memoryIdentifier, claim.triggerItemId());
            if (!matchesClaim(row, claim)) {
                return false;
            }
        }
        for (MemoryThreadIntakeClaim claim : claimedEntries) {
            MemoryThreadIntakeOutboxDO row = outboxRow(memoryIdentifier, claim.triggerItemId());
            row.setStatus(MemoryThreadIntakeStatus.COMPLETED.name());
            row.setFailureReason(null);
            row.setLastProcessedItemId(replayCutoffItemId);
            row.setFinalizedAt(finalizedAt);
            row.setUpdatedAt(finalizedAt);
            threadIntakeOutboxMapper.updateById(row);
        }
        replaceProjection(memoryId, threads, events, memberships, runtimeState, finalizedAt);
        return true;
    }

    @Override
    @Transactional
    public void finalizeClaimedIntakeFailure(
            MemoryId memoryId,
            List<MemoryThreadIntakeClaim> claimedEntries,
            String reason,
            int maxAttempts,
            Instant finalizedAt) {
        if (threadIntakeOutboxMapper == null) {
            return;
        }
        String memoryIdentifier = memoryId.toIdentifier();
        boolean changed = false;
        for (MemoryThreadIntakeClaim claim : claimedEntries) {
            MemoryThreadIntakeOutboxDO row = outboxRow(memoryIdentifier, claim.triggerItemId());
            if (!matchesClaim(row, claim)) {
                continue;
            }
            int attempts =
                    Math.max(
                            (row.getAttemptCount() != null ? row.getAttemptCount() : 0) + 1,
                            maxAttempts);
            row.setStatus(MemoryThreadIntakeStatus.FAILED.name());
            row.setAttemptCount(attempts);
            row.setFailureReason(reason);
            row.setFinalizedAt(finalizedAt);
            row.setUpdatedAt(finalizedAt);
            threadIntakeOutboxMapper.updateById(row);
            changed = true;
        }
        if (changed) {
            MemoryThreadRuntimeDO runtime = threadRuntimeMapper.selectById(memoryIdentifier);
            if (runtime != null) {
                refreshRuntimeCounters(memoryIdentifier, runtime);
            }
        }
    }

    @Override
    @Transactional
    public void releaseClaims(MemoryId memoryId, List<MemoryThreadIntakeClaim> claimedEntries) {
        if (threadIntakeOutboxMapper == null) {
            return;
        }
        String memoryIdentifier = memoryId.toIdentifier();
        boolean changed = false;
        for (MemoryThreadIntakeClaim claim : claimedEntries) {
            MemoryThreadIntakeOutboxDO row = outboxRow(memoryIdentifier, claim.triggerItemId());
            if (!matchesClaim(row, claim)) {
                continue;
            }
            row.setStatus(MemoryThreadIntakeStatus.PENDING.name());
            row.setClaimedAt(null);
            row.setLeaseExpiresAt(null);
            row.setFailureReason(null);
            row.setFinalizedAt(null);
            row.setUpdatedAt(Instant.now());
            threadIntakeOutboxMapper.updateById(row);
            changed = true;
        }
        if (changed) {
            MemoryThreadRuntimeDO runtime = threadRuntimeMapper.selectById(memoryIdentifier);
            if (runtime != null) {
                refreshRuntimeCounters(memoryIdentifier, runtime);
            }
        }
    }

    @Override
    @Transactional
    public void commitRebuildReplaySuccess(
            MemoryId memoryId,
            long rebuildCutoffItemId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt) {
        finalizeOutboxSkippedPrefix(memoryId, rebuildCutoffItemId, finalizedAt);
        replaceProjection(memoryId, threads, events, memberships, runtimeState, finalizedAt);
    }

    @Override
    @Transactional
    public void replaceProjection(
            MemoryId memoryId,
            List<MemoryThreadProjection> threads,
            List<MemoryThreadEvent> events,
            List<MemoryThreadMembership> memberships,
            MemoryThreadRuntimeState runtimeState,
            Instant finalizedAt) {
        if (threadProjectionMapper == null
                || threadEventMapper == null
                || threadMembershipMapper == null
                || threadRuntimeMapper == null) {
            return;
        }
        String memoryIdentifier = memoryId.toIdentifier();
        threadEventMapper.delete(threadMemoryQuery(memoryIdentifier, MemoryThreadEventDO.class));
        threadMembershipMapper.delete(
                threadMemoryQuery(memoryIdentifier, MemoryThreadMembershipDO.class));
        threadProjectionMapper.delete(
                threadMemoryQuery(memoryIdentifier, MemoryThreadProjectionDO.class));

        if (threads != null) {
            for (MemoryThreadProjection thread : threads) {
                threadProjectionMapper.insert(toProjectionDO(thread));
            }
        }
        if (events != null) {
            for (MemoryThreadEvent event : events) {
                threadEventMapper.insert(toEventDO(event));
            }
        }
        if (memberships != null) {
            for (MemoryThreadMembership membership : memberships) {
                threadMembershipMapper.insert(toMembershipDO(membership));
            }
        }
        MemoryThreadRuntimeState adjustedRuntime =
                new MemoryThreadRuntimeState(
                        runtimeState.memoryId(),
                        runtimeState.projectionState(),
                        outboxCount(memoryIdentifier, MemoryThreadIntakeStatus.PENDING),
                        outboxCount(memoryIdentifier, MemoryThreadIntakeStatus.FAILED),
                        runtimeState.lastEnqueuedItemId(),
                        runtimeState.lastProcessedItemId(),
                        runtimeState.rebuildInProgress(),
                        runtimeState.rebuildCutoffItemId(),
                        runtimeState.rebuildEpoch(),
                        runtimeState.materializationPolicyVersion(),
                        runtimeState.invalidationReason(),
                        finalizedAt != null ? finalizedAt : runtimeState.updatedAt());
        upsertRuntime(adjustedRuntime);
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

    private <T> QueryWrapper<T> threadMemoryQuery(String memoryId, Class<T> clazz) {
        return new QueryWrapper<T>().eq("memory_id", memoryId);
    }

    private MemoryThreadIntakeOutboxDO outboxRow(String memoryId, long triggerItemId) {
        return threadIntakeOutboxMapper.selectOne(
                threadMemoryQuery(memoryId, MemoryThreadIntakeOutboxDO.class)
                        .eq("trigger_item_id", triggerItemId)
                        .last("LIMIT 1"));
    }

    private long outboxCount(String memoryId, MemoryThreadIntakeStatus status) {
        if (threadIntakeOutboxMapper == null) {
            return 0L;
        }
        Long count =
                threadIntakeOutboxMapper.selectCount(
                        threadMemoryQuery(memoryId, MemoryThreadIntakeOutboxDO.class)
                                .eq("status", status.name()));
        return count != null ? count : 0L;
    }

    private void refreshRuntimeCounters(String memoryId, MemoryThreadRuntimeDO runtime) {
        runtime.setPendingCount(outboxCount(memoryId, MemoryThreadIntakeStatus.PENDING));
        runtime.setFailedCount(outboxCount(memoryId, MemoryThreadIntakeStatus.FAILED));
        upsertRuntime(toRuntimeRecord(runtime));
    }

    private static boolean matchesClaim(
            MemoryThreadIntakeOutboxDO row, MemoryThreadIntakeClaim claim) {
        return row != null
                && Objects.equals(row.getEnqueueGeneration(), claim.enqueueGeneration())
                && Objects.equals(row.getStatus(), MemoryThreadIntakeStatus.PROCESSING.name());
    }

    private void upsertRuntime(MemoryThreadRuntimeState runtimeState) {
        if (threadRuntimeMapper == null) {
            return;
        }
        MemoryThreadRuntimeDO dataObject = toRuntimeDO(runtimeState);
        if (threadRuntimeMapper.selectById(runtimeState.memoryId()) != null) {
            threadRuntimeMapper.updateById(dataObject);
        } else {
            threadRuntimeMapper.insert(dataObject);
        }
    }

    private static MemoryThreadProjectionDO toProjectionDO(MemoryThreadProjection projection) {
        MemoryThreadProjectionDO dataObject = new MemoryThreadProjectionDO();
        dataObject.setMemoryId(projection.memoryId());
        dataObject.setThreadKey(projection.threadKey());
        dataObject.setThreadType(projection.threadType().name());
        dataObject.setAnchorKind(projection.anchorKind());
        dataObject.setAnchorKey(projection.anchorKey());
        dataObject.setDisplayLabel(projection.displayLabel());
        dataObject.setLifecycleStatus(projection.lifecycleStatus().name());
        dataObject.setObjectState(projection.objectState().name());
        dataObject.setHeadline(projection.headline());
        dataObject.setSnapshotJson(projection.snapshotJson());
        dataObject.setSnapshotVersion(projection.snapshotVersion());
        dataObject.setOpenedAt(projection.openedAt());
        dataObject.setLastEventAt(projection.lastEventAt());
        dataObject.setLastMeaningfulUpdateAt(projection.lastMeaningfulUpdateAt());
        dataObject.setClosedAt(projection.closedAt());
        dataObject.setEventCount(projection.eventCount());
        dataObject.setMemberCount(projection.memberCount());
        dataObject.setCreatedAt(projection.createdAt());
        dataObject.setUpdatedAt(projection.updatedAt());
        return dataObject;
    }

    private static MemoryThreadProjection toProjectionRecord(MemoryThreadProjectionDO dataObject) {
        return new MemoryThreadProjection(
                dataObject.getMemoryId(),
                dataObject.getThreadKey(),
                MemoryThreadType.valueOf(dataObject.getThreadType()),
                dataObject.getAnchorKind(),
                dataObject.getAnchorKey(),
                dataObject.getDisplayLabel(),
                MemoryThreadLifecycleStatus.valueOf(dataObject.getLifecycleStatus()),
                MemoryThreadObjectState.valueOf(dataObject.getObjectState()),
                dataObject.getHeadline(),
                dataObject.getSnapshotJson(),
                dataObject.getSnapshotVersion() != null ? dataObject.getSnapshotVersion() : 0,
                dataObject.getOpenedAt(),
                dataObject.getLastEventAt(),
                dataObject.getLastMeaningfulUpdateAt(),
                dataObject.getClosedAt(),
                dataObject.getEventCount() != null ? dataObject.getEventCount() : 0L,
                dataObject.getMemberCount() != null ? dataObject.getMemberCount() : 0L,
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static MemoryThreadEventDO toEventDO(MemoryThreadEvent event) {
        MemoryThreadEventDO dataObject = new MemoryThreadEventDO();
        dataObject.setMemoryId(event.memoryId());
        dataObject.setThreadKey(event.threadKey());
        dataObject.setEventKey(event.eventKey());
        dataObject.setEventSeq(event.eventSeq());
        dataObject.setEventType(event.eventType().name());
        dataObject.setEventTime(event.eventTime());
        dataObject.setEventPayloadJson(event.eventPayloadJson());
        dataObject.setEventPayloadVersion(event.eventPayloadVersion());
        dataObject.setMeaningful(event.meaningful());
        dataObject.setConfidence(event.confidence());
        dataObject.setCreatedAt(event.createdAt());
        return dataObject;
    }

    private static MemoryThreadEvent toEventRecord(MemoryThreadEventDO dataObject) {
        return new MemoryThreadEvent(
                dataObject.getMemoryId(),
                dataObject.getThreadKey(),
                dataObject.getEventKey(),
                dataObject.getEventSeq() != null ? dataObject.getEventSeq() : 0L,
                MemoryThreadEventType.valueOf(dataObject.getEventType()),
                dataObject.getEventTime(),
                dataObject.getEventPayloadJson(),
                dataObject.getEventPayloadVersion() != null
                        ? dataObject.getEventPayloadVersion()
                        : 0,
                Boolean.TRUE.equals(dataObject.getMeaningful()),
                dataObject.getConfidence(),
                dataObject.getCreatedAt());
    }

    private static MemoryThreadEnrichmentInputDO toEnrichmentInputDO(
            MemoryThreadEnrichmentInput input) {
        MemoryThreadEnrichmentInputDO dataObject = new MemoryThreadEnrichmentInputDO();
        dataObject.setMemoryId(input.memoryId());
        dataObject.setThreadKey(input.threadKey());
        dataObject.setInputRunKey(input.inputRunKey());
        dataObject.setEntrySeq(input.entrySeq());
        dataObject.setBasisCutoffItemId(input.basisCutoffItemId());
        dataObject.setBasisMeaningfulEventCount(input.basisMeaningfulEventCount());
        dataObject.setBasisMaterializationPolicyVersion(input.basisMaterializationPolicyVersion());
        dataObject.setPayloadJson(input.payloadJson());
        dataObject.setProvenanceJson(input.provenanceJson());
        dataObject.setCreatedAt(input.createdAt());
        return dataObject;
    }

    private static MemoryThreadEnrichmentInput toEnrichmentInputRecord(
            MemoryThreadEnrichmentInputDO dataObject) {
        return new MemoryThreadEnrichmentInput(
                dataObject.getMemoryId(),
                dataObject.getThreadKey(),
                dataObject.getInputRunKey(),
                dataObject.getEntrySeq() != null ? dataObject.getEntrySeq() : 0,
                dataObject.getBasisCutoffItemId() != null ? dataObject.getBasisCutoffItemId() : 0L,
                dataObject.getBasisMeaningfulEventCount() != null
                        ? dataObject.getBasisMeaningfulEventCount()
                        : 0L,
                dataObject.getBasisMaterializationPolicyVersion(),
                dataObject.getPayloadJson(),
                dataObject.getProvenanceJson(),
                dataObject.getCreatedAt());
    }

    private static MemoryThreadMembershipDO toMembershipDO(MemoryThreadMembership membership) {
        MemoryThreadMembershipDO dataObject = new MemoryThreadMembershipDO();
        dataObject.setMemoryId(membership.memoryId());
        dataObject.setThreadKey(membership.threadKey());
        dataObject.setItemId(membership.itemId());
        dataObject.setRole(membership.role().name());
        dataObject.setPrimary(membership.primary());
        dataObject.setRelevanceWeight(membership.relevanceWeight());
        dataObject.setCreatedAt(membership.createdAt());
        dataObject.setUpdatedAt(membership.updatedAt());
        return dataObject;
    }

    private static MemoryThreadMembership toMembershipRecord(MemoryThreadMembershipDO dataObject) {
        return new MemoryThreadMembership(
                dataObject.getMemoryId(),
                dataObject.getThreadKey(),
                dataObject.getItemId(),
                MemoryThreadMembershipRole.valueOf(dataObject.getRole()),
                Boolean.TRUE.equals(dataObject.getPrimary()),
                dataObject.getRelevanceWeight() != null ? dataObject.getRelevanceWeight() : 0.0d,
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static MemoryThreadIntakeOutboxEntry toOutboxRecord(
            MemoryThreadIntakeOutboxDO dataObject) {
        return new MemoryThreadIntakeOutboxEntry(
                dataObject.getMemoryId(),
                dataObject.getTriggerItemId(),
                dataObject.getEnqueueGeneration() != null ? dataObject.getEnqueueGeneration() : 1L,
                MemoryThreadIntakeStatus.valueOf(dataObject.getStatus()),
                dataObject.getAttemptCount() != null ? dataObject.getAttemptCount() : 0,
                dataObject.getClaimedAt(),
                dataObject.getLeaseExpiresAt(),
                dataObject.getFailureReason(),
                dataObject.getLastProcessedItemId(),
                dataObject.getEnqueuedAt(),
                dataObject.getFinalizedAt());
    }

    private static MemoryThreadIntakeClaim toClaimRecord(MemoryThreadIntakeOutboxDO dataObject) {
        return new MemoryThreadIntakeClaim(
                dataObject.getTriggerItemId(),
                dataObject.getEnqueueGeneration() != null ? dataObject.getEnqueueGeneration() : 1L,
                dataObject.getClaimedAt(),
                dataObject.getLeaseExpiresAt());
    }

    private static MemoryThreadRuntimeDO toRuntimeDO(MemoryThreadRuntimeState runtimeState) {
        MemoryThreadRuntimeDO dataObject = new MemoryThreadRuntimeDO();
        dataObject.setMemoryId(runtimeState.memoryId());
        dataObject.setProjectionState(runtimeState.projectionState().name());
        dataObject.setPendingCount(runtimeState.pendingCount());
        dataObject.setFailedCount(runtimeState.failedCount());
        dataObject.setLastEnqueuedItemId(runtimeState.lastEnqueuedItemId());
        dataObject.setLastProcessedItemId(runtimeState.lastProcessedItemId());
        dataObject.setRebuildInProgress(runtimeState.rebuildInProgress());
        dataObject.setRebuildCutoffItemId(runtimeState.rebuildCutoffItemId());
        dataObject.setRebuildEpoch(runtimeState.rebuildEpoch());
        dataObject.setMaterializationPolicyVersion(runtimeState.materializationPolicyVersion());
        dataObject.setInvalidationReason(runtimeState.invalidationReason());
        dataObject.setUpdatedAt(runtimeState.updatedAt());
        return dataObject;
    }

    private static MemoryThreadRuntimeState toRuntimeRecord(MemoryThreadRuntimeDO dataObject) {
        return new MemoryThreadRuntimeState(
                dataObject.getMemoryId(),
                MemoryThreadProjectionState.valueOf(dataObject.getProjectionState()),
                dataObject.getPendingCount() != null ? dataObject.getPendingCount() : 0L,
                dataObject.getFailedCount() != null ? dataObject.getFailedCount() : 0L,
                dataObject.getLastEnqueuedItemId(),
                dataObject.getLastProcessedItemId(),
                Boolean.TRUE.equals(dataObject.getRebuildInProgress()),
                dataObject.getRebuildCutoffItemId(),
                dataObject.getRebuildEpoch() != null ? dataObject.getRebuildEpoch() : 0L,
                dataObject.getMaterializationPolicyVersion(),
                dataObject.getInvalidationReason(),
                dataObject.getUpdatedAt());
    }

    private static void validateEnrichmentInput(
            String memoryIdentifier, MemoryThreadEnrichmentInput input) {
        Objects.requireNonNull(input, "input");
        if (!Objects.equals(memoryIdentifier, input.memoryId())) {
            throw new IllegalArgumentException("runInputs memoryId must match append memoryId");
        }
    }

    private static String enrichmentInputKey(String inputRunKey, Integer entrySeq) {
        return inputRunKey + "#" + entrySeq;
    }

    private static String enrichmentInputKey(String inputRunKey, int entrySeq) {
        return inputRunKey + "#" + entrySeq;
    }

    private static boolean equivalent(
            MemoryThreadEnrichmentInputDO existing, MemoryThreadEnrichmentInput input) {
        return Objects.equals(existing.getMemoryId(), input.memoryId())
                && Objects.equals(existing.getThreadKey(), input.threadKey())
                && Objects.equals(existing.getInputRunKey(), input.inputRunKey())
                && Objects.equals(existing.getEntrySeq(), input.entrySeq())
                && Objects.equals(existing.getBasisCutoffItemId(), input.basisCutoffItemId())
                && Objects.equals(
                        existing.getBasisMeaningfulEventCount(), input.basisMeaningfulEventCount())
                && Objects.equals(
                        existing.getBasisMaterializationPolicyVersion(),
                        input.basisMaterializationPolicyVersion())
                && Objects.equals(
                        canonicalJson(existing.getPayloadJson()),
                        canonicalJson(input.payloadJson()))
                && Objects.equals(
                        canonicalJson(existing.getProvenanceJson()),
                        canonicalJson(input.provenanceJson()));
    }

    private static String canonicalJson(Map<String, Object> value) {
        try {
            return JsonUtils.newMapper()
                    .writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(value == null ? Map.of() : value);
        } catch (tools.jackson.core.JacksonException error) {
            throw new IllegalStateException("Failed to canonicalize enrichment input JSON", error);
        }
    }
}
