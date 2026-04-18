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
package com.openmemind.ai.memory.server.controller.admin.memorythread;

import com.openmemind.ai.memory.server.domain.common.ApiResult;
import com.openmemind.ai.memory.server.domain.common.PageResult;
import com.openmemind.ai.memory.server.domain.memorythread.query.MemoryThreadPageQuery;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadItemView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadStatusView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadView;
import com.openmemind.ai.memory.server.service.memorythread.MemoryThreadQueryService;
import com.openmemind.ai.memory.server.service.memorythread.MemoryThreadRebuildService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/admin/v1/memory-threads")
public class AdminMemoryThreadController {

    private final MemoryThreadQueryService queryService;
    private final MemoryThreadRebuildService rebuildService;

    public AdminMemoryThreadController(
            MemoryThreadQueryService queryService, MemoryThreadRebuildService rebuildService) {
        this.queryService = queryService;
        this.rebuildService = rebuildService;
    }

    @GetMapping
    public ApiResult<PageResult<AdminMemoryThreadView>> page(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String status) {
        return ApiResult.success(
                PageResult.from(
                        queryService.listThreads(
                                MemoryThreadPageQuery.of(
                                        pageNo, pageSize, userId, agentId, status))));
    }

    @GetMapping("/{threadId}")
    public ApiResult<AdminMemoryThreadView> detail(@PathVariable Long threadId) {
        return ApiResult.success(queryService.getThread(threadId));
    }

    @GetMapping("/{threadId}/items")
    public ApiResult<List<AdminMemoryThreadItemView>> items(@PathVariable Long threadId) {
        return ApiResult.success(queryService.listThreadItems(threadId));
    }

    @GetMapping("/status")
    public ApiResult<AdminMemoryThreadStatusView> status() {
        return ApiResult.success(queryService.getStatus());
    }

    @PostMapping("/rebuild")
    public ApiResult<Integer> rebuildAll() {
        return ApiResult.success(rebuildService.rebuildAll());
    }

    @PostMapping("/rebuild/{memoryId}")
    public ApiResult<Integer> rebuildMemory(@PathVariable String memoryId) {
        return ApiResult.success(rebuildService.rebuildMemory(memoryId));
    }
}
