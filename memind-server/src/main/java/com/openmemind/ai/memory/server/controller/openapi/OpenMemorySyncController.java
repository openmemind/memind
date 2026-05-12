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

import com.openmemind.ai.memory.server.domain.common.ApiError;
import com.openmemind.ai.memory.server.domain.common.ApiErrorCode;
import com.openmemind.ai.memory.server.domain.common.ApiResult;
import com.openmemind.ai.memory.server.domain.common.ErrorResult;
import com.openmemind.ai.memory.server.domain.common.OperationErrorDetails;
import com.openmemind.ai.memory.server.domain.common.SuccessResult;
import com.openmemind.ai.memory.server.domain.memory.request.AddMessageRequest;
import com.openmemind.ai.memory.server.domain.memory.request.CommitMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.request.ExtractMemoryRequest;
import com.openmemind.ai.memory.server.domain.memory.response.AddMessageResponse;
import com.openmemind.ai.memory.server.domain.memory.response.ExtractMemoryResponse;
import com.openmemind.ai.memory.server.service.memory.OpenMemoryApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/v1/memory/sync")
public class OpenMemorySyncController {

    private static final String FAILURE_MESSAGE = "Memory extraction failed";

    private final OpenMemoryApplicationService service;

    public OpenMemorySyncController(OpenMemoryApplicationService service) {
        this.service = service;
    }

    @PostMapping("/extract")
    public ResponseEntity<ApiResult<?>> extract(@Valid @RequestBody ExtractMemoryRequest request) {
        return extractionResponse("extract", service.extract(request));
    }

    @PostMapping("/add-message")
    public ResponseEntity<ApiResult<?>> addMessage(@Valid @RequestBody AddMessageRequest request) {
        AddMessageResponse response = service.addMessage(request);
        if (!response.triggered() || response.result() == null) {
            return ResponseEntity.ok(new SuccessResult<>(response));
        }
        if (isFailed(response.result())) {
            return failedExtraction("add-message", response.result());
        }
        return ResponseEntity.ok(new SuccessResult<>(response));
    }

    @PostMapping("/commit")
    public ResponseEntity<ApiResult<?>> commit(@Valid @RequestBody CommitMemoryRequest request) {
        return extractionResponse("commit", service.commit(request));
    }

    private static ResponseEntity<ApiResult<?>> extractionResponse(
            String operation, ExtractMemoryResponse response) {
        if (isFailed(response)) {
            return failedExtraction(operation, response);
        }
        return ResponseEntity.ok(new SuccessResult<>(response));
    }

    private static ResponseEntity<ApiResult<?>> failedExtraction(
            String operation, ExtractMemoryResponse response) {
        String reason =
                response.errorMessage() == null || response.errorMessage().isBlank()
                        ? FAILURE_MESSAGE
                        : response.errorMessage();
        OperationErrorDetails details = new OperationErrorDetails(operation, reason);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        new ErrorResult<>(
                                new ApiError<>(
                                        ApiErrorCode.INTERNAL_ERROR, FAILURE_MESSAGE, details)));
    }

    private static boolean isFailed(ExtractMemoryResponse response) {
        return response != null && "FAILED".equals(response.status());
    }
}
