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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.domain.config.response.MemoryOptionsSnapshot;
import com.openmemind.ai.memory.server.domain.config.view.MemoryOptionItemView;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.config.MemoryOptionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

class MemoryOptionsControllerTest {

    private final StubMemoryOptionService configService = new StubMemoryOptionService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(new MemoryOptionsController(configService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void getReturnsVersionAndGroupedConfig() throws Exception {
        mockMvc.perform(get("/admin/v1/config/memory-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(
                        jsonPath("$.data.config.extraction[0].key")
                                .value("extraction.common.timeout"));
    }

    @Test
    void putDelegatesUpdateAndReturnsUpdatedSnapshot() throws Exception {
        mockMvc.perform(
                        put("/admin/v1/config/memory-options")
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(updateRequestBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.config.extraction[0].value").value("PT45S"));

        assertThat(configService.recordedExpectedVersion).isEqualTo(3L);
        assertThat(configService.recordedConfig)
                .containsKey("extraction")
                .satisfies(
                        config ->
                                assertThat(config.get("extraction"))
                                        .singleElement()
                                        .extracting(MemoryOptionItemView::value)
                                        .isEqualTo("PT45S"));
    }

    @Test
    void putReturnsConflictWhenExpectedVersionIsStale() throws Exception {
        configService.updateFailure =
                new OptimisticLockingFailureException("Memory options version conflict");

        mockMvc.perform(
                        put("/admin/v1/config/memory-options")
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(updateRequestBody())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    private static Map<String, Object> updateRequestBody() {
        return Map.of(
                "expectedVersion",
                3,
                "config",
                Map.of(
                        "extraction",
                        List.of(
                                Map.of(
                                        "key",
                                        "extraction.common.timeout",
                                        "value",
                                        "PT45S",
                                        "description",
                                        "Maximum extraction timeout"))));
    }

    private static final class StubMemoryOptionService extends MemoryOptionService {

        private final MemoryOptionsSnapshot current =
                new MemoryOptionsSnapshot(
                        3L,
                        Map.of(
                                "extraction",
                                List.of(
                                        new MemoryOptionItemView(
                                                "extraction.common.timeout",
                                                "PT30S",
                                                "Maximum extraction timeout",
                                                "duration",
                                                "PT30S",
                                                Map.of("format", "iso-8601-duration")))));

        private long recordedExpectedVersion;
        private Map<String, List<MemoryOptionItemView>> recordedConfig;
        private RuntimeException updateFailure;

        private StubMemoryOptionService() {
            super(null, null, null, null, null);
        }

        @Override
        public MemoryOptionsSnapshot getCurrent() {
            return current;
        }

        @Override
        public MemoryOptionsSnapshot update(
                long expectedVersion, Map<String, List<MemoryOptionItemView>> config) {
            this.recordedExpectedVersion = expectedVersion;
            this.recordedConfig = config;
            if (updateFailure != null) {
                throw updateFailure;
            }
            return new MemoryOptionsSnapshot(4L, config);
        }
    }
}
