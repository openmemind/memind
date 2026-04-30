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
package com.openmemind.ai.memory.server.controller.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.domain.dashboard.view.AdminDashboardView;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.dashboard.DashboardQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AdminDashboardControllerTest {

    private final StubDashboardQueryService queryService = new StubDashboardQueryService();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(new AdminDashboardController(queryService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void dashboardDefaultsToSevenDaysAndReturnsSections() throws Exception {
        mockMvc.perform(get("/admin/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.totals.rawData").value(3))
                .andExpect(jsonPath("$.data.backlog.conversationPending").value(1))
                .andExpect(jsonPath("$.data.activity.days").value(7))
                .andExpect(jsonPath("$.data.breakdown.sourceClients[0].name").value("claude-code"))
                .andExpect(jsonPath("$.data.healthSignals.graphEnabled").value(true))
                .andExpect(
                        jsonPath("$.data.healthSignals.threadProjectionStates[0].state")
                                .value("AVAILABLE"));

        assertThat(queryService.recordedMemoryId).isNull();
        assertThat(queryService.recordedDays).isEqualTo(7);
    }

    @Test
    void dashboardRejectsTooManyDays() throws Exception {
        mockMvc.perform(get("/admin/v1/dashboard").param("days", "31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    void dashboardPassesMemoryIdToService() throws Exception {
        mockMvc.perform(get("/admin/v1/dashboard").param("memoryId", "u1:a1").param("days", "14"))
                .andExpect(status().isOk());

        assertThat(queryService.recordedMemoryId).isEqualTo("u1:a1");
        assertThat(queryService.recordedDays).isEqualTo(14);
    }

    private static final class StubDashboardQueryService extends DashboardQueryService {

        private String recordedMemoryId;
        private int recordedDays;

        private StubDashboardQueryService() {
            super(null, null);
        }

        @Override
        public AdminDashboardView getDashboard(String memoryId, int days) {
            this.recordedMemoryId = memoryId;
            this.recordedDays = days;
            return dashboard(days);
        }
    }

    private static AdminDashboardView dashboard(int days) {
        return new AdminDashboardView(
                new AdminDashboardView.Totals(3, 2, 1, 4, 5, 6),
                new AdminDashboardView.Backlog(1, 2, 3, 4, 5, 6),
                new AdminDashboardView.Activity(
                        days,
                        List.of(new AdminDashboardView.DailyCount("2026-04-30", 3)),
                        List.of(),
                        List.of()),
                new AdminDashboardView.Breakdown(
                        List.of(new AdminDashboardView.NamedCount("claude-code", 3)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()),
                new AdminDashboardView.HealthSignals(
                        true, true, List.of(new AdminDashboardView.StateCount("AVAILABLE", 4))));
    }
}
