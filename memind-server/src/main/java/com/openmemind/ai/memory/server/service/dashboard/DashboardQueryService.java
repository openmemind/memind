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
package com.openmemind.ai.memory.server.service.dashboard;

import com.openmemind.ai.memory.server.domain.config.view.MemoryOptionItemView;
import com.openmemind.ai.memory.server.domain.dashboard.view.AdminDashboardView;
import com.openmemind.ai.memory.server.mapper.dashboard.AdminDashboardQueryMapper;
import com.openmemind.ai.memory.server.service.config.MemoryOptionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardQueryService {

    private final AdminDashboardQueryMapper dashboardQueryMapper;
    private final MemoryOptionService memoryOptionService;

    public DashboardQueryService(
            AdminDashboardQueryMapper dashboardQueryMapper,
            MemoryOptionService memoryOptionService) {
        this.dashboardQueryMapper = dashboardQueryMapper;
        this.memoryOptionService = memoryOptionService;
    }

    public AdminDashboardView getDashboard(String memoryId, int days) {
        Map<String, MemoryOptionItemView> options =
                flattenOptions(memoryOptionService.getCurrent().config());
        boolean graphEnabled = booleanOption(options, "extraction.item.graph.enabled");
        boolean retrievalGraphAssistEnabled =
                booleanOption(options, "retrieval.simple.graphAssist.enabled")
                        || booleanOption(options, "retrieval.deep.graphAssist.enabled");
        return dashboardQueryMapper.dashboard(
                memoryId, days, graphEnabled, retrievalGraphAssistEnabled);
    }

    private static Map<String, MemoryOptionItemView> flattenOptions(
            Map<String, List<MemoryOptionItemView>> config) {
        Map<String, MemoryOptionItemView> options = new LinkedHashMap<>();
        if (config == null) {
            return options;
        }
        config.values().stream()
                .filter(items -> items != null)
                .flatMap(List::stream)
                .forEach(item -> options.put(item.key(), item));
        return options;
    }

    private static boolean booleanOption(Map<String, MemoryOptionItemView> options, String key) {
        MemoryOptionItemView item = options.get(key);
        return item != null && Boolean.TRUE.equals(item.value());
    }
}
