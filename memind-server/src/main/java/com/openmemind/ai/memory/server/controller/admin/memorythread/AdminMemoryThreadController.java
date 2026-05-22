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

import com.openmemind.ai.memory.server.domain.common.PageResult;
import com.openmemind.ai.memory.server.domain.common.SuccessResult;
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
    public SuccessResult<PageResult<AdminMemoryThreadView>> page(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String status) {
        return new SuccessResult<>(
                PageResult.from(
                        queryService.listThreads(
                                MemoryThreadPageQuery.of(
                                        page, pageSize, userId, agentId, status))));
    }

    @GetMapping("/{threadKey}")
    public SuccessResult<AdminMemoryThreadView> detail(
            @PathVariable String threadKey,
            @RequestParam String userId,
            @RequestParam(required = false) String agentId) {
        return new SuccessResult<>(queryService.getThread(userId, agentId, threadKey));
    }

    @GetMapping("/{threadKey}/items")
    public SuccessResult<List<AdminMemoryThreadItemView>> items(
            @PathVariable String threadKey,
            @RequestParam String userId,
            @RequestParam(required = false) String agentId) {
        return new SuccessResult<>(queryService.listThreadItems(userId, agentId, threadKey));
    }

    @GetMapping("/{threadKey}/timeline")
    public SuccessResult<
                    List<
                            com.openmemind.ai.memory.server.domain.memorythread.view
                                    .AdminMemoryThreadTimelineItemView>>
            timeline(
                    @PathVariable String threadKey,
                    @RequestParam String userId,
                    @RequestParam(required = false) String agentId) {
        return new SuccessResult<>(queryService.timeline(userId, agentId, threadKey));
    }

    @GetMapping("/status")
    public SuccessResult<AdminMemoryThreadStatusView> status(
            @RequestParam String userId, @RequestParam(required = false) String agentId) {
        return new SuccessResult<>(queryService.getStatus(userId, agentId));
    }

    @PostMapping("/rebuild")
    public SuccessResult<Integer> rebuild(
            @RequestParam String userId, @RequestParam(required = false) String agentId) {
        return new SuccessResult<>(rebuildService.rebuild(userId, agentId));
    }
}
