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
package com.openmemind.ai.memory.server.controller.admin.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.configuration.RequestIdFilter;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.item.query.ItemPageQuery;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.domain.memory.query.MemoryPageQuery;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemoryActivityView;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemorySnapshotResult;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemorySummaryItem;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemoryWorkspaceView;
import com.openmemind.ai.memory.server.domain.rawdata.query.RawDataPageQuery;
import com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.item.ItemQueryService;
import com.openmemind.ai.memory.server.service.memory.MemoryAdminService;
import com.openmemind.ai.memory.server.service.rawdata.RawDataQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AdminMemoryControllerTest {

    private final StubMemoryAdminService memoryService = new StubMemoryAdminService();
    private final StubRawDataQueryService rawDataQueryService = new StubRawDataQueryService();
    private final StubItemQueryService itemQueryService = new StubItemQueryService();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new AdminMemoryController(
                                        memoryService, rawDataQueryService, itemQueryService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .addFilters(new RequestIdFilter())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void memoriesPageReturnsWorkspaceRows() throws Exception {
        mockMvc.perform(get("/admin/v1/memories").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.page.page").value(2))
                .andExpect(jsonPath("$.data.page.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("u1:a1"))
                .andExpect(jsonPath("$.data.items[0].userId").value("u1"))
                .andExpect(jsonPath("$.data.items[0].agentId").value("a1"));

        assertThat(memoryService.recordedPageQuery.pageNo()).isEqualTo(2);
    }

    @Test
    void memoryScopedRawDataDelegatesToRawDataQuery() throws Exception {
        mockMvc.perform(get("/admin/v1/memories/{memoryId}/raw-data", "u1:a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].rawDataId").value("rd-1"));

        assertThat(rawDataQueryService.recordedQuery.userId()).isEqualTo("u1");
        assertThat(rawDataQueryService.recordedQuery.agentId()).isEqualTo("a1");
    }

    @Test
    void memoryScopedItemsDelegatesToItemQuery() throws Exception {
        mockMvc.perform(get("/admin/v1/memories/{memoryId}/items", "u1:a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].itemId").value(101));

        assertThat(itemQueryService.recordedQuery.userId()).isEqualTo("u1");
        assertThat(itemQueryService.recordedQuery.agentId()).isEqualTo("a1");
    }

    @Test
    void memoryScopedItemsSummaryReturnsAggregateCards() throws Exception {
        mockMvc.perform(get("/admin/v1/memories/{memoryId}/items/summary", "u1:a1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data[0].label").value("Items"))
                .andExpect(jsonPath("$.data[0].value").value("2"))
                .andExpect(jsonPath("$.data[1].label").value("Types"))
                .andExpect(jsonPath("$.data[1].value").value("1"));
    }

    @Test
    void memoryActivityAndSnapshotReturnAcceptedPayloads() throws Exception {
        mockMvc.perform(get("/admin/v1/memories/{memoryId}/activity", "u1:a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Raw data ingested"));

        mockMvc.perform(post("/admin/v1/memories/{memoryId}/snapshot", "u1:a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memoryId").value("u1:a1"))
                .andExpect(jsonPath("$.data.status").value("accepted"));
    }

    private static final class StubMemoryAdminService extends MemoryAdminService {

        private MemoryPageQuery recordedPageQuery;

        private StubMemoryAdminService() {
            super(null);
        }

        @Override
        public PageResponse<AdminMemoryWorkspaceView> listMemories(MemoryPageQuery query) {
            this.recordedPageQuery = query;
            return new PageResponse<>(query.pageNo(), query.pageSize(), 1, List.of(memory()));
        }

        @Override
        public List<AdminMemoryActivityView> activity(String memoryId) {
            return List.of(
                    new AdminMemoryActivityView(
                            "Raw data ingested", "rd-1", "2026-05-21T10:00:00Z", "default"));
        }

        @Override
        public AdminMemorySnapshotResult forceSnapshot(String memoryId) {
            return new AdminMemorySnapshotResult(memoryId, "accepted");
        }

        @Override
        public List<AdminMemorySummaryItem> itemsSummary(String memoryId) {
            return List.of(
                    new AdminMemorySummaryItem("Items", "2", "1 user / 1 agent"),
                    new AdminMemorySummaryItem("Types", "1", null));
        }
    }

    private static final class StubRawDataQueryService extends RawDataQueryService {

        private RawDataPageQuery recordedQuery;

        private StubRawDataQueryService() {
            super(null);
        }

        @Override
        public PageResponse<AdminRawDataView> listRawData(RawDataPageQuery query) {
            this.recordedQuery = query;
            return new PageResponse<>(query.pageNo(), query.pageSize(), 1, List.of(rawData()));
        }
    }

    private static final class StubItemQueryService extends ItemQueryService {

        private ItemPageQuery recordedQuery;

        private StubItemQueryService() {
            super(null);
        }

        @Override
        public PageResponse<AdminItemView> listItems(ItemPageQuery query) {
            this.recordedQuery = query;
            return new PageResponse<>(query.pageNo(), query.pageSize(), 1, List.of(item()));
        }
    }

    private static AdminMemoryWorkspaceView memory() {
        return new AdminMemoryWorkspaceView(
                "u1:a1",
                "u1:a1",
                "u1",
                "a1",
                "active",
                1,
                2,
                3,
                new AdminMemoryWorkspaceView.Alerts(0, 1),
                "2026-05-21T10:00:00Z",
                "2026-05-21T09:00:00Z");
    }

    private static AdminRawDataView rawData() {
        return new AdminRawDataView(
                "rd-1",
                "u1",
                "a1",
                "u1:a1",
                "conversation",
                "api",
                "content-1",
                Map.of(),
                "caption",
                "cap-1",
                Map.of(),
                Instant.parse("2026-05-21T10:00:00Z"),
                Instant.parse("2026-05-21T10:01:00Z"),
                Instant.parse("2026-05-21T10:02:00Z"),
                Instant.parse("2026-05-21T10:03:00Z"));
    }

    private static AdminItemView item() {
        return new AdminItemView(
                101L,
                "u1",
                "a1",
                "u1:a1",
                "content",
                "USER",
                "profile",
                "vec-1",
                "rd-1",
                "hash-1",
                Instant.parse("2026-05-21T10:00:00Z"),
                Instant.parse("2026-05-21T10:00:00Z"),
                Map.of(),
                "FACT",
                "conversation",
                "api",
                Instant.parse("2026-05-21T10:02:00Z"),
                Instant.parse("2026-05-21T10:03:00Z"));
    }
}
