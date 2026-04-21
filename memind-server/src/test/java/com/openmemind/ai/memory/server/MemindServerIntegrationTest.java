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
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryRawDataDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadMembershipDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadProjectionDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadRuntimeDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryRawDataMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadMembershipMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadProjectionMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadRuntimeMapper;
import com.openmemind.ai.memory.server.domain.config.model.ServerRuntimeConfigDO;
import com.openmemind.ai.memory.server.domain.config.response.MemoryOptionsSnapshot;
import com.openmemind.ai.memory.server.domain.config.view.MemoryOptionItemView;
import com.openmemind.ai.memory.server.mapper.config.ServerRuntimeConfigMapper;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeFactory;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import com.openmemind.ai.memory.server.service.config.MemoryOptionService;
import com.openmemind.ai.memory.server.support.NoopRuntimeTestConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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

    @Autowired private MemoryThreadProjectionMapper threadProjectionMapper;

    @Autowired private MemoryThreadMembershipMapper threadMembershipMapper;

    @Autowired private MemoryThreadRuntimeMapper threadRuntimeMapper;

    @Autowired private MemoryOptionService memoryOptionService;

    @Autowired private MemoryRuntimeFactory memoryRuntimeFactory;

    @Autowired private MemoryRuntimeManager runtimeManager;

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.update("DELETE FROM memory_insight_buffer");
        jdbcTemplate.update("DELETE FROM memory_conversation_buffer");
        jdbcTemplate.update("DELETE FROM thread_intake_outbox");
        jdbcTemplate.update("DELETE FROM memory_thread_event");
        jdbcTemplate.update("DELETE FROM memory_thread_membership");
        jdbcTemplate.update("DELETE FROM memory_thread_runtime");
        jdbcTemplate.update("DELETE FROM memory_thread");
        jdbcTemplate.update("DELETE FROM memory_insight");
        jdbcTemplate.update("DELETE FROM memory_item");
        jdbcTemplate.update("DELETE FROM memory_raw_data");
        jdbcTemplate.update("DELETE FROM insight_fts");
        jdbcTemplate.update("DELETE FROM item_fts");
        jdbcTemplate.update("DELETE FROM raw_data_fts");
        jdbcTemplate.update("DELETE FROM memind_server_runtime_config");

        MemoryBuildOptions defaults = MemoryBuildOptions.defaults();
        MemoryRuntimeFactory.CreationResult created = memoryRuntimeFactory.create(defaults);
        runtimeManager.swap(created.memory(), created.effectiveOptions(), 1L);
        memoryOptionService.getCurrent();
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
        enableThreadProjectionRuntime();
        try (var lease = runtimeManager.acquire()) {
            assertThat(lease.handle().options().extraction().item().graph().enabled()).isTrue();
            assertThat(lease.handle().options().memoryThread().enabled()).isTrue();
            assertThat(lease.handle().options().memoryThread().derivation().enabled()).isTrue();
        }
        insertItem(item(101L, "u1", "a1", "u1:a1", "rd-1", "USER", "profile", "FACT"));
        insertThread(
                thread(
                        "u1:a1",
                        "topic:concept:travel",
                        "TOPIC",
                        "concept",
                        "travel",
                        "Travel planning"));
        insertThread(
                thread(
                        "u1:a1",
                        "relationship:relationship:person:alice|person:bob",
                        "RELATIONSHIP",
                        "relationship",
                        "person:alice|person:bob",
                        "Alice and Bob"));
        insertThreadMembership(
                threadMembership("u1:a1", "topic:concept:travel", 101L, "CORE", true, 0.92d));
        insertThreadMembership(
                threadMembership(
                        "u1:a1",
                        "relationship:relationship:person:alice|person:bob",
                        101L,
                        "RELATED",
                        false,
                        0.74d));
        insertThreadRuntime(threadRuntime("u1:a1", "AVAILABLE", 101L, 0L, 0L));

        mockMvc.perform(get("/admin/v1/memory-threads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].threadId").doesNotExist());

        mockMvc.perform(
                        get("/admin/v1/memory-threads/{threadKey}", "topic:concept:travel")
                                .queryParam("userId", "u1")
                                .queryParam("agentId", "a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.threadKey").value("topic:concept:travel"))
                .andExpect(jsonPath("$.data.threadId").doesNotExist());

        mockMvc.perform(
                        get("/admin/v1/memory-threads/{threadKey}/items", "topic:concept:travel")
                                .queryParam("userId", "u1")
                                .queryParam("agentId", "a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data[0].itemId").value(101))
                .andExpect(jsonPath("$.data[0].threadKey").value("topic:concept:travel"))
                .andExpect(jsonPath("$.data[0].threadId").doesNotExist());

        mockMvc.perform(
                        get("/admin/v1/items/{itemId}/memory-threads", 101L)
                                .queryParam("userId", "u1")
                                .queryParam("agentId", "a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].threadId").doesNotExist());

        mockMvc.perform(
                        get("/admin/v1/memory-threads/status")
                                .queryParam("userId", "u1")
                                .queryParam("agentId", "a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success"))
                .andExpect(jsonPath("$.data.projectionState").value("available"))
                .andExpect(jsonPath("$.data.pendingCount").value(0));

        mockMvc.perform(
                        post("/admin/v1/memory-threads/rebuild")
                                .queryParam("userId", "u1")
                                .queryParam("agentId", "a1"))
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

    private void insertThread(MemoryThreadProjectionDO thread) {
        threadProjectionMapper.insert(thread);
    }

    private void insertThreadMembership(MemoryThreadMembershipDO threadMembership) {
        threadMembershipMapper.insert(threadMembership);
    }

    private void insertThreadRuntime(MemoryThreadRuntimeDO threadRuntime) {
        threadRuntimeMapper.insert(threadRuntime);
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

    private static MemoryThreadProjectionDO thread(
            String memoryId,
            String threadKey,
            String threadType,
            String anchorKind,
            String anchorKey,
            String displayLabel) {
        MemoryThreadProjectionDO dataObject = new MemoryThreadProjectionDO();
        dataObject.setMemoryId(memoryId);
        dataObject.setThreadKey(threadKey);
        dataObject.setThreadType(threadType);
        dataObject.setAnchorKind(anchorKind);
        dataObject.setAnchorKey(anchorKey);
        dataObject.setDisplayLabel(displayLabel);
        dataObject.setLifecycleStatus("ACTIVE");
        dataObject.setObjectState("ONGOING");
        dataObject.setHeadline("Discussing a summer trip.");
        dataObject.setSnapshotJson(Map.of("latestUpdate", "Booked flights"));
        dataObject.setSnapshotVersion(1);
        dataObject.setOpenedAt(Instant.parse("2026-03-31T09:00:00Z"));
        dataObject.setLastEventAt(Instant.parse("2026-03-31T10:00:00Z"));
        dataObject.setLastMeaningfulUpdateAt(Instant.parse("2026-03-31T10:00:00Z"));
        dataObject.setEventCount(3L);
        dataObject.setMemberCount(1L);
        dataObject.setCreatedAt(Instant.parse("2026-03-31T10:00:01Z"));
        dataObject.setUpdatedAt(Instant.parse("2026-03-31T10:00:02Z"));
        return dataObject;
    }

    private static MemoryThreadMembershipDO threadMembership(
            String memoryId,
            String threadKey,
            long itemId,
            String role,
            boolean primary,
            double relevanceWeight) {
        MemoryThreadMembershipDO dataObject = new MemoryThreadMembershipDO();
        dataObject.setMemoryId(memoryId);
        dataObject.setThreadKey(threadKey);
        dataObject.setItemId(itemId);
        dataObject.setRole(role);
        dataObject.setPrimary(primary);
        dataObject.setRelevanceWeight(relevanceWeight);
        dataObject.setCreatedAt(Instant.parse("2026-03-31T10:00:04Z"));
        dataObject.setUpdatedAt(Instant.parse("2026-03-31T10:00:05Z"));
        return dataObject;
    }

    private static MemoryThreadRuntimeDO threadRuntime(
            String memoryId,
            String projectionState,
            Long lastProcessedItemId,
            Long pendingCount,
            Long failedCount) {
        MemoryThreadRuntimeDO dataObject = new MemoryThreadRuntimeDO();
        dataObject.setMemoryId(memoryId);
        dataObject.setProjectionState(projectionState);
        dataObject.setPendingCount(pendingCount);
        dataObject.setFailedCount(failedCount);
        dataObject.setLastProcessedItemId(lastProcessedItemId);
        dataObject.setLastEnqueuedItemId(lastProcessedItemId);
        dataObject.setRebuildInProgress(Boolean.FALSE);
        dataObject.setMaterializationPolicyVersion("thread-core-v1");
        dataObject.setInvalidationReason(null);
        dataObject.setUpdatedAt(Instant.parse("2026-03-31T10:00:06Z"));
        return dataObject;
    }

    private void enableThreadProjectionRuntime() {
        MemoryOptionsSnapshot snapshot = memoryOptionService.getCurrent();
        Map<String, List<MemoryOptionItemView>> projection = deepCopy(snapshot.config());
        updateValue(projection, "memoryThread.enabled", true);
        updateValue(projection, "memoryThread.derivation.enabled", true);
        updateValue(projection, "extraction.item.graph.enabled", true);
        memoryOptionService.update(snapshot.version(), projection);
    }

    private static Map<String, List<MemoryOptionItemView>> deepCopy(
            Map<String, List<MemoryOptionItemView>> config) {
        Map<String, List<MemoryOptionItemView>> copy = new HashMap<>();
        config.forEach((key, value) -> copy.put(key, new ArrayList<>(value)));
        return copy;
    }

    private static void updateValue(
            Map<String, List<MemoryOptionItemView>> projection, String key, Object value) {
        projection
                .values()
                .forEach(
                        items -> {
                            for (int i = 0; i < items.size(); i++) {
                                MemoryOptionItemView item = items.get(i);
                                if (item.key().equals(key)) {
                                    items.set(
                                            i,
                                            new MemoryOptionItemView(
                                                    item.key(),
                                                    value,
                                                    item.description(),
                                                    item.type(),
                                                    item.defaultValue(),
                                                    item.constraints()));
                                }
                            }
                        });
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
