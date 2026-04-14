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
package com.openmemind.ai.memory.server.controller.admin.rawdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.rawdata.query.RawDataPageQuery;
import com.openmemind.ai.memory.server.domain.rawdata.response.RawDataDeleteResult;
import com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.rawdata.RawDataDeleteService;
import com.openmemind.ai.memory.server.service.rawdata.RawDataQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

class AdminRawDataControllerTest {

    private final StubRawDataQueryService queryService = new StubRawDataQueryService();
    private final StubRawDataDeleteService deleteService = new StubRawDataDeleteService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new AdminRawDataController(queryService, deleteService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void pageUsesDefaultPaginationAndReturnsPagePayload() throws Exception {
        mockMvc.perform(get("/admin/v1/raw-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].rawDataId").value("rd-1"));

        assertThat(queryService.recordedQuery.pageNo()).isEqualTo(1);
        assertThat(queryService.recordedQuery.pageSize()).isEqualTo(20);
    }

    @Test
    void deleteRequiresIds() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/raw-data")
                                .contentType(APPLICATION_JSON)
                                .content("{\"rawDataIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void deleteReturnsCleanupFlag() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/raw-data")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                Map.of("rawDataIds", List.of("rd-1")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.deletedRawDataCount").value(1))
                .andExpect(jsonPath("$.data.deletedItemCount").value(2))
                .andExpect(jsonPath("$.data.insightCleanupRequired").value(true));
    }

    private static final class StubRawDataQueryService extends RawDataQueryService {

        private RawDataPageQuery recordedQuery;

        private StubRawDataQueryService() {
            super(null);
        }

        @Override
        public PageResponse<AdminRawDataView> listRawData(RawDataPageQuery query) {
            this.recordedQuery = query;
            return new PageResponse<>(1, 20, 1, List.of(rawDataView()));
        }

        @Override
        public AdminRawDataView getRawData(String rawDataId) {
            return rawDataView();
        }
    }

    private static final class StubRawDataDeleteService extends RawDataDeleteService {

        private StubRawDataDeleteService() {
            super(null, null, null, null);
        }

        @Override
        public RawDataDeleteResult deleteRawData(List<String> rawDataIds) {
            return new RawDataDeleteResult(1, 2, List.of("u1:a1"), true);
        }
    }

    private static AdminRawDataView rawDataView() {
        return new AdminRawDataView(
                "rd-1",
                "u1",
                "a1",
                "u1:a1",
                "conversation",
                "content-1",
                Map.of("type", "conversation"),
                "caption",
                "cap-1",
                Map.of(),
                Instant.parse("2026-03-31T10:00:00Z"),
                Instant.parse("2026-03-31T10:01:00Z"),
                Instant.parse("2026-03-31T10:02:00Z"),
                Instant.parse("2026-03-31T10:03:00Z"));
    }
}
