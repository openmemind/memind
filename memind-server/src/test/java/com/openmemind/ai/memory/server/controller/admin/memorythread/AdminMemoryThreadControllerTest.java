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
                .andExpect(jsonPath("$.data.list[0].threadKey").value("mt:101"));

        assertThat(queryService.recordedQuery.pageNo()).isEqualTo(1);
        assertThat(queryService.recordedQuery.pageSize()).isEqualTo(20);
    }

    @Test
    void detailEndpointReturnsMemoryThread() throws Exception {
        mockMvc.perform(get("/admin/v1/memory-threads/{threadId}", 101L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.threadKey").value("mt:101"));
    }

    @Test
    void itemsEndpointReturnsThreadMembers() throws Exception {
        mockMvc.perform(get("/admin/v1/memory-threads/{threadId}/items", 101L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data[0].itemId").value(301))
                .andExpect(jsonPath("$.data[0].role").value("core"));
    }

    @Test
    void statusEndpointReturnsDerivationRuntimeState() throws Exception {
        mockMvc.perform(get("/admin/v1/memory-threads/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.derivationAvailable").value(true))
                .andExpect(jsonPath("$.data.queueDepth").value(2));
    }

    @Test
    void rebuildEndpointTriggersSingleMemoryRebuild() throws Exception {
        mockMvc.perform(post("/admin/v1/memory-threads/rebuild/{memoryId}", "u1:a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void rebuildAllEndpointTriggersGlobalBackfill() throws Exception {
        mockMvc.perform(post("/admin/v1/memory-threads/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data").value(7));
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
        public AdminMemoryThreadView getThread(Long threadId) {
            return threadView();
        }

        @Override
        public List<AdminMemoryThreadItemView> listThreadItems(Long threadId) {
            return List.of(threadItemView());
        }

        @Override
        public AdminMemoryThreadStatusView getStatus() {
            return new AdminMemoryThreadStatusView(
                    true, true, true, null, 2, Instant.parse("2026-04-17T10:00:00Z"), null, 0L);
        }
    }

    private static final class StubMemoryThreadRebuildService extends MemoryThreadRebuildService {

        private StubMemoryThreadRebuildService() {
            super(null, null);
        }

        @Override
        public int rebuildMemory(String memoryIdText) {
            return 1;
        }

        @Override
        public int rebuildAll() {
            return 7;
        }
    }

    private static AdminMemoryThreadView threadView() {
        return new AdminMemoryThreadView(
                101L,
                "u1",
                "a1",
                "u1:a1",
                "mt:101",
                "conversation",
                "Travel planning",
                "Discussed a summer trip.",
                "open",
                0.88d,
                Instant.parse("2026-03-31T09:00:00Z"),
                Instant.parse("2026-03-31T10:00:00Z"),
                Instant.parse("2026-03-31T10:00:00Z"),
                301L,
                301L,
                1,
                Map.of("source", "test"),
                Instant.parse("2026-03-31T10:00:01Z"),
                Instant.parse("2026-03-31T10:00:02Z"));
    }

    private static AdminMemoryThreadItemView threadItemView() {
        return new AdminMemoryThreadItemView(
                401L,
                "u1",
                "a1",
                "u1:a1",
                101L,
                "mt:101",
                301L,
                "core",
                0.91d,
                1,
                Instant.parse("2026-03-31T10:00:03Z"),
                Map.of("source", "test"),
                Instant.parse("2026-03-31T10:00:04Z"),
                Instant.parse("2026-03-31T10:00:05Z"));
    }
}
