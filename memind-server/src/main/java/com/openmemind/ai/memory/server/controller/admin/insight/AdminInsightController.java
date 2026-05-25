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
package com.openmemind.ai.memory.server.controller.admin.insight;

import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.common.PageResult;
import com.openmemind.ai.memory.server.domain.common.SuccessResult;
import com.openmemind.ai.memory.server.domain.insight.query.InsightPageQuery;
import com.openmemind.ai.memory.server.domain.insight.request.InsightDeleteRequest;
import com.openmemind.ai.memory.server.domain.insight.view.AdminInsightView;
import com.openmemind.ai.memory.server.service.insight.InsightDeleteService;
import com.openmemind.ai.memory.server.service.insight.InsightQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
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
@RequestMapping("/admin/v1/insights")
public class AdminInsightController {

    private final InsightQueryService queryService;
    private final InsightDeleteService deleteService;

    public AdminInsightController(
            InsightQueryService queryService, InsightDeleteService deleteService) {
        this.queryService = queryService;
        this.deleteService = deleteService;
    }

    @GetMapping
    public SuccessResult<PageResult<AdminInsightView>> page(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String tier) {
        return new SuccessResult<>(
                PageResult.from(
                        queryService.listInsights(
                                InsightPageQuery.of(
                                        page, pageSize, userId, agentId, scope, type, tier))));
    }

    @GetMapping("/{insightId}")
    public SuccessResult<AdminInsightView> detail(@PathVariable Long insightId) {
        return new SuccessResult<>(queryService.getInsight(insightId));
    }

    @GetMapping("/list")
    public SuccessResult<List<AdminInsightView>> details(@RequestParam List<Long> insightIds) {
        return new SuccessResult<>(insightIds.stream().map(queryService::getInsight).toList());
    }

    @GetMapping("/tree")
    public SuccessResult<com.openmemind.ai.memory.server.domain.insight.view.AdminInsightTreeView>
            tree(
                    @RequestParam(required = false) String userId,
                    @RequestParam(required = false) String agentId) {
        return new SuccessResult<>(queryService.tree(userId, agentId));
    }

    @org.springframework.web.bind.annotation.PostMapping("/{insightId}/regenerate")
    public SuccessResult<
                    com.openmemind.ai.memory.server.domain.insight.view
                            .AdminInsightRegenerateResult>
            regenerate(@PathVariable Long insightId) {
        return new SuccessResult<>(queryService.regenerate(insightId));
    }

    @DeleteMapping
    public SuccessResult<BatchDeleteResult> delete(
            @Valid @RequestBody InsightDeleteRequest request) {
        return new SuccessResult<>(deleteService.deleteInsights(request.insightIds()));
    }
}
