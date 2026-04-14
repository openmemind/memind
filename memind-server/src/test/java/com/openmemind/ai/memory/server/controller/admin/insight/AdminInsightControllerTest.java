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
package com.openmemind.ai.memory.server.controller.admin.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.insight.query.InsightPageQuery;
import com.openmemind.ai.memory.server.domain.insight.view.AdminInsightView;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.insight.InsightDeleteService;
import com.openmemind.ai.memory.server.service.insight.InsightQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

class AdminInsightControllerTest {

    private final StubInsightQueryService queryService = new StubInsightQueryService();
    private final StubInsightDeleteService deleteService = new StubInsightDeleteService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new AdminInsightController(queryService, deleteService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void pageUsesDefaultPaginationAndReturnsPagePayload() throws Exception {
        mockMvc.perform(get("/admin/v1/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].insightId").value(201));

        assertThat(queryService.recordedQuery.pageNo()).isEqualTo(1);
        assertThat(queryService.recordedQuery.pageSize()).isEqualTo(20);
    }

    @Test
    void deleteRequiresIds() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/insights")
                                .contentType(APPLICATION_JSON)
                                .content("{\"insightIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void deleteReturnsAffectedMemoryIds() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/insights")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                Map.of("insightIds", List.of(201L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.deletedCount").value(1))
                .andExpect(jsonPath("$.data.affectedMemoryIds[0]").value("u1:a1"));
    }

    @Test
    void missingInsightReturnsNotFound() throws Exception {
        queryService.missingInsight = true;

        mockMvc.perform(get("/admin/v1/insights/201"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    private static final class StubInsightQueryService extends InsightQueryService {

        private InsightPageQuery recordedQuery;
        private boolean missingInsight;

        private StubInsightQueryService() {
            super(null);
        }

        @Override
        public PageResponse<AdminInsightView> listInsights(InsightPageQuery query) {
            this.recordedQuery = query;
            return new PageResponse<>(query.pageNo(), query.pageSize(), 1, List.of(insightView()));
        }

        @Override
        public AdminInsightView getInsight(Long insightId) {
            if (missingInsight) {
                throw new java.util.NoSuchElementException("Insight not found: " + insightId);
            }
            return insightView();
        }
    }

    private static final class StubInsightDeleteService extends InsightDeleteService {

        private StubInsightDeleteService() {
            super(null, null);
        }

        @Override
        public BatchDeleteResult deleteInsights(List<Long> insightIds) {
            return new BatchDeleteResult(1, List.of("u1:a1"));
        }
    }

    private static AdminInsightView insightView() {
        return new AdminInsightView(
                201L,
                "u1",
                "a1",
                "u1:a1",
                "profile",
                "user",
                "preference",
                List.of("profile"),
                "prefers concise answers",
                List.of(
                        new InsightPoint(
                                InsightPoint.PointType.SUMMARY, "point-1", 0.9F, List.of())),
                "group-1",
                0.95F,
                Instant.parse("2026-03-31T10:00:00Z"),
                List.of(),
                "LEAF",
                null,
                List.of(),
                1,
                Instant.parse("2026-03-31T10:00:01Z"),
                Instant.parse("2026-03-31T10:00:02Z"));
    }
}
