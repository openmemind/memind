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
package com.openmemind.ai.memory.server.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.core.utils.JsonUtils;
import com.openmemind.ai.memory.server.configuration.RequestIdFilter;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeUnavailableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(new ThrowingController())
                        .setControllerAdvice(new ApiExceptionHandler())
                        .addFilters(new RequestIdFilter())
                        .setMessageConverters(
                                new JacksonJsonHttpMessageConverter(JsonUtils.mapper()))
                        .setValidator(validator)
                        .build();
    }

    @Test
    void serviceUnavailableExceptionsReturnRuntimeUnavailableError(CapturedOutput output)
            throws Exception {
        mockMvc.perform(get("/boom/service-unavailable").header("X-Request-Id", "rid-1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Request-Id", "rid-1"))
                .andExpect(jsonPath("$.error.code").value("runtime_unavailable"))
                .andExpect(jsonPath("$.error.message").value("runtime missing"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist());

        assertThat(output.getOut())
                .contains("runtime_unavailable")
                .contains("GET /boom/service-unavailable")
                .contains("rid-1");
    }

    @Test
    void conflictExceptionsReturnVersionConflictError() throws Exception {
        mockMvc.perform(get("/boom/conflict").header("X-Request-Id", "rid-conflict"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Request-Id", "rid-conflict"))
                .andExpect(jsonPath("$.error.code").value("version_conflict"))
                .andExpect(jsonPath("$.error.message").value("stale version"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void validationExceptionsReturnFieldErrors() throws Exception {
        mockMvc.perform(
                        post("/boom/validation")
                                .contentType(APPLICATION_JSON)
                                .content("{\"userId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.error.code").value("validation_failed"))
                .andExpect(jsonPath("$.error.message").value("Request validation failed"))
                .andExpect(jsonPath("$.error.details.fieldErrors.userId").exists())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void malformedJsonReturnsMalformedJsonError() throws Exception {
        mockMvc.perform(
                        post("/boom/validation")
                                .contentType(APPLICATION_JSON)
                                .content("{\"userId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.error.code").value("malformed_json"))
                .andExpect(jsonPath("$.error.message").value("Malformed JSON request body"))
                .andExpect(jsonPath("$.error.details").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void notFoundExceptionsReturnNotFoundError() throws Exception {
        mockMvc.perform(get("/boom/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.error.code").value("not_found"))
                .andExpect(jsonPath("$.error.message").value("missing resource"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void internalErrorsReturnInternalErrorEnvelope(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/boom/internal").header("X-Request-Id", "rid-2"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", "rid-2"))
                .andExpect(jsonPath("$.error.code").value("internal_error"))
                .andExpect(jsonPath("$.error.message").value("Internal server error"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist());

        assertThat(output.getOut())
                .contains("internal_error")
                .contains("GET /boom/internal")
                .contains("rid-2");
    }

    @RestController
    private static final class ThrowingController {

        @GetMapping("/boom/service-unavailable")
        void serviceUnavailable() {
            throw new MemoryRuntimeUnavailableException("runtime missing");
        }

        @GetMapping("/boom/conflict")
        void conflict() {
            throw new OptimisticLockingFailureException("stale version");
        }

        @PostMapping("/boom/validation")
        void validation(@Valid @RequestBody ValidationRequest request) {}

        @GetMapping("/boom/not-found")
        void notFound() {
            throw new NoSuchElementException("missing resource");
        }

        @GetMapping("/boom/internal")
        void internal() {
            throw new IllegalStateException("boom");
        }
    }

    private record ValidationRequest(@NotBlank String userId) {}
}
