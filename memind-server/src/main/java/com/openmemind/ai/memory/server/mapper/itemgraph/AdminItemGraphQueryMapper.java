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
package com.openmemind.ai.memory.server.mapper.itemgraph;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryEntityCooccurrenceDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryGraphEntityAliasDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryGraphEntityDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemEntityMentionDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemGraphBatchDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryItemLinkDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryEntityCooccurrenceMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryGraphEntityAliasMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryGraphEntityMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemEntityMentionMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemGraphBatchMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemLinkMapper;
import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.itemgraph.query.ItemGraphPageQueries;
import com.openmemind.ai.memory.server.domain.itemgraph.view.GraphEntityDetailView;
import com.openmemind.ai.memory.server.domain.itemgraph.view.ItemGraphViews;
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

public interface AdminItemGraphQueryMapper {

    ItemGraphViews.SummaryView summary(String memoryId);

    PageResponse<ItemGraphViews.EntityView> pageEntities(
            ItemGraphPageQueries.EntityPageQuery query);

    Optional<GraphEntityDetailView> findEntity(Integer id);

    PageResponse<ItemGraphViews.AliasView> pageAliases(ItemGraphPageQueries.AliasPageQuery query);

    List<ItemGraphViews.AliasView> findAliases(Collection<Integer> ids);

    PageResponse<ItemGraphViews.MentionView> pageMentions(
            ItemGraphPageQueries.MentionPageQuery query);

    List<ItemGraphViews.MentionView> findMentions(Collection<Integer> ids);

    PageResponse<ItemGraphViews.ItemLinkView> pageItemLinks(
            ItemGraphPageQueries.ItemLinkPageQuery query);

    List<ItemGraphViews.ItemLinkView> findItemLinks(Collection<Integer> ids);

    PageResponse<ItemGraphViews.CooccurrenceView> pageCooccurrences(
            ItemGraphPageQueries.CooccurrencePageQuery query);

    List<ItemGraphViews.CooccurrenceView> findCooccurrences(Collection<Integer> ids);

    PageResponse<ItemGraphViews.BatchView> pageBatches(ItemGraphPageQueries.BatchPageQuery query);

    long countEntityOverlapItemLinks(String memoryId, Collection<String> entityKeys);

    int physicalDeleteScopedEntities(String memoryId, Collection<String> entityKeys);

    int physicalDeleteScopedAliases(String memoryId, Collection<String> entityKeys);

    int physicalDeleteScopedMentions(String memoryId, Collection<String> entityKeys);

    int physicalDeleteScopedCooccurrences(String memoryId, Collection<String> entityKeys);

    int physicalDeleteAliases(Collection<Integer> ids);

    int physicalDeleteMentions(Collection<Integer> ids);

    int physicalDeleteItemLinks(Collection<Integer> ids);

    int physicalDeleteCooccurrences(Collection<Integer> ids);
}

@Component
final class MybatisAdminItemGraphQueryMapper implements AdminItemGraphQueryMapper {

    private static final String TABLE_GRAPH_ENTITY = "memory_graph_entity";
    private static final String TABLE_GRAPH_ALIAS = "memory_graph_entity_alias";
    private static final String TABLE_GRAPH_MENTION = "memory_item_entity_mention";
    private static final String TABLE_GRAPH_ITEM_LINK = "memory_item_link";
    private static final String TABLE_GRAPH_COOCCURRENCE = "memory_entity_cooccurrence";
    private static final String TABLE_GRAPH_BATCH = "memory_item_graph_batch";
    private static final List<String> BATCH_STATES =
            List.of("PENDING", "COMMITTED", "REPAIR_REQUIRED");

    private final MemoryGraphEntityMapper entityMapper;
    private final MemoryGraphEntityAliasMapper aliasMapper;
    private final MemoryItemEntityMentionMapper mentionMapper;
    private final MemoryItemLinkMapper itemLinkMapper;
    private final MemoryEntityCooccurrenceMapper cooccurrenceMapper;
    private final MemoryItemGraphBatchMapper batchMapper;
    private final JdbcTemplate jdbcTemplate;

