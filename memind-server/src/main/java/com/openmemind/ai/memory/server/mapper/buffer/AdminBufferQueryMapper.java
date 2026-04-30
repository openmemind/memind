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
package com.openmemind.ai.memory.server.mapper.buffer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.InsightBufferDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.ConversationBufferMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.InsightBufferMapper;
import com.openmemind.ai.memory.server.domain.buffer.query.ConversationBufferPageQuery;
import com.openmemind.ai.memory.server.domain.buffer.query.InsightBufferPageQuery;
import com.openmemind.ai.memory.server.domain.buffer.view.ConversationBufferView;
import com.openmemind.ai.memory.server.domain.buffer.view.InsightBufferGroupView;
import com.openmemind.ai.memory.server.domain.buffer.view.InsightBufferView;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.support.AdminMemoryScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

public interface AdminBufferQueryMapper {

    PageResponse<ConversationBufferView> pageConversations(ConversationBufferPageQuery query);

    Optional<ConversationBufferView> findConversation(Long id);

    List<ConversationBufferView> findConversations(Collection<Long> ids);

    int markConversationsExtracted(List<Long> ids);

    int deleteConversations(Collection<Long> ids);

    PageResponse<InsightBufferView> pageInsights(InsightBufferPageQuery query);

    List<InsightBufferView> findInsights(Collection<Integer> ids);

    List<InsightBufferGroupView> listInsightGroups(String memoryId, String insightTypeName);

    int updateInsightGroup(Collection<Integer> ids, String groupName);

    int updateInsightBuilt(Collection<Integer> ids, Boolean built);

    int physicalDeleteInsightBuffers(Collection<Integer> ids);
}

@Component
final class MybatisAdminBufferQueryMapper implements AdminBufferQueryMapper {

    private final ConversationBufferMapper conversationBufferMapper;
    private final InsightBufferMapper insightBufferMapper;
    private final JdbcTemplate jdbcTemplate;

    MybatisAdminBufferQueryMapper(
            ConversationBufferMapper conversationBufferMapper,
            InsightBufferMapper insightBufferMapper,
            JdbcTemplate jdbcTemplate) {
        this.conversationBufferMapper = conversationBufferMapper;
        this.insightBufferMapper = insightBufferMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResponse<ConversationBufferView> pageConversations(
            ConversationBufferPageQuery query) {
        Page<ConversationBufferDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper = Wrappers.lambdaQuery(ConversationBufferDO.class);
        AdminMemoryScope.fromMemoryId(query.memoryId())
                .applyToUserAgentQuery(
                        wrapper, ConversationBufferDO::getUserId, ConversationBufferDO::getAgentId);
        if (StringUtils.hasText(query.sessionId())) {
            wrapper.eq(ConversationBufferDO::getSessionId, query.sessionId());
        }
        applyConversationState(wrapper, query.state());
        wrapper.orderByDesc(ConversationBufferDO::getCreatedAt, ConversationBufferDO::getId);
        Page<ConversationBufferDO> result = conversationBufferMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(MybatisAdminBufferQueryMapper::toConversationView)
                        .toList());
    }

    @Override
    public Optional<ConversationBufferView> findConversation(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(conversationBufferMapper.selectById(id))
                .map(MybatisAdminBufferQueryMapper::toConversationView);
    }

    @Override
    public List<ConversationBufferView> findConversations(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var wrapper =
                Wrappers.lambdaQuery(ConversationBufferDO.class)
                        .in(ConversationBufferDO::getId, ids);
        return conversationBufferMapper.selectList(wrapper).stream()
                .map(MybatisAdminBufferQueryMapper::toConversationView)
                .toList();
    }

    @Override
    public int markConversationsExtracted(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return conversationBufferMapper.markExtractedByIds(ids);
    }

    @Override
    public int deleteConversations(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return conversationBufferMapper.deleteBatchIds(ids);
    }

