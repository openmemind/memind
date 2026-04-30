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
package com.openmemind.ai.memory.server.mapper.dashboard;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.InsightBufferDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryGraphEntityDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemGraphBatchDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemLinkDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryRawDataDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadIntakeOutboxDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadProjectionDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.ConversationBufferMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.InsightBufferMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryGraphEntityMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemGraphBatchMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemLinkMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryRawDataMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadIntakeOutboxMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadProjectionMapper;
import com.openmemind.ai.memory.server.domain.dashboard.view.AdminDashboardView;
import com.openmemind.ai.memory.server.support.AdminMemoryScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

public interface AdminDashboardQueryMapper {

    AdminDashboardView dashboard(
            String memoryId, int days, boolean graphEnabled, boolean retrievalGraphAssistEnabled);
}

@Component
final class MybatisAdminDashboardQueryMapper implements AdminDashboardQueryMapper {

    private static final String OUTBOX_PENDING = "PENDING";
    private static final String OUTBOX_FAILED = "FAILED";
    private static final String GRAPH_BATCH_REPAIR_REQUIRED = "REPAIR_REQUIRED";

    private final MemoryRawDataMapper rawDataMapper;
    private final MemoryItemMapper itemMapper;
    private final MemoryInsightMapper insightMapper;
    private final MemoryThreadProjectionMapper threadProjectionMapper;
    private final MemoryGraphEntityMapper graphEntityMapper;
    private final MemoryItemLinkMapper itemLinkMapper;
    private final ConversationBufferMapper conversationBufferMapper;
    private final InsightBufferMapper insightBufferMapper;
    private final MemoryThreadIntakeOutboxMapper threadOutboxMapper;
    private final MemoryItemGraphBatchMapper graphBatchMapper;
    private final JdbcTemplate jdbcTemplate;

