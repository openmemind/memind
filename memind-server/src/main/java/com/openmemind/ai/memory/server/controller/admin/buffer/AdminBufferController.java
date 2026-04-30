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
package com.openmemind.ai.memory.server.controller.admin.buffer;

import com.openmemind.ai.memory.server.domain.buffer.query.ConversationBufferPageQuery;
import com.openmemind.ai.memory.server.domain.buffer.query.InsightBufferPageQuery;
import com.openmemind.ai.memory.server.domain.buffer.request.AdminIdsRequest;
import com.openmemind.ai.memory.server.domain.buffer.request.AdminLongIdsRequest;
import com.openmemind.ai.memory.server.domain.buffer.request.InsightBufferBuiltUpdateRequest;
import com.openmemind.ai.memory.server.domain.buffer.request.InsightBufferGroupUpdateRequest;
import com.openmemind.ai.memory.server.domain.buffer.view.ConversationBufferView;
import com.openmemind.ai.memory.server.domain.buffer.view.InsightBufferGroupView;
import com.openmemind.ai.memory.server.domain.buffer.view.InsightBufferView;
import com.openmemind.ai.memory.server.domain.common.AdminUpdateResult;
import com.openmemind.ai.memory.server.domain.common.ApiResult;
import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.common.PageResult;
import com.openmemind.ai.memory.server.service.buffer.BufferManagementService;
import com.openmemind.ai.memory.server.service.buffer.BufferQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/v1/buffers")
public class AdminBufferController {

    private final BufferQueryService queryService;
    private final BufferManagementService managementService;

    public AdminBufferController(
            BufferQueryService queryService, BufferManagementService managementService) {
        this.queryService = queryService;
        this.managementService = managementService;
    }

    @GetMapping("/conversations")
    public ApiResult<PageResult<ConversationBufferView>> conversations(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "pending") String state) {
        return ApiResult.success(
                PageResult.from(
                        queryService.listConversations(
                                ConversationBufferPageQuery.of(
                                        pageNo, pageSize, memoryId, sessionId, state))));
    }

    @GetMapping("/conversations/{id}")
    public ApiResult<ConversationBufferView> conversationDetail(@PathVariable Long id) {
        return ApiResult.success(queryService.getConversation(id));
    }

    @PatchMapping("/conversations/extracted")
    public ApiResult<AdminUpdateResult> markConversationsExtracted(
            @Valid @RequestBody AdminLongIdsRequest request) {
        return ApiResult.success(managementService.markConversationsExtracted(request.ids()));
    }

    @DeleteMapping("/conversations")
    public ApiResult<BatchDeleteResult> deleteConversations(
            @Valid @RequestBody AdminLongIdsRequest request) {
        return ApiResult.success(managementService.deleteConversations(request.ids()));
    }

    @GetMapping("/insights")
    public ApiResult<PageResult<InsightBufferView>> insights(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) String insightTypeName,
            @RequestParam(defaultValue = "unbuilt") String state) {
        return ApiResult.success(
                PageResult.from(
                        queryService.listInsights(
                                InsightBufferPageQuery.of(
                                        pageNo, pageSize, memoryId, insightTypeName, state))));
    }

    @GetMapping("/insights/groups")
    public ApiResult<List<InsightBufferGroupView>> insightGroups(
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) String insightTypeName) {
        return ApiResult.success(queryService.listInsightGroups(memoryId, insightTypeName));
    }

    @PatchMapping("/insights/group")
    public ApiResult<AdminUpdateResult> updateInsightGroup(
            @Valid @RequestBody InsightBufferGroupUpdateRequest request) {
        return ApiResult.success(
                managementService.updateInsightGroup(request.ids(), request.groupName()));
    }

    @PatchMapping("/insights/built")
    public ApiResult<AdminUpdateResult> updateInsightBuilt(
            @Valid @RequestBody InsightBufferBuiltUpdateRequest request) {
        return ApiResult.success(
                managementService.updateInsightBuilt(request.ids(), request.built()));
    }

    @DeleteMapping("/insights")
    public ApiResult<BatchDeleteResult> deleteInsights(
            @Valid @RequestBody AdminIdsRequest request) {
        return ApiResult.success(managementService.deleteInsightBuffers(request.ids()));
    }
}
