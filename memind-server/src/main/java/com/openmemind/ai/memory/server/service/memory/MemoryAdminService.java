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
package com.openmemind.ai.memory.server.service.memory;

import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.memory.query.MemoryPageQuery;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemoryActivityView;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemorySnapshotResult;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemorySummaryItem;
import com.openmemind.ai.memory.server.domain.memory.view.AdminMemoryWorkspaceView;
import com.openmemind.ai.memory.server.support.AdminMemoryScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MemoryAdminService {

    private final JdbcTemplate jdbcTemplate;

    public MemoryAdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<AdminMemoryWorkspaceView> listMemories(MemoryPageQuery query) {
        List<Object> args = new ArrayList<>();
        StringBuilder where =
                new StringBuilder(
                        """
                        FROM (
                            SELECT memory_id, user_id, agent_id, created_at, updated_at
                            FROM memory_raw_data WHERE deleted = 0
                            UNION ALL
                            SELECT memory_id, user_id, agent_id, created_at, updated_at
                            FROM memory_item WHERE deleted = 0
                            UNION ALL
                            SELECT memory_id, user_id, agent_id, created_at, updated_at
                            FROM memory_insight WHERE deleted = 0
                        ) memory_rows
                        WHERE 1 = 1
                        """);
        if (StringUtils.hasText(query.q())) {
            where.append(" AND (memory_id LIKE ? OR user_id LIKE ? OR agent_id LIKE ?)");
            String like = "%" + query.q().trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        Long total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM (SELECT memory_id " + where + " GROUP BY memory_id)",
                        Long.class,
                        args.toArray());
        long totalItems = total == null ? 0 : total;
        int offset = (query.pageNo() - 1) * query.pageSize();
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.pageSize());
        pageArgs.add(offset);
        List<AdminMemoryWorkspaceView> items =
                jdbcTemplate.query(
                        """
                        SELECT memory_id, user_id, agent_id,
                               MIN(created_at) AS created_at,
                               MAX(updated_at) AS updated_at
                        """
                                + where
                                + """
                                 GROUP BY memory_id, user_id, agent_id
                                 ORDER BY MAX(updated_at) DESC, memory_id ASC
                                 LIMIT ? OFFSET ?
                                """,
                        (rs, rowNum) -> toWorkspace(rs),
                        pageArgs.toArray());
        return new PageResponse<>(query.pageNo(), query.pageSize(), totalItems, items);
    }

    public List<AdminMemoryActivityView> activity(String memoryId) {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId(memoryId);
        List<AdminMemoryActivityView> activity = new ArrayList<>();
        activity.addAll(
                recentActivity(
                        "memory_raw_data", "Raw data ingested", "biz_id", "caption", scope, 5));
        activity.addAll(
                recentActivity(
                        "memory_item", "Memory item extracted", "biz_id", "content", scope, 5));
        activity.addAll(
                recentActivity(
                        "memory_insight", "Insight reasoned", "biz_id", "content", scope, 5));
        return activity.stream()
                .sorted((left, right) -> right.time().compareTo(left.time()))
                .limit(10)
                .toList();
    }

    public AdminMemorySnapshotResult forceSnapshot(String memoryId) {
        AdminMemoryScope.fromMemoryId(memoryId);
        return new AdminMemorySnapshotResult(memoryId, "accepted");
    }

    public List<AdminMemorySummaryItem> itemsSummary(String memoryId) {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId(memoryId);
        long itemCount = countItems(scope);
        long typeCount = distinctItemCount(scope, "type");
        long categoryCount = distinctItemCount(scope, "category");
        long userScopeCount = countItemsByScope(scope, "USER");
        long agentScopeCount = countItemsByScope(scope, "AGENT");
        return List.of(
                new AdminMemorySummaryItem(
                        "Items",
                        String.valueOf(itemCount),
                        userScopeCount + " user / " + agentScopeCount + " agent"),
                new AdminMemorySummaryItem("Types", String.valueOf(typeCount), null),
                new AdminMemorySummaryItem("Categories", String.valueOf(categoryCount), null));
    }

    private AdminMemoryWorkspaceView toWorkspace(ResultSet rs) throws SQLException {
        String memoryId = rs.getString("memory_id");
        String userId = rs.getString("user_id");
        String agentId = rs.getString("agent_id");
        return new AdminMemoryWorkspaceView(
                memoryId,
                memoryId,
                userId,
                agentId,
                "active",
                countRows("memory_raw_data", memoryId),
                countRows("memory_item", memoryId),
                countRows("memory_insight", memoryId),
                new AdminMemoryWorkspaceView.Alerts(0, warningCount(memoryId)),
                rs.getString("updated_at"),
                rs.getString("created_at"));
    }

    private long warningCount(String memoryId) {
        return countRows("memory_item_graph_batch", memoryId, "state", "REPAIR_REQUIRED");
    }

    private long countRows(String tableName, String memoryId) {
        return countRows(tableName, memoryId, null, null);
    }

    private long countRows(String tableName, String memoryId, String column, String value) {
        List<Object> args = new ArrayList<>();
        args.add(memoryId);
        StringBuilder sql =
                new StringBuilder("SELECT COUNT(*) FROM ")
                        .append(tableName)
                        .append(" WHERE deleted = 0 AND memory_id = ?");
        if (StringUtils.hasText(column)) {
            sql.append(" AND ").append(column).append(" = ?");
            args.add(value);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private long countItems(AdminMemoryScope scope) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM memory_item WHERE deleted = 0");
        appendScope(sql, args, scope);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private long countItemsByScope(AdminMemoryScope scope, String itemScope) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM memory_item WHERE deleted = 0");
        appendScope(sql, args, scope);
        sql.append(" AND scope = ?");
        args.add(itemScope);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private long distinctItemCount(AdminMemoryScope scope, String columnName) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql =
                new StringBuilder("SELECT COUNT(DISTINCT ")
                        .append(columnName)
                        .append(") FROM memory_item WHERE deleted = 0");
        appendScope(sql, args, scope);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private List<AdminMemoryActivityView> recentActivity(
            String tableName,
            String title,
            String idColumn,
            String detailColumn,
            AdminMemoryScope scope,
            int limit) {
        List<Object> args = new ArrayList<>();
        args.add(scope.memoryId());
        args.add(limit);
        return jdbcTemplate.query(
                "SELECT "
                        + idColumn
                        + " AS id, "
                        + detailColumn
                        + " AS detail, updated_at FROM "
                        + tableName
                        + " WHERE deleted = 0 AND memory_id = ?"
                        + " ORDER BY updated_at DESC LIMIT ?",
                (rs, rowNum) ->
                        new AdminMemoryActivityView(
                                title,
                                detail(rs.getString("id"), rs.getString("detail")),
                                time(rs.getString("updated_at")),
                                "default"),
                args.toArray());
    }

    private static void appendScope(StringBuilder sql, List<Object> args, AdminMemoryScope scope) {
        if (!scope.present()) {
            return;
        }
        sql.append(" AND user_id = ? AND agent_id = ?");
        args.add(scope.userId());
        args.add(scope.databaseAgentId());
    }

    private static String detail(String id, String detail) {
        return StringUtils.hasText(detail) ? detail : id;
    }

    private static String time(String value) {
        return StringUtils.hasText(value) ? value : Instant.EPOCH.toString();
    }
}
