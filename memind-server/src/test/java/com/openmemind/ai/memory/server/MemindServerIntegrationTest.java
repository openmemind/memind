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
package com.openmemind.ai.memory.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryRawDataDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadItemDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryRawDataMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadItemMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadMapper;
import com.openmemind.ai.memory.server.domain.config.model.ServerRuntimeConfigDO;
import com.openmemind.ai.memory.server.mapper.config.ServerRuntimeConfigMapper;
import com.openmemind.ai.memory.server.service.config.MemoryOptionService;
import com.openmemind.ai.memory.server.support.NoopRuntimeTestConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {MemindServerApplication.class, NoopRuntimeTestConfiguration.class})
class MemindServerIntegrationTest {

    private static final Path DB_PATH =
            Path.of("target", "memind-server-integration-" + UUID.randomUUID() + ".db")
                    .toAbsolutePath();

    static {
        try {
            Files.createDirectories(DB_PATH.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to prepare integration-test database path", exception);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.main.web-application-type", () -> "servlet");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH);
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("memind.store.init-schema", () -> "true");
        registry.add(
                "spring.autoconfigure.exclude",
                () -> NoopRuntimeTestConfiguration.SPRING_AI_AUTOCONFIG_EXCLUDES);
    }

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext webApplicationContext;

    @Autowired private ServerRuntimeConfigMapper serverRuntimeConfigMapper;

    @Autowired private MemoryRawDataMapper rawDataMapper;

    @Autowired private MemoryItemMapper itemMapper;

    @Autowired private MemoryInsightMapper insightMapper;

    @Autowired private MemoryThreadMapper threadMapper;

