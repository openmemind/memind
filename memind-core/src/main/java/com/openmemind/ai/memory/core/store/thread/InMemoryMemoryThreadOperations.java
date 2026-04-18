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
package com.openmemind.ai.memory.core.store.thread;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryThread;
import com.openmemind.ai.memory.core.data.MemoryThreadItem;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link MemoryThreadOperations}.
 */
public class InMemoryMemoryThreadOperations implements MemoryThreadOperations {

    private final Map<String, Map<String, MemoryThread>> threadsByKey = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, MemoryThreadItem>> membershipsByItemId =
            new ConcurrentHashMap<>();

    @Override
    public void upsertThreads(MemoryId memoryId, List<MemoryThread> threads) {
        if (threads == null || threads.isEmpty()) {
            return;
        }
        var store =
                threadsByKey.computeIfAbsent(key(memoryId), ignored -> new ConcurrentHashMap<>());
        for (MemoryThread thread : threads) {
            store.put(thread.threadKey(), thread);
        }
    }

    @Override
    public void upsertThreadItems(MemoryId memoryId, List<MemoryThreadItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        var store =
                membershipsByItemId.computeIfAbsent(
                        key(memoryId), ignored -> new ConcurrentHashMap<>());
        for (MemoryThreadItem item : items) {
            MemoryThreadItem existing = store.get(item.itemId());
            if (existing != null && !existing.threadId().equals(item.threadId())) {
                throw new IllegalStateException(
                        "single-thread-per-item violated for itemId=" + item.itemId());
            }
            store.put(item.itemId(), item);
        }
    }

    @Override
    public List<MemoryThread> listThreads(MemoryId memoryId) {
        return threadsByKey.getOrDefault(key(memoryId), Map.of()).values().stream()
                .sorted(
                        Comparator.comparingInt(MemoryThread::displayOrderHint)
                                .thenComparing(InMemoryMemoryThreadOperations::threadIdSortKey)
                                .thenComparing(MemoryThread::threadKey))
                .toList();
    }

    @Override
    public List<MemoryThreadItem> listThreadItems(MemoryId memoryId) {
        return membershipsByItemId.getOrDefault(key(memoryId), Map.of()).values().stream()
                .sorted(
                        Comparator.comparingInt(MemoryThreadItem::sequenceHint)
                                .thenComparing(MemoryThreadItem::itemId)
                                .thenComparing(InMemoryMemoryThreadOperations::threadItemIdSortKey))
                .toList();
    }

    @Override
    public void deleteMembershipsByItemIds(MemoryId memoryId, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        var store = membershipsByItemId.get(key(memoryId));
        if (store == null) {
            return;
        }
        for (Long itemId : itemIds) {
            store.remove(itemId);
        }
    }

    private static String key(MemoryId memoryId) {
        return memoryId.toIdentifier();
    }

    private static long threadIdSortKey(MemoryThread thread) {
        return thread.id() != null ? thread.id() : Long.MAX_VALUE;
    }

    private static long threadItemIdSortKey(MemoryThreadItem item) {
        return item.id() != null ? item.id() : Long.MAX_VALUE;
    }
}
