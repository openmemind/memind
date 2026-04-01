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
package com.openmemind.ai.memory.server.service.insight;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.insight.view.AdminInsightView;
import com.openmemind.ai.memory.server.mapper.insight.AdminInsightQueryMapper;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class InsightDeleteService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final AdminInsightQueryMapper insightQueryMapper;
    private final MemoryRuntimeManager runtimeManager;

    public InsightDeleteService(
            AdminInsightQueryMapper insightQueryMapper, MemoryRuntimeManager runtimeManager) {
        this.insightQueryMapper = insightQueryMapper;
        this.runtimeManager = runtimeManager;
    }

    public BatchDeleteResult deleteInsights(List<Long> insightIds) {
        if (insightIds == null || insightIds.isEmpty()) {
            return new BatchDeleteResult(0, List.of());
        }
        List<AdminInsightView> insightViews = insightQueryMapper.findByBizIds(insightIds);
        Map<MemoryId, List<Long>> insightIdsByMemory =
                insightViews.stream()
                        .collect(
                                Collectors.groupingBy(
                                        view -> DefaultMemoryId.of(view.userId(), view.agentId()),
                                        LinkedHashMap::new,
                                        Collectors.mapping(
                                                AdminInsightView::insightId,
                                                Collectors.collectingAndThen(
                                                        Collectors.toCollection(LinkedHashSet::new),
                                                        List::copyOf))));

        try (var lease = runtimeManager.acquire()) {
            for (var entry : insightIdsByMemory.entrySet()) {
                lease.handle()
                        .memory()
                        .deleteInsights(entry.getKey(), entry.getValue())
                        .block(REQUEST_TIMEOUT);
            }
        }
        return new BatchDeleteResult(
                insightViews.size(),
                insightViews.stream().map(AdminInsightView::memoryId).distinct().toList());
    }
}
