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
package com.openmemind.ai.memory.server.controller.admin.itemgraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.domain.common.BatchDeleteResult;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.itemgraph.query.ItemGraphPageQueries;
import com.openmemind.ai.memory.server.domain.itemgraph.view.AdminGraphEntityDeleteResult;
import com.openmemind.ai.memory.server.domain.itemgraph.view.GraphEntityDetailView;
import com.openmemind.ai.memory.server.domain.itemgraph.view.ItemGraphViews;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.itemgraph.ItemGraphManagementService;
import com.openmemind.ai.memory.server.service.itemgraph.ItemGraphQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AdminItemGraphControllerTest {

    private final StubItemGraphQueryService queryService = new StubItemGraphQueryService();
    private final StubItemGraphManagementService managementService =
            new StubItemGraphManagementService();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new AdminItemGraphController(queryService, managementService))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void summaryReturnsGraphCounts() throws Exception {
        mockMvc.perform(get("/admin/v1/item-graph/summary").param("memoryId", "u1:a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entityCount").value(2))
                .andExpect(jsonPath("$.data.itemLinkCountByType[0].name").value("SEMANTIC"));
    }

    @Test
    void entityListReturnsPage() throws Exception {
        mockMvc.perform(
                        get("/admin/v1/item-graph/entities")
                                .param("entityType", "PERSON")
                                .param("q", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].entityKey").value("person:alice"));
    }

    @Test
    void entityDetailReturnsRelatedCounts() throws Exception {
        mockMvc.perform(get("/admin/v1/item-graph/entities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entity.entityKey").value("person:alice"))
                .andExpect(jsonPath("$.data.mentionCount").value(3))
                .andExpect(jsonPath("$.data.entityOverlapItemLinkCount").value(1));
    }

    @Test
    void itemLinksCanFilterByEvidenceSource() throws Exception {
        mockMvc.perform(
                        get("/admin/v1/item-graph/item-links")
                                .param("evidenceSource", "entity_overlap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].evidenceSource").value("entity_overlap"));
    }

    @Test
    void aliasListUsesSpecFilters() throws Exception {
        mockMvc.perform(
                        get("/admin/v1/item-graph/aliases")
                                .param("memoryId", "u1:a1")
                                .param("entityKey", "person:alice")
                                .param("q", "ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].normalizedAlias").value("alice"));

        assertThat(queryService.recordedAliasQuery.memoryId()).isEqualTo("u1:a1");
        assertThat(queryService.recordedAliasQuery.entityKey()).isEqualTo("person:alice");
        assertThat(queryService.recordedAliasQuery.q()).isEqualTo("ali");
    }

    @Test
    void entityDeleteRequiresMemoryId() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/item-graph/entities")
                                .contentType(APPLICATION_JSON)
                                .content("{\"entityKeys\":[\"person:alice\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void entityDeleteReturnsCascadeCounts() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/item-graph/entities")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"memoryId\":\"u1:a1\","
                                                + "\"entityKeys\":[\"person:alice\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(1))
                .andExpect(jsonPath("$.data.deletedAliases").value(2))
                .andExpect(jsonPath("$.data.possiblyStaleEntityOverlapLinks").value(1));
    }

    @Test
    void deleteItemLinksRequiresIds() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/item-graph/item-links")
                                .contentType(APPLICATION_JSON)
                                .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteAliasesReturnsBatchDeleteResult() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/item-graph/aliases")
                                .contentType(APPLICATION_JSON)
                                .content("{\"ids\":[10,11]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(2))
                .andExpect(jsonPath("$.data.affectedMemoryIds[0]").value("u1:a1"));
    }

    @Test
    void deleteMentionsReturnsBatchDeleteResult() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/item-graph/mentions")
                                .contentType(APPLICATION_JSON)
                                .content("{\"ids\":[20]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(1));
    }

    @Test
    void deleteItemLinksReturnsBatchDeleteResult() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/item-graph/item-links")
                                .contentType(APPLICATION_JSON)
                                .content("{\"ids\":[30]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(1));
    }

    @Test
    void deleteCooccurrencesReturnsBatchDeleteResult() throws Exception {
        mockMvc.perform(
                        delete("/admin/v1/item-graph/cooccurrences")
                                .contentType(APPLICATION_JSON)
                                .content("{\"ids\":[40]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(1));
    }

    private static final class StubItemGraphQueryService extends ItemGraphQueryService {

        private ItemGraphPageQueries.AliasPageQuery recordedAliasQuery;

        private StubItemGraphQueryService() {
            super(null);
        }

        @Override
        public ItemGraphViews.SummaryView summary(String memoryId) {
            return new ItemGraphViews.SummaryView(
                    2,
                    1,
                    3,
                    1,
                    1,
                    List.of(new ItemGraphViews.NamedCount("COMMITTED", 1)),
                    List.of(new ItemGraphViews.NamedCount("SEMANTIC", 1)),
                    List.of(new ItemGraphViews.NamedCount("PERSON", 2)));
        }

        @Override
        public PageResponse<ItemGraphViews.EntityView> listEntities(
                ItemGraphPageQueries.EntityPageQuery query) {
            return new PageResponse<>(query.pageNo(), query.pageSize(), 1, List.of(entity()));
        }

        @Override
        public GraphEntityDetailView getEntity(Integer id) {
            return new GraphEntityDetailView(
                    entity(), List.of(alias()), 3, List.of(101L, 102L), List.of(cooccurrence()), 1);
        }

        @Override
        public PageResponse<ItemGraphViews.AliasView> listAliases(
                ItemGraphPageQueries.AliasPageQuery query) {
            this.recordedAliasQuery = query;
            return new PageResponse<>(query.pageNo(), query.pageSize(), 1, List.of(alias()));
        }

        @Override
        public PageResponse<ItemGraphViews.ItemLinkView> listItemLinks(
                ItemGraphPageQueries.ItemLinkPageQuery query) {
            return new PageResponse<>(query.pageNo(), query.pageSize(), 1, List.of(itemLink()));
        }
    }

    private static final class StubItemGraphManagementService extends ItemGraphManagementService {

        private StubItemGraphManagementService() {
            super(null);
        }

        @Override
        public AdminGraphEntityDeleteResult deleteEntities(
                String memoryId, List<String> entityKeys) {
            return new AdminGraphEntityDeleteResult(1, List.of("u1:a1"), 2, 3, 4, 1);
        }

        @Override
        public BatchDeleteResult deleteAliases(List<Integer> ids) {
            return new BatchDeleteResult(ids.size(), List.of("u1:a1"));
        }

        @Override
        public BatchDeleteResult deleteMentions(List<Integer> ids) {
            return new BatchDeleteResult(ids.size(), List.of("u1:a1"));
        }

        @Override
        public BatchDeleteResult deleteItemLinks(List<Integer> ids) {
            return new BatchDeleteResult(ids.size(), List.of("u1:a1"));
        }

        @Override
        public BatchDeleteResult deleteCooccurrences(List<Integer> ids) {
            return new BatchDeleteResult(ids.size(), List.of("u1:a1"));
        }
    }

    private static ItemGraphViews.EntityView entity() {
        return new ItemGraphViews.EntityView(
                1,
                "u1:a1",
                "u1",
                "a1",
                "person:alice",
                "Alice",
                "PERSON",
                Map.of(),
                instant(1),
                instant(2));
    }

    private static ItemGraphViews.AliasView alias() {
        return new ItemGraphViews.AliasView(
                10,
                "u1:a1",
                "u1",
                "a1",
                "person:alice",
                "PERSON",
                "alice",
                2,
                Map.of(),
                instant(1),
                instant(2));
    }

    private static ItemGraphViews.ItemLinkView itemLink() {
        return new ItemGraphViews.ItemLinkView(
                30,
                "u1:a1",
                "u1",
                "a1",
                101L,
                102L,
                "SEMANTIC",
                "related",
                "entity_overlap",
                0.8,
                Map.of(),
                instant(1),
                instant(2));
    }

    private static ItemGraphViews.CooccurrenceView cooccurrence() {
        return new ItemGraphViews.CooccurrenceView(
                40,
                "u1:a1",
                "u1",
                "a1",
                "person:alice",
                "concept:project",
                3,
                Map.of(),
                instant(1),
                instant(2));
    }

    private static Instant instant(int second) {
        return Instant.parse("2026-04-30T00:00:0" + second + "Z");
    }
}
