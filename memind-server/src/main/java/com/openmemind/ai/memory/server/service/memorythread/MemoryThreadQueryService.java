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
package com.openmemind.ai.memory.server.service.memorythread;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.memorythread.query.MemoryThreadPageQuery;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminItemMemoryThreadView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadItemView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadStatusView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadView;
import com.openmemind.ai.memory.server.mapper.memorythread.AdminMemoryThreadQueryMapper;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class MemoryThreadQueryService {

    private final AdminMemoryThreadQueryMapper queryMapper;
    private final MemoryRuntimeManager runtimeManager;

    public MemoryThreadQueryService(
            AdminMemoryThreadQueryMapper queryMapper, MemoryRuntimeManager runtimeManager) {
        this.queryMapper = queryMapper;
        this.runtimeManager = runtimeManager;
    }

    public PageResponse<AdminMemoryThreadView> listThreads(MemoryThreadPageQuery query) {
        return queryMapper.page(query);
    }

    public AdminMemoryThreadView getThread(String userId, String agentId, String threadKey) {
        String memoryId = toMemoryId(userId, agentId);
        return queryMapper
                .findByThreadKey(memoryId, threadKey)
                .orElseThrow(
                        () -> new NoSuchElementException("Memory thread not found: " + threadKey));
    }

    public List<AdminMemoryThreadItemView> listThreadItems(
            String userId, String agentId, String threadKey) {
        String memoryId = toMemoryId(userId, agentId);
        List<AdminMemoryThreadItemView> items =
                queryMapper.findItemsByThreadKey(memoryId, threadKey);
        if (!items.isEmpty()) {
            return items;
        }
        getThread(userId, agentId, threadKey);
        return List.of();
    }

    public List<AdminItemMemoryThreadView> listThreadsByItemId(
            String userId, String agentId, Long itemId) {
        return queryMapper.findThreadsByItemId(toMemoryId(userId, agentId), itemId);
    }

    public AdminMemoryThreadStatusView getStatus(String userId, String agentId) {
        try (var lease = runtimeManager.acquire()) {
            return AdminMemoryThreadStatusView.from(
                    lease.handle()
                            .memory()
                            .getThreadRuntimeStatus(DefaultMemoryId.of(userId, agentId)));
        }
    }

    private static String toMemoryId(String userId, String agentId) {
        return DefaultMemoryId.of(userId, agentId).toIdentifier();
    }
}
