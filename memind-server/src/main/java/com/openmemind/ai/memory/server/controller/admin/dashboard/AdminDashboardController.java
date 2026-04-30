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

import com.openmemind.ai.memory.server.domain.common.ApiResult;
import com.openmemind.ai.memory.server.domain.dashboard.view.AdminDashboardView;
import com.openmemind.ai.memory.server.service.dashboard.DashboardQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    public ApiResult<AdminDashboardView> get(
            @RequestParam(required = false) String memoryId,
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int days) {
        if (days < 1 || days > 30) {
            throw new IllegalArgumentException("days must be between 1 and 30");
        }
        return ApiResult.success(queryService.getDashboard(memoryId, days));
    }
}
