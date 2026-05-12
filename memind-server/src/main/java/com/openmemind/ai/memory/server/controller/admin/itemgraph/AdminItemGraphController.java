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
package com.openmemind.ai.memory.server.controller.admin.itemgraph;

import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.common.PageResult;
import com.openmemind.ai.memory.server.domain.common.SuccessResult;
import com.openmemind.ai.memory.server.domain.itemgraph.query.ItemGraphPageQueries;
import com.openmemind.ai.memory.server.domain.itemgraph.request.GraphEntityDeleteRequest;
import com.openmemind.ai.memory.server.domain.itemgraph.request.GraphIdsRequest;
import com.openmemind.ai.memory.server.domain.itemgraph.view.AdminGraphEntityDeleteResult;
import com.openmemind.ai.memory.server.domain.itemgraph.view.GraphEntityDetailView;
import com.openmemind.ai.memory.server.domain.itemgraph.view.ItemGraphViews;
import com.openmemind.ai.memory.server.service.itemgraph.ItemGraphManagementService;
import com.openmemind.ai.memory.server.service.itemgraph.ItemGraphQueryService;
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
@RequestMapping("/admin/v1/item-graph")
public class AdminItemGraphController {

    private final ItemGraphQueryService queryService;
    private final ItemGraphManagementService managementService;

    public AdminItemGraphController(
            ItemGraphQueryService queryService, ItemGraphManagementService managementService) {
        this.queryService = queryService;
        this.managementService = managementService;
    }

    @GetMapping("/summary")
    public SuccessResult<ItemGraphViews.SummaryView> summary(
            @RequestParam(required = false) String memoryId) {
        return new SuccessResult<>(queryService.summary(memoryId));
    }

    @GetMapping("/entities")
    public SuccessResult<PageResult<ItemGraphViews.EntityView>> entities(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String q) {
        return new SuccessResult<>(
                PageResult.from(
                        queryService.listEntities(
                                new ItemGraphPageQueries.EntityPageQuery(
                                        page, pageSize, memoryId, entityType, q))));
    }

    @GetMapping("/entities/{id}")
    public SuccessResult<GraphEntityDetailView> entityDetail(@PathVariable Integer id) {
        return new SuccessResult<>(queryService.getEntity(id));
    }

    @DeleteMapping("/entities")
    public SuccessResult<AdminGraphEntityDeleteResult> deleteEntities(
            @Valid @RequestBody GraphEntityDeleteRequest request) {
        return new SuccessResult<>(
                managementService.deleteEntities(request.memoryId(), request.entityKeys()));
    }

    @GetMapping("/aliases")
    public SuccessResult<PageResult<ItemGraphViews.AliasView>> aliases(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) String entityKey,
            @RequestParam(required = false) String q) {
        return new SuccessResult<>(
                PageResult.from(
                        queryService.listAliases(
                                new ItemGraphPageQueries.AliasPageQuery(
                                        page, pageSize, memoryId, entityKey, q))));
    }

    @DeleteMapping("/aliases")
    public SuccessResult<BatchDeleteResult> deleteAliases(
            @Valid @RequestBody GraphIdsRequest request) {
        return new SuccessResult<>(managementService.deleteAliases(request.ids()));
    }

    @GetMapping("/mentions")
    public SuccessResult<PageResult<ItemGraphViews.MentionView>> mentions(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) String entityKey) {
        return new SuccessResult<>(
                PageResult.from(
                        queryService.listMentions(
                                new ItemGraphPageQueries.MentionPageQuery(
                                        page, pageSize, memoryId, itemId, entityKey))));
    }

    @DeleteMapping("/mentions")
    public SuccessResult<BatchDeleteResult> deleteMentions(
            @Valid @RequestBody GraphIdsRequest request) {
        return new SuccessResult<>(managementService.deleteMentions(request.ids()));
    }

    @GetMapping("/item-links")
    public SuccessResult<PageResult<ItemGraphViews.ItemLinkView>> itemLinks(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) String linkType,
            @RequestParam(required = false) String evidenceSource) {
        return new SuccessResult<>(
                PageResult.from(
                        queryService.listItemLinks(
                                new ItemGraphPageQueries.ItemLinkPageQuery(
                                        page,
                                        pageSize,
                                        memoryId,
                                        itemId,
                                        linkType,
                                        evidenceSource))));
    }

    @DeleteMapping("/item-links")
    public SuccessResult<BatchDeleteResult> deleteItemLinks(
            @Valid @RequestBody GraphIdsRequest request) {
        return new SuccessResult<>(managementService.deleteItemLinks(request.ids()));
    }

    @GetMapping("/cooccurrences")
    public SuccessResult<PageResult<ItemGraphViews.CooccurrenceView>> cooccurrences(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) String entityKey) {
        return new SuccessResult<>(
                PageResult.from(
                        queryService.listCooccurrences(
                                new ItemGraphPageQueries.CooccurrencePageQuery(
                                        page, pageSize, memoryId, entityKey))));
    }

    @DeleteMapping("/cooccurrences")
    public SuccessResult<BatchDeleteResult> deleteCooccurrences(
            @Valid @RequestBody GraphIdsRequest request) {
        return new SuccessResult<>(managementService.deleteCooccurrences(request.ids()));
    }

    @GetMapping("/batches")
    public SuccessResult<PageResult<ItemGraphViews.BatchView>> batches(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) String state) {
        return new SuccessResult<>(
                PageResult.from(
                        queryService.listBatches(
                                new ItemGraphPageQueries.BatchPageQuery(
                                        page, pageSize, memoryId, state))));
    }
}
