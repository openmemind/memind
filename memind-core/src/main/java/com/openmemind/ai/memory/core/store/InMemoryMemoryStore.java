package com.openmemind.ai.memory.core.store;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
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
import java.util.stream.Collectors;

/**
 * In-memory implementation of MemoryStore
 *
 */
public class InMemoryMemoryStore implements MemoryStore {

    private final Map<String, Map<String, MemoryRawData>> rawDataStore = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, MemoryItem>> itemStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, MemoryInsightType>> insightTypeStore =
            new ConcurrentHashMap<>();
    private final Map<String, Map<Long, MemoryInsight>> insightStore = new ConcurrentHashMap<>();

    private String key(MemoryId id) {
        return id.toIdentifier();
    }

    // ===== MemoryRawData =====

    @Override
    public void saveRawData(MemoryId id, MemoryRawData rawData) {
        rawDataStore
                .computeIfAbsent(key(id), k -> new ConcurrentHashMap<>())
                .put(rawData.id(), rawData);
    }

    @Override
    public void saveRawDataList(MemoryId id, List<MemoryRawData> rawDataList) {
        Map<String, MemoryRawData> map =
                rawDataStore.computeIfAbsent(key(id), k -> new ConcurrentHashMap<>());
        rawDataList.forEach(rawData -> map.put(rawData.id(), rawData));
    }

    @Override
    public Optional<MemoryRawData> getRawData(MemoryId id, String rawDataId) {
        return Optional.ofNullable(rawDataStore.getOrDefault(key(id), Map.of()).get(rawDataId));
    }

    @Override
    public List<MemoryRawData> getAllRawData(MemoryId id) {
        return List.copyOf(rawDataStore.getOrDefault(key(id), Map.of()).values());
    }

    @Override
    public Optional<MemoryRawData> getRawDataByContentId(MemoryId id, String contentId) {
        return rawDataStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(r -> Objects.equals(r.contentId(), contentId))
                .findFirst();
    }

    @Override
    public void deleteRawData(MemoryId id, String rawDataId) {
        Map<String, MemoryRawData> map = rawDataStore.get(key(id));
        if (map != null) {
            map.remove(rawDataId);
        }
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

    // ===== MemoryItem =====

    @Override
    public void addItem(MemoryId id, MemoryItem item) {
        itemStore.computeIfAbsent(key(id), k -> new ConcurrentHashMap<>()).put(item.id(), item);
    }

    @Override
    public void addItems(MemoryId id, List<MemoryItem> items) {
        Map<Long, MemoryItem> map =
                itemStore.computeIfAbsent(key(id), k -> new ConcurrentHashMap<>());
        for (MemoryItem item : items) {
            map.put(item.id(), item);
        }
    }

    @Override
    public Optional<MemoryItem> getItem(MemoryId id, Long itemId) {
        return Optional.ofNullable(itemStore.getOrDefault(key(id), Map.of()).get(itemId));
    }

    @Override
    public Optional<MemoryItem> getItemByContentHash(MemoryId id, String contentHash) {
        return itemStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> Objects.equals(i.contentHash(), contentHash))
                .findFirst();
    }

    @Override
    public List<MemoryItem> getItemsByContentHashes(MemoryId id, Collection<String> contentHashes) {
        if (contentHashes.isEmpty()) {
            return List.of();
        }
        Set<String> hashSet = new HashSet<>(contentHashes);
        return itemStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> i.contentHash() != null && hashSet.contains(i.contentHash()))
                .toList();
    }

    @Override
    public List<MemoryItem> getItemsByVectorIds(MemoryId id, Collection<String> vectorIds) {
        if (vectorIds.isEmpty()) {
            return List.of();
        }
        Set<String> idSet = new HashSet<>(vectorIds);
        return itemStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> i.vectorId() != null && idSet.contains(i.vectorId()))
                .toList();
    }

    @Override
    public List<MemoryItem> getItemsByIds(MemoryId id, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        var idSet = new HashSet<>(itemIds);
        return itemStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(item -> idSet.contains(item.id()))
                .toList();
    }

    @Override
    public List<MemoryItem> getAllItems(MemoryId id) {
        return new ArrayList<>(itemStore.getOrDefault(key(id), Map.of()).values());
    }

    @Override
    public boolean hasItems(MemoryId id) {
        return !itemStore.getOrDefault(key(id), Map.of()).isEmpty();
    }

    @Override
    public List<MemoryItem> getItemsByRawDataId(MemoryId id, String rawDataId) {
        return itemStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> Objects.equals(i.rawDataId(), rawDataId))
                .toList();
    }

    @Override
    public List<MemoryItem> getItemsByScope(MemoryId id, MemoryScope scope) {
        return itemStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> i.scope() == scope)
                .collect(Collectors.toList());
    }

    @Override
    public void updateItems(MemoryId id, List<MemoryItem> items) {
        Map<Long, MemoryItem> map = itemStore.get(key(id));
        if (map == null) {
            return;
        }
        for (MemoryItem item : items) {
            if (map.containsKey(item.id())) {
                map.put(item.id(), item);
            }
        }
    }

    @Override
    public void deleteItem(MemoryId id, Long itemId) {
        Map<Long, MemoryItem> map = itemStore.get(key(id));
        if (map != null) {
            map.remove(itemId);
        }
    }

    // ===== MemoryInsightType =====

    @Override
    public void saveInsightType(MemoryId id, MemoryInsightType insightType) {
        insightTypeStore
                .computeIfAbsent(key(id), k -> new ConcurrentHashMap<>())
                .put(insightType.name(), insightType);
    }

    @Override
    public Optional<MemoryInsightType> getInsightType(MemoryId id, String insightType) {
        return Optional.ofNullable(
                insightTypeStore.getOrDefault(key(id), Map.of()).get(insightType));
    }

    @Override
    public List<MemoryInsightType> getAllInsightTypes(MemoryId id) {
        return new ArrayList<>(insightTypeStore.getOrDefault(key(id), Map.of()).values());
    }

    @Override
    public void deleteInsightType(MemoryId id, String insightType) {
        Map<String, MemoryInsightType> map = insightTypeStore.get(key(id));
        if (map != null) {
            map.remove(insightType);
        }
    }

    // ===== MemoryInsight =====

    @Override
    public void saveInsight(MemoryId id, MemoryInsight insight) {
        insightStore
                .computeIfAbsent(key(id), k -> new ConcurrentHashMap<>())
                .put(insight.id(), insight);
    }

    @Override
    public Optional<MemoryInsight> getInsight(MemoryId id, Long insightId) {
        return Optional.ofNullable(insightStore.getOrDefault(key(id), Map.of()).get(insightId));
    }

    @Override
    public List<MemoryInsight> getAllInsights(MemoryId id) {
        return new ArrayList<>(insightStore.getOrDefault(key(id), Map.of()).values());
    }

    @Override
    public List<MemoryInsight> getInsightsByTypeId(MemoryId id, String insightType) {
        return insightStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> Objects.equals(i.type(), insightType))
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryInsight> getInsightsByScope(MemoryId id, MemoryScope scope) {
        return insightStore.getOrDefault(key(id), Map.of()).values().stream()
                .filter(i -> i.scope() == scope)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteInsight(MemoryId id, Long insightId) {
        Map<Long, MemoryInsight> map = insightStore.get(key(id));
        if (map != null) {
            map.remove(insightId);
        }
    }
}
