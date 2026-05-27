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
package com.openmemind.ai.memory.server.controller.openapi;

import com.openmemind.ai.memory.server.domain.common.SuccessResult;
import com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryItemsRequest;
import com.openmemind.ai.memory.server.domain.memory.request.QueryMemoryRawDataRequest;
import com.openmemind.ai.memory.server.domain.memory.request.RetrieveMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryItemsResponse;
import com.openmemind.ai.memory.server.domain.memory.response.QueryMemoryRawDataResponse;
import com.openmemind.ai.memory.server.domain.memory.response.RetrieveMemoryResponse;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryApplicationService;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryAssetQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/v1/memory")
public class OpenMemoryQueryController {

    private final OpenMemoryApplicationService service;
    private final OpenMemoryAssetQueryService assetQueryService;

    public OpenMemoryQueryController(
            OpenMemoryApplicationService service, OpenMemoryAssetQueryService assetQueryService) {
        this.service = service;
        this.assetQueryService = assetQueryService;
    }

    @PostMapping("/retrieve")
    public SuccessResult<RetrieveMemoryResponse> retrieve(
            @Valid @RequestBody RetrieveMemoryRequest request) {
        return new SuccessResult<>(service.retrieve(request));
    }

    @PostMapping("/items/query")
    public SuccessResult<QueryMemoryItemsResponse> queryItems(
            @Valid @RequestBody QueryMemoryItemsRequest request) {
        return new SuccessResult<>(assetQueryService.queryItems(request));
    }

    @PostMapping("/raw-data/query")
    public SuccessResult<QueryMemoryRawDataResponse> queryRawData(
            @Valid @RequestBody QueryMemoryRawDataRequest request) {
        return new SuccessResult<>(assetQueryService.queryRawData(request));
    }
}
