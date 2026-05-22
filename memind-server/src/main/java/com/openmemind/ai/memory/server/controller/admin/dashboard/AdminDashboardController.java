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
package com.openmemind.ai.memory.server.controller.admin.dashboard;

import com.openmemind.ai.memory.server.domain.common.SuccessResult;
import com.openmemind.ai.memory.server.domain.dashboard.view.AdminDashboardView;
import com.openmemind.ai.memory.server.domain.dashboard.view.AdminRecentMemoryView;
import com.openmemind.ai.memory.server.service.dashboard.DashboardQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/v1/dashboard")
public class AdminDashboardController {

    private final DashboardQueryService queryService;

    public AdminDashboardController(DashboardQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public SuccessResult<AdminDashboardView> get(
            @RequestParam(required = false) String memoryId,
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int days) {
        if (days < 1 || days > 30) {
            throw new IllegalArgumentException("days must be between 1 and 30");
        }
        return new SuccessResult<>(queryService.getDashboard(memoryId, days));
    }

    @GetMapping("/recent-memories")
    public SuccessResult<List<AdminRecentMemoryView>> recentMemories(
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }
        return new SuccessResult<>(queryService.recentMemories(limit));
    }
}
