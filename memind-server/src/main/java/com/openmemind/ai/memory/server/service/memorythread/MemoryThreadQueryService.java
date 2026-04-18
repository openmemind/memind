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

import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.memorythread.query.MemoryThreadPageQuery;
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

    public AdminMemoryThreadView getThread(Long threadId) {
        return queryMapper
                .findByBizId(threadId)
                .orElseThrow(
                        () -> new NoSuchElementException("Memory thread not found: " + threadId));
    }

    public List<AdminMemoryThreadItemView> listThreadItems(Long threadId) {
        List<AdminMemoryThreadItemView> items = queryMapper.findItemsByThreadId(threadId);
        if (!items.isEmpty()) {
            return items;
        }
        getThread(threadId);
        return List.of();
    }

    public AdminMemoryThreadItemView getThreadByItemId(Long itemId) {
        return queryMapper
                .findByItemId(itemId)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Memory thread not found for item: " + itemId));
    }

    public AdminMemoryThreadStatusView getStatus() {
        try (var lease = runtimeManager.acquire()) {
            return AdminMemoryThreadStatusView.from(lease.handle().memory().memoryThreadStatus());
        }
    }
}
