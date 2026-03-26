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
package com.openmemind.ai.memory.plugin.jdbc.internal.buffer;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.store.buffer.BufferEntry;
import com.openmemind.ai.memory.core.store.buffer.InsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcExecutor;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

public abstract class AbstractJdbcInsightBuffer implements InsightBuffer {

    private final DataSource dataSource;

    protected AbstractJdbcInsightBuffer(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void append(MemoryId memoryId, String insightTypeName, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        JdbcExecutor.inTransaction(
                dataSource,
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement(appendSql())) {
                        for (Long itemId : itemIds) {
                            if (itemId == null) {
                                continue;
                            }
                            bindScope(statement, scope, insightTypeName, itemId);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    return null;
                });
    }

    @Override
    public List<BufferEntry> getUnGrouped(MemoryId memoryId, String insightTypeName) {
        ScopeContext scope = scopeOf(memoryId);
        String sql =
                "SELECT item_id, group_name, built "
                        + "FROM memory_insight_buffer "
                        + "WHERE user_id = ? AND agent_id = ? AND insight_type_name = ? "
                        + "AND group_name IS NULL AND built = "
                        + falseLiteral()
                        + " "
                        + "AND deleted = "
                        + falseLiteral()
                        + " "
                        + "ORDER BY id ASC";
        return JdbcExecutor.queryList(
                dataSource, sql, this::mapEntry, scope.userId(), scope.agentId(), insightTypeName);
    }

    @Override
    public int countUnGrouped(MemoryId memoryId, String insightTypeName) {
        ScopeContext scope = scopeOf(memoryId);
        String sql =
                "SELECT COUNT(*) "
                        + "FROM memory_insight_buffer "
                        + "WHERE user_id = ? AND agent_id = ? AND insight_type_name = ? "
                        + "AND group_name IS NULL AND built = "
                        + falseLiteral()
                        + " "
                        + "AND deleted = "
                        + falseLiteral();
        return Math.toIntExact(
                JdbcExecutor.queryCount(
                        dataSource, sql, scope.userId(), scope.agentId(), insightTypeName));
    }

    @Override
    public List<BufferEntry> getGroupUnbuilt(
            MemoryId memoryId, String insightTypeName, String groupName) {
        ScopeContext scope = scopeOf(memoryId);
        String sql =
                "SELECT item_id, group_name, built "
                        + "FROM memory_insight_buffer "
                        + "WHERE user_id = ? AND agent_id = ? AND insight_type_name = ? "
                        + "AND group_name = ? AND built = "
                        + falseLiteral()
                        + " "
                        + "AND deleted = "
                        + falseLiteral()
                        + " "
                        + "ORDER BY id ASC";
        return JdbcExecutor.queryList(
                dataSource,
                sql,
                this::mapEntry,
                scope.userId(),
                scope.agentId(),
                insightTypeName,
                groupName);
    }

    @Override
    public int countGroupUnbuilt(MemoryId memoryId, String insightTypeName, String groupName) {
        ScopeContext scope = scopeOf(memoryId);
        String sql =
                "SELECT COUNT(*) "
                        + "FROM memory_insight_buffer "
                        + "WHERE user_id = ? AND agent_id = ? AND insight_type_name = ? "
                        + "AND group_name = ? AND built = "
                        + falseLiteral()
                        + " "
                        + "AND deleted = "
                        + falseLiteral();
        return Math.toIntExact(
                JdbcExecutor.queryCount(
                        dataSource,
                        sql,
                        scope.userId(),
                        scope.agentId(),
                        insightTypeName,
                        groupName));
    }

    @Override
    public void assignGroup(
            MemoryId memoryId, String insightTypeName, List<Long> itemIds, String groupName) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        String sql =
                "UPDATE memory_insight_buffer "
                        + "SET group_name = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ? AND agent_id = ? AND insight_type_name = ? "
                        + "AND item_id IN ("
                        + placeholders(itemIds.size())
                        + ") "
                        + "AND deleted = "
                        + falseLiteral();
        JdbcExecutor.update(
                dataSource, sql, paramsWithItemIds(groupName, scope, insightTypeName, itemIds));
    }

    @Override
    public void markBuilt(MemoryId memoryId, String insightTypeName, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }

