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
package com.openmemind.ai.memory.server.controller.admin.memoryoption;

import com.openmemind.ai.memory.server.domain.common.ApiResult;
import com.openmemind.ai.memory.server.domain.config.request.MemoryOptionsPutRequest;
import com.openmemind.ai.memory.server.domain.config.response.MemoryOptionsGetResponse;
import com.openmemind.ai.memory.server.service.config.MemoryOptionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/config/memory-options")
public class MemoryOptionsController {

    private final MemoryOptionService memoryOptionService;

    public MemoryOptionsController(MemoryOptionService memoryOptionService) {
        this.memoryOptionService = memoryOptionService;
    }

    @GetMapping
    public ApiResult<MemoryOptionsGetResponse> get() {
        var snapshot = memoryOptionService.getCurrent();
        return ApiResult.success(
                new MemoryOptionsGetResponse(snapshot.version(), snapshot.config()));
    }

    @PutMapping
    public ApiResult<MemoryOptionsGetResponse> update(
            @Valid @RequestBody MemoryOptionsPutRequest request) {
        var snapshot = memoryOptionService.update(request.expectedVersion(), request.config());
        return ApiResult.success(
                new MemoryOptionsGetResponse(snapshot.version(), snapshot.config()));
    }
}