    MybatisAdminDashboardQueryMapper(
            MemoryRawDataMapper rawDataMapper,
            MemoryItemMapper itemMapper,
            MemoryInsightMapper insightMapper,
            MemoryThreadProjectionMapper threadProjectionMapper,
            MemoryGraphEntityMapper graphEntityMapper,
            MemoryItemLinkMapper itemLinkMapper,
            ConversationBufferMapper conversationBufferMapper,
            InsightBufferMapper insightBufferMapper,
            MemoryThreadIntakeOutboxMapper threadOutboxMapper,
            MemoryItemGraphBatchMapper graphBatchMapper,
            JdbcTemplate jdbcTemplate) {
        this.rawDataMapper = rawDataMapper;
        this.itemMapper = itemMapper;
        this.insightMapper = insightMapper;
        this.threadProjectionMapper = threadProjectionMapper;
        this.graphEntityMapper = graphEntityMapper;
        this.itemLinkMapper = itemLinkMapper;
        this.conversationBufferMapper = conversationBufferMapper;
        this.insightBufferMapper = insightBufferMapper;
        this.threadOutboxMapper = threadOutboxMapper;
        this.graphBatchMapper = graphBatchMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AdminDashboardView dashboard(
            String memoryId, int days, boolean graphEnabled, boolean retrievalGraphAssistEnabled) {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId(memoryId);
        return new AdminDashboardView(
                totals(scope),
                backlog(scope),
                activity(scope, days),
                breakdown(scope),
                healthSignals(scope, graphEnabled, retrievalGraphAssistEnabled));
    }

    private AdminDashboardView.Totals totals(AdminMemoryScope scope) {
        return new AdminDashboardView.Totals(
                countUserAgent(
                        rawDataMapper,
                        MemoryRawDataDO.class,
                        scope,
                        MemoryRawDataDO::getUserId,
                        MemoryRawDataDO::getAgentId),
                countUserAgent(
                        itemMapper,
                        MemoryItemDO.class,
                        scope,
                        MemoryItemDO::getUserId,
                        MemoryItemDO::getAgentId),
                countUserAgent(
                        insightMapper,
                        MemoryInsightDO.class,
                        scope,
                        MemoryInsightDO::getUserId,
                        MemoryInsightDO::getAgentId),
                countMemoryId(
                        threadProjectionMapper,
                        MemoryThreadProjectionDO.class,
                        scope,
                        MemoryThreadProjectionDO::getMemoryId),
                countMemoryId(
                        graphEntityMapper,
                        MemoryGraphEntityDO.class,
                        scope,
                        MemoryGraphEntityDO::getMemoryId),
                countMemoryId(
                        itemLinkMapper,
                        MemoryItemLinkDO.class,
                        scope,
                        MemoryItemLinkDO::getMemoryId));
    }

    private AdminDashboardView.Backlog backlog(AdminMemoryScope scope) {
        var conversationPending =
                userAgentWrapper(
                                ConversationBufferDO.class,
                                scope,
                                ConversationBufferDO::getUserId,
                                ConversationBufferDO::getAgentId)
                        .eq(ConversationBufferDO::getExtracted, false);
        var insightUnbuilt =
                userAgentWrapper(
                                InsightBufferDO.class,
                                scope,
                                InsightBufferDO::getUserId,
                                InsightBufferDO::getAgentId)
                        .eq(InsightBufferDO::getBuilt, false);
        var insightUngrouped =
                userAgentWrapper(
                                InsightBufferDO.class,
                                scope,
                                InsightBufferDO::getUserId,
                                InsightBufferDO::getAgentId)
                        .eq(InsightBufferDO::getBuilt, false)
                        .isNull(InsightBufferDO::getGroupName);
        var outboxPending =
                memoryIdWrapper(
                                MemoryThreadIntakeOutboxDO.class,
                                scope,
                                MemoryThreadIntakeOutboxDO::getMemoryId)
                        .eq(MemoryThreadIntakeOutboxDO::getStatus, OUTBOX_PENDING);
        var outboxFailed =
                memoryIdWrapper(
                                MemoryThreadIntakeOutboxDO.class,
                                scope,
                                MemoryThreadIntakeOutboxDO::getMemoryId)
                        .eq(MemoryThreadIntakeOutboxDO::getStatus, OUTBOX_FAILED);
        var graphBatchRepairRequired =
                memoryIdWrapper(
                                MemoryItemGraphBatchDO.class,
                                scope,
                                MemoryItemGraphBatchDO::getMemoryId)
                        .eq(MemoryItemGraphBatchDO::getState, GRAPH_BATCH_REPAIR_REQUIRED);

        return new AdminDashboardView.Backlog(
                conversationBufferMapper.selectCount(conversationPending),
                insightBufferMapper.selectCount(insightUnbuilt),
                insightBufferMapper.selectCount(insightUngrouped),
                threadOutboxMapper.selectCount(outboxPending),
                threadOutboxMapper.selectCount(outboxFailed),
                graphBatchMapper.selectCount(graphBatchRepairRequired));
    }

    private AdminDashboardView.Activity activity(AdminMemoryScope scope, int days) {
        return new AdminDashboardView.Activity(
                days,
                dailyCounts("memory_raw_data", scope, days),
                dailyCounts("memory_item", scope, days),
                dailyCounts("memory_insight", scope, days));
    }

    private AdminDashboardView.Breakdown breakdown(AdminMemoryScope scope) {
        return new AdminDashboardView.Breakdown(
                groupedUserAgentCounts("memory_raw_data", "source_client", scope),
                groupedUserAgentCounts("memory_raw_data", "type", scope),
                groupedUserAgentCounts("memory_item", "type", scope),
                groupedUserAgentCounts("memory_insight", "type", scope),
                groupedMemoryIdCounts("memory_item_link", "link_type", scope));
    }

    private AdminDashboardView.HealthSignals healthSignals(
            AdminMemoryScope scope, boolean graphEnabled, boolean retrievalGraphAssistEnabled) {
        return new AdminDashboardView.HealthSignals(
                graphEnabled, retrievalGraphAssistEnabled, threadProjectionStates(scope));
    }

    private <T> long countUserAgent(
            BaseMapper<T> mapper,
            Class<T> entityClass,
            AdminMemoryScope scope,
            SFunction<T, ?> userColumn,
            SFunction<T, ?> agentColumn) {
        return mapper.selectCount(userAgentWrapper(entityClass, scope, userColumn, agentColumn));
    }

    private <T> long countMemoryId(
            BaseMapper<T> mapper,
            Class<T> entityClass,
            AdminMemoryScope scope,
            SFunction<T, ?> memoryIdColumn) {
        return mapper.selectCount(memoryIdWrapper(entityClass, scope, memoryIdColumn));
    }

    private static <T>
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> userAgentWrapper(
                    Class<T> entityClass,
                    AdminMemoryScope scope,
                    SFunction<T, ?> userColumn,
                    SFunction<T, ?> agentColumn) {
        var wrapper = Wrappers.lambdaQuery(entityClass);
        scope.applyToUserAgentQuery(wrapper, userColumn, agentColumn);
        return wrapper;
    }

    private static <T>
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> memoryIdWrapper(
                    Class<T> entityClass, AdminMemoryScope scope, SFunction<T, ?> memoryIdColumn) {
        var wrapper = Wrappers.lambdaQuery(entityClass);
        scope.applyToMemoryIdQuery(wrapper, memoryIdColumn);
        return wrapper;
    }

    private List<AdminDashboardView.DailyCount> dailyCounts(
            String tableName, AdminMemoryScope scope, int days) {
        LocalDate startDate = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
        List<Object> args = new ArrayList<>();
        StringBuilder sql =
                new StringBuilder(
                        "SELECT substr(created_at, 1, 10) AS date, COUNT(*) AS count FROM ");
        sql.append(tableName).append(" WHERE deleted = 0 AND created_at >= ?");
        args.add(startDate.toString());
        appendUserAgentScope(sql, args, scope);
        sql.append(" GROUP BY substr(created_at, 1, 10) ORDER BY date ASC");
        return jdbcTemplate.query(
                sql.toString(), MybatisAdminDashboardQueryMapper::toDailyCount, args.toArray());
    }

    private List<AdminDashboardView.NamedCount> groupedUserAgentCounts(
            String tableName, String columnName, AdminMemoryScope scope) {
        return groupedCounts(tableName, columnName, scope, true);
    }

    private List<AdminDashboardView.NamedCount> groupedMemoryIdCounts(
            String tableName, String columnName, AdminMemoryScope scope) {
        return groupedCounts(tableName, columnName, scope, false);
    }

    private List<AdminDashboardView.NamedCount> groupedCounts(
            String tableName, String columnName, AdminMemoryScope scope, boolean userAgentScoped) {
        String expression = "COALESCE(NULLIF(" + columnName + ", ''), 'unknown')";
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(expression).append(" AS name, COUNT(*) AS count FROM ");
        sql.append(tableName).append(" WHERE deleted = 0");
        if (userAgentScoped) {
            appendUserAgentScope(sql, args, scope);
        } else {
            appendMemoryIdScope(sql, args, scope);
        }
        sql.append(" GROUP BY ").append(expression).append(" ORDER BY count DESC, name ASC");
        return jdbcTemplate.query(
                sql.toString(), MybatisAdminDashboardQueryMapper::toNamedCount, args.toArray());
    }

    private List<AdminDashboardView.StateCount> threadProjectionStates(AdminMemoryScope scope) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT COALESCE(NULLIF(projection_state, ''), 'UNKNOWN') AS state,
                               COUNT(*) AS count
                        FROM memory_thread_runtime
                        WHERE 1 = 1
                        """);
        appendMemoryIdScope(sql, args, scope);
        sql.append(" GROUP BY COALESCE(NULLIF(projection_state, ''), 'UNKNOWN')");
        sql.append(" ORDER BY count DESC, state ASC");
        return jdbcTemplate.query(
                sql.toString(), MybatisAdminDashboardQueryMapper::toStateCount, args.toArray());
    }

    private static void appendUserAgentScope(
            StringBuilder sql, List<Object> args, AdminMemoryScope scope) {
        if (!scope.present()) {
            return;
        }
        sql.append(" AND user_id = ? AND agent_id = ?");
        args.add(scope.userId());
        args.add(scope.databaseAgentId());
    }

    private static void appendMemoryIdScope(
            StringBuilder sql, List<Object> args, AdminMemoryScope scope) {
        if (!scope.present()) {
            return;
        }
        sql.append(" AND memory_id = ?");
        args.add(scope.memoryId());
    }

    private static AdminDashboardView.DailyCount toDailyCount(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new AdminDashboardView.DailyCount(
                resultSet.getString("date"), resultSet.getLong("count"));
    }

    private static AdminDashboardView.NamedCount toNamedCount(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new AdminDashboardView.NamedCount(
                resultSet.getString("name"), resultSet.getLong("count"));
    }

    private static AdminDashboardView.StateCount toStateCount(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new AdminDashboardView.StateCount(
                resultSet.getString("state"), resultSet.getLong("count"));
    }
}
