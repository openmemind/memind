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
package com.openmemind.ai.memory.core.buffer;

import com.openmemind.ai.memory.core.data.MemoryId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory Insight buffer implementation
 *
 * <p>Used for testing and scenarios that do not require persistence. Data is lost after application restart.
 *
 */
public class InMemoryInsightBuffer implements InsightBuffer {

    private final ConcurrentHashMap<String, List<BufferEntry>> buffers = new ConcurrentHashMap<>();

    private String bufferKey(MemoryId memoryId, String insightTypeName) {
        return memoryId.toIdentifier() + "::" + insightTypeName;
    }

    @Override
    public void append(MemoryId memoryId, String insightTypeName, List<Long> itemIds) {
        var key = bufferKey(memoryId, insightTypeName);
        var entries = itemIds.stream().map(BufferEntry::ungrouped).toList();
        buffers.compute(
                key,
                (k, existing) -> {
                    var list = existing != null ? existing : new ArrayList<BufferEntry>();
                    synchronized (list) {
                        list.addAll(entries);
                    }
                    return list;
                });
    }

    @Override
    public List<BufferEntry> getUnGrouped(MemoryId memoryId, String insightTypeName) {
        var key = bufferKey(memoryId, insightTypeName);
        var list = buffers.get(key);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return list.stream().filter(e -> e.isUngrouped() && !e.built()).toList();
        }
    }

    @Override
    public int countUnGrouped(MemoryId memoryId, String insightTypeName) {
        return getUnGrouped(memoryId, insightTypeName).size();
    }

    @Override
    public List<BufferEntry> getGroupUnbuilt(
            MemoryId memoryId, String insightTypeName, String groupName) {
        var key = bufferKey(memoryId, insightTypeName);
        var list = buffers.get(key);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return list.stream()
                    .filter(e -> groupName.equals(e.groupName()) && !e.built())
                    .toList();
        }
    }

    @Override
    public int countGroupUnbuilt(MemoryId memoryId, String insightTypeName, String groupName) {
        return getGroupUnbuilt(memoryId, insightTypeName, groupName).size();
    }

    @Override
    public void assignGroup(
            MemoryId memoryId, String insightTypeName, List<Long> itemIds, String groupName) {
        var key = bufferKey(memoryId, insightTypeName);
        var idSet = Set.copyOf(itemIds);
        buffers.computeIfPresent(
                key,
                (k, list) -> {
                    synchronized (list) {
                        list.replaceAll(
                                e ->
                                        idSet.contains(e.itemId()) && e.isUngrouped()
                                                ? new BufferEntry(e.itemId(), groupName, false)
                                                : e);
                    }
                    return list;
                });
    }

    @Override
    public void markBuilt(MemoryId memoryId, String insightTypeName, List<Long> itemIds) {
        var key = bufferKey(memoryId, insightTypeName);
        var idSet = Set.copyOf(itemIds);
        buffers.computeIfPresent(
                key,
                (k, list) -> {
                    synchronized (list) {
                        list.replaceAll(
                                e ->
                                        idSet.contains(e.itemId()) && !e.built()
                                                ? new BufferEntry(e.itemId(), e.groupName(), true)
                                                : e);
                    }
                    return list;
                });
    }

    @Override
    public UngroupedContext getUngroupedContext(MemoryId memoryId, String insightTypeName) {
        var key = bufferKey(memoryId, insightTypeName);
        var list = buffers.get(key);
        if (list == null) {
            return new UngroupedContext(List.of(), Set.of());
        }
        synchronized (list) {
            var ungrouped = new ArrayList<BufferEntry>();
            var groupNames = new HashSet<String>();
            for (var e : list) {
                if (e.groupName() != null) {
                    groupNames.add(e.groupName());
                }
                if (e.isUngrouped() && !e.built()) {
                    ungrouped.add(e);
                }
            }
            return new UngroupedContext(List.copyOf(ungrouped), Set.copyOf(groupNames));
        }
    }

    @Override
    public Map<String, List<BufferEntry>> getUnbuiltByGroup(
            MemoryId memoryId, String insightTypeName) {
        var key = bufferKey(memoryId, insightTypeName);
        var list = buffers.get(key);
        if (list == null) {
            return Map.of();
        }
        synchronized (list) {
            var result = new HashMap<String, List<BufferEntry>>();
            for (var e : list) {
                if (!e.isUngrouped() && !e.built()) {
                    result.computeIfAbsent(e.groupName(), k -> new ArrayList<>()).add(e);
                }
            }
            return Map.copyOf(result);
        }
    }

    @Override
    public Set<String> listGroups(MemoryId memoryId, String insightTypeName) {
        var key = bufferKey(memoryId, insightTypeName);
        var list = buffers.get(key);
        if (list == null) {
            return Set.of();
        }
        synchronized (list) {
            return list.stream()
                    .map(BufferEntry::groupName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    @Override
    public boolean hasWork(MemoryId memoryId, String insightTypeName) {
        var list = buffers.get(bufferKey(memoryId, insightTypeName));
        if (list == null) {
            return false;
        }
        synchronized (list) {
            return list.stream().anyMatch(e -> !e.built());
        }
    }
}
