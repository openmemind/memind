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
package com.openmemind.ai.memory.server.controller.admin.item;

import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.common.PageResult;
import com.openmemind.ai.memory.server.domain.common.SuccessResult;
import com.openmemind.ai.memory.server.domain.item.query.ItemPageQuery;
import com.openmemind.ai.memory.server.domain.item.request.ItemDeleteRequest;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminItemMemoryThreadView;
import com.openmemind.ai.memory.server.service.item.ItemDeleteService;
import com.openmemind.ai.memory.server.service.item.ItemQueryService;
import com.openmemind.ai.memory.server.service.memorythread.MemoryThreadQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/v1/items")
public class AdminItemController {

    private final ItemQueryService queryService;
    private final ItemDeleteService deleteService;
    private final MemoryThreadQueryService memoryThreadQueryService;

    public AdminItemController(
            ItemQueryService queryService,
            ItemDeleteService deleteService,
            MemoryThreadQueryService memoryThreadQueryService) {
        this.queryService = queryService;
        this.deleteService = deleteService;
        this.memoryThreadQueryService = memoryThreadQueryService;
    }

    @GetMapping
    public SuccessResult<PageResult<AdminItemView>> page(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String rawDataId) {
        return new SuccessResult<>(
                PageResult.from(
                        queryService.listItems(
                                ItemPageQuery.of(
                                        page, pageSize, userId, agentId, scope, category, type,
                                        rawDataId))));
    }

    @GetMapping("/{itemId}")
    public SuccessResult<AdminItemView> detail(@PathVariable Long itemId) {
        return new SuccessResult<>(queryService.getItem(itemId));
    }

    @GetMapping("/{itemId}/memory-threads")
    public SuccessResult<java.util.List<AdminItemMemoryThreadView>> itemThreads(
            @PathVariable Long itemId,
            @RequestParam String userId,
            @RequestParam(required = false) String agentId) {
        return new SuccessResult<>(
                memoryThreadQueryService.listThreadsByItemId(userId, agentId, itemId));
    }

    @DeleteMapping
    public SuccessResult<BatchDeleteResult> delete(@Valid @RequestBody ItemDeleteRequest request) {
        return new SuccessResult<>(deleteService.deleteItems(request.itemIds()));
    }
}
