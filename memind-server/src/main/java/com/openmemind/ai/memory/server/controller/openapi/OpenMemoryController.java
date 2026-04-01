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

import com.openmemind.ai.memory.server.domain.common.ApiResult;
import com.openmemind.ai.memory.server.domain.memory.request.AddMessageRequest;
import com.openmemind.ai.memory.server.domain.memory.request.CommitMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.ExtractMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.RetrieveMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.response.RetrieveMemoryResponse;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/open/v1/memory")
public class OpenMemoryController {

    private final OpenMemoryApplicationService service;

    public OpenMemoryController(OpenMemoryApplicationService service) {
        this.service = service;
    }

    @PostMapping("/extract")
    public Mono<ApiResult<Void>> extract(@Valid @RequestBody ExtractMemoryRequest request) {
        service.extractAsync(request);
        return Mono.just(ApiResult.ok());
    }

    @PostMapping("/add-message")
    public Mono<ApiResult<Void>> addMessage(@Valid @RequestBody AddMessageRequest request) {
        service.addMessageAsync(request);
        return Mono.just(ApiResult.ok());
    }

    @PostMapping("/commit")
    public Mono<ApiResult<Void>> commit(@Valid @RequestBody CommitMemoryRequest request) {
        service.commitAsync(request);
        return Mono.just(ApiResult.ok());
    }

    @PostMapping("/retrieve")
    public ApiResult<RetrieveMemoryResponse> retrieve(
            @Valid @RequestBody RetrieveMemoryRequest request) {
        return ApiResult.success(service.retrieve(request));
    }
}
