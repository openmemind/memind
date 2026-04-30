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

import com.openmemind.ai.memory.core.buffer.BufferEntry;
import com.openmemind.ai.memory.core.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.plugin.jdbc.internal.jdbi.JdbiFactory;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;

public class SqliteInsightBuffer implements InsightBuffer {

    private static final String APPEND_SQL =
            """
            INSERT OR IGNORE INTO memory_insight_buffer
                (user_id, agent_id, memory_id, insight_type_name, item_id, built, deleted)
            VALUES (:userId, :agentId, :memoryId, :insightTypeName, :itemId, 0, 0)
            """;

    private static final String GET_UNGROUPED_SQL =
            """
            SELECT item_id, group_name, built
            FROM memory_insight_buffer
            WHERE user_id = :userId
              AND agent_id = :agentId
              AND insight_type_name = :insightTypeName
              AND group_name IS NULL
              AND built = 0
              AND deleted = 0
            ORDER BY id ASC
            """;

    private static final String COUNT_UNGROUPED_SQL =
            """
            SELECT COUNT(*)
            FROM memory_insight_buffer
            WHERE user_id = :userId
              AND agent_id = :agentId
              AND insight_type_name = :insightTypeName
              AND group_name IS NULL
              AND built = 0
              AND deleted = 0
            """;

    private static final String GET_GROUP_UNBUILT_SQL =
            """
            SELECT item_id, group_name, built
            FROM memory_insight_buffer
            WHERE user_id = :userId
              AND agent_id = :agentId
              AND insight_type_name = :insightTypeName
              AND group_name = :groupName
              AND built = 0
              AND deleted = 0
            ORDER BY id ASC
            """;

    private static final String COUNT_GROUP_UNBUILT_SQL =
            """
            SELECT COUNT(*)
            FROM memory_insight_buffer
            WHERE user_id = :userId
              AND agent_id = :agentId
              AND insight_type_name = :insightTypeName
              AND group_name = :groupName
              AND built = 0
              AND deleted = 0
            """;

    private static final String ASSIGN_GROUP_SQL =
            """
            UPDATE memory_insight_buffer
            SET group_name = :groupName, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
              AND agent_id = :agentId
              AND insight_type_name = :insightTypeName
              AND item_id IN (<itemIds>)
              AND deleted = 0
            """;

    private static final String MARK_BUILT_SQL =
            """
            UPDATE memory_insight_buffer
            SET built = 1, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
              AND agent_id = :agentId
              AND insight_type_name = :insightTypeName
              AND item_id IN (<itemIds>)
              AND deleted = 0
            """;

    private static final String GET_UNBUILT_BY_GROUP_SQL =
            """
            SELECT item_id, group_name, built
            FROM memory_insight_buffer
            WHERE user_id = :userId
              AND agent_id = :agentId
              AND insight_type_name = :insightTypeName
              AND group_name IS NOT NULL
              AND built = 0
              AND deleted = 0
            ORDER BY group_name ASC, id ASC
            """;

    private static final String LIST_GROUPS_SQL =
            """
            SELECT DISTINCT group_name
            FROM memory_insight_buffer
            WHERE user_id = :userId
              AND agent_id = :agentId
              AND insight_type_name = :insightTypeName
              AND group_name IS NOT NULL
              AND deleted = 0
            ORDER BY group_name ASC
            """;

    private static final String HAS_WORK_SQL =
            """
            SELECT COUNT(*)
            FROM memory_insight_buffer
            WHERE user_id = :userId
              AND agent_id = :agentId
              AND insight_type_name = :insightTypeName
              AND built = 0
              AND deleted = 0
            """;

    private final Jdbi jdbi;

    public SqliteInsightBuffer(DataSource dataSource) {
        this(dataSource, true);
    }

    public SqliteInsightBuffer(DataSource dataSource, boolean createIfNotExist) {
        DataSource checkedDataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbi = JdbiFactory.create(checkedDataSource);
        StoreSchemaBootstrap.ensureSqlite(checkedDataSource, createIfNotExist);
    }

