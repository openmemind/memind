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
package com.openmemind.ai.memory.core.store.item;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ItemOperations}.
 */
public class InMemoryItemOperations implements ItemOperations {

    private final Map<String, Map<Long, MemoryItem>> itemStore = new ConcurrentHashMap<>();

    private String key(MemoryId id) {
        return id.toIdentifier();
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

    public List<MemoryItem> previewCommittedBatch(MemoryId id, List<MemoryItem> stagedItems) {
        var nextItems =
                new LinkedHashMap<Long, MemoryItem>(itemStore.getOrDefault(key(id), Map.of()));
        if (stagedItems != null) {
            stagedItems.forEach(item -> nextItems.put(item.id(), item));
        }
        return List.copyOf(nextItems.values());
    }

    public void installCommittedBatch(MemoryId id, List<MemoryItem> committedItems) {
        var nextItems = new ConcurrentHashMap<Long, MemoryItem>();
        if (committedItems != null) {
            committedItems.forEach(item -> nextItems.put(item.id(), item));
        }
        itemStore.put(key(id), nextItems);
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
}