        ScopeContext scope = scopeOf(memoryId);
        String sql =
                "UPDATE memory_insight_buffer "
                        + "SET built = "
                        + trueLiteral()
                        + ", updated_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ? AND agent_id = ? AND insight_type_name = ? "
                        + "AND item_id IN ("
                        + placeholders(itemIds.size())
                        + ") "
                        + "AND deleted = "
                        + falseLiteral();
        JdbcExecutor.update(dataSource, sql, paramsWithItemIds(scope, insightTypeName, itemIds));
    }

    @Override
    public Map<String, List<BufferEntry>> getUnbuiltByGroup(
            MemoryId memoryId, String insightTypeName) {
        ScopeContext scope = scopeOf(memoryId);
        String sql =
                "SELECT item_id, group_name, built "
                        + "FROM memory_insight_buffer "
                        + "WHERE user_id = ? AND agent_id = ? AND insight_type_name = ? "
                        + "AND group_name IS NOT NULL AND built = "
                        + falseLiteral()
                        + " "
                        + "AND deleted = "
                        + falseLiteral()
                        + " "
                        + "ORDER BY group_name ASC, id ASC";
        Map<String, List<BufferEntry>> grouped = new LinkedHashMap<>();
        for (BufferEntry entry :
                JdbcExecutor.queryList(
                        dataSource,
                        sql,
                        this::mapEntry,
                        scope.userId(),
                        scope.agentId(),
                        insightTypeName)) {
            grouped.computeIfAbsent(entry.groupName(), key -> new java.util.ArrayList<>())
                    .add(entry);
        }
        return Map.copyOf(grouped);
    }

    @Override
    public Set<String> listGroups(MemoryId memoryId, String insightTypeName) {
        ScopeContext scope = scopeOf(memoryId);
        String sql =
                "SELECT DISTINCT group_name "
                        + "FROM memory_insight_buffer "
                        + "WHERE user_id = ? AND agent_id = ? AND insight_type_name = ? "
                        + "AND group_name IS NOT NULL "
                        + "AND deleted = "
                        + falseLiteral()
                        + " "
                        + "ORDER BY group_name ASC";
        return JdbcExecutor.queryList(
                        dataSource,
                        sql,
                        resultSet -> resultSet.getString(1),
                        scope.userId(),
                        scope.agentId(),
                        insightTypeName)
                .stream()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public boolean hasWork(MemoryId memoryId, String insightTypeName) {
        ScopeContext scope = scopeOf(memoryId);
        String sql =
                "SELECT COUNT(*) "
                        + "FROM memory_insight_buffer "
                        + "WHERE user_id = ? AND agent_id = ? AND insight_type_name = ? "
                        + "AND built = "
                        + falseLiteral()
                        + " "
                        + "AND deleted = "
                        + falseLiteral();
        return JdbcExecutor.queryCount(
                        dataSource, sql, scope.userId(), scope.agentId(), insightTypeName)
                > 0;
    }

    protected abstract String appendSql();

    protected abstract String falseLiteral();

    protected abstract String trueLiteral();

    private void bindScope(
            PreparedStatement statement, ScopeContext scope, String insightTypeName, long itemId)
            throws SQLException {
        statement.setString(1, scope.userId());
        statement.setString(2, scope.agentId());
        statement.setString(3, scope.memoryId());
        statement.setString(4, insightTypeName);
        statement.setLong(5, itemId);
    }

    private BufferEntry mapEntry(ResultSet resultSet) throws SQLException {
        return new BufferEntry(
                resultSet.getLong("item_id"),
                resultSet.getString("group_name"),
                resultSet.getBoolean("built"));
    }

    private Object[] paramsWithItemIds(
            String groupName, ScopeContext scope, String insightTypeName, List<Long> itemIds) {
        Object[] params = new Object[4 + itemIds.size()];
        params[0] = groupName;
        params[1] = scope.userId();
        params[2] = scope.agentId();
        params[3] = insightTypeName;
        for (int i = 0; i < itemIds.size(); i++) {
            params[4 + i] = itemIds.get(i);
        }
        return params;
    }

    private Object[] paramsWithItemIds(
            ScopeContext scope, String insightTypeName, List<Long> itemIds) {
        Object[] params = new Object[3 + itemIds.size()];
        params[0] = scope.userId();
        params[1] = scope.agentId();
        params[2] = insightTypeName;
        for (int i = 0; i < itemIds.size(); i++) {
            params[3 + i] = itemIds.get(i);
        }
        return params;
    }

    private String placeholders(int size) {
        return String.join(", ", java.util.Collections.nCopies(size, "?"));
    }

    private ScopeContext scopeOf(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        return new ScopeContext(
                memoryId.getAttribute("userId"),
                memoryId.getAttribute("agentId"),
                memoryId.toIdentifier());
    }

    private record ScopeContext(String userId, String agentId, String memoryId) {}
}
