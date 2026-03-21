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
package com.openmemind.ai.memory.core.store;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link MemoryStore}.
 */
public class InMemoryMemoryStore implements MemoryStore {

    private final Map<String, Map<String, MemoryRawData>> rawDataStore = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, MemoryItem>> itemStore = new ConcurrentHashMap<>();
    private final Map<String, MemoryInsightType> insightTypeStore = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, MemoryInsight>> insightStore = new ConcurrentHashMap<>();

    private String key(MemoryId id) {
        return id.toIdentifier();
    }

    @Override
    public void upsertRawData(MemoryId id, List<MemoryRawData> rawDataList) {
        if (rawDataList == null || rawDataList.isEmpty()) {
            return;
        }
        Map<String, MemoryRawData> map =
                rawDataStore.computeIfAbsent(key(id), k -> new ConcurrentHashMap<>());
        rawDataList.forEach(rawData -> map.put(rawData.id(), rawData));
    }

    @Override
    public Optional<MemoryRawData> getRawData(MemoryId id, String rawDataId) {
        return Optional.ofNullable(rawDataStore.getOrDefault(key(id), Map.of()).get(rawDataId));
    }

    @Override
    public Optional<MemoryRawData> getRawDataByContentId(MemoryId id, String contentId) {
        return rawDataStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(r -> Objects.equals(r.contentId(), contentId))
                .findFirst();
    }

    @Override
    public List<MemoryRawData> listRawData(MemoryId id) {
        return List.copyOf(rawDataStore.getOrDefault(key(id), Map.of()).values());
    }

    @Override
    public List<MemoryRawData> pollRawDataWithoutVector(MemoryId id, int limit, Duration minAge) {
        Map<String, MemoryRawData> map = rawDataStore.getOrDefault(key(id), Map.of());
        if (map.isEmpty() || limit <= 0) {
            return List.of();
        }
        var cutoff = java.time.Instant.now().minus(minAge);
        return map.values().stream()
                .filter(r -> r.captionVectorId() == null)
                .filter(r -> r.createdAt().isBefore(cutoff))
                .limit(limit)
                .toList();
    }

    @Override
    public void updateRawDataVectorIds(
            MemoryId id, Map<String, String> vectorIds, Map<String, Object> metadataPatch) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        Map<String, MemoryRawData> map = rawDataStore.get(key(id));
        if (map == null || map.isEmpty()) {
            return;
        }
        vectorIds.forEach(
                (rawDataId, vectorId) -> {
                    MemoryRawData existing = map.get(rawDataId);
                    if (existing != null) {
                        map.put(rawDataId, existing.withVectorId(vectorId, metadataPatch));
                    }
                });
    }

    @Override
    public void insertItems(MemoryId id, List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<Long, MemoryItem> map =
                itemStore.computeIfAbsent(key(id), k -> new ConcurrentHashMap<>());
        items.forEach(item -> map.put(item.id(), item));
    }

    @Override
    public List<MemoryItem> getItemsByIds(MemoryId id, Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        var idSet = new HashSet<>(itemIds);
        return itemStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(item -> idSet.contains(item.id()))
                .toList();
    }

    @Override
    public List<MemoryItem> getItemsByVectorIds(MemoryId id, Collection<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return List.of();
        }
        Set<String> idSet = new HashSet<>(vectorIds);
        return itemStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> i.vectorId() != null && idSet.contains(i.vectorId()))
                .toList();
    }

    @Override
    public List<MemoryItem> getItemsByContentHashes(MemoryId id, Collection<String> contentHashes) {
        if (contentHashes == null || contentHashes.isEmpty()) {
            return List.of();
        }
        Set<String> hashSet = new HashSet<>(contentHashes);
        return itemStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> i.contentHash() != null && hashSet.contains(i.contentHash()))
                .toList();
    }

    @Override
    public List<MemoryItem> listItems(MemoryId id) {
        return new ArrayList<>(itemStore.getOrDefault(key(id), Map.of()).values());
    }

    @Override
    public boolean hasItems(MemoryId id) {
        return !itemStore.getOrDefault(key(id), Map.of()).isEmpty();
    }

    @Override
    public void deleteItems(MemoryId id, Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        Map<Long, MemoryItem> map = itemStore.get(key(id));
        if (map == null) {
            return;
        }
        itemIds.forEach(map::remove);
    }

    @Override
    public void upsertInsightTypes(List<MemoryInsightType> insightTypes) {
        if (insightTypes == null || insightTypes.isEmpty()) {
            return;
        }
        insightTypes.forEach(insightType -> insightTypeStore.put(insightType.name(), insightType));
    }

    @Override
    public Optional<MemoryInsightType> getInsightType(String insightType) {
        return Optional.ofNullable(insightTypeStore.get(insightType));
    }

    @Override
    public List<MemoryInsightType> listInsightTypes() {
        return new ArrayList<>(insightTypeStore.values());
    }

    @Override
    public void upsertInsights(MemoryId id, List<MemoryInsight> insights) {
        if (insights == null || insights.isEmpty()) {
            return;
        }
        Map<Long, MemoryInsight> map =
                insightStore.computeIfAbsent(key(id), k -> new ConcurrentHashMap<>());
        insights.forEach(insight -> map.put(insight.id(), insight));
    }

    @Override
    public Optional<MemoryInsight> getInsight(MemoryId id, Long insightId) {
        return Optional.ofNullable(insightStore.getOrDefault(key(id), Map.of()).get(insightId));
    }

    @Override
    public List<MemoryInsight> listInsights(MemoryId id) {
        return new ArrayList<>(insightStore.getOrDefault(key(id), Map.of()).values());
    }

    @Override
    public List<MemoryInsight> getInsightsByType(MemoryId id, String insightType) {
        return insightStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> Objects.equals(i.type(), insightType))
                .toList();
    }

    @Override
    public List<MemoryInsight> getInsightsByTier(MemoryId id, InsightTier tier) {
        return insightStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> tier.equals(i.tier()))
                .toList();
    }

    @Override
    public Optional<MemoryInsight> getLeafByGroup(MemoryId id, String type, String group) {
        return getInsightsByType(id, type).stream()
                .filter(i -> InsightTier.LEAF.equals(i.tier()) && Objects.equals(group, i.group()))
                .findFirst();
    }

    @Override
    public Optional<MemoryInsight> getBranchByType(MemoryId id, String type) {
        return getInsightsByType(id, type).stream()
                .filter(i -> InsightTier.BRANCH.equals(i.tier()))
                .findFirst();
    }

    @Override
    public Optional<MemoryInsight> getRootByType(MemoryId id, String type) {
        return getInsightsByType(id, type).stream()
                .filter(i -> InsightTier.ROOT.equals(i.tier()))
                .findFirst();
    }

    @Override
    public void deleteInsights(MemoryId id, Collection<Long> insightIds) {
        if (insightIds == null || insightIds.isEmpty()) {
            return;
        }
        Map<Long, MemoryInsight> map = insightStore.get(key(id));
        if (map == null) {
            return;
        }
        insightIds.forEach(map::remove);
    }
}
