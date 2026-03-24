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
package com.openmemind.ai.memory.core.store.rawdata;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link RawDataOperations}.
 */
public class InMemoryRawDataOperations implements RawDataOperations {

    private final Map<String, Map<String, MemoryRawData>> rawDataStore = new ConcurrentHashMap<>();

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
        var cutoff = Instant.now().minus(minAge);
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
}
