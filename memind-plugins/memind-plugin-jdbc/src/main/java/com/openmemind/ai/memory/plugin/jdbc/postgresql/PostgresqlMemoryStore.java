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
package com.openmemind.ai.memory.plugin.jdbc.postgresql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.insight.tree.InsightTreeConfig;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.MessageBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentBoundary;
import com.openmemind.ai.memory.core.store.insight.InsightOperations;
import com.openmemind.ai.memory.core.store.item.ItemOperations;
import com.openmemind.ai.memory.core.store.rawdata.RawDataOperations;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcExecutor;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JsonCodec;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.postgresql.util.PGobject;

public class PostgresqlMemoryStore implements RawDataOperations, ItemOperations, InsightOperations {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Float>> FLOAT_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<InsightPoint>> INSIGHT_POINT_LIST_TYPE =
            new TypeReference<>() {};

    private final DataSource dataSource;
    private final JsonCodec jsonHelper;

    public PostgresqlMemoryStore(DataSource dataSource) {
        this(dataSource, true);
    }

    public PostgresqlMemoryStore(DataSource dataSource, boolean createIfNotExist) {
        this(dataSource, JsonCodec.createDefaultObjectMapper(), createIfNotExist);
    }

    private PostgresqlMemoryStore(
            DataSource dataSource, ObjectMapper objectMapper, boolean createIfNotExist) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        StoreSchemaBootstrap.ensurePostgresql(this.dataSource, createIfNotExist);
        this.jsonHelper = new JsonCodec(Objects.requireNonNull(objectMapper, "objectMapper"));
    }

    @Override
    public void upsertRawData(MemoryId memoryId, List<MemoryRawData> rawDataList) {
        if (rawDataList == null || rawDataList.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        JdbcExecutor.inTransaction(
                dataSource,
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO memory_raw_data
                                        (biz_id, user_id, agent_id, memory_id, type, content_id, segment,
                                         caption, caption_vector_id, metadata, start_time, end_time,
                                         created_at, updated_at, deleted)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)
                                    ON CONFLICT (user_id, agent_id, biz_id) DO UPDATE SET
                                        memory_id = EXCLUDED.memory_id,
                                        type = EXCLUDED.type,
                                        content_id = EXCLUDED.content_id,
                                        segment = EXCLUDED.segment,
                                        caption = EXCLUDED.caption,
                                        caption_vector_id = EXCLUDED.caption_vector_id,
                                        metadata = EXCLUDED.metadata,
                                        start_time = EXCLUDED.start_time,
                                        end_time = EXCLUDED.end_time,
                                        created_at = EXCLUDED.created_at,
                                        updated_at = EXCLUDED.updated_at,
                                        deleted = FALSE
                                    """)) {
                        Instant now = Instant.now();
                        for (MemoryRawData rawData : rawDataList) {
                            bindRawDataUpsert(statement, scope, rawData, now);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    return null;
                });
    }

    @Override
    public Optional<MemoryRawData> getRawData(MemoryId memoryId, String rawDataId) {
        ScopeContext scope = scopeOf(memoryId);
        return Optional.ofNullable(
                JdbcExecutor.queryOne(
                        dataSource,
                        """
                        SELECT * FROM memory_raw_data
                        WHERE user_id = ? AND agent_id = ? AND biz_id = ? AND deleted = FALSE
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
                        WHERE user_id = ? AND agent_id = ? AND content_id = ? AND deleted = FALSE
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
                WHERE user_id = ? AND agent_id = ? AND deleted = FALSE
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
        return JdbcExecutor.queryList(
                dataSource,
                """
                SELECT * FROM memory_raw_data
                WHERE user_id = ? AND agent_id = ? AND deleted = FALSE
                  AND caption_vector_id IS NULL
                  AND created_at < ?
                ORDER BY created_at ASC, id ASC
                LIMIT ?
                """,
                this::mapRawData,
                scope.userId(),
                scope.agentId(),
                Timestamp.from(Instant.now().minus(minAge)),
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
                                    WHERE user_id = ? AND agent_id = ? AND biz_id = ? AND deleted = FALSE
                                    """)) {
                        Timestamp updatedAt = Timestamp.from(Instant.now());
                        for (Map.Entry<String, String> entry : vectorIds.entrySet()) {
                            MemoryRawData existing = existingById.get(entry.getKey());
                            if (existing == null) {
                                continue;
                            }
                            MemoryRawData updated =
                                    existing.withVectorId(entry.getValue(), metadataPatch);
                            statement.setString(1, entry.getValue());
                            setJsonb(statement, 2, jsonHelper.toJson(updated.metadata()));
                            statement.setTimestamp(3, updatedAt);
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
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)
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
                WHERE user_id = ? AND agent_id = ? AND deleted = FALSE
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
                        WHERE user_id = ? AND agent_id = ? AND deleted = FALSE
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
        params.add(Timestamp.from(Instant.now()));
        params.add(scope.userId());
        params.add(scope.agentId());
        params.addAll(itemIds);
        JdbcExecutor.update(
                dataSource,
                """
                UPDATE memory_item
                SET deleted = TRUE, updated_at = ?
                WHERE user_id = ? AND agent_id = ? AND deleted = FALSE
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
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)
                                    ON CONFLICT (name) DO UPDATE SET
                                        biz_id = EXCLUDED.biz_id,
                                        description = EXCLUDED.description,
                                        description_vector_id = EXCLUDED.description_vector_id,
                                        categories = EXCLUDED.categories,
                                        target_tokens = EXCLUDED.target_tokens,
                                        analysis_mode = EXCLUDED.analysis_mode,
                                        scope = EXCLUDED.scope,
                                        tree_config = EXCLUDED.tree_config,
                                        last_updated_at = EXCLUDED.last_updated_at,
                                        created_at = EXCLUDED.created_at,
                                        updated_at = EXCLUDED.updated_at,
                                        deleted = FALSE
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
                        WHERE name = ? AND deleted = FALSE
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
                WHERE deleted = FALSE
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
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)
                                    ON CONFLICT (user_id, agent_id, biz_id) DO UPDATE SET
                                        memory_id = EXCLUDED.memory_id,
                                        type = EXCLUDED.type,
                                        scope = EXCLUDED.scope,
                                        name = EXCLUDED.name,
                                        categories = EXCLUDED.categories,
                                        content = EXCLUDED.content,
                                        points = EXCLUDED.points,
                                        group_name = EXCLUDED.group_name,
                                        confidence = EXCLUDED.confidence,
                                        last_reasoned_at = EXCLUDED.last_reasoned_at,
                                        summary_embedding = EXCLUDED.summary_embedding,
                                        tier = EXCLUDED.tier,
                                        parent_insight_id = EXCLUDED.parent_insight_id,
                                        child_insight_ids = EXCLUDED.child_insight_ids,
                                        version = EXCLUDED.version,
                                        created_at = EXCLUDED.created_at,
                                        updated_at = EXCLUDED.updated_at,
                                        deleted = FALSE
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
                        WHERE user_id = ? AND agent_id = ? AND biz_id = ? AND deleted = FALSE
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
                WHERE user_id = ? AND agent_id = ? AND deleted = FALSE
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
                WHERE user_id = ? AND agent_id = ? AND type = ? AND deleted = FALSE
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
                WHERE user_id = ? AND agent_id = ? AND tier = ? AND deleted = FALSE
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
        params.add(Timestamp.from(Instant.now()));
        params.add(scope.userId());
        params.add(scope.agentId());
        params.addAll(insightIds);
        JdbcExecutor.update(
                dataSource,
                """
                UPDATE memory_insight
                SET deleted = TRUE, updated_at = ?
                WHERE user_id = ? AND agent_id = ? AND deleted = FALSE
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
                WHERE user_id = ? AND agent_id = ? AND deleted = FALSE
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
                WHERE user_id = ? AND agent_id = ? AND deleted = FALSE
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
                            WHERE user_id = ? AND agent_id = ? AND type = ? AND tier = ? AND deleted = FALSE
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
                          AND deleted = FALSE
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

    private void bindRawDataUpsert(
            PreparedStatement statement, ScopeContext scope, MemoryRawData rawData, Instant now)
            throws SQLException {
        statement.setString(1, rawData.id());
        statement.setString(2, scope.userId());
        statement.setString(3, scope.agentId());
        statement.setString(4, scope.memoryId());
        statement.setString(
                5,
                rawData.contentType() != null ? rawData.contentType() : ContentTypes.CONVERSATION);
        statement.setString(6, rawData.contentId());
        setJsonb(statement, 7, toSegmentJson(rawData.segment()));
        statement.setString(8, rawData.caption());
        statement.setString(9, rawData.captionVectorId());
        setJsonb(statement, 10, jsonHelper.toJson(rawData.metadata()));
        setTimestamp(statement, 11, rawData.startTime());
        setTimestamp(statement, 12, rawData.endTime());
        setTimestamp(statement, 13, rawData.createdAt() != null ? rawData.createdAt() : now);
        setTimestamp(statement, 14, now);
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
        setTimestamp(statement, 11, item.occurredAt());
        setTimestamp(statement, 12, item.observedAt());
        statement.setString(
                13, item.type() != null ? item.type().name() : MemoryItemType.FACT.name());
        statement.setString(
                14, item.contentType() != null ? item.contentType() : ContentTypes.CONVERSATION);
        setJsonb(statement, 15, jsonHelper.toJson(item.metadata()));
        setTimestamp(statement, 16, item.createdAt() != null ? item.createdAt() : now);
        setTimestamp(statement, 17, now);
    }

    private void bindInsightTypeUpsert(
            PreparedStatement statement, MemoryInsightType insightType, Instant now)
            throws SQLException {
        statement.setObject(1, insightType.id());
        statement.setString(2, insightType.name());
        statement.setString(3, insightType.description());
        statement.setString(4, insightType.descriptionVectorId());
        setJsonb(statement, 5, jsonHelper.toJson(insightType.categories()));
        statement.setInt(6, insightType.targetTokens());
        statement.setString(
                7,
                insightType.insightAnalysisMode() != null
                        ? insightType.insightAnalysisMode().name()
                        : null);
        statement.setString(8, insightType.scope() != null ? insightType.scope().name() : null);
        setJsonb(statement, 9, jsonHelper.toJson(insightType.treeConfig()));
        setTimestamp(statement, 10, insightType.lastUpdatedAt());
        setTimestamp(
                statement, 11, insightType.createdAt() != null ? insightType.createdAt() : now);
        setTimestamp(statement, 12, now);
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
        setJsonb(statement, 8, jsonHelper.toJson(insight.categories()));
        statement.setString(9, insight.pointsContent());
        setJsonb(statement, 10, jsonHelper.toJson(insight.points()));
        statement.setString(11, insight.group());
        statement.setFloat(12, insight.confidence());
        setTimestamp(statement, 13, insight.lastReasonedAt());
        setJsonb(statement, 14, jsonHelper.toJson(insight.summaryEmbedding()));
        statement.setString(15, insight.tier() != null ? insight.tier().name() : null);
        statement.setObject(16, insight.parentInsightId());
        setJsonb(statement, 17, jsonHelper.toJson(insight.childInsightIds()));
        statement.setInt(18, insight.version());
        setTimestamp(statement, 19, insight.createdAt() != null ? insight.createdAt() : now);
        setTimestamp(statement, 20, now);
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
                parseInstant(resultSet.getTimestamp("created_at")),
                parseInstant(resultSet.getTimestamp("start_time")),
                parseInstant(resultSet.getTimestamp("end_time")));
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
                parseInstant(resultSet.getTimestamp("occurred_at")),
                parseInstant(resultSet.getTimestamp("observed_at")),
                jsonHelper.fromJson(resultSet.getString("metadata"), OBJECT_MAP_TYPE),
                parseInstant(resultSet.getTimestamp("created_at")),
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
                parseInstant(resultSet.getTimestamp("last_updated_at")),
                parseInstant(resultSet.getTimestamp("created_at")),
                parseInstant(resultSet.getTimestamp("updated_at")),
                parseInsightAnalysisMode(resultSet.getString("analysis_mode")),
                jsonHelper.fromJson(resultSet.getString("tree_config"), InsightTreeConfig.class),
                parseScope(resultSet.getString("scope")),
                null);
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
                parseInstant(resultSet.getTimestamp("last_reasoned_at")),
                jsonHelper.fromJson(resultSet.getString("summary_embedding"), FLOAT_LIST_TYPE),
                parseInstant(resultSet.getTimestamp("created_at")),
                parseInstant(resultSet.getTimestamp("updated_at")),
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
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", segment.content());
        map.put("caption", segment.caption());
        map.put("metadata", segment.metadata());
        if (segment.boundary() != null) {
            Map<String, Object> boundaryMap = new LinkedHashMap<>();
            switch (segment.boundary()) {
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
            return ContentTypes.CONVERSATION;
        }
        return value;
    }

    private Instant parseInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void setTimestamp(PreparedStatement statement, int parameterIndex, Instant instant)
            throws SQLException {
        if (instant == null) {
            statement.setTimestamp(parameterIndex, null);
        } else {
            statement.setTimestamp(parameterIndex, Timestamp.from(instant));
        }
    }

    private void setJsonb(PreparedStatement statement, int parameterIndex, String json)
            throws SQLException {
        if (json == null) {
            statement.setObject(parameterIndex, null);
            return;
        }
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(json);
        statement.setObject(parameterIndex, jsonb);
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
