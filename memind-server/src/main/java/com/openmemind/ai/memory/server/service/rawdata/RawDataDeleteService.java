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
package com.openmemind.ai.memory.server.service.rawdata;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.domain.rawdata.response.RawDataDeleteResult;
import com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView;
import com.openmemind.ai.memory.server.mapper.item.AdminItemQueryMapper;
import com.openmemind.ai.memory.server.mapper.rawdata.AdminRawDataQueryMapper;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeUnavailableException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class RawDataDeleteService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final AdminRawDataQueryMapper rawDataQueryMapper;
    private final AdminItemQueryMapper itemQueryMapper;
    private final MemoryRuntimeManager runtimeManager;
    private final MemoryVector memoryVector;

    public RawDataDeleteService(
            AdminRawDataQueryMapper rawDataQueryMapper,
            AdminItemQueryMapper itemQueryMapper,
            MemoryRuntimeManager runtimeManager,
            @Nullable MemoryVector memoryVector) {
        this.rawDataQueryMapper = rawDataQueryMapper;
        this.itemQueryMapper = itemQueryMapper;
        this.runtimeManager = runtimeManager;
        this.memoryVector = memoryVector;
    }

    public RawDataDeleteResult deleteRawData(List<String> rawDataIds) {
        if (rawDataIds == null || rawDataIds.isEmpty()) {
            return new RawDataDeleteResult(0, 0, List.of(), true);
        }
        List<AdminRawDataView> rawDataViews = rawDataQueryMapper.findByBizIds(rawDataIds);
        List<AdminItemView> itemViews = itemQueryMapper.findByRawDataIds(rawDataIds);

        deleteCaptionVectors(rawDataViews);
        deleteItemsViaRuntime(itemViews);

        int deletedRawDataCount =
                rawDataQueryMapper.logicalDeleteByBizIds(
                        rawDataViews.stream().map(AdminRawDataView::rawDataId).distinct().toList());
        invalidateRawDataChanges(rawDataViews);

        return new RawDataDeleteResult(
                deletedRawDataCount,
                itemViews.size(),
                rawDataViews.stream().map(AdminRawDataView::memoryId).distinct().toList(),
                true);
    }

    private void deleteCaptionVectors(List<AdminRawDataView> rawDataViews) {
        Map<MemoryId, List<String>> vectorsByMemory =
                rawDataViews.stream()
                        .filter(
                                view ->
                                        view.captionVectorId() != null
                                                && !view.captionVectorId().isBlank())
                        .collect(
                                Collectors.groupingBy(
                                        view -> DefaultMemoryId.of(view.userId(), view.agentId()),
                                        LinkedHashMap::new,
                                        Collectors.mapping(
                                                AdminRawDataView::captionVectorId,
                                                Collectors.collectingAndThen(
                                                        Collectors.toCollection(LinkedHashSet::new),
                                                        List::copyOf))));

        for (var entry : vectorsByMemory.entrySet()) {
            requireMemoryVector()
                    .deleteBatch(entry.getKey(), entry.getValue())
                    .block(REQUEST_TIMEOUT);
        }
    }

    private MemoryVector requireMemoryVector() {
        if (memoryVector == null) {
            throw new MemoryRuntimeUnavailableException(
                    "Memory runtime is unavailable. Configure the required runtime beans and"
                            + " restart the application.");
        }
        return memoryVector;
    }

    private void deleteItemsViaRuntime(List<AdminItemView> itemViews) {
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
    }

    private void invalidateRawDataChanges(List<AdminRawDataView> rawDataViews) {
        List<MemoryId> memoryIds =
                rawDataViews.stream()
                        .map(view -> (MemoryId) DefaultMemoryId.of(view.userId(), view.agentId()))
                        .distinct()
                        .toList();
        if (memoryIds.isEmpty()) {
            return;
        }
        try (var lease = runtimeManager.acquire()) {
            for (MemoryId memoryId : memoryIds) {
                lease.handle().memory().invalidate(memoryId).block(REQUEST_TIMEOUT);
            }
        }
    }
}