    @Override
    public PageResponse<InsightBufferView> pageInsights(InsightBufferPageQuery query) {
        Page<InsightBufferDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper = Wrappers.lambdaQuery(InsightBufferDO.class);
        AdminMemoryScope.fromMemoryId(query.memoryId())
                .applyToUserAgentQuery(
                        wrapper, InsightBufferDO::getUserId, InsightBufferDO::getAgentId);
        if (StringUtils.hasText(query.insightTypeName())) {
            wrapper.eq(InsightBufferDO::getInsightTypeName, query.insightTypeName());
        }
        applyInsightState(wrapper, query.state());
        wrapper.orderByDesc(InsightBufferDO::getCreatedAt, InsightBufferDO::getId);
        Page<InsightBufferDO> result = insightBufferMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(MybatisAdminBufferQueryMapper::toInsightView)
                        .toList());
    }

    @Override
    public List<InsightBufferView> findInsights(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var wrapper = Wrappers.lambdaQuery(InsightBufferDO.class).in(InsightBufferDO::getId, ids);
        return insightBufferMapper.selectList(wrapper).stream()
                .map(MybatisAdminBufferQueryMapper::toInsightView)
                .toList();
    }

    @Override
    public List<InsightBufferGroupView> listInsightGroups(String memoryId, String insightTypeName) {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId(memoryId);
        List<Object> args = new ArrayList<>();
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT memory_id, insight_type_name, group_name, COUNT(*) AS total,
                               SUM(CASE WHEN built = 0 THEN 1 ELSE 0 END) AS unbuilt,
                               SUM(CASE WHEN built = 1 THEN 1 ELSE 0 END) AS built
                        FROM memory_insight_buffer
                        WHERE deleted = 0
                        """);
        if (scope.present()) {
            sql.append(" AND user_id = ? AND agent_id = ?");
            args.add(scope.userId());
            args.add(scope.databaseAgentId());
        }
        if (StringUtils.hasText(insightTypeName)) {
            sql.append(" AND insight_type_name = ?");
            args.add(insightTypeName);
        }
        sql.append(
                """
                 GROUP BY memory_id, insight_type_name, group_name
                 ORDER BY total DESC, memory_id ASC, insight_type_name ASC, group_name ASC
                """);
        return jdbcTemplate.query(
                sql.toString(), MybatisAdminBufferQueryMapper::toInsightGroupView, args.toArray());
    }

    @Override
    public int updateInsightGroup(Collection<Integer> ids, String groupName) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        var wrapper =
                Wrappers.lambdaUpdate(InsightBufferDO.class)
                        .set(InsightBufferDO::getGroupName, groupName)
                        .in(InsightBufferDO::getId, ids);
        return insightBufferMapper.update(wrapper);
    }

    @Override
    public int updateInsightBuilt(Collection<Integer> ids, Boolean built) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        var wrapper =
                Wrappers.lambdaUpdate(InsightBufferDO.class)
                        .set(InsightBufferDO::getBuilt, built)
                        .in(InsightBufferDO::getId, ids);
        return insightBufferMapper.update(wrapper);
    }

    @Override
    public int physicalDeleteInsightBuffers(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String inClause = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.update(
                "DELETE FROM memory_insight_buffer WHERE id IN (" + inClause + ")", ids.toArray());
    }

    private static void applyConversationState(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConversationBufferDO>
                    wrapper,
            String state) {
        String normalized = StringUtils.hasText(state) ? state.trim() : "pending";
        switch (normalized) {
            case "pending" -> wrapper.eq(ConversationBufferDO::getExtracted, false);
            case "extracted" -> wrapper.eq(ConversationBufferDO::getExtracted, true);
            case "all" -> {
                return;
            }
            default ->
                    throw new IllegalArgumentException(
                            "conversation buffer state must be pending, extracted, or all");
        }
    }

    private static void applyInsightState(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InsightBufferDO>
                    wrapper,
            String state) {
        String normalized = StringUtils.hasText(state) ? state.trim() : "unbuilt";
        switch (normalized) {
            case "unbuilt" -> wrapper.eq(InsightBufferDO::getBuilt, false);
            case "ungrouped" ->
                    wrapper.eq(InsightBufferDO::getBuilt, false)
                            .isNull(InsightBufferDO::getGroupName);
            case "grouped" ->
                    wrapper.eq(InsightBufferDO::getBuilt, false)
                            .isNotNull(InsightBufferDO::getGroupName);
            case "built" -> wrapper.eq(InsightBufferDO::getBuilt, true);
            case "all" -> {
                return;
            }
            default ->
                    throw new IllegalArgumentException(
                            "insight buffer state must be unbuilt, ungrouped, grouped, built, or"
                                    + " all");
        }
    }

    private static ConversationBufferView toConversationView(ConversationBufferDO dataObject) {
        return new ConversationBufferView(
                dataObject.getId(),
                dataObject.getSessionId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getMemoryId(),
                dataObject.getRole(),
                dataObject.getContent(),
                dataObject.getUserName(),
                dataObject.getSourceClient(),
                dataObject.getTimestamp(),
                dataObject.getExtracted(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static InsightBufferView toInsightView(InsightBufferDO dataObject) {
        return new InsightBufferView(
                dataObject.getId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getMemoryId(),
                dataObject.getInsightTypeName(),
                dataObject.getItemId(),
                dataObject.getGroupName(),
                dataObject.getBuilt(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static InsightBufferGroupView toInsightGroupView(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new InsightBufferGroupView(
                resultSet.getString("memory_id"),
                resultSet.getString("insight_type_name"),
                resultSet.getString("group_name"),
                resultSet.getLong("total"),
                resultSet.getLong("unbuilt"),
                resultSet.getLong("built"));
    }
}
