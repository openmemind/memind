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
package com.openmemind.ai.memory.server.controller.admin.buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.domain.buffer.query.ConversationBufferPageQuery;
import com.openmemind.ai.memory.server.domain.buffer.query.InsightBufferPageQuery;
import com.openmemind.ai.memory.server.domain.buffer.view.ConversationBufferView;
import com.openmemind.ai.memory.server.domain.buffer.view.InsightBufferGroupView;
import com.openmemind.ai.memory.server.domain.buffer.view.InsightBufferView;
import com.openmemind.ai.memory.server.domain.common.AdminUpdateResult;
import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.buffer.BufferManagementService;
import com.openmemind.ai.memory.server.service.buffer.BufferQueryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AdminBufferControllerTest {

    private final StubBufferQueryService queryService = new StubBufferQueryService();
    private final StubBufferManagementService managementService = new StubBufferManagementService();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new AdminBufferController(queryService, managementService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void conversationListDefaultsToPendingAndReturnsPagePayload() throws Exception {
        mockMvc.perform(get("/admin/v1/buffers/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.list[0].sessionId").value("s1"))
                .andExpect(jsonPath("$.data.list[0].extracted").value(false));

        assertThat(queryService.recordedConversationQuery.state()).isEqualTo("pending");
        assertThat(queryService.recordedConversationQuery.pageNo()).isEqualTo(1);
        assertThat(queryService.recordedConversationQuery.pageSize()).isEqualTo(20);
    }

    @Test
    void conversationDetailReturnsRow() throws Exception {
        mockMvc.perform(get("/admin/v1/buffers/conversations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.content").value("hello"));
    }

    @Test
    void insightListDefaultsToUnbuiltAndReturnsPagePayload() throws Exception {
        mockMvc.perform(get("/admin/v1/buffers/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.list[0].insightTypeName").value("preference"))
                .andExpect(jsonPath("$.data.list[0].built").value(false));

        assertThat(queryService.recordedInsightQuery.state()).isEqualTo("unbuilt");
    }

    @Test
    void insightGroupsReturnAggregates() throws Exception {
        mockMvc.perform(
                        get("/admin/v1/buffers/insights/groups")
                                .param("memoryId", "u1:a1")
                                .param("insightTypeName", "preference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data[0].memoryId").value("u1:a1"))
                .andExpect(jsonPath("$.data[0].groupName").value("project"))
                .andExpect(jsonPath("$.data[0].unbuilt").value(2));
    }

    @Test
    void markConversationExtractedRequiresIds() throws Exception {
        mockMvc.perform(
                        patch("/admin/v1/buffers/conversations/extracted")
                                .contentType(APPLICATION_JSON)
                                .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    void markConversationExtractedReturnsUpdateResult() throws Exception {
        mockMvc.perform(
                        patch("/admin/v1/buffers/conversations/extracted")
                                .contentType(APPLICATION_JSON)
                                .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(2))
                .andExpect(jsonPath("$.data.affectedMemoryIds[0]").value("u1:a1"));
    }

    @Test
    void deleteConversationRowsReturnsBatchDeleteResult() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/buffers/conversations")
                                .contentType(APPLICATION_JSON)
                                .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(2))
                .andExpect(jsonPath("$.data.affectedMemoryIds[0]").value("u1:a1"));
    }

    @Test
    void updateInsightGroupAllowsNullGroup() throws Exception {
        mockMvc.perform(
                        patch("/admin/v1/buffers/insights/group")
                                .contentType(APPLICATION_JSON)
                                .content("{\"ids\":[10],\"groupName\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(1));
    }

    @Test
    void updateInsightBuiltRequiresBuiltFlag() throws Exception {
        mockMvc.perform(
                        patch("/admin/v1/buffers/insights/built")
                                .contentType(APPLICATION_JSON)
                                .content("{\"ids\":[10]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteInsightBufferRowsReturnsBatchDeleteResult() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/buffers/insights")
                                .contentType(APPLICATION_JSON)
                                .content("{\"ids\":[10,11]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(2));
    }

    private static final class StubBufferQueryService extends BufferQueryService {

        private ConversationBufferPageQuery recordedConversationQuery;
        private InsightBufferPageQuery recordedInsightQuery;

        private StubBufferQueryService() {
            super(null);
        }

        @Override
        public PageResponse<ConversationBufferView> listConversations(
                ConversationBufferPageQuery query) {
            this.recordedConversationQuery = query;
            return new PageResponse<>(
                    query.pageNo(), query.pageSize(), 1, List.of(conversationView()));
        }

        @Override
        public ConversationBufferView getConversation(Long id) {
            return conversationView();
        }

        @Override
        public PageResponse<InsightBufferView> listInsights(InsightBufferPageQuery query) {
            this.recordedInsightQuery = query;
            return new PageResponse<>(query.pageNo(), query.pageSize(), 1, List.of(insightView()));
        }

        @Override
        public List<InsightBufferGroupView> listInsightGroups(
                String memoryId, String insightTypeName) {
            return List.of(new InsightBufferGroupView("u1:a1", "preference", "project", 3, 2, 1));
        }
    }

    private static final class StubBufferManagementService extends BufferManagementService {

        private StubBufferManagementService() {
            super(null);
        }

        @Override
        public AdminUpdateResult markConversationsExtracted(List<Long> ids) {
            return new AdminUpdateResult(ids.size(), List.of("u1:a1"));
        }

        @Override
        public BatchDeleteResult deleteConversations(List<Long> ids) {
            return new BatchDeleteResult(ids.size(), List.of("u1:a1"));
        }

        @Override
        public AdminUpdateResult updateInsightGroup(List<Integer> ids, String groupName) {
            return new AdminUpdateResult(ids.size(), List.of("u1:a1"));
        }

        @Override
        public AdminUpdateResult updateInsightBuilt(List<Integer> ids, Boolean built) {
            return new AdminUpdateResult(ids.size(), List.of("u1:a1"));
        }

        @Override
        public BatchDeleteResult deleteInsightBuffers(List<Integer> ids) {
            return new BatchDeleteResult(ids.size(), List.of("u1:a1"));
        }
    }

    private static ConversationBufferView conversationView() {
        return new ConversationBufferView(
                1L,
                "s1",
                "u1",
                "a1",
                "u1:a1",
                "USER",
                "hello",
                "Ada",
                "claude-code",
                Instant.parse("2026-04-30T00:00:00Z"),
                false,
                Instant.parse("2026-04-30T00:00:01Z"),
                Instant.parse("2026-04-30T00:00:02Z"));
    }

    private static InsightBufferView insightView() {
        return new InsightBufferView(
                10,
                "u1",
                "a1",
                "u1:a1",
                "preference",
                101L,
                "project",
                false,
                Instant.parse("2026-04-30T00:00:01Z"),
                Instant.parse("2026-04-30T00:00:02Z"));
    }
}
