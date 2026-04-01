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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.runtime.MemoryRuntimeUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(new ThrowingController())
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
    }

    @Test
    void serviceUnavailableExceptionsAreLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/boom/service-unavailable").header("X-Request-Id", "rid-1"))
                .andExpect(status().isServiceUnavailable());

        assertThat(output.getOut())
                .contains("service_unavailable")
                .contains("GET /boom/service-unavailable")
                .contains("rid-1");
    }

    @Test
    void internalErrorsAreLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/boom/internal").header("X-Request-Id", "rid-2"))
                .andExpect(status().isInternalServerError());

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

        @GetMapping("/boom/internal")
        void internal() {
            throw new IllegalStateException("boom");
        }
    }
}
