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
package com.openmemind.ai.memory.server.controller.admin.memory;

import com.openmemind.ai.memory.server.domain.common.PageResult;
import com.openmemind.ai.memory.server.domain.common.SuccessResult;
import com.openmemind.ai.memory.server.domain.item.query.ItemPageQuery;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.domain.memory.query.MemoryPageQuery;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemoryActivityView;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemorySnapshotResult;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemorySummaryItem;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemoryWorkspaceView;
import com.openmemind.ai.memory.server.domain.rawdata.query.RawDataPageQuery;
import com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView;
import com.openmemind.ai.memory.server.service.item.ItemQueryService;
import com.openmemind.ai.memory.server.service.memory.MemoryAdminService;
import com.openmemind.ai.memory.server.service.rawdata.RawDataQueryService;
import com.openmemind.ai.memory.server.support.AdminMemoryScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/v1/memories")
public class AdminMemoryController {

    private final MemoryAdminService memoryService;
    private final RawDataQueryService rawDataQueryService;
    private final ItemQueryService itemQueryService;

    public AdminMemoryController(
            MemoryAdminService memoryService,
            RawDataQueryService rawDataQueryService,
            ItemQueryService itemQueryService) {
        this.memoryService = memoryService;
        this.rawDataQueryService = rawDataQueryService;
        this.itemQueryService = itemQueryService;
    }

    @GetMapping
    public SuccessResult<PageResult<AdminMemoryWorkspaceView>> page(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String sort) {
        return new SuccessResult<>(
                PageResult.from(
                        memoryService.listMemories(
                                MemoryPageQuery.of(page, pageSize, q, status, alertLevel, sort))));
    }

    @GetMapping("/{memoryId}/activity")
    public SuccessResult<List<AdminMemoryActivityView>> activity(@PathVariable String memoryId) {
        return new SuccessResult<>(memoryService.activity(memoryId));
    }

    @PostMapping("/{memoryId}/snapshot")
    public SuccessResult<AdminMemorySnapshotResult> snapshot(@PathVariable String memoryId) {
        return new SuccessResult<>(memoryService.forceSnapshot(memoryId));
    }

    @GetMapping("/{memoryId}/raw-data")
    public SuccessResult<PageResult<AdminRawDataView>> rawData(
            @PathVariable String memoryId,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Instant startTimeFrom,
            @RequestParam(required = false) Instant startTimeTo) {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId(memoryId);
        return new SuccessResult<>(
                PageResult.from(
                        rawDataQueryService.listRawData(
                                RawDataPageQuery.of(
                                        page,
                                        pageSize,
                                        scope.userId(),
                                        scope.agentId(),
                                        startTimeFrom,
                                        startTimeTo))));
    }

    @GetMapping("/{memoryId}/items")
    public SuccessResult<PageResult<AdminItemView>> items(
            @PathVariable String memoryId,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String rawDataId) {
        AdminMemoryScope memoryScope = AdminMemoryScope.fromMemoryId(memoryId);
        return new SuccessResult<>(
                PageResult.from(
                        itemQueryService.listItems(
                                ItemPageQuery.of(
                                        page,
                                        pageSize,
                                        memoryScope.userId(),
                                        memoryScope.agentId(),
                                        scope,
                                        category,
                                        type,
                                        rawDataId))));
    }

    @GetMapping("/{memoryId}/items/summary")
    public SuccessResult<List<AdminMemorySummaryItem>> itemsSummary(@PathVariable String memoryId) {
        return new SuccessResult<>(memoryService.itemsSummary(memoryId));
    }
}
