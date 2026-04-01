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
package com.openmemind.ai.memory.server.domain.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(String code, String message, T data, Instant timestamp, String traceId) {

    public static final String OK_CODE = "200";
    public static final String SUCCESS_CODE = "success";

    public static ApiResult<Void> ok() {
        return new ApiResult<>(OK_CODE, null, null, Instant.now(), null);
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(SUCCESS_CODE, null, data, Instant.now(), null);
    }

    public static <T> ApiResult<T> failure(String code, String message, T data, String traceId) {
        return new ApiResult<>(code, message, data, Instant.now(), traceId);
    }
}
