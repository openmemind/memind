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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.utils.JsonUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ApiResultSerializationTest {

    private final JsonMapper mapper = JsonUtils.mapper();

    @Test
    void successResultSerializesOnlyData() throws Exception {
        String json = mapper.writeValueAsString(new SuccessResult<>(Map.of("status", "up")));

        assertThat(json).contains("\"data\"");
        assertThat(json).doesNotContain("code", "message", "timestamp", "traceId", "meta");
    }

    @Test
    void errorResultSerializesSnakeCaseCodeAndGenericDetails() throws Exception {
        var details = new ValidationErrorDetails(Map.of("userId", "must not be blank"));

        String json =
                mapper.writeValueAsString(
                        new ErrorResult<>(
                                new ApiError<>(
                                        ApiErrorCode.VALIDATION_FAILED,
                                        "Request validation failed",
                                        details)));

        assertThat(json).contains("\"code\":\"validation_failed\"");
        assertThat(json).contains("\"fieldErrors\"");
        assertThat(json).doesNotContain("timestamp", "traceId", "meta");
    }
}
