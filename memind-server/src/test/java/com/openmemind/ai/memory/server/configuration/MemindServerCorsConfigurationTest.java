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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.controller.openapi.OpenHealthController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.cors.CorsConfiguration;

class MemindServerCorsConfigurationTest {

    @Test
    void corsConfigurationIsOpenForAllOriginsMethodsAndHeaders() {
        CorsConfiguration cors = MemindServerCorsConfiguration.openCorsConfiguration();

        assertThat(cors.getAllowedOrigins()).containsExactly(CorsConfiguration.ALL);
        assertThat(cors.getAllowedMethods()).containsExactly(CorsConfiguration.ALL);
        assertThat(cors.getAllowedHeaders()).containsExactly(CorsConfiguration.ALL);
        assertThat(cors.getAllowCredentials()).isFalse();
    }

    @Test
    void preflightRequestsReceiveOpenCorsHeaders() throws Exception {
        MockMvc mockMvc =
                MockMvcBuilders.standaloneSetup(new OpenHealthController())
                        .addFilters(new MemindServerCorsConfiguration().corsFilter())
                        .build();

        mockMvc.perform(
                        options("/open/v1/health")
                                .header(HttpHeaders.ORIGIN, "http://example.test")
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        HttpMethod.GET.name()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET"));
    }
}