    @Override
    public void append(MemoryId memoryId, String insightTypeName, List<Long> itemIds) {
        List<Long> ids = nonNullItemIds(itemIds);
        if (ids.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        jdbi.useTransaction(
                handle -> {
                    var batch = handle.prepareBatch(APPEND_SQL);
                    for (Long itemId : ids) {
                        bindScope(batch, scope, insightTypeName).bind("itemId", itemId).add();
                    }
                    batch.execute();
                });
    }

    @Override
    public List<BufferEntry> getUnGrouped(MemoryId memoryId, String insightTypeName) {
        ScopeContext scope = scopeOf(memoryId);
        return jdbi.withHandle(
                handle ->
                        bindScope(handle.createQuery(GET_UNGROUPED_SQL), scope, insightTypeName)
                                .map(SqliteInsightBuffer::mapEntry)
                                .list());
    }

    @Override
    public int countUnGrouped(MemoryId memoryId, String insightTypeName) {
        ScopeContext scope = scopeOf(memoryId);
        return Math.toIntExact(
                jdbi.withHandle(
                        handle ->
                                bindScope(
                                                handle.createQuery(COUNT_UNGROUPED_SQL),
                                                scope,
                                                insightTypeName)
                                        .mapTo(Long.class)
                                        .one()));
    }

    @Override
    public List<BufferEntry> getGroupUnbuilt(
            MemoryId memoryId, String insightTypeName, String groupName) {
        ScopeContext scope = scopeOf(memoryId);
        return jdbi.withHandle(
                handle ->
                        bindScope(handle.createQuery(GET_GROUP_UNBUILT_SQL), scope, insightTypeName)
                                .bind("groupName", groupName)
                                .map(SqliteInsightBuffer::mapEntry)
                                .list());
    }

    @Override
    public int countGroupUnbuilt(MemoryId memoryId, String insightTypeName, String groupName) {
        ScopeContext scope = scopeOf(memoryId);
        return Math.toIntExact(
                jdbi.withHandle(
                        handle ->
                                bindScope(
                                                handle.createQuery(COUNT_GROUP_UNBUILT_SQL),
                                                scope,
                                                insightTypeName)
                                        .bind("groupName", groupName)
                                        .mapTo(Long.class)
                                        .one()));
    }

    @Override
    public void assignGroup(
            MemoryId memoryId, String insightTypeName, List<Long> itemIds, String groupName) {
        List<Long> ids = nonNullItemIds(itemIds);
        if (ids.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        jdbi.useHandle(
                handle ->
                        bindScope(handle.createUpdate(ASSIGN_GROUP_SQL), scope, insightTypeName)
                                .bind("groupName", groupName)
                                .bindList("itemIds", ids)
                                .execute());
    }

    @Override
    public void markBuilt(MemoryId memoryId, String insightTypeName, List<Long> itemIds) {
        List<Long> ids = nonNullItemIds(itemIds);
        if (ids.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        jdbi.useHandle(
                handle ->
                        bindScope(handle.createUpdate(MARK_BUILT_SQL), scope, insightTypeName)
                                .bindList("itemIds", ids)
                                .execute());
    }

    @Override
    public Map<String, List<BufferEntry>> getUnbuiltByGroup(
            MemoryId memoryId, String insightTypeName) {
        ScopeContext scope = scopeOf(memoryId);
        List<BufferEntry> entries =
                jdbi.withHandle(
                        handle ->
                                bindScope(
                                                handle.createQuery(GET_UNBUILT_BY_GROUP_SQL),
                                                scope,
                                                insightTypeName)
                                        .map(SqliteInsightBuffer::mapEntry)
                                        .list());
        Map<String, List<BufferEntry>> grouped = new LinkedHashMap<>();
        for (BufferEntry entry : entries) {
            grouped.computeIfAbsent(entry.groupName(), key -> new ArrayList<>()).add(entry);
        }
        return Map.copyOf(grouped);
    }

    @Override
    public Set<String> listGroups(MemoryId memoryId, String insightTypeName) {
        ScopeContext scope = scopeOf(memoryId);
        return jdbi.withHandle(
                handle ->
                        bindScope(handle.createQuery(LIST_GROUPS_SQL), scope, insightTypeName)
                                .mapTo(String.class)
                                .list()
                                .stream()
                                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    @Override
    public boolean hasWork(MemoryId memoryId, String insightTypeName) {
        ScopeContext scope = scopeOf(memoryId);
        return jdbi.withHandle(
                        handle ->
                                bindScope(handle.createQuery(HAS_WORK_SQL), scope, insightTypeName)
                                        .mapTo(Long.class)
                                        .one())
                > 0;
    }

    private static <StatementT extends SqlStatement<StatementT>> StatementT bindScope(
            StatementT statement, ScopeContext scope, String insightTypeName) {
        return statement
                .bind("userId", scope.userId())
                .bind("agentId", scope.agentId())
                .bind("memoryId", scope.memoryId())
                .bind("insightTypeName", insightTypeName);
    }

    private static BufferEntry mapEntry(ResultSet resultSet, StatementContext context)
            throws SQLException {
        return new BufferEntry(
                resultSet.getLong("item_id"),
                resultSet.getString("group_name"),
                resultSet.getBoolean("built"));
    }

    private static List<Long> nonNullItemIds(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return itemIds.stream().filter(Objects::nonNull).toList();
    }

    private static ScopeContext scopeOf(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        return new ScopeContext(
                memoryId.getAttribute("userId"),
                memoryId.getAttribute("agentId"),
                memoryId.toIdentifier());
    }

    private record ScopeContext(String userId, String agentId, String memoryId) {}
}
