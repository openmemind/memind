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
package com.openmemind.ai.memory.server.controller.admin.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.item.query.ItemPageQuery;
import com.openmemind.ai.memory.server.domain.item.view.AdminItemView;
import com.openmemind.ai.memory.server.domain.memorythread.view.AdminItemMemoryThreadView;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.item.ItemDeleteService;
import com.openmemind.ai.memory.server.service.item.ItemQueryService;
import com.openmemind.ai.memory.server.service.memorythread.MemoryThreadQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

class AdminItemControllerTest {

    private final StubItemQueryService queryService = new StubItemQueryService();
    private final StubItemDeleteService deleteService = new StubItemDeleteService();
    private final StubMemoryThreadQueryService memoryThreadQueryService =
            new StubMemoryThreadQueryService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new AdminItemController(
                                        queryService, deleteService, memoryThreadQueryService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void pageUsesDefaultPaginationAndReturnsPagePayload() throws Exception {
        mockMvc.perform(get("/admin/v1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].itemId").value(101));

        assertThat(queryService.recordedQuery.pageNo()).isEqualTo(1);
        assertThat(queryService.recordedQuery.pageSize()).isEqualTo(20);
    }

    @Test
    void deleteRequiresIds() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/items")
                                .contentType(APPLICATION_JSON)
                                .content("{\"itemIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void deleteReturnsAffectedMemoryIds() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/items")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                Map.of("itemIds", List.of(101L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.deletedCount").value(1))
                .andExpect(jsonPath("$.data.affectedMemoryIds[0]").value("u1:a1"));
    }

    @Test
    void missingItemReturnsNotFound() throws Exception {
        queryService.missingItem = true;

        mockMvc.perform(get("/admin/v1/items/101"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void memoryThreadsReturnAllThreadsForItem() throws Exception {
        mockMvc.perform(
                        get("/admin/v1/items/101/memory-threads")
                                .param("userId", "u1")
                                .param("agentId", "a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].threadKey").value("topic:concept:travel"))
                .andExpect(jsonPath("$.data[0].threadId").doesNotExist())
                .andExpect(
                        jsonPath("$.data[1].threadKey")
                                .value("relationship:relationship:person:alice|person:bob"));
    }

    private static final class StubItemQueryService extends ItemQueryService {

        private ItemPageQuery recordedQuery;
        private boolean missingItem;

        private StubItemQueryService() {
            super(null);
        }

        @Override
        public PageResponse<AdminItemView> listItems(ItemPageQuery query) {
            this.recordedQuery = query;
            return new PageResponse<>(query.pageNo(), query.pageSize(), 1, List.of(itemView()));
        }

        @Override
        public AdminItemView getItem(Long itemId) {
            if (missingItem) {
                throw new java.util.NoSuchElementException("Item not found: " + itemId);
            }
            return itemView();
        }
    }

    private static final class StubItemDeleteService extends ItemDeleteService {

        private StubItemDeleteService() {
            super(null, null);
        }

        @Override
        public BatchDeleteResult deleteItems(List<Long> itemIds) {
            return new BatchDeleteResult(1, List.of("u1:a1"));
        }
    }

    private static final class StubMemoryThreadQueryService extends MemoryThreadQueryService {

        private StubMemoryThreadQueryService() {
            super(null, null);
        }

        @Override
        public List<AdminItemMemoryThreadView> listThreadsByItemId(
                String userId, String agentId, Long itemId) {
            return List.of(
                    memoryThreadView("topic:concept:travel", "topic", "Travel planning", "active"),
                    memoryThreadView(
                            "relationship:relationship:person:alice|person:bob",
                            "relationship",
                            "Alice and Bob",
                            "active"));
        }
    }

    private static AdminItemView itemView() {
        return new AdminItemView(
                101L,
                "u1",
                "a1",
                "u1:a1",
                "likes coffee",
                "user",
                "profile",
                "vec-1",
                "rd-1",
                "hash-1",
                Instant.parse("2026-03-31T10:00:00Z"),
                Instant.parse("2026-03-31T10:00:01Z"),
                Map.of(),
                "FACT",
                "conversation",
                Instant.parse("2026-03-31T10:00:02Z"),
                Instant.parse("2026-03-31T10:00:03Z"));
    }

    private static AdminItemMemoryThreadView memoryThreadView(
            String threadKey, String threadType, String displayLabel, String lifecycleStatus) {
        return new AdminItemMemoryThreadView(
                "u1",
                "a1",
                "u1:a1",
                threadKey,
                threadType,
                "concept",
                "travel",
                displayLabel,
                lifecycleStatus,
                "ongoing",
                "Discussing a summer trip.",
                101L,
                "core",
                true,
                0.91d,
                Instant.parse("2026-03-31T10:00:05Z"),
                Instant.parse("2026-03-31T10:00:06Z"));
    }
}