    @Autowired private MemoryThreadItemMapper threadItemMapper;

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.update("DELETE FROM memory_insight_buffer");
        jdbcTemplate.update("DELETE FROM memory_conversation_buffer");
        jdbcTemplate.update("DELETE FROM memory_thread_item");
        jdbcTemplate.update("DELETE FROM memory_thread");
        jdbcTemplate.update("DELETE FROM memory_insight");
        jdbcTemplate.update("DELETE FROM memory_item");
        jdbcTemplate.update("DELETE FROM memory_raw_data");
        jdbcTemplate.update("DELETE FROM insight_fts");
        jdbcTemplate.update("DELETE FROM item_fts");
        jdbcTemplate.update("DELETE FROM raw_data_fts");
    }

    @AfterAll
    static void cleanUpDatabase() throws IOException {
        Files.deleteIfExists(DB_PATH);
    }

    @Test
    void configIsInitializedOnEmptyDatabase() throws Exception {
        List<ServerRuntimeConfigDO> configs =
                serverRuntimeConfigMapper.selectList(new LambdaQueryWrapper<>());

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().getConfigKey()).isEqualTo(MemoryOptionService.CONFIG_KEY);
        assertThat(configs.getFirst().getConfigVersion()).isEqualTo(1L);
        assertThat(configs.getFirst().getConfigJson()).isNotBlank();

        mockMvc.perform(get("/admin/v1/config/memory-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.config.extraction").isArray())
                .andExpect(jsonPath("$.data.config.retrieval").isArray());
    }

    @Test
    void pageValidationIsAppliedInRealMvcContext() throws Exception {
        mockMvc.perform(get("/admin/v1/items").queryParam("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void adminQueriesApplyApprovedFilters() throws Exception {
        insertRawData(
                rawData(
                        "rd-match",
                        "u1",
                        "a1",
                        "u1:a1",
                        "cap-1",
                        Instant.parse("2026-03-31T11:00:00Z")));
        insertRawData(
                rawData(
                        "rd-other-time",
                        "u1",
                        "a1",
                        "u1:a1",
                        "cap-2",
                        Instant.parse("2026-03-31T08:00:00Z")));
        insertRawData(
                rawData(
                        "rd-other-user",
                        "u2",
                        "a1",
                        "u2:a1",
                        "cap-3",
                        Instant.parse("2026-03-31T11:00:00Z")));

        insertItem(item(101L, "u1", "a1", "u1:a1", "rd-match", "USER", "profile", "FACT"));
        insertItem(item(102L, "u1", "a1", "u1:a1", "rd-other-time", "AGENT", "profile", "FACT"));
        insertItem(item(103L, "u2", "a1", "u2:a1", "rd-other-user", "USER", "profile", "FACT"));

        insertInsight(insight(201L, "u1", "a1", "u1:a1", "USER", "profile", "LEAF"));
        insertInsight(insight(202L, "u1", "a1", "u1:a1", "USER", "profile", "BRANCH"));
        insertInsight(insight(203L, "u2", "a1", "u2:a1", "USER", "profile", "LEAF"));

        mockMvc.perform(
                        get("/admin/v1/raw-data")
                                .queryParam("userId", "u1")
                                .queryParam("agentId", "a1")
                                .queryParam("startTimeFrom", "2026-03-31T10:30:00Z")
                                .queryParam("startTimeTo", "2026-03-31T11:30:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].rawDataId").value("rd-match"));

        mockMvc.perform(
                        get("/admin/v1/items")
                                .queryParam("userId", "u1")
                                .queryParam("agentId", "a1")
                                .queryParam("scope", "USER")
                                .queryParam("category", "profile")
                                .queryParam("type", "FACT")
                                .queryParam("rawDataId", "rd-match"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].itemId").value(101));

        mockMvc.perform(
                        get("/admin/v1/insights")
                                .queryParam("userId", "u1")
                                .queryParam("agentId", "a1")
                                .queryParam("scope", "USER")
                                .queryParam("type", "profile")
                                .queryParam("tier", "LEAF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].insightId").value(201));
    }

    @Test
    void rawDataDeletionRemovesLinkedItemsButKeepsInsights() throws Exception {
        insertRawData(
                rawData(
                        "rd-1",
                        "u1",
                        "a1",
                        "u1:a1",
                        "cap-1",
                        Instant.parse("2026-03-31T11:00:00Z")));
        insertItem(item(101L, "u1", "a1", "u1:a1", "rd-1", "USER", "profile", "FACT"));
        insertInsight(insight(201L, "u1", "a1", "u1:a1", "USER", "profile", "LEAF"));

        mockMvc.perform(
                        delete("/admin/v1/raw-data")
                                .contentType(APPLICATION_JSON)
                                .content("{\"rawDataIds\":[\"rd-1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.deletedRawDataCount").value(1))
                .andExpect(jsonPath("$.data.deletedItemCount").value(1))
                .andExpect(jsonPath("$.data.affectedMemoryIds[0]").value("u1:a1"))
                .andExpect(jsonPath("$.data.insightCleanupRequired").value(true));

        assertThat(
                        rawDataMapper.selectList(
                                new LambdaQueryWrapper<MemoryRawDataDO>()
                                        .eq(MemoryRawDataDO::getBizId, "rd-1")))
                .isEmpty();
        assertThat(
                        itemMapper.selectList(
                                new LambdaQueryWrapper<MemoryItemDO>()
                                        .eq(MemoryItemDO::getBizId, 101L)))
                .isEmpty();
        assertThat(
                        insightMapper.selectList(
                                new LambdaQueryWrapper<MemoryInsightDO>()
                                        .eq(MemoryInsightDO::getBizId, 201L)))
                .hasSize(1);
    }

    @Test
    void memoryThreadAdminEndpointsAreVisibleInRunningServer() throws Exception {
        insertThread(thread(301L, "u1", "a1", "u1:a1", "mt:301"));
        insertThreadItem(threadItem(401L, "u1", "a1", "u1:a1", 301L, 101L));

        mockMvc.perform(get("/admin/v1/memory-threads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].threadKey").value("mt:301"));

        mockMvc.perform(get("/admin/v1/memory-threads/{threadId}", 301L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.threadKey").value("mt:301"));

        mockMvc.perform(get("/admin/v1/memory-threads/{threadId}/items", 301L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data[0].itemId").value(101));

        mockMvc.perform(get("/admin/v1/items/{itemId}/memory-thread", 101L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.threadId").value(301))
                .andExpect(jsonPath("$.data.threadKey").value("mt:301"));

        mockMvc.perform(get("/admin/v1/memory-threads/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.memoryThreadEnabled").value(false));

        mockMvc.perform(post("/admin/v1/memory-threads/rebuild/{memoryId}", "u1:a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(post("/admin/v1/memory-threads/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data").value(1));
    }

    private void insertRawData(MemoryRawDataDO rawData) {
        rawDataMapper.insert(rawData);
    }

    private void insertItem(MemoryItemDO item) {
        itemMapper.insert(item);
    }

    private void insertThread(MemoryThreadDO thread) {
        threadMapper.insert(thread);
    }

    private void insertThreadItem(MemoryThreadItemDO threadItem) {
        threadItemMapper.insert(threadItem);
    }

    private void insertInsight(MemoryInsightDO insight) {
        insightMapper.insert(insight);
    }

    private static MemoryRawDataDO rawData(
            String bizId,
            String userId,
            String agentId,
            String memoryId,
            String captionVectorId,
            Instant startTime) {
        MemoryRawDataDO dataObject = new MemoryRawDataDO();
        dataObject.setBizId(bizId);
        dataObject.setUserId(userId);
        dataObject.setAgentId(agentId);
        dataObject.setMemoryId(memoryId);
        dataObject.setType("conversation");
        dataObject.setContentId("content-" + bizId);
        dataObject.setSegment(Map.of("type", "conversation"));
        dataObject.setCaption("caption-" + bizId);
        dataObject.setCaptionVectorId(captionVectorId);
        dataObject.setMetadata(Map.of("source", "integration-test"));
        dataObject.setStartTime(startTime);
        dataObject.setEndTime(startTime.plusSeconds(30));
        dataObject.setCreatedAt(startTime.plusSeconds(60));
        dataObject.setUpdatedAt(startTime.plusSeconds(90));
        dataObject.setDeleted(Boolean.FALSE);
        return dataObject;
    }

    private static MemoryThreadDO thread(
            long bizId, String userId, String agentId, String memoryId, String threadKey) {
        MemoryThreadDO dataObject = new MemoryThreadDO();
        dataObject.setBizId(bizId);
        dataObject.setUserId(userId);
        dataObject.setAgentId(agentId);
        dataObject.setMemoryId(memoryId);
        dataObject.setThreadKey(threadKey);
        dataObject.setEpisodeType("conversation");
        dataObject.setTitle("Travel planning");
        dataObject.setSummarySnapshot("Discussed a summer trip.");
        dataObject.setStatus("OPEN");
        dataObject.setConfidence(0.88d);
        dataObject.setStartAt(Instant.parse("2026-03-31T09:00:00Z"));
        dataObject.setEndAt(Instant.parse("2026-03-31T10:00:00Z"));
        dataObject.setLastActivityAt(Instant.parse("2026-03-31T10:00:00Z"));
        dataObject.setOriginItemId(101L);
        dataObject.setAnchorItemId(101L);
        dataObject.setDisplayOrderHint(1);
        dataObject.setMetadata(Map.of("source", "integration-test"));
        dataObject.setCreatedAt(Instant.parse("2026-03-31T10:00:01Z"));
        dataObject.setUpdatedAt(Instant.parse("2026-03-31T10:00:02Z"));
        dataObject.setDeleted(Boolean.FALSE);
        return dataObject;
    }

    private static MemoryThreadItemDO threadItem(
            long bizId,
            String userId,
            String agentId,
            String memoryId,
            long threadId,
            long itemId) {
        MemoryThreadItemDO dataObject = new MemoryThreadItemDO();
        dataObject.setBizId(bizId);
        dataObject.setUserId(userId);
        dataObject.setAgentId(agentId);
        dataObject.setMemoryId(memoryId);
        dataObject.setThreadId(threadId);
        dataObject.setItemId(itemId);
        dataObject.setMembershipWeight(0.92d);
        dataObject.setRole("CORE");
        dataObject.setSequenceHint(1);
        dataObject.setJoinedAt(Instant.parse("2026-03-31T10:00:03Z"));
        dataObject.setMetadata(Map.of("source", "integration-test"));
        dataObject.setCreatedAt(Instant.parse("2026-03-31T10:00:04Z"));
        dataObject.setUpdatedAt(Instant.parse("2026-03-31T10:00:05Z"));
        dataObject.setDeleted(Boolean.FALSE);
        return dataObject;
    }

    private static MemoryItemDO item(
            Long bizId,
            String userId,
            String agentId,
            String memoryId,
            String rawDataId,
            String scope,
            String category,
            String type) {
        Instant baseTime = Instant.parse("2026-03-31T11:05:00Z");
        MemoryItemDO dataObject = new MemoryItemDO();
        dataObject.setBizId(bizId);
        dataObject.setUserId(userId);
        dataObject.setAgentId(agentId);
        dataObject.setMemoryId(memoryId);
        dataObject.setContent("content-" + bizId);
        dataObject.setScope(scope);
        dataObject.setCategory(category);
        dataObject.setVectorId("vec-" + bizId);
        dataObject.setRawDataId(rawDataId);
        dataObject.setContentHash("hash-" + bizId);
        dataObject.setOccurredAt(baseTime);
        dataObject.setObservedAt(baseTime.plusSeconds(10));
        dataObject.setMetadata(Map.of("source", "integration-test"));
        dataObject.setType(type);
        dataObject.setRawDataType("conversation");
        dataObject.setCreatedAt(baseTime.plusSeconds(20));
        dataObject.setUpdatedAt(baseTime.plusSeconds(30));
        dataObject.setDeleted(Boolean.FALSE);
        return dataObject;
    }

    private static MemoryInsightDO insight(
            Long bizId,
            String userId,
            String agentId,
            String memoryId,
            String scope,
            String type,
            String tier) {
        Instant baseTime = Instant.parse("2026-03-31T11:10:00Z");
        MemoryInsightDO dataObject = new MemoryInsightDO();
        dataObject.setBizId(bizId);
        dataObject.setUserId(userId);
        dataObject.setAgentId(agentId);
        dataObject.setMemoryId(memoryId);
        dataObject.setType(type);
        dataObject.setScope(scope);
        dataObject.setName("name-" + bizId);
        dataObject.setCategories(List.of("profile"));
        dataObject.setContent("content-" + bizId);
        dataObject.setPoints(
                List.of(
                        new InsightPoint(
                                InsightPoint.PointType.SUMMARY, "point-" + bizId, List.of())));
        dataObject.setGroupName("group-" + bizId);
        dataObject.setLastReasonedAt(baseTime);
        dataObject.setSummaryEmbedding(List.of());
        dataObject.setTier(tier);
        dataObject.setChildInsightIds(List.of());
        dataObject.setVersion(1);
        dataObject.setCreatedAt(baseTime.plusSeconds(20));
        dataObject.setUpdatedAt(baseTime.plusSeconds(30));
        dataObject.setDeleted(Boolean.FALSE);
        return dataObject;
    }
}
