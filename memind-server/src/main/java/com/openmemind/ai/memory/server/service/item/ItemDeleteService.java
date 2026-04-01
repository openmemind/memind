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
package com.openmemind.ai.memory.server.service.item;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.mapper.item.AdminItemQueryMapper;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ItemDeleteService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final AdminItemQueryMapper itemQueryMapper;
    private final MemoryRuntimeManager runtimeManager;

    public ItemDeleteService(
            AdminItemQueryMapper itemQueryMapper, MemoryRuntimeManager runtimeManager) {
        this.itemQueryMapper = itemQueryMapper;
        this.runtimeManager = runtimeManager;
    }

    public BatchDeleteResult deleteItems(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return new BatchDeleteResult(0, List.of());
        }
        List<AdminItemView> itemViews = itemQueryMapper.findByBizIds(itemIds);
        Map<MemoryId, List<Long>> itemIdsByMemory =
                itemViews.stream()
                        .collect(
                                Collectors.groupingBy(
                                        view -> DefaultMemoryId.of(view.userId(), view.agentId()),
                                        LinkedHashMap::new,
                                        Collectors.mapping(
                                                AdminItemView::itemId,
                                                Collectors.collectingAndThen(
                                                        Collectors.toCollection(LinkedHashSet::new),
                                                        List::copyOf))));

        try (var lease = runtimeManager.acquire()) {
            for (var entry : itemIdsByMemory.entrySet()) {
                lease.handle()
                        .memory()
                        .deleteItems(entry.getKey(), entry.getValue())
                        .block(REQUEST_TIMEOUT);
            }
        }
        return new BatchDeleteResult(
                itemViews.size(),
                itemViews.stream().map(AdminItemView::memoryId).distinct().toList());
    }
}
