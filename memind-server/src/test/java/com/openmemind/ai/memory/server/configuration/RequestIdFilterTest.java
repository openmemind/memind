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
package com.openmemind.ai.memory.server.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.core.utils.JsonUtils;
import com.openmemind.ai.memory.server.controller.openapi.OpenHealthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RequestIdFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(new OpenHealthController())
                        .addFilters(new RequestIdFilter())
                        .setMessageConverters(
                                new JacksonJsonHttpMessageConverter(JsonUtils.mapper()))
                        .build();
    }

    @Test
    void echoesExistingRequestIdHeader() throws Exception {
        mockMvc.perform(get("/open/v1/health").header(RequestIdFilter.HEADER, "rid-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER, "rid-1"));
    }

    @Test
    void generatesMissingRequestIdHeader() throws Exception {
        mockMvc.perform(get("/open/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    void clearsRequestIdFromMdcAfterRequest() throws Exception {
        mockMvc.perform(get("/open/v1/health").header(RequestIdFilter.HEADER, "rid-2"))
                .andExpect(status().isOk());

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void doesNotAddRequestIdToResponseBody() throws Exception {
        mockMvc.perform(get("/open/v1/health").header(RequestIdFilter.HEADER, "rid-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }
}
