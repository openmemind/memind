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

import com.openmemind.ai.memory.server.domain.common.OperationAccepted;
import com.openmemind.ai.memory.server.domain.common.SuccessResult;
import com.openmemind.ai.memory.server.domain.memory.request.AddMessageRequest;
import com.openmemind.ai.memory.server.domain.memory.request.CommitMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.ExtractMemoryRequest;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryApplicationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/v1/memory/async")
public class OpenMemoryAsyncController {

    private final OpenMemoryApplicationService service;

    public OpenMemoryAsyncController(OpenMemoryApplicationService service) {
        this.service = service;
    }

    @PostMapping("/extract")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SuccessResult<OperationAccepted> extract(
            @Valid @RequestBody ExtractMemoryRequest request) {
        service.extractAsync(request);
        return new SuccessResult<>(accepted());
    }

    @PostMapping("/add-message")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SuccessResult<OperationAccepted> addMessage(
            @Valid @RequestBody AddMessageRequest request) {
        service.addMessageAsync(request);
        return new SuccessResult<>(accepted());
    }

    @PostMapping("/commit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SuccessResult<OperationAccepted> commit(
            @Valid @RequestBody CommitMemoryRequest request) {
        service.commitAsync(request);
        return new SuccessResult<>(accepted());
    }

    private static OperationAccepted accepted() {
        return new OperationAccepted("op_" + UUID.randomUUID(), "accepted", "async");
    }
}
