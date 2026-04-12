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
package com.openmemind.ai.memory.plugin.jdbc.sqlite;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.MemoryResource;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentBoundary;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.core.store.resource.ResourceOperations;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaInitResult;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcExecutor;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JsonCodec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;

public class SqliteMemoryStore
        implements MemoryStore,
                RawDataOperations,
                ItemOperations,
                InsightOperations,
                ResourceOperations {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Float>> FLOAT_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<InsightPoint>> INSIGHT_POINT_LIST_TYPE =
            new TypeReference<>() {};
    private static final DateTimeFormatter SQLITE_TIME_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd HH:mm:ss")
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                    .optionalEnd()
                    .toFormatter();

    private final DataSource dataSource;
    private final JsonCodec jsonHelper;
    private final ResourceStore resourceStore;

    public SqliteMemoryStore(DataSource dataSource) {
        this(dataSource, null, true);
    }

    public SqliteMemoryStore(DataSource dataSource, boolean createIfNotExist) {
        this(dataSource, null, createIfNotExist);
    }

    public SqliteMemoryStore(
            DataSource dataSource, ResourceStore resourceStore, boolean createIfNotExist) {
        this(dataSource, resourceStore, JsonCodec.createDefaultObjectMapper(), createIfNotExist);
    }

    public SqliteMemoryStore(
            DataSource dataSource,
            ResourceStore resourceStore,
            ObjectMapper objectMapper,
            boolean createIfNotExist) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jsonHelper = new JsonCodec(Objects.requireNonNull(objectMapper, "objectMapper"));
        this.resourceStore = resourceStore;
        StoreSchemaInitResult initResult =
                StoreSchemaBootstrap.ensureSqlite(this.dataSource, createIfNotExist);
        if (initResult.createdInsightTypeTable()) {
            upsertInsightTypes(DefaultInsightTypes.all());
        }
    }

    @Override
    public RawDataOperations rawDataOperations() {
        return this;
    }

    @Override
    public ItemOperations itemOperations() {
        return this;
    }

    @Override
    public InsightOperations insightOperations() {
        return this;
    }

    @Override
    public ResourceOperations resourceOperations() {
        return this;
    }

    @Override
    public ResourceStore resourceStore() {
        return resourceStore;
    }

    @Override
    public void upsertRawData(MemoryId memoryId, List<MemoryRawData> rawDataList) {
        upsertRawDataWithResources(memoryId, List.of(), rawDataList);
    }

    @Override
    public void upsertRawDataWithResources(
            MemoryId memoryId, List<MemoryResource> resources, List<MemoryRawData> rawDataList) {
        boolean noResources = resources == null || resources.isEmpty();
        boolean noRawData = rawDataList == null || rawDataList.isEmpty();
        if (noResources && noRawData) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        JdbcExecutor.inTransaction(
                dataSource,
                connection -> {
                    upsertResources(connection, scope, resources);
                    upsertRawData(connection, scope, rawDataList);
                    return null;
                });
    }

    @Override
    public void upsertResources(MemoryId memoryId, List<MemoryResource> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        JdbcExecutor.inTransaction(
                dataSource,
                connection -> {
                    upsertResources(connection, scope, resources);
                    return null;
                });
    }

    @Override
    public Optional<MemoryResource> getResource(MemoryId memoryId, String resourceId) {
        ScopeContext scope = scopeOf(memoryId);
        return Optional.ofNullable(
                JdbcExecutor.queryOne(
                        dataSource,
                        """
                        SELECT * FROM memory_resource
                        WHERE user_id = ? AND agent_id = ? AND biz_id = ? AND deleted = 0
                        LIMIT 1
                        """,
                        this::mapResource,
                        scope.userId(),
                        scope.agentId(),
                        resourceId));
    }

    @Override
    public List<MemoryResource> listResources(MemoryId memoryId) {
        ScopeContext scope = scopeOf(memoryId);
        return JdbcExecutor.queryList(
                dataSource,
                """
                SELECT * FROM memory_resource
                WHERE user_id = ? AND agent_id = ? AND deleted = 0
                ORDER BY id ASC
                """,
                this::mapResource,
                scope.userId(),
                scope.agentId());
    }

    @Override
    public Optional<MemoryRawData> getRawData(MemoryId memoryId, String rawDataId) {
        ScopeContext scope = scopeOf(memoryId);
        return Optional.ofNullable(
                JdbcExecutor.queryOne(
                        dataSource,
                        """
                        SELECT * FROM memory_raw_data
                        WHERE user_id = ? AND agent_id = ? AND biz_id = ? AND deleted = 0
                        LIMIT 1
                        """,
                        this::mapRawData,
                        scope.userId(),
                        scope.agentId(),
                        rawDataId));
    }

    @Override
    public Optional<MemoryRawData> getRawDataByContentId(MemoryId memoryId, String contentId) {
        ScopeContext scope = scopeOf(memoryId);
        return Optional.ofNullable(
                JdbcExecutor.queryOne(
                        dataSource,
                        """
                        SELECT * FROM memory_raw_data
                        WHERE user_id = ? AND agent_id = ? AND content_id = ? AND deleted = 0
                        ORDER BY id ASC
                        LIMIT 1
                        """,
                        this::mapRawData,
                        scope.userId(),
                        scope.agentId(),
                        contentId));
    }

    @Override
    public List<MemoryRawData> listRawData(MemoryId memoryId) {
        ScopeContext scope = scopeOf(memoryId);
        return JdbcExecutor.queryList(
                dataSource,
                """
                SELECT * FROM memory_raw_data
                WHERE user_id = ? AND agent_id = ? AND deleted = 0
                ORDER BY id ASC
                """,
                this::mapRawData,
                scope.userId(),
                scope.agentId());
    }

    @Override
    public List<MemoryRawData> pollRawDataWithoutVector(
            MemoryId memoryId, int limit, Duration minAge) {
        if (limit <= 0) {
            return List.of();
        }

        ScopeContext scope = scopeOf(memoryId);
        String cutoff = writeInstant(Instant.now().minus(minAge));
        return JdbcExecutor.queryList(
                dataSource,
                """
                SELECT * FROM memory_raw_data
                WHERE user_id = ? AND agent_id = ? AND deleted = 0
                  AND caption_vector_id IS NULL
                  AND unixepoch(created_at) < unixepoch(?)
                ORDER BY unixepoch(created_at) ASC, id ASC
                LIMIT ?
                """,
                this::mapRawData,
                scope.userId(),
                scope.agentId(),
                cutoff,
                limit);
    }

    @Override
    public void updateRawDataVectorIds(
            MemoryId memoryId, Map<String, String> vectorIds, Map<String, Object> metadataPatch) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        Map<String, MemoryRawData> existingById =
                getRawDataByIds(scope, vectorIds.keySet()).stream()
                        .collect(Collectors.toMap(MemoryRawData::id, rawData -> rawData));
        if (existingById.isEmpty()) {
            return;
        }

        JdbcExecutor.inTransaction(
                dataSource,
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    """
                                    UPDATE memory_raw_data
                                    SET caption_vector_id = ?, metadata = ?, updated_at = ?
                                    WHERE user_id = ? AND agent_id = ? AND biz_id = ? AND deleted = 0
                                    """)) {
                        String updatedAt = writeInstant(Instant.now());
                        for (Map.Entry<String, String> entry : vectorIds.entrySet()) {
                            MemoryRawData existing = existingById.get(entry.getKey());
                            if (existing == null) {
                                continue;
                            }
                            MemoryRawData updated =
                                    existing.withVectorId(entry.getValue(), metadataPatch);
                            statement.setString(1, entry.getValue());
                            statement.setString(2, jsonHelper.toJson(updated.metadata()));
                            statement.setString(3, updatedAt);
                            statement.setString(4, scope.userId());
                            statement.setString(5, scope.agentId());
                            statement.setString(6, entry.getKey());
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    return null;
                });
    }

    @Override
    public void insertItems(MemoryId memoryId, List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        JdbcExecutor.inTransaction(
                dataSource,
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO memory_item
                                        (biz_id, user_id, agent_id, memory_id, content, scope, category,
                                         vector_id, raw_data_id, content_hash, occurred_at,
                                         observed_at, type, raw_data_type, metadata, created_at,
                                         updated_at, deleted)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                                    """)) {
                        Instant now = Instant.now();
                        for (MemoryItem item : items) {
                            bindItemInsert(statement, scope, item, now);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    return null;
                });
    }

    @Override
    public List<MemoryItem> getItemsByIds(MemoryId memoryId, Collection<Long> itemIds) {
        return queryItemsByField(memoryId, "biz_id", itemIds);
    }

    @Override
    public List<MemoryItem> getItemsByVectorIds(MemoryId memoryId, Collection<String> vectorIds) {
        return queryItemsByField(memoryId, "vector_id", vectorIds);
    }

    @Override
    public List<MemoryItem> getItemsByContentHashes(
            MemoryId memoryId, Collection<String> contentHashes) {
        return queryItemsByField(memoryId, "content_hash", contentHashes);
    }

    @Override
    public List<MemoryItem> listItems(MemoryId memoryId) {
        ScopeContext scope = scopeOf(memoryId);
        return JdbcExecutor.queryList(
                dataSource,
                """
                SELECT * FROM memory_item
                WHERE user_id = ? AND agent_id = ? AND deleted = 0
                ORDER BY id ASC
                """,
                this::mapItem,
                scope.userId(),
                scope.agentId());
    }

    @Override
    public boolean hasItems(MemoryId memoryId) {
        ScopeContext scope = scopeOf(memoryId);
        return JdbcExecutor.queryCount(
                        dataSource,
                        """
                        SELECT COUNT(*) FROM memory_item
                        WHERE user_id = ? AND agent_id = ? AND deleted = 0
                        """,
                        scope.userId(),
                        scope.agentId())
                > 0;
    }

    @Override
    public void deleteItems(MemoryId memoryId, Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        List<Object> params = new ArrayList<>();
        params.add(writeInstant(Instant.now()));
        params.add(scope.userId());
        params.add(scope.agentId());
        params.addAll(itemIds);
        JdbcExecutor.update(
                dataSource,
                """
                UPDATE memory_item
                SET deleted = 1, updated_at = ?
                WHERE user_id = ? AND agent_id = ? AND deleted = 0
                  AND biz_id IN (%s)
                """
                        .formatted(placeholders(itemIds.size())),
                params.toArray());
    }

    @Override
    public void upsertInsightTypes(List<MemoryInsightType> insightTypes) {
        if (insightTypes == null || insightTypes.isEmpty()) {
            return;
        }

        JdbcExecutor.inTransaction(
                dataSource,
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO memory_insight_type
                                        (biz_id, name, description, description_vector_id, categories,
                                         target_tokens, analysis_mode, scope, tree_config,
                                         last_updated_at, created_at, updated_at, deleted)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                                    ON CONFLICT(name) DO UPDATE SET
                                        biz_id = excluded.biz_id,
                                        description = excluded.description,
                                        description_vector_id = excluded.description_vector_id,
                                        categories = excluded.categories,
                                        target_tokens = excluded.target_tokens,
                                        analysis_mode = excluded.analysis_mode,
                                        scope = excluded.scope,
                                        tree_config = excluded.tree_config,
                                        last_updated_at = excluded.last_updated_at,
                                        created_at = excluded.created_at,
                                        updated_at = excluded.updated_at,
                                        deleted = 0
                                    """)) {
                        Instant now = Instant.now();
                        for (MemoryInsightType insightType : insightTypes) {
                            bindInsightTypeUpsert(statement, insightType, now);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    return null;
                });
    }

    @Override
    public Optional<MemoryInsightType> getInsightType(String insightType) {
        return Optional.ofNullable(
                JdbcExecutor.queryOne(
                        dataSource,
                        """
                        SELECT * FROM memory_insight_type
                        WHERE name = ? AND deleted = 0
                        LIMIT 1
                        """,
                        this::mapInsightType,
                        insightType));
    }

    @Override
    public List<MemoryInsightType> listInsightTypes() {
        return JdbcExecutor.queryList(
                dataSource,
                """
                SELECT * FROM memory_insight_type
                WHERE deleted = 0
                ORDER BY id ASC
                """,
                this::mapInsightType);
    }

    @Override
    public void upsertInsights(MemoryId memoryId, List<MemoryInsight> insights) {
        if (insights == null || insights.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        JdbcExecutor.inTransaction(
                dataSource,
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO memory_insight
                                        (biz_id, user_id, agent_id, memory_id, type, scope, name,
                                         categories, content, points, group_name, confidence,
                                         last_reasoned_at, summary_embedding, tier, parent_insight_id,
                                         child_insight_ids, version, created_at, updated_at, deleted)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                                    ON CONFLICT(user_id, agent_id, biz_id) DO UPDATE SET
                                        memory_id = excluded.memory_id,
                                        type = excluded.type,
                                        scope = excluded.scope,
                                        name = excluded.name,
                                        categories = excluded.categories,
                                        content = excluded.content,
                                        points = excluded.points,
                                        group_name = excluded.group_name,
                                        confidence = excluded.confidence,
                                        last_reasoned_at = excluded.last_reasoned_at,
                                        summary_embedding = excluded.summary_embedding,
                                        tier = excluded.tier,
                                        parent_insight_id = excluded.parent_insight_id,
                                        child_insight_ids = excluded.child_insight_ids,
                                        version = excluded.version,
                                        created_at = excluded.created_at,
                                        updated_at = excluded.updated_at,
                                        deleted = 0
                                    """)) {
                        Instant now = Instant.now();
                        for (MemoryInsight insight : insights) {
                            bindInsightUpsert(statement, scope, insight, now);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    return null;
                });
    }

    @Override
    public Optional<MemoryInsight> getInsight(MemoryId memoryId, Long insightId) {
        ScopeContext scope = scopeOf(memoryId);
        return Optional.ofNullable(
                JdbcExecutor.queryOne(
                        dataSource,
                        """
                        SELECT * FROM memory_insight
                        WHERE user_id = ? AND agent_id = ? AND biz_id = ? AND deleted = 0
                        LIMIT 1
                        """,
                        this::mapInsight,
                        scope.userId(),
                        scope.agentId(),
                        insightId));
    }

    @Override
    public List<MemoryInsight> listInsights(MemoryId memoryId) {
        ScopeContext scope = scopeOf(memoryId);
        return JdbcExecutor.queryList(
                dataSource,
                """
                SELECT * FROM memory_insight
                WHERE user_id = ? AND agent_id = ? AND deleted = 0
                ORDER BY id ASC
                """,
                this::mapInsight,
                scope.userId(),
                scope.agentId());
    }

    @Override
    public List<MemoryInsight> getInsightsByType(MemoryId memoryId, String insightType) {
        ScopeContext scope = scopeOf(memoryId);
        return JdbcExecutor.queryList(
                dataSource,
                """
                SELECT * FROM memory_insight
                WHERE user_id = ? AND agent_id = ? AND type = ? AND deleted = 0
                ORDER BY id ASC
                """,
                this::mapInsight,
                scope.userId(),
                scope.agentId(),
                insightType);
    }

    @Override
    public List<MemoryInsight> getInsightsByTier(MemoryId memoryId, InsightTier tier) {
        if (tier == null) {
            return List.of();
        }

        ScopeContext scope = scopeOf(memoryId);
        return JdbcExecutor.queryList(
                dataSource,
                """
                SELECT * FROM memory_insight
                WHERE user_id = ? AND agent_id = ? AND tier = ? AND deleted = 0
                ORDER BY id ASC
                """,
                this::mapInsight,
                scope.userId(),
                scope.agentId(),
                tier.name());
    }

    @Override
    public Optional<MemoryInsight> getLeafByGroup(MemoryId memoryId, String type, String group) {
        return findSingleInsightByTreeSelector(memoryId, type, InsightTier.LEAF, group);
    }

    @Override
    public Optional<MemoryInsight> getBranchByType(MemoryId memoryId, String type) {
        return findSingleInsightByTreeSelector(memoryId, type, InsightTier.BRANCH, null);
    }

    @Override
    public Optional<MemoryInsight> getRootByType(MemoryId memoryId, String type) {
        return findSingleInsightByTreeSelector(memoryId, type, InsightTier.ROOT, null);
    }

    @Override
    public void deleteInsights(MemoryId memoryId, Collection<Long> insightIds) {
        if (insightIds == null || insightIds.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        List<Object> params = new ArrayList<>();
        params.add(writeInstant(Instant.now()));
        params.add(scope.userId());
        params.add(scope.agentId());
        params.addAll(insightIds);
        JdbcExecutor.update(
                dataSource,
                """
                UPDATE memory_insight
                SET deleted = 1, updated_at = ?
                WHERE user_id = ? AND agent_id = ? AND deleted = 0
                  AND biz_id IN (%s)
                """
                        .formatted(placeholders(insightIds.size())),
                params.toArray());
    }

    private List<MemoryRawData> getRawDataByIds(ScopeContext scope, Collection<String> rawDataIds) {
        if (rawDataIds == null || rawDataIds.isEmpty()) {
            return List.of();
        }

        List<Object> params = new ArrayList<>();
        params.add(scope.userId());
        params.add(scope.agentId());
        params.addAll(rawDataIds);
        return JdbcExecutor.queryList(
                dataSource,
                """
                SELECT * FROM memory_raw_data
                WHERE user_id = ? AND agent_id = ? AND deleted = 0
                  AND biz_id IN (%s)
                ORDER BY id ASC
                """
                        .formatted(placeholders(rawDataIds.size())),
                this::mapRawData,
                params.toArray());
    }

    private <T> List<MemoryItem> queryItemsByField(
            MemoryId memoryId, String fieldName, Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        ScopeContext scope = scopeOf(memoryId);
        List<Object> params = new ArrayList<>();
        params.add(scope.userId());
        params.add(scope.agentId());
        params.addAll(values);
        return JdbcExecutor.queryList(
                dataSource,
                """
                SELECT * FROM memory_item
                WHERE user_id = ? AND agent_id = ? AND deleted = 0
                  AND %s IN (%s)
                ORDER BY id ASC
                """
                        .formatted(fieldName, placeholders(values.size())),
                this::mapItem,
                params.toArray());
    }

    private Optional<MemoryInsight> findSingleInsightByTreeSelector(
            MemoryId memoryId, String type, InsightTier tier, String group) {
        ScopeContext scope = scopeOf(memoryId);
        if (group == null) {
            return Optional.ofNullable(
                    JdbcExecutor.queryOne(
                            dataSource,
                            """
                            SELECT * FROM memory_insight
                            WHERE user_id = ? AND agent_id = ? AND type = ? AND tier = ? AND deleted = 0
                            ORDER BY id ASC
                            LIMIT 1
                            """,
                            this::mapInsight,
                            scope.userId(),
                            scope.agentId(),
                            type,
                            tier.name()));
        }
        return Optional.ofNullable(
                JdbcExecutor.queryOne(
                        dataSource,
                        """
                        SELECT * FROM memory_insight
                        WHERE user_id = ? AND agent_id = ? AND type = ? AND tier = ? AND group_name = ?
                          AND deleted = 0
                        ORDER BY id ASC
                        LIMIT 1
                        """,
                        this::mapInsight,
                        scope.userId(),
                        scope.agentId(),
                        type,
                        tier.name(),
                        group));
    }

    private void upsertRawData(
            Connection connection, ScopeContext scope, List<MemoryRawData> rawDataList)
            throws SQLException {
        if (rawDataList == null || rawDataList.isEmpty()) {
            return;
        }
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO memory_raw_data
                            (biz_id, user_id, agent_id, memory_id, type, content_id, segment,
                             caption, caption_vector_id, metadata, resource_id, mime_type,
                             start_time, end_time, created_at, updated_at, deleted)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        ON CONFLICT(user_id, agent_id, biz_id) DO UPDATE SET
                            memory_id = excluded.memory_id,
                            type = excluded.type,
                            content_id = excluded.content_id,
                            segment = excluded.segment,
                            caption = excluded.caption,
                            caption_vector_id = excluded.caption_vector_id,
                            metadata = excluded.metadata,
                            resource_id = excluded.resource_id,
                            mime_type = excluded.mime_type,
                            start_time = excluded.start_time,
                            end_time = excluded.end_time,
                            created_at = excluded.created_at,
                            updated_at = excluded.updated_at,
                            deleted = 0
                        """)) {
            Instant now = Instant.now();
            for (MemoryRawData rawData : rawDataList) {
                bindRawDataUpsert(statement, scope, rawData, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertResources(
            Connection connection, ScopeContext scope, List<MemoryResource> resources)
            throws SQLException {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO memory_resource
                            (biz_id, user_id, agent_id, memory_id, source_uri, storage_uri, file_name,
                             mime_type, checksum, size_bytes, metadata, created_at, updated_at, deleted)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        ON CONFLICT(user_id, agent_id, biz_id) DO UPDATE SET
                            memory_id = excluded.memory_id,
                            source_uri = excluded.source_uri,
                            storage_uri = excluded.storage_uri,
                            file_name = excluded.file_name,
                            mime_type = excluded.mime_type,
                            checksum = excluded.checksum,
                            size_bytes = excluded.size_bytes,
                            metadata = excluded.metadata,
                            created_at = excluded.created_at,
                            updated_at = excluded.updated_at,
                            deleted = 0
                        """)) {
            Instant now = Instant.now();
            for (MemoryResource resource : resources) {
                bindResourceUpsert(statement, scope, resource, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void bindRawDataUpsert(
            PreparedStatement statement, ScopeContext scope, MemoryRawData rawData, Instant now)
            throws SQLException {
        statement.setString(1, rawData.id());
        statement.setString(2, scope.userId());
        statement.setString(3, scope.agentId());
        statement.setString(4, scope.memoryId());
        statement.setString(
                5,
                rawData.contentType() != null ? rawData.contentType() : ConversationContent.TYPE);
        statement.setString(6, rawData.contentId());
        statement.setString(7, toSegmentJson(rawData.segment()));
        statement.setString(8, rawData.caption());
        statement.setString(9, rawData.captionVectorId());
        statement.setString(10, jsonHelper.toJson(rawData.metadata()));
        statement.setString(11, rawData.resourceId());
        statement.setString(12, rawData.mimeType());
        statement.setString(13, writeInstant(rawData.startTime()));
        statement.setString(14, writeInstant(rawData.endTime()));
        statement.setString(
                15, writeInstant(rawData.createdAt() != null ? rawData.createdAt() : now));
        statement.setString(16, writeInstant(now));
    }

    private void bindResourceUpsert(
            PreparedStatement statement, ScopeContext scope, MemoryResource resource, Instant now)
            throws SQLException {
        statement.setString(1, resource.id());
        statement.setString(2, scope.userId());
        statement.setString(3, scope.agentId());
        statement.setString(4, scope.memoryId());
        statement.setString(5, resource.sourceUri());
        statement.setString(6, resource.storageUri());
        statement.setString(7, resource.fileName());
        statement.setString(8, resource.mimeType());
        statement.setString(9, resource.checksum());
        statement.setObject(10, resource.sizeBytes());
        statement.setString(11, jsonHelper.toJson(resource.metadata()));
        statement.setString(
                12, writeInstant(resource.createdAt() != null ? resource.createdAt() : now));
        statement.setString(13, writeInstant(now));
    }

    private void bindItemInsert(
            PreparedStatement statement, ScopeContext scope, MemoryItem item, Instant now)
            throws SQLException {
        statement.setLong(1, item.id());
        statement.setString(2, scope.userId());
        statement.setString(3, scope.agentId());
        statement.setString(4, scope.memoryId());
        statement.setString(5, item.content());
        statement.setString(6, item.scope() != null ? item.scope().name() : null);
        statement.setString(7, item.category() != null ? item.category().name() : null);
        statement.setString(8, item.vectorId());
        statement.setString(9, item.rawDataId());
        statement.setString(10, item.contentHash());
        statement.setString(11, writeInstant(item.occurredAt()));
        statement.setString(12, writeInstant(item.observedAt()));
        statement.setString(
                13, item.type() != null ? item.type().name() : MemoryItemType.FACT.name());
        statement.setString(
                14, item.contentType() != null ? item.contentType() : ConversationContent.TYPE);
        statement.setString(15, jsonHelper.toJson(item.metadata()));
        statement.setString(16, writeInstant(item.createdAt() != null ? item.createdAt() : now));
        statement.setString(17, writeInstant(now));
    }

    private void bindInsightTypeUpsert(
            PreparedStatement statement, MemoryInsightType insightType, Instant now)
            throws SQLException {
        statement.setObject(1, insightType.id());
        statement.setString(2, insightType.name());
        statement.setString(3, insightType.description());
        statement.setString(4, insightType.descriptionVectorId());
        statement.setString(5, jsonHelper.toJson(insightType.categories()));
        statement.setInt(6, insightType.targetTokens());
        statement.setString(
                7,
                insightType.insightAnalysisMode() != null
                        ? insightType.insightAnalysisMode().name()
                        : null);
        statement.setString(8, insightType.scope() != null ? insightType.scope().name() : null);
        statement.setString(9, jsonHelper.toJson(insightType.treeConfig()));
        statement.setString(10, writeInstant(insightType.lastUpdatedAt()));
        statement.setString(
                11, writeInstant(insightType.createdAt() != null ? insightType.createdAt() : now));
        statement.setString(12, writeInstant(now));
    }

    private void bindInsightUpsert(
            PreparedStatement statement, ScopeContext scope, MemoryInsight insight, Instant now)
            throws SQLException {
        statement.setLong(1, insight.id());
        statement.setString(2, scope.userId());
        statement.setString(3, scope.agentId());
        statement.setString(4, scope.memoryId());
        statement.setString(5, insight.type());
        statement.setString(6, insight.scope() != null ? insight.scope().name() : null);
        statement.setString(7, insight.name());
        statement.setString(8, jsonHelper.toJson(insight.categories()));
        statement.setString(9, insight.pointsContent());
        statement.setString(10, jsonHelper.toJson(insight.points()));
        statement.setString(11, insight.group());
        statement.setFloat(12, insight.confidence());
        statement.setString(13, writeInstant(insight.lastReasonedAt()));
        statement.setString(14, jsonHelper.toJson(insight.summaryEmbedding()));
        statement.setString(15, insight.tier() != null ? insight.tier().name() : null);
        statement.setObject(16, insight.parentInsightId());
        statement.setString(17, jsonHelper.toJson(insight.childInsightIds()));
        statement.setInt(18, insight.version());
        statement.setString(
                19, writeInstant(insight.createdAt() != null ? insight.createdAt() : now));
        statement.setString(20, writeInstant(now));
    }

    private MemoryRawData mapRawData(ResultSet resultSet) throws SQLException {
        return new MemoryRawData(
                resultSet.getString("biz_id"),
                resultSet.getString("memory_id"),
                parseContentType(resultSet.getString("type")),
                resultSet.getString("content_id"),
                fromSegmentJson(resultSet.getString("segment")),
                resultSet.getString("caption"),
                resultSet.getString("caption_vector_id"),
                jsonHelper.fromJson(resultSet.getString("metadata"), OBJECT_MAP_TYPE),
                resultSet.getString("resource_id"),
                resultSet.getString("mime_type"),
                parseInstant(resultSet.getString("created_at")),
                parseInstant(resultSet.getString("start_time")),
                parseInstant(resultSet.getString("end_time")));
    }

    private MemoryResource mapResource(ResultSet resultSet) throws SQLException {
        return new MemoryResource(
                resultSet.getString("biz_id"),
                resultSet.getString("memory_id"),
                resultSet.getString("source_uri"),
                resultSet.getString("storage_uri"),
                resultSet.getString("file_name"),
                resultSet.getString("mime_type"),
                resultSet.getString("checksum"),
                nullableLong(resultSet, "size_bytes"),
                jsonHelper.fromJson(resultSet.getString("metadata"), OBJECT_MAP_TYPE),
                parseInstant(resultSet.getString("created_at")));
    }

    private MemoryItem mapItem(ResultSet resultSet) throws SQLException {
        return new MemoryItem(
                resultSet.getLong("biz_id"),
                resultSet.getString("memory_id"),
                resultSet.getString("content"),
                parseScope(resultSet.getString("scope")),
                parseCategory(resultSet.getString("category")),
                parseContentType(resultSet.getString("raw_data_type")),
                resultSet.getString("vector_id"),
                resultSet.getString("raw_data_id"),
                resultSet.getString("content_hash"),
                parseInstant(resultSet.getString("occurred_at")),
                parseInstant(resultSet.getString("observed_at")),
                jsonHelper.fromJson(resultSet.getString("metadata"), OBJECT_MAP_TYPE),
                parseInstant(resultSet.getString("created_at")),
                parseItemType(resultSet.getString("type")));
    }

    private MemoryInsightType mapInsightType(ResultSet resultSet) throws SQLException {
        return new MemoryInsightType(
                nullableLong(resultSet, "biz_id"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("description_vector_id"),
                jsonHelper.fromJson(resultSet.getString("categories"), STRING_LIST_TYPE),
                resultSet.getInt("target_tokens"),
                parseInstant(resultSet.getString("last_updated_at")),
                parseInstant(resultSet.getString("created_at")),
                parseInstant(resultSet.getString("updated_at")),
                parseInsightAnalysisMode(resultSet.getString("analysis_mode")),
                jsonHelper.fromJson(resultSet.getString("tree_config"), InsightTreeConfig.class),
                parseScope(resultSet.getString("scope")));
    }

    private MemoryInsight mapInsight(ResultSet resultSet) throws SQLException {
        List<InsightPoint> points =
                jsonHelper.fromJson(resultSet.getString("points"), INSIGHT_POINT_LIST_TYPE);
        if (points == null) {
            points = List.of();
        }
        List<Long> childInsightIds =
                jsonHelper.fromJson(resultSet.getString("child_insight_ids"), LONG_LIST_TYPE);
        return new MemoryInsight(
                resultSet.getLong("biz_id"),
                resultSet.getString("memory_id"),
                resultSet.getString("type"),
                parseScope(resultSet.getString("scope")),
                resultSet.getString("name"),
                jsonHelper.fromJson(resultSet.getString("categories"), STRING_LIST_TYPE),
                points,
                resultSet.getString("group_name"),
                resultSet.getFloat("confidence"),
                parseInstant(resultSet.getString("last_reasoned_at")),
                jsonHelper.fromJson(resultSet.getString("summary_embedding"), FLOAT_LIST_TYPE),
                parseInstant(resultSet.getString("created_at")),
                parseInstant(resultSet.getString("updated_at")),
                parseInsightTier(resultSet.getString("tier")),
                nullableLong(resultSet, "parent_insight_id"),
                childInsightIds != null ? childInsightIds : List.of(),
                resultSet.getInt("version"));
    }

    private String toSegmentJson(Segment segment) {
        if (segment == null) {
            return null;
        }
        return jsonHelper.toJson(segmentToMap(segment));
    }

    private Segment fromSegmentJson(String json) {
        Map<String, Object> segmentMap = jsonHelper.fromJson(json, OBJECT_MAP_TYPE);
        if (segmentMap == null) {
            return null;
        }
        return mapToSegment(segmentMap);
    }

    private Map<String, Object> segmentToMap(Segment segment) {
        Segment durableSegment = segment.withoutRuntimeContext();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", durableSegment.content());
        map.put("caption", durableSegment.caption());
        map.put("metadata", durableSegment.metadata());
        if (durableSegment.boundary() != null) {
            Map<String, Object> boundaryMap = new LinkedHashMap<>();
            switch (durableSegment.boundary()) {
                case CharBoundary charBoundary -> {
                    boundaryMap.put("type", "char");
                    boundaryMap.put("startChar", charBoundary.startChar());
                    boundaryMap.put("endChar", charBoundary.endChar());
                }
                case MessageBoundary messageBoundary -> {
                    boundaryMap.put("type", "message");
                    boundaryMap.put("startMessage", messageBoundary.startMessage());
                    boundaryMap.put("endMessage", messageBoundary.endMessage());
                }
                default -> {}
            }
            if (!boundaryMap.isEmpty()) {
                map.put("boundary", boundaryMap);
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Segment mapToSegment(Map<String, Object> map) {
        String content = (String) map.get("content");
        String caption = (String) map.get("caption");
        Map<String, Object> metadata =
                map.get("metadata") instanceof Map<?, ?> metadataMap
                        ? (Map<String, Object>) metadataMap
                        : Map.of();
        SegmentBoundary boundary = null;
        if (map.get("boundary") instanceof Map<?, ?> rawBoundary) {
            String type = (String) rawBoundary.get("type");
            boundary =
                    switch (type) {
                        case "char" ->
                                new CharBoundary(
                                        ((Number) rawBoundary.get("startChar")).intValue(),
                                        ((Number) rawBoundary.get("endChar")).intValue());
                        case "message" ->
                                new MessageBoundary(
                                        ((Number) rawBoundary.get("startMessage")).intValue(),
                                        ((Number) rawBoundary.get("endMessage")).intValue());
                        default -> null;
                    };
        }
        return new Segment(content, caption, boundary, metadata);
    }

    private MemoryScope parseScope(String value) {
        return value == null ? null : MemoryScope.valueOf(value);
    }

    private MemoryCategory parseCategory(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MemoryCategory.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return MemoryCategory.byName(value).orElse(null);
        }
    }

    private MemoryItemType parseItemType(String value) {
        return value == null ? MemoryItemType.FACT : MemoryItemType.valueOf(value);
    }

    private InsightAnalysisMode parseInsightAnalysisMode(String value) {
        return value == null ? InsightAnalysisMode.BRANCH : InsightAnalysisMode.valueOf(value);
    }

    private InsightTier parseInsightTier(String value) {
        return value == null ? InsightTier.LEAF : InsightTier.valueOf(value);
    }

    private String parseContentType(String value) {
        if (value == null || value.isBlank()) {
            return ConversationContent.TYPE;
        }
        return value;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, SQLITE_TIME_FORMATTER).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("Unsupported datetime value: " + value);
    }

    private String writeInstant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private Long nullableLong(ResultSet resultSet, String columnLabel) throws SQLException {
        long value = resultSet.getLong(columnLabel);
        return resultSet.wasNull() ? null : value;
    }

    private ScopeContext scopeOf(MemoryId memoryId) {
        String userId = memoryId.getAttribute("userId");
        String agentId = memoryId.getAttribute("agentId");
        return new ScopeContext(userId, agentId, memoryId.toIdentifier());
    }

    private String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private record ScopeContext(String userId, String agentId, String memoryId) {}
}
