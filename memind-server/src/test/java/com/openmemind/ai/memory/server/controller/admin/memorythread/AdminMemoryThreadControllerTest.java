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
package com.openmemind.ai.memory.server.controller.admin.memorythread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.memorythread.query.MemoryThreadPageQuery;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadItemView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadStatusView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminMemoryThreadView;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.memorythread.MemoryThreadQueryService;
import com.openmemind.ai.memory.server.service.memorythread.MemoryThreadRebuildService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AdminMemoryThreadControllerTest {

    private final StubMemoryThreadQueryService queryService = new StubMemoryThreadQueryService();
    private final StubMemoryThreadRebuildService rebuildService =
            new StubMemoryThreadRebuildService();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new AdminMemoryThreadController(queryService, rebuildService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void pageEndpointReturnsMemoryThreads() throws Exception {
        mockMvc.perform(get("/admin/v1/memory-threads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].threadKey").value("topic:concept:travel"))
                .andExpect(jsonPath("$.data.list[0].threadId").doesNotExist());

        assertThat(queryService.recordedQuery.pageNo()).isEqualTo(1);
        assertThat(queryService.recordedQuery.pageSize()).isEqualTo(20);
    }

    @Test
    void detailEndpointUsesThreadKeyAsTheOnlyPublicIdentifier() throws Exception {
        mockMvc.perform(
                        get("/admin/v1/memory-threads/{threadKey}", "topic:concept:travel")
                                .param("userId", "u1")
                                .param("agentId", "a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.threadKey").value("topic:concept:travel"))
                .andExpect(jsonPath("$.data.threadId").doesNotExist());
    }

    @Test
    void threadMembersEndpointUsesThreadKeyAndDoesNotExposeThreadId() throws Exception {
        mockMvc.perform(
                        get("/admin/v1/memory-threads/{threadKey}/items", "topic:concept:travel")
                                .param("userId", "u1")
                                .param("agentId", "a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data[0].threadKey").value("topic:concept:travel"))
                .andExpect(jsonPath("$.data[0].itemId").value(301))
                .andExpect(jsonPath("$.data[0].role").value("core"))
                .andExpect(jsonPath("$.data[0].threadId").doesNotExist());
    }

    @Test
    void statusEndpointIsMemoryScoped() throws Exception {
        mockMvc.perform(
                        get("/admin/v1/memory-threads/status")
                                .param("userId", "u1")
                                .param("agentId", "a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.projectionState").value("available"))
                .andExpect(jsonPath("$.data.pendingCount").value(0));
    }

    @Test
    void rebuildEndpointRequiresMemoryScope() throws Exception {
        mockMvc.perform(
                        post("/admin/v1/memory-threads/rebuild")
                                .param("userId", "u1")
                                .param("agentId", "a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void rebuildEndpointRejectsGlobalBackfillRequests() throws Exception {
        mockMvc.perform(post("/admin/v1/memory-threads/rebuild"))
                .andExpect(status().isBadRequest());
    }

    private static final class StubMemoryThreadQueryService extends MemoryThreadQueryService {

        private MemoryThreadPageQuery recordedQuery;

        private StubMemoryThreadQueryService() {
            super(null, null);
        }

        @Override
        public PageResponse<AdminMemoryThreadView> listThreads(MemoryThreadPageQuery query) {
            this.recordedQuery = query;
            return new PageResponse<>(query.pageNo(), query.pageSize(), 1, List.of(threadView()));
        }

        @Override
        public AdminMemoryThreadView getThread(String userId, String agentId, String threadKey) {
            return threadView();
        }

        @Override
        public List<AdminMemoryThreadItemView> listThreadItems(
                String userId, String agentId, String threadKey) {
            return List.of(threadItemView());
        }

        @Override
        public AdminMemoryThreadStatusView getStatus(String userId, String agentId) {
            return new AdminMemoryThreadStatusView(
                    "available",
                    0L,
                    0L,
                    false,
                    301L,
                    "thread-core-v1",
                    Instant.parse("2026-04-17T10:00:00Z"),
                    null);
        }
    }

    private static final class StubMemoryThreadRebuildService extends MemoryThreadRebuildService {

        private StubMemoryThreadRebuildService() {
            super(null);
        }

        @Override
        public int rebuild(String userId, String agentId) {
            return 1;
        }
    }

    private static AdminMemoryThreadView threadView() {
        return new AdminMemoryThreadView(
                "u1",
                "a1",
                "u1:a1",
                "topic:concept:travel",
                "topic",
                "concept",
                "travel",
                "Travel planning",
                "active",
                "ongoing",
                "Discussing a summer trip.",
                Map.of("latestUpdate", "Booked flights"),
                1,
                Instant.parse("2026-03-31T09:00:00Z"),
                Instant.parse("2026-03-31T10:00:00Z"),
                Instant.parse("2026-03-31T10:00:00Z"),
                null,
                3L,
                2L,
                Instant.parse("2026-03-31T10:00:01Z"),
                Instant.parse("2026-03-31T10:00:02Z"));
    }

    private static AdminMemoryThreadItemView threadItemView() {
        return new AdminMemoryThreadItemView(
                "u1",
                "a1",
                "u1:a1",
                "topic:concept:travel",
                301L,
                "core",
                true,
                0.91d,
                Instant.parse("2026-03-31T10:00:04Z"),
                Instant.parse("2026-03-31T10:00:05Z"));
    }
}
