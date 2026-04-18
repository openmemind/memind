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
import com.openmemind.ai.memory.server.mapper.memorythread.AdminMemoryThreadQueryMapper;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;

@Service
public class MemoryThreadRebuildService {

    private final AdminMemoryThreadQueryMapper queryMapper;
    private final MemoryRuntimeManager runtimeManager;

    public MemoryThreadRebuildService(
            AdminMemoryThreadQueryMapper queryMapper, MemoryRuntimeManager runtimeManager) {
        this.queryMapper = queryMapper;
        this.runtimeManager = runtimeManager;
    }

    public int rebuildMemory(String memoryIdText) {
        var memoryId = parseMemoryId(memoryIdText);
        try (var lease = runtimeManager.acquire()) {
            lease.handle().memory().rebuildMemoryThreads(memoryId);
        }
        return 1;
    }

    public int rebuildAll() {
        var memoryIds = new LinkedHashSet<>(queryMapper.listDistinctMemoryIds());
        try (var lease = runtimeManager.acquire()) {
            for (String memoryIdText : memoryIds) {
                lease.handle().memory().rebuildMemoryThreads(parseMemoryId(memoryIdText));
            }
        }
        return memoryIds.size();
    }

    private static DefaultMemoryId parseMemoryId(String memoryIdText) {
        int separator = memoryIdText.indexOf(':');
        if (separator < 0) {
            return DefaultMemoryId.of(memoryIdText, null);
        }
        return DefaultMemoryId.of(
                memoryIdText.substring(0, separator), memoryIdText.substring(separator + 1));
    }
}