    MybatisAdminItemGraphQueryMapper(
            MemoryGraphEntityMapper entityMapper,
            MemoryGraphEntityAliasMapper aliasMapper,
            MemoryItemEntityMentionMapper mentionMapper,
            MemoryItemLinkMapper itemLinkMapper,
            MemoryEntityCooccurrenceMapper cooccurrenceMapper,
            MemoryItemGraphBatchMapper batchMapper,
            JdbcTemplate jdbcTemplate) {
        this.entityMapper = entityMapper;
        this.aliasMapper = aliasMapper;
        this.mentionMapper = mentionMapper;
        this.itemLinkMapper = itemLinkMapper;
        this.cooccurrenceMapper = cooccurrenceMapper;
        this.batchMapper = batchMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ItemGraphViews.SummaryView summary(String memoryId) {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId(memoryId);
        return new ItemGraphViews.SummaryView(
                entityMapper.selectCount(
                        memoryIdWrapper(
                                MemoryGraphEntityDO.class,
                                scope,
                                MemoryGraphEntityDO::getMemoryId)),
                aliasMapper.selectCount(
                        memoryIdWrapper(
                                MemoryGraphEntityAliasDO.class,
                                scope,
                                MemoryGraphEntityAliasDO::getMemoryId)),
                mentionMapper.selectCount(
                        memoryIdWrapper(
                                MemoryItemEntityMentionDO.class,
                                scope,
                                MemoryItemEntityMentionDO::getMemoryId)),
                itemLinkMapper.selectCount(
                        memoryIdWrapper(
                                MemoryItemLinkDO.class, scope, MemoryItemLinkDO::getMemoryId)),
                cooccurrenceMapper.selectCount(
                        memoryIdWrapper(
                                MemoryEntityCooccurrenceDO.class,
                                scope,
                                MemoryEntityCooccurrenceDO::getMemoryId)),
                groupedCounts(TABLE_GRAPH_BATCH, "state", scope),
                groupedCounts(TABLE_GRAPH_ITEM_LINK, "link_type", scope),
                groupedCounts(TABLE_GRAPH_ENTITY, "entity_type", scope));
    }

    @Override
    public PageResponse<ItemGraphViews.EntityView> pageEntities(
            ItemGraphPageQueries.EntityPageQuery query) {
        Page<MemoryGraphEntityDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper =
                memoryIdWrapper(
                        MemoryGraphEntityDO.class,
                        AdminMemoryScope.fromMemoryId(query.memoryId()),
                        MemoryGraphEntityDO::getMemoryId);
        if (StringUtils.hasText(query.entityType())) {
            wrapper.eq(MemoryGraphEntityDO::getEntityType, query.entityType());
        }
        if (StringUtils.hasText(query.q())) {
            String like = query.q().trim();
            wrapper.and(
                    nested ->
                            nested.like(MemoryGraphEntityDO::getEntityKey, like)
                                    .or()
                                    .like(MemoryGraphEntityDO::getDisplayName, like));
        }
        wrapper.orderByDesc(MemoryGraphEntityDO::getCreatedAt, MemoryGraphEntityDO::getId);
        Page<MemoryGraphEntityDO> result = entityMapper.selectPage(page, wrapper);
        return pageResponse(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(MybatisAdminItemGraphQueryMapper::toEntityView)
                        .toList());
    }

    @Override
    public Optional<GraphEntityDetailView> findEntity(Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        MemoryGraphEntityDO entity = entityMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        var aliasWrapper =
                Wrappers.lambdaQuery(MemoryGraphEntityAliasDO.class)
                        .eq(MemoryGraphEntityAliasDO::getMemoryId, entity.getMemoryId())
                        .eq(MemoryGraphEntityAliasDO::getEntityKey, entity.getEntityKey())
                        .orderByDesc(
                                MemoryGraphEntityAliasDO::getEvidenceCount,
                                MemoryGraphEntityAliasDO::getCreatedAt,
                                MemoryGraphEntityAliasDO::getId);
        List<ItemGraphViews.AliasView> aliases =
                aliasMapper.selectList(aliasWrapper).stream()
                        .map(MybatisAdminItemGraphQueryMapper::toAliasView)
                        .toList();
        var mentionWrapper =
                Wrappers.lambdaQuery(MemoryItemEntityMentionDO.class)
                        .eq(MemoryItemEntityMentionDO::getMemoryId, entity.getMemoryId())
                        .eq(MemoryItemEntityMentionDO::getEntityKey, entity.getEntityKey());
        long mentionCount = mentionMapper.selectCount(mentionWrapper);
        List<Long> topMentionedItemIds =
                topMentionedItemIds(entity.getMemoryId(), entity.getEntityKey());
        var cooccurrenceWrapper =
                Wrappers.lambdaQuery(MemoryEntityCooccurrenceDO.class)
                        .eq(MemoryEntityCooccurrenceDO::getMemoryId, entity.getMemoryId())
                        .and(
                                nested ->
                                        nested.eq(
                                                        MemoryEntityCooccurrenceDO
                                                                ::getLeftEntityKey,
                                                        entity.getEntityKey())
                                                .or()
                                                .eq(
                                                        MemoryEntityCooccurrenceDO
                                                                ::getRightEntityKey,
                                                        entity.getEntityKey()))
                        .orderByDesc(
                                MemoryEntityCooccurrenceDO::getCooccurrenceCount,
                                MemoryEntityCooccurrenceDO::getCreatedAt,
                                MemoryEntityCooccurrenceDO::getId)
                        .last("LIMIT 10");
        List<ItemGraphViews.CooccurrenceView> topCooccurrences =
                cooccurrenceMapper.selectList(cooccurrenceWrapper).stream()
                        .map(MybatisAdminItemGraphQueryMapper::toCooccurrenceView)
                        .toList();
        return Optional.of(
                new GraphEntityDetailView(
                        toEntityView(entity),
                        aliases,
                        mentionCount,
                        topMentionedItemIds,
                        topCooccurrences,
                        countEntityOverlapItemLinks(
                                entity.getMemoryId(), List.of(entity.getEntityKey()))));
    }

    @Override
    public PageResponse<ItemGraphViews.AliasView> pageAliases(
            ItemGraphPageQueries.AliasPageQuery query) {
        Page<MemoryGraphEntityAliasDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper =
                memoryIdWrapper(
                        MemoryGraphEntityAliasDO.class,
                        AdminMemoryScope.fromMemoryId(query.memoryId()),
                        MemoryGraphEntityAliasDO::getMemoryId);
        if (StringUtils.hasText(query.entityKey())) {
            wrapper.eq(MemoryGraphEntityAliasDO::getEntityKey, query.entityKey());
        }
        if (StringUtils.hasText(query.q())) {
            wrapper.like(MemoryGraphEntityAliasDO::getNormalizedAlias, query.q().trim());
        }
        wrapper.orderByDesc(
                MemoryGraphEntityAliasDO::getEvidenceCount,
                MemoryGraphEntityAliasDO::getCreatedAt,
                MemoryGraphEntityAliasDO::getId);
        Page<MemoryGraphEntityAliasDO> result = aliasMapper.selectPage(page, wrapper);
        return pageResponse(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(MybatisAdminItemGraphQueryMapper::toAliasView)
                        .toList());
    }

    @Override
    public List<ItemGraphViews.AliasView> findAliases(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var wrapper =
                Wrappers.lambdaQuery(MemoryGraphEntityAliasDO.class)
                        .in(MemoryGraphEntityAliasDO::getId, ids);
        return aliasMapper.selectList(wrapper).stream()
                .map(MybatisAdminItemGraphQueryMapper::toAliasView)
                .toList();
    }

    @Override
    public PageResponse<ItemGraphViews.MentionView> pageMentions(
            ItemGraphPageQueries.MentionPageQuery query) {
        Page<MemoryItemEntityMentionDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper =
                memoryIdWrapper(
                        MemoryItemEntityMentionDO.class,
                        AdminMemoryScope.fromMemoryId(query.memoryId()),
                        MemoryItemEntityMentionDO::getMemoryId);
        if (query.itemId() != null) {
            wrapper.eq(MemoryItemEntityMentionDO::getItemId, query.itemId());
        }
        if (StringUtils.hasText(query.entityKey())) {
            wrapper.eq(MemoryItemEntityMentionDO::getEntityKey, query.entityKey());
        }
        wrapper.orderByDesc(
                MemoryItemEntityMentionDO::getCreatedAt, MemoryItemEntityMentionDO::getId);
        Page<MemoryItemEntityMentionDO> result = mentionMapper.selectPage(page, wrapper);
        return pageResponse(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(MybatisAdminItemGraphQueryMapper::toMentionView)
                        .toList());
    }

    @Override
    public List<ItemGraphViews.MentionView> findMentions(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var wrapper =
                Wrappers.lambdaQuery(MemoryItemEntityMentionDO.class)
                        .in(MemoryItemEntityMentionDO::getId, ids);
        return mentionMapper.selectList(wrapper).stream()
                .map(MybatisAdminItemGraphQueryMapper::toMentionView)
                .toList();
    }

    @Override
    public PageResponse<ItemGraphViews.ItemLinkView> pageItemLinks(
            ItemGraphPageQueries.ItemLinkPageQuery query) {
        Page<MemoryItemLinkDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper =
                memoryIdWrapper(
                        MemoryItemLinkDO.class,
                        AdminMemoryScope.fromMemoryId(query.memoryId()),
                        MemoryItemLinkDO::getMemoryId);
        if (query.itemId() != null) {
            wrapper.and(
                    nested ->
                            nested.eq(MemoryItemLinkDO::getSourceItemId, query.itemId())
                                    .or()
                                    .eq(MemoryItemLinkDO::getTargetItemId, query.itemId()));
        }
        if (StringUtils.hasText(query.linkType())) {
            wrapper.eq(MemoryItemLinkDO::getLinkType, query.linkType());
        }
        if (StringUtils.hasText(query.evidenceSource())) {
            wrapper.eq(MemoryItemLinkDO::getEvidenceSource, query.evidenceSource());
        }
        wrapper.orderByDesc(MemoryItemLinkDO::getCreatedAt, MemoryItemLinkDO::getId);
        Page<MemoryItemLinkDO> result = itemLinkMapper.selectPage(page, wrapper);
        return pageResponse(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(MybatisAdminItemGraphQueryMapper::toItemLinkView)
                        .toList());
    }

    @Override
    public List<ItemGraphViews.ItemLinkView> findItemLinks(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var wrapper = Wrappers.lambdaQuery(MemoryItemLinkDO.class).in(MemoryItemLinkDO::getId, ids);
        return itemLinkMapper.selectList(wrapper).stream()
                .map(MybatisAdminItemGraphQueryMapper::toItemLinkView)
                .toList();
    }

    @Override
    public PageResponse<ItemGraphViews.CooccurrenceView> pageCooccurrences(
            ItemGraphPageQueries.CooccurrencePageQuery query) {
        Page<MemoryEntityCooccurrenceDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper =
                memoryIdWrapper(
                        MemoryEntityCooccurrenceDO.class,
                        AdminMemoryScope.fromMemoryId(query.memoryId()),
                        MemoryEntityCooccurrenceDO::getMemoryId);
        if (StringUtils.hasText(query.entityKey())) {
            wrapper.and(
                    nested ->
                            nested.eq(
                                            MemoryEntityCooccurrenceDO::getLeftEntityKey,
                                            query.entityKey())
                                    .or()
                                    .eq(
                                            MemoryEntityCooccurrenceDO::getRightEntityKey,
                                            query.entityKey()));
        }
        wrapper.orderByDesc(
                MemoryEntityCooccurrenceDO::getCooccurrenceCount,
                MemoryEntityCooccurrenceDO::getCreatedAt,
                MemoryEntityCooccurrenceDO::getId);
        Page<MemoryEntityCooccurrenceDO> result = cooccurrenceMapper.selectPage(page, wrapper);
        return pageResponse(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(MybatisAdminItemGraphQueryMapper::toCooccurrenceView)
                        .toList());
    }

    @Override
    public List<ItemGraphViews.CooccurrenceView> findCooccurrences(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var wrapper =
                Wrappers.lambdaQuery(MemoryEntityCooccurrenceDO.class)
                        .in(MemoryEntityCooccurrenceDO::getId, ids);
        return cooccurrenceMapper.selectList(wrapper).stream()
                .map(MybatisAdminItemGraphQueryMapper::toCooccurrenceView)
                .toList();
    }

    @Override
    public PageResponse<ItemGraphViews.BatchView> pageBatches(
            ItemGraphPageQueries.BatchPageQuery query) {
        Page<MemoryItemGraphBatchDO> page = new Page<>(query.pageNo(), query.pageSize());
        var wrapper =
                memoryIdWrapper(
                        MemoryItemGraphBatchDO.class,
                        AdminMemoryScope.fromMemoryId(query.memoryId()),
                        MemoryItemGraphBatchDO::getMemoryId);
        if (StringUtils.hasText(query.state())) {
            String state = query.state().trim();
            if (!BATCH_STATES.contains(state)) {
                throw new IllegalArgumentException(
                        "graph batch state must be PENDING, COMMITTED, or REPAIR_REQUIRED");
            }
            wrapper.eq(MemoryItemGraphBatchDO::getState, state);
        }
        wrapper.orderByDesc(MemoryItemGraphBatchDO::getCreatedAt, MemoryItemGraphBatchDO::getId);
        Page<MemoryItemGraphBatchDO> result = batchMapper.selectPage(page, wrapper);
        return pageResponse(
                query.pageNo(),
                query.pageSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(MybatisAdminItemGraphQueryMapper::toBatchView)
                        .toList());
    }

    @Override
    public long countEntityOverlapItemLinks(String memoryId, Collection<String> entityKeys) {
        List<Long> itemIds = mentionedItemIds(memoryId, entityKeys);
        if (itemIds.isEmpty()) {
            return 0;
        }
        String itemInClause = String.join(",", Collections.nCopies(itemIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(memoryId);
        args.add("SEMANTIC");
        args.add("entity_overlap");
        args.addAll(itemIds);
        args.addAll(itemIds);
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM memory_item_link "
                                + "WHERE deleted = 0 AND memory_id = ? AND link_type = ? "
                                + "AND evidence_source = ? "
                                + "AND (source_item_id IN ("
                                + itemInClause
                                + ") OR target_item_id IN ("
                                + itemInClause
                                + "))",
                        Long.class,
                        args.toArray());
        return count == null ? 0 : count;
    }

    @Override
    public int physicalDeleteScopedEntities(String memoryId, Collection<String> entityKeys) {
        return physicalDeleteScopedEntityRows(TABLE_GRAPH_ENTITY, memoryId, entityKeys);
    }

    @Override
    public int physicalDeleteScopedAliases(String memoryId, Collection<String> entityKeys) {
        return physicalDeleteScopedEntityRows(TABLE_GRAPH_ALIAS, memoryId, entityKeys);
    }

    @Override
    public int physicalDeleteScopedMentions(String memoryId, Collection<String> entityKeys) {
        return physicalDeleteScopedEntityRows(TABLE_GRAPH_MENTION, memoryId, entityKeys);
    }

    @Override
    public int physicalDeleteScopedCooccurrences(String memoryId, Collection<String> entityKeys) {
        if (entityKeys == null || entityKeys.isEmpty()) {
            return 0;
        }
        String inClause = String.join(",", Collections.nCopies(entityKeys.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(memoryId);
        args.addAll(entityKeys);
        args.addAll(entityKeys);
        return jdbcTemplate.update(
                "DELETE FROM "
                        + TABLE_GRAPH_COOCCURRENCE
                        + " WHERE memory_id = ? AND (left_entity_key IN ("
                        + inClause
                        + ") OR right_entity_key IN ("
                        + inClause
                        + "))",
                args.toArray());
    }

    @Override
    public int physicalDeleteAliases(Collection<Integer> ids) {
        return physicalDeleteByIds(TABLE_GRAPH_ALIAS, ids);
    }

    @Override
    public int physicalDeleteMentions(Collection<Integer> ids) {
        return physicalDeleteByIds(TABLE_GRAPH_MENTION, ids);
    }

    @Override
    public int physicalDeleteItemLinks(Collection<Integer> ids) {
        return physicalDeleteByIds(TABLE_GRAPH_ITEM_LINK, ids);
    }

    @Override
    public int physicalDeleteCooccurrences(Collection<Integer> ids) {
        return physicalDeleteByIds(TABLE_GRAPH_COOCCURRENCE, ids);
    }

    private static <T>
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> memoryIdWrapper(
                    Class<T> entityClass, AdminMemoryScope scope, SFunction<T, ?> memoryIdColumn) {
        var wrapper = Wrappers.lambdaQuery(entityClass);
        scope.applyToMemoryIdQuery(wrapper, memoryIdColumn);
        return wrapper;
    }

    private List<ItemGraphViews.NamedCount> groupedCounts(
            String tableName, String columnName, AdminMemoryScope scope) {
        String expression = "COALESCE(NULLIF(" + columnName + ", ''), 'unknown')";
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(expression).append(" AS name, COUNT(*) AS count FROM ");
        sql.append(tableName).append(" WHERE deleted = 0");
        appendMemoryIdScope(sql, args, scope);
        sql.append(" GROUP BY ").append(expression).append(" ORDER BY count DESC, name ASC");
        return jdbcTemplate.query(
                sql.toString(), MybatisAdminItemGraphQueryMapper::toNamedCount, args.toArray());
    }

    private List<Long> topMentionedItemIds(String memoryId, String entityKey) {
        return jdbcTemplate.queryForList(
                """
                SELECT item_id
                FROM memory_item_entity_mention
                WHERE deleted = 0 AND memory_id = ? AND entity_key = ?
                GROUP BY item_id
                ORDER BY COUNT(*) DESC, item_id ASC
                LIMIT 10
                """,
                Long.class,
                memoryId,
                entityKey);
    }

    private List<Long> mentionedItemIds(String memoryId, Collection<String> entityKeys) {
        if (entityKeys == null || entityKeys.isEmpty()) {
            return List.of();
        }
        String inClause = String.join(",", Collections.nCopies(entityKeys.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(memoryId);
        args.addAll(entityKeys);
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT item_id FROM memory_item_entity_mention "
                        + "WHERE deleted = 0 AND memory_id = ? AND entity_key IN ("
                        + inClause
                        + ")",
                Long.class,
                args.toArray());
    }

    private int physicalDeleteByIds(String tableName, Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String inClause = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.update(
                "DELETE FROM " + tableName + " WHERE id IN (" + inClause + ")", ids.toArray());
    }

    private int physicalDeleteScopedEntityRows(
            String tableName, String memoryId, Collection<String> entityKeys) {
        if (entityKeys == null || entityKeys.isEmpty()) {
            return 0;
        }
        String inClause = String.join(",", Collections.nCopies(entityKeys.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(memoryId);
        args.addAll(entityKeys);
        return jdbcTemplate.update(
                "DELETE FROM "
                        + tableName
                        + " WHERE memory_id = ? AND entity_key IN ("
                        + inClause
                        + ")",
                args.toArray());
    }

    private static void appendMemoryIdScope(
            StringBuilder sql, List<Object> args, AdminMemoryScope scope) {
        if (!scope.present()) {
            return;
        }
        sql.append(" AND memory_id = ?");
        args.add(scope.memoryId());
    }

    private static <T> PageResponse<T> pageResponse(
            int pageNo, int pageSize, long total, List<T> items) {
        return new PageResponse<>(pageNo, pageSize, total, items);
    }

    private static ItemGraphViews.EntityView toEntityView(MemoryGraphEntityDO dataObject) {
        return new ItemGraphViews.EntityView(
                dataObject.getId(),
                dataObject.getMemoryId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getEntityKey(),
                dataObject.getDisplayName(),
                dataObject.getEntityType(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static ItemGraphViews.AliasView toAliasView(MemoryGraphEntityAliasDO dataObject) {
        return new ItemGraphViews.AliasView(
                dataObject.getId(),
                dataObject.getMemoryId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getEntityKey(),
                dataObject.getEntityType(),
                dataObject.getNormalizedAlias(),
                dataObject.getEvidenceCount(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static ItemGraphViews.MentionView toMentionView(MemoryItemEntityMentionDO dataObject) {
        return new ItemGraphViews.MentionView(
                dataObject.getId(),
                dataObject.getMemoryId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getItemId(),
                dataObject.getEntityKey(),
                dataObject.getConfidence(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static ItemGraphViews.ItemLinkView toItemLinkView(MemoryItemLinkDO dataObject) {
        return new ItemGraphViews.ItemLinkView(
                dataObject.getId(),
                dataObject.getMemoryId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getSourceItemId(),
                dataObject.getTargetItemId(),
                dataObject.getLinkType(),
                dataObject.getRelationCode(),
                dataObject.getEvidenceSource(),
                dataObject.getStrength(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static ItemGraphViews.CooccurrenceView toCooccurrenceView(
            MemoryEntityCooccurrenceDO dataObject) {
        return new ItemGraphViews.CooccurrenceView(
                dataObject.getId(),
                dataObject.getMemoryId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getLeftEntityKey(),
                dataObject.getRightEntityKey(),
                dataObject.getCooccurrenceCount(),
                dataObject.getMetadata(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static ItemGraphViews.BatchView toBatchView(MemoryItemGraphBatchDO dataObject) {
        return new ItemGraphViews.BatchView(
                dataObject.getId(),
                dataObject.getMemoryId(),
                dataObject.getUserId(),
                dataObject.getAgentId(),
                dataObject.getExtractionBatchId(),
                dataObject.getState(),
                dataObject.getErrorMessage(),
                dataObject.getRetryPromotionSupported(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt());
    }

    private static ItemGraphViews.NamedCount toNamedCount(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new ItemGraphViews.NamedCount(
                resultSet.getString("name"), resultSet.getLong("count"));
    }
}
