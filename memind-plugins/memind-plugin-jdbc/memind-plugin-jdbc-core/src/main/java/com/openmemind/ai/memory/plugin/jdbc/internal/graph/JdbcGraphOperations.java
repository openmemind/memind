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
package com.openmemind.ai.memory.plugin.jdbc.internal.graph;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.store.graph.EntityCooccurrence;
import com.openmemind.ai.memory.core.store.graph.GraphEntity;
import com.openmemind.ai.memory.core.store.graph.GraphEntityAlias;
import com.openmemind.ai.memory.core.store.graph.GraphEntityType;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.ItemEntityMention;
import com.openmemind.ai.memory.core.store.graph.ItemLink;
import com.openmemind.ai.memory.core.store.graph.ItemLinkType;
import com.openmemind.ai.memory.plugin.jdbc.internal.jdbi.JdbiFactory;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcPluginException;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JsonCodec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import tools.jackson.core.type.TypeReference;

public class JdbcGraphOperations implements GraphOperations {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP_TYPE =
            new TypeReference<>() {};

    private final DataSource dataSource;
    private final Jdbi jdbi;
    private final JdbcGraphDialect dialect;
    private final JsonCodec jsonCodec = new JsonCodec();

    protected JdbcGraphOperations(
            DataSource dataSource, JdbcGraphDialect dialect, boolean createIfNotExist) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbi = JdbiFactory.create(this.dataSource);
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        ensureSchema(createIfNotExist);
    }

    @Override
    public void applyGraphWritePlan(
            MemoryId memoryId, ExtractionBatchId extractionBatchId, ItemGraphWritePlan writePlan) {
        if (writePlan == null) {
            return;
        }
        inTransaction(
                connection -> {
                    applyGraphWritePlan(connection, memoryId, extractionBatchId, writePlan);
                    return null;
                });
    }

    public void applyGraphWritePlan(
            Connection connection,
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            ItemGraphWritePlan writePlan) {
        if (writePlan == null) {
            return;
        }
        ItemGraphWritePlan normalized = writePlan.normalized();
        upsertEntities(connection, memoryId, normalized.entities());
        upsertItemEntityMentions(connection, memoryId, normalized.mentions());
        upsertItemLinks(connection, memoryId, normalized.toPersistedLinks(memoryId));
        upsertAliasesWithBatchReceipts(
                connection, memoryId, extractionBatchId, normalized.aliases());
        rebuildEntityCooccurrences(connection, memoryId, normalized.affectedEntityKeys());
    }

    @Override
    public boolean supportsTransactionalBatchCommit() {
        return true;
    }

    @Override
    public void upsertEntities(MemoryId memoryId, List<GraphEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        inTransaction(
                connection -> {
                    upsertEntities(connection, memoryId, entities);
                    return null;
                });
    }

    public void upsertEntities(
            Connection connection, MemoryId memoryId, List<GraphEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        ScopeContext scope = scopeOf(memoryId);
        executeBatch(
                connection,
                upsertEntitySql(),
                entities,
                (statement, entity) -> {
                    statement.setString(1, scope.userId());
                    statement.setString(2, scope.agentId());
                    statement.setString(3, scope.memoryId());
                    statement.setString(4, entity.entityKey());
                    statement.setString(5, entity.displayName());
                    statement.setString(6, entity.entityType().name());
                    statement.setString(7, jsonCodec.toJson(entity.metadata()));
                    statement.setString(8, formatInstant(entity.createdAt()));
                    statement.setString(9, formatInstant(entity.updatedAt()));
                });
    }

    @Override
    public void upsertItemEntityMentions(MemoryId memoryId, List<ItemEntityMention> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return;
        }
        inTransaction(
                connection -> {
                    upsertItemEntityMentions(connection, memoryId, mentions);
                    return null;
                });
    }

    public void upsertItemEntityMentions(
            Connection connection, MemoryId memoryId, List<ItemEntityMention> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return;
        }
        ScopeContext scope = scopeOf(memoryId);
        executeBatch(
                connection,
                upsertMentionSql(),
                mentions,
                (statement, mention) -> {
                    statement.setString(1, scope.userId());
                    statement.setString(2, scope.agentId());
                    statement.setString(3, scope.memoryId());
                    statement.setLong(4, mention.itemId());
                    statement.setString(5, mention.entityKey());
                    statement.setObject(6, mention.confidence());
                    statement.setString(7, jsonCodec.toJson(mention.metadata()));
                    statement.setString(8, formatInstant(mention.createdAt()));
                });
    }

    @Override
    public void rebuildEntityCooccurrences(MemoryId memoryId, Collection<String> entityKeys) {
        inTransaction(
                connection -> {
                    rebuildEntityCooccurrences(connection, memoryId, entityKeys);
                    return null;
                });
    }

    public void rebuildEntityCooccurrences(
            Connection connection, MemoryId memoryId, Collection<String> entityKeys) {
        Set<String> affectedEntityKeys = normalizeEntityKeys(entityKeys);
        if (affectedEntityKeys.isEmpty()) {
            return;
        }
        ScopeContext scope = scopeOf(memoryId);
        executeUpdate(
                connection,
                "DELETE FROM memory_entity_cooccurrence WHERE memory_id = ? AND "
                        + "(left_entity_key IN ("
                        + placeholders(affectedEntityKeys.size())
                        + ") OR right_entity_key IN ("
                        + placeholders(affectedEntityKeys.size())
                        + "))",
                concat(List.of(scope.memoryId()), affectedEntityKeys, affectedEntityKeys));
        Map<Long, Set<String>> mentionsByItem = new HashMap<>();
        listItemEntityMentions(connection, memoryId)
                .forEach(
                        mention ->
                                mentionsByItem
                                        .computeIfAbsent(
                                                mention.itemId(), ignored -> new HashSet<>())
                                        .add(mention.entityKey()));
        Map<CooccurrenceIdentity, Integer> counts = new HashMap<>();
        mentionsByItem
                .values()
                .forEach(entitySet -> accumulateCounts(entitySet, affectedEntityKeys, counts));
        Instant rebuiltAt = Instant.now();
        counts.forEach(
                (identity, count) ->
                        upsertCooccurrence(
                                connection,
                                scope,
                                new EntityCooccurrence(
                                        scope.memoryId(),
                                        identity.leftEntityKey(),
                                        identity.rightEntityKey(),
                                        count,
                                        Map.of(),
                                        rebuiltAt)));
    }

    @Override
    public void upsertItemLinks(MemoryId memoryId, List<ItemLink> links) {
        if (links == null || links.isEmpty()) {
            return;
        }
        inTransaction(
                connection -> {
                    upsertItemLinks(connection, memoryId, links);
                    return null;
                });
    }

    public void upsertItemLinks(Connection connection, MemoryId memoryId, List<ItemLink> links) {
        if (links == null || links.isEmpty()) {
            return;
        }
        ScopeContext scope = scopeOf(memoryId);
        executeBatch(
                connection,
                upsertItemLinkSql(),
                links,
                (statement, link) -> {
                    statement.setString(1, scope.userId());
                    statement.setString(2, scope.agentId());
                    statement.setString(3, scope.memoryId());
                    statement.setLong(4, link.sourceItemId());
                    statement.setLong(5, link.targetItemId());
                    statement.setString(6, link.linkType().name());
                    statement.setObject(7, link.strength());
                    statement.setString(8, jsonCodec.toJson(link.metadata()));
                    statement.setString(9, formatInstant(link.createdAt()));
                    statement.setString(10, link.relationCode());
                    statement.setString(11, link.evidenceSource());
                });
    }

    @Override
    public void upsertEntityAliases(MemoryId memoryId, List<GraphEntityAlias> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }
        inTransaction(
                connection -> {
                    upsertEntityAliases(connection, memoryId, aliases);
                    return null;
                });
    }

    public void upsertEntityAliases(
            Connection connection, MemoryId memoryId, List<GraphEntityAlias> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }
        ScopeContext scope = scopeOf(memoryId);
        executeBatch(
                connection,
                upsertAliasSql(),
                aliases,
                (statement, alias) -> {
                    statement.setString(1, scope.userId());
                    statement.setString(2, scope.agentId());
                    statement.setString(3, scope.memoryId());
                    statement.setString(4, alias.entityKey());
                    statement.setString(5, alias.entityType().name());
                    statement.setString(6, alias.normalizedAlias());
                    statement.setInt(7, alias.evidenceCount());
                    statement.setString(8, jsonCodec.toJson(alias.metadata()));
                    statement.setString(9, formatInstant(alias.createdAt()));
                    statement.setString(10, formatInstant(alias.updatedAt()));
                });
    }

    @Override
    public List<GraphEntity> listEntities(MemoryId memoryId) {
        return queryList(
                "SELECT * FROM memory_graph_entity WHERE deleted = "
                        + falseValue()
                        + " AND memory_id = ? ORDER BY entity_key ASC",
                this::mapEntity,
                scopeOf(memoryId).memoryId());
    }

    @Override
    public List<GraphEntityAlias> listEntityAliases(MemoryId memoryId) {
        return queryList(
                "SELECT * FROM memory_graph_entity_alias WHERE deleted = "
                        + falseValue()
                        + " AND memory_id = ? ORDER BY entity_type ASC, normalized_alias ASC,"
                        + " entity_key ASC",
                this::mapAlias,
                scopeOf(memoryId).memoryId());
    }

    @Override
    public List<GraphEntity> listEntitiesByEntityKeys(
            MemoryId memoryId, Collection<String> entityKeys) {
        Set<String> keys = normalizeEntityKeys(entityKeys);
        if (keys.isEmpty()) {
            return List.of();
        }
        List<Object> params = concat(List.of(scopeOf(memoryId).memoryId()), keys);
        return queryList(
                "SELECT * FROM memory_graph_entity WHERE deleted = "
                        + falseValue()
                        + " AND memory_id = ? AND entity_key IN ("
                        + placeholders(keys.size())
                        + ") ORDER BY entity_key ASC",
                this::mapEntity,
                params.toArray());
    }

    @Override
    public List<GraphEntityAlias> listEntityAliasesByNormalizedAlias(
            MemoryId memoryId, GraphEntityType entityType, String normalizedAlias) {
        if (entityType == null || normalizedAlias == null || normalizedAlias.isBlank()) {
            return List.of();
        }
        return queryList(
                "SELECT * FROM memory_graph_entity_alias WHERE deleted = "
                        + falseValue()
                        + " AND memory_id = ? AND entity_type = ? AND normalized_alias = ? "
                        + "ORDER BY entity_key ASC",
                this::mapAlias,
                scopeOf(memoryId).memoryId(),
                entityType.name(),
                normalizedAlias);
    }

    @Override
    public List<ItemEntityMention> listItemEntityMentions(MemoryId memoryId) {
        return queryList(
                "SELECT * FROM memory_item_entity_mention WHERE deleted = "
                        + falseValue()
                        + " AND memory_id = ? ORDER BY item_id ASC, entity_key ASC, created_at ASC",
                this::mapMention,
                scopeOf(memoryId).memoryId());
    }

    @Override
    public List<ItemEntityMention> listItemEntityMentions(
            MemoryId memoryId, Collection<Long> itemIds) {
        Set<Long> ids = normalizeItemIds(itemIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return queryList(
                "SELECT * FROM memory_item_entity_mention WHERE deleted = "
                        + falseValue()
                        + " AND memory_id = ? AND item_id IN ("
                        + placeholders(ids.size())
                        + ") ORDER BY item_id ASC, entity_key ASC, created_at ASC",
                this::mapMention,
                concat(List.of(scopeOf(memoryId).memoryId()), ids).toArray());
    }

    @Override
    public List<EntityCooccurrence> listEntityCooccurrences(MemoryId memoryId) {
        return queryList(
                "SELECT * FROM memory_entity_cooccurrence WHERE deleted = "
                        + falseValue()
                        + " AND memory_id = ? ORDER BY left_entity_key ASC, right_entity_key ASC",
                this::mapCooccurrence,
                scopeOf(memoryId).memoryId());
    }

    @Override
    public List<ItemLink> listItemLinks(MemoryId memoryId) {
        return queryList(
                "SELECT * FROM memory_item_link WHERE deleted = "
                        + falseValue()
                        + " AND memory_id = ? ORDER BY source_item_id ASC, target_item_id ASC,"
                        + " link_type ASC",
                this::mapItemLink,
                scopeOf(memoryId).memoryId());
    }

    @Override
    public List<ItemLink> listItemLinks(
            MemoryId memoryId, Collection<Long> itemIds, Collection<ItemLinkType> linkTypes) {
        Set<Long> ids = normalizeItemIds(itemIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ItemLinkType> types = normalizeLinkTypes(linkTypes);
        String typeClause =
                types.isEmpty() ? "" : " AND link_type IN (" + placeholders(types.size()) + ")";
        List<Object> params = new ArrayList<>();
        params.add(scopeOf(memoryId).memoryId());
        params.addAll(ids);
        params.addAll(ids);
        types.forEach(type -> params.add(type.name()));
        return queryList(
                "SELECT * FROM memory_item_link WHERE deleted = "
                        + falseValue()
                        + " AND memory_id = ? AND source_item_id IN ("
                        + placeholders(ids.size())
                        + ") AND target_item_id IN ("
                        + placeholders(ids.size())
                        + ")"
                        + typeClause
                        + " ORDER BY source_item_id ASC, target_item_id ASC, link_type ASC",
                this::mapItemLink,
                params.toArray());
    }

    @Override
    public List<ItemLink> listAdjacentItemLinks(
            MemoryId memoryId, Collection<Long> seedItemIds, Collection<ItemLinkType> linkTypes) {
        Set<Long> ids = normalizeItemIds(seedItemIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ItemLinkType> types = normalizeLinkTypes(linkTypes);
        String typeClause =
                types.isEmpty() ? "" : " AND link_type IN (" + placeholders(types.size()) + ")";
        List<Object> params = new ArrayList<>();
        params.add(scopeOf(memoryId).memoryId());
        params.addAll(ids);
        params.addAll(ids);
        types.forEach(type -> params.add(type.name()));
        return queryList(
                "SELECT * FROM memory_item_link WHERE deleted = "
                        + falseValue()
                        + " AND memory_id = ? AND (source_item_id IN ("
                        + placeholders(ids.size())
                        + ") OR target_item_id IN ("
                        + placeholders(ids.size())
                        + "))"
                        + typeClause
                        + " ORDER BY source_item_id ASC, target_item_id ASC, link_type ASC",
                this::mapItemLink,
                params.toArray());
    }

    private void upsertAliasesWithBatchReceipts(
            Connection connection,
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            List<GraphEntityAlias> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }
        if (extractionBatchId == null) {
            upsertEntityAliases(connection, memoryId, aliases);
            return;
        }
        ScopeContext scope = scopeOf(memoryId);
        List<GraphEntityAlias> aliasesToPersist = new ArrayList<>();
        for (GraphEntityAlias alias : aliases) {
            int inserted =
                    executeUpdate(
                            connection,
                            insertAliasReceiptSql(),
                            scope.userId(),
                            scope.agentId(),
                            scope.memoryId(),
                            alias.entityKey(),
                            alias.entityType().name(),
                            alias.normalizedAlias(),
                            extractionBatchId.value());
            if (inserted > 0) {
                aliasesToPersist.add(alias);
            }
        }
        upsertEntityAliases(connection, memoryId, aliasesToPersist);
    }

    private void upsertCooccurrence(
            Connection connection, ScopeContext scope, EntityCooccurrence cooccurrence) {
        executeUpdate(
                connection,
                upsertCooccurrenceSql(),
                scope.userId(),
                scope.agentId(),
                scope.memoryId(),
                cooccurrence.leftEntityKey(),
                cooccurrence.rightEntityKey(),
                cooccurrence.cooccurrenceCount(),
                jsonCodec.toJson(cooccurrence.metadata()),
                formatInstant(cooccurrence.updatedAt()));
    }

    private List<ItemEntityMention> listItemEntityMentions(
            Connection connection, MemoryId memoryId) {
        return queryList(
                connection,
                "SELECT * FROM memory_item_entity_mention WHERE deleted = "
                        + falseValue()
                        + " AND memory_id = ? ORDER BY item_id ASC, entity_key ASC, created_at ASC",
                this::mapMention,
                scopeOf(memoryId).memoryId());
    }

    private GraphEntity mapEntity(ResultSet resultSet) throws SQLException {
        return new GraphEntity(
                resultSet.getString("entity_key"),
                resultSet.getString("memory_id"),
                resultSet.getString("display_name"),
                GraphEntityType.valueOf(resultSet.getString("entity_type")),
                readMetadata(resultSet.getString("metadata")),
                parseInstant(resultSet.getString("created_at")),
                parseInstant(resultSet.getString("updated_at")));
    }

    private ItemEntityMention mapMention(ResultSet resultSet) throws SQLException {
        Float confidence = null;
        float value = resultSet.getFloat("confidence");
        if (!resultSet.wasNull()) {
            confidence = value;
        }
        return new ItemEntityMention(
                resultSet.getString("memory_id"),
                resultSet.getLong("item_id"),
                resultSet.getString("entity_key"),
                confidence,
                readMetadata(resultSet.getString("metadata")),
                parseInstant(resultSet.getString("created_at")));
    }

    private ItemLink mapItemLink(ResultSet resultSet) throws SQLException {
        Double strength = null;
        double value = resultSet.getDouble("strength");
        if (!resultSet.wasNull()) {
            strength = value;
        }
        return new ItemLink(
                resultSet.getString("memory_id"),
                resultSet.getLong("source_item_id"),
                resultSet.getLong("target_item_id"),
                ItemLinkType.valueOf(resultSet.getString("link_type")),
                resultSet.getString("relation_code"),
                resultSet.getString("evidence_source"),
                strength,
                readMetadata(resultSet.getString("metadata")),
                parseInstant(resultSet.getString("created_at")));
    }

    private EntityCooccurrence mapCooccurrence(ResultSet resultSet) throws SQLException {
        return new EntityCooccurrence(
                resultSet.getString("memory_id"),
                resultSet.getString("left_entity_key"),
                resultSet.getString("right_entity_key"),
                resultSet.getInt("cooccurrence_count"),
                readMetadata(resultSet.getString("metadata")),
                parseInstant(resultSet.getString("updated_at")));
    }

    private GraphEntityAlias mapAlias(ResultSet resultSet) throws SQLException {
        return new GraphEntityAlias(
                resultSet.getString("memory_id"),
                resultSet.getString("entity_key"),
                GraphEntityType.valueOf(resultSet.getString("entity_type")),
                resultSet.getString("normalized_alias"),
                resultSet.getInt("evidence_count"),
                readMetadata(resultSet.getString("metadata")),
                parseInstant(resultSet.getString("created_at")),
                parseInstant(resultSet.getString("updated_at")));
    }

    private Map<String, Object> readMetadata(String json) {
        Map<String, Object> metadata = jsonCodec.fromJson(json, OBJECT_MAP_TYPE);
        return metadata == null ? Map.of() : metadata;
    }

    private <T> T inTransaction(TransactionCallback<T> callback) {
        return jdbi.inTransaction(
                handle -> {
                    try {
                        return callback.execute(handle.getConnection());
                    } catch (SQLException e) {
                        throw new JdbcPluginException(e);
                    }
                });
    }

    private List<GraphEntity> queryList(
            String sql, ResultSetMapper<GraphEntity> mapper, Object... params) {
        return jdbi.withHandle(handle -> queryList(handle.getConnection(), sql, mapper, params));
    }

    private List<GraphEntityAlias> queryList(String sql, AliasMapper mapper, Object... params) {
        return jdbi.withHandle(
                handle -> queryList(handle.getConnection(), sql, mapper::map, params));
    }

    private List<ItemEntityMention> queryList(String sql, MentionMapper mapper, Object... params) {
        return jdbi.withHandle(
                handle -> queryList(handle.getConnection(), sql, mapper::map, params));
    }

    private List<ItemLink> queryList(String sql, ItemLinkMapper mapper, Object... params) {
        return jdbi.withHandle(
                handle -> queryList(handle.getConnection(), sql, mapper::map, params));
    }

    private List<EntityCooccurrence> queryList(
            String sql, CooccurrenceMapper mapper, Object... params) {
        return jdbi.withHandle(
                handle -> queryList(handle.getConnection(), sql, mapper::map, params));
    }

    private static <T> List<T> queryList(
            Connection connection, String sql, ResultSetMapper<T> mapper, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapper.map(resultSet));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private static int executeUpdate(Connection connection, String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParams(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private static <T> void executeBatch(
            Connection connection, String sql, List<T> values, StatementBinder<T> binder) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (T value : values) {
                binder.bind(statement, value);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private static void setParams(PreparedStatement statement, Object... params)
            throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    private interface TransactionCallback<T> {
        T execute(Connection connection) throws SQLException;
    }

    private void ensureSchema(boolean createIfNotExist) {
        switch (dialect) {
            case SQLITE -> StoreSchemaBootstrap.ensureSqlite(dataSource, createIfNotExist);
            case MYSQL -> StoreSchemaBootstrap.ensureMysql(dataSource, createIfNotExist);
            case POSTGRESQL -> StoreSchemaBootstrap.ensurePostgresql(dataSource, createIfNotExist);
        }
    }

    private String upsertEntitySql() {
        return switch (dialect) {
            case SQLITE, POSTGRESQL ->
                    "INSERT INTO memory_graph_entity (user_id, agent_id, memory_id, entity_key,"
                        + " display_name, entity_type, metadata, created_at, updated_at, deleted)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, "
                            + currentTimestamp()
                            + "), COALESCE(?, "
                            + currentTimestamp()
                            + "), "
                            + falseValue()
                            + ") ON CONFLICT(memory_id, entity_key) DO UPDATE SET display_name ="
                            + " excluded.display_name, entity_type = excluded.entity_type, metadata"
                            + " = excluded.metadata, updated_at = excluded.updated_at, deleted = "
                            + falseValue();
            case MYSQL ->
                    "INSERT INTO memory_graph_entity (user_id, agent_id, memory_id, entity_key,"
                        + " display_name, entity_type, metadata, created_at, updated_at, deleted)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, "
                            + currentTimestamp()
                            + "), COALESCE(?, "
                            + currentTimestamp()
                            + "), 0) ON DUPLICATE KEY UPDATE display_name = VALUES(display_name),"
                            + " entity_type = VALUES(entity_type), metadata = VALUES(metadata),"
                            + " updated_at = VALUES(updated_at), deleted = 0";
        };
    }

    private String upsertMentionSql() {
        return switch (dialect) {
            case SQLITE, POSTGRESQL ->
                    "INSERT INTO memory_item_entity_mention (user_id, agent_id, memory_id, item_id,"
                        + " entity_key, confidence, metadata, created_at, deleted) VALUES (?, ?, ?,"
                        + " ?, ?, ?, ?, COALESCE(?, "
                            + currentTimestamp()
                            + "), "
                            + falseValue()
                            + ") ON CONFLICT(memory_id, item_id, entity_key) DO UPDATE SET"
                            + " confidence = excluded.confidence, metadata = excluded.metadata,"
                            + " deleted = "
                            + falseValue();
            case MYSQL ->
                    "INSERT INTO memory_item_entity_mention (user_id, agent_id, memory_id, item_id,"
                        + " entity_key, confidence, metadata, created_at, deleted) VALUES (?, ?, ?,"
                        + " ?, ?, ?, ?, COALESCE(?, "
                            + currentTimestamp()
                            + "), 0) ON DUPLICATE KEY UPDATE confidence = VALUES(confidence),"
                            + " metadata = VALUES(metadata), deleted = 0";
        };
    }

    private String upsertItemLinkSql() {
        return switch (dialect) {
            case SQLITE, POSTGRESQL ->
                    "INSERT INTO memory_item_link (user_id, agent_id, memory_id, source_item_id,"
                        + " target_item_id, link_type, strength, metadata, created_at,"
                        + " relation_code, evidence_source, deleted) VALUES (?, ?, ?, ?, ?, ?, ?,"
                        + " ?, COALESCE(?, "
                            + currentTimestamp()
                            + "), ?, ?, "
                            + falseValue()
                            + ") ON CONFLICT(memory_id, source_item_id, target_item_id, link_type)"
                            + " DO UPDATE SET strength = excluded.strength, metadata ="
                            + " excluded.metadata, relation_code = excluded.relation_code,"
                            + " evidence_source = excluded.evidence_source, deleted = "
                            + falseValue();
            case MYSQL ->
                    "INSERT INTO memory_item_link (user_id, agent_id, memory_id, source_item_id,"
                        + " target_item_id, link_type, strength, metadata, created_at,"
                        + " relation_code, evidence_source, deleted) VALUES (?, ?, ?, ?, ?, ?, ?,"
                        + " ?, COALESCE(?, "
                            + currentTimestamp()
                            + "), ?, ?, 0) ON DUPLICATE KEY UPDATE strength = VALUES(strength),"
                            + " metadata = VALUES(metadata), relation_code = VALUES(relation_code),"
                            + " evidence_source = VALUES(evidence_source), deleted = 0";
        };
    }

    private String upsertAliasSql() {
        return switch (dialect) {
            case SQLITE, POSTGRESQL ->
                    "INSERT INTO memory_graph_entity_alias (user_id, agent_id, memory_id,"
                        + " entity_key, entity_type, normalized_alias, evidence_count, metadata,"
                        + " created_at, updated_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?,"
                        + " COALESCE(?, "
                            + currentTimestamp()
                            + "), COALESCE(?, "
                            + currentTimestamp()
                            + "), "
                            + falseValue()
                            + ") ON CONFLICT(memory_id, entity_type, entity_key, normalized_alias)"
                            + " DO UPDATE SET evidence_count ="
                            + " memory_graph_entity_alias.evidence_count + excluded.evidence_count,"
                            + " metadata = excluded.metadata, updated_at = excluded.updated_at,"
                            + " deleted = "
                            + falseValue();
            case MYSQL ->
                    "INSERT INTO memory_graph_entity_alias (user_id, agent_id, memory_id,"
                        + " entity_key, entity_type, normalized_alias, evidence_count, metadata,"
                        + " created_at, updated_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?,"
                        + " COALESCE(?, "
                            + currentTimestamp()
                            + "), COALESCE(?, "
                            + currentTimestamp()
                            + "), 0) ON DUPLICATE KEY UPDATE evidence_count = evidence_count +"
                            + " VALUES(evidence_count), metadata = VALUES(metadata), updated_at ="
                            + " VALUES(updated_at), deleted = 0";
        };
    }

    private String upsertCooccurrenceSql() {
        return switch (dialect) {
            case SQLITE, POSTGRESQL ->
                    "INSERT INTO memory_entity_cooccurrence (user_id, agent_id, memory_id,"
                            + " left_entity_key, right_entity_key, cooccurrence_count, metadata,"
                            + " updated_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, "
                            + currentTimestamp()
                            + "), "
                            + falseValue()
                            + ") ON CONFLICT(memory_id, left_entity_key, right_entity_key) DO"
                            + " UPDATE SET cooccurrence_count = excluded.cooccurrence_count,"
                            + " metadata = excluded.metadata, updated_at = excluded.updated_at,"
                            + " deleted = "
                            + falseValue();
            case MYSQL ->
                    "INSERT INTO memory_entity_cooccurrence (user_id, agent_id, memory_id,"
                            + " left_entity_key, right_entity_key, cooccurrence_count, metadata,"
                            + " updated_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, "
                            + currentTimestamp()
                            + "), 0) ON DUPLICATE KEY UPDATE cooccurrence_count ="
                            + " VALUES(cooccurrence_count), metadata = VALUES(metadata), updated_at"
                            + " = VALUES(updated_at), deleted = 0";
        };
    }

    private String insertAliasReceiptSql() {
        return switch (dialect) {
            case SQLITE ->
                    "INSERT OR IGNORE INTO memory_graph_alias_batch_receipt (user_id, agent_id,"
                            + " memory_id, entity_key, entity_type, normalized_alias,"
                            + " extraction_batch_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
            case POSTGRESQL ->
                    "INSERT INTO memory_graph_alias_batch_receipt (user_id, agent_id, memory_id,"
                        + " entity_key, entity_type, normalized_alias, extraction_batch_id) VALUES"
                        + " (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(memory_id, entity_key, entity_type,"
                        + " normalized_alias, extraction_batch_id) DO NOTHING";
            case MYSQL ->
                    "INSERT IGNORE INTO memory_graph_alias_batch_receipt (user_id, agent_id,"
                            + " memory_id, entity_key, entity_type, normalized_alias,"
                            + " extraction_batch_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        };
    }

    private String falseValue() {
        return dialect == JdbcGraphDialect.POSTGRESQL ? "FALSE" : "0";
    }

    private String currentTimestamp() {
        return switch (dialect) {
            case SQLITE -> "datetime('now')";
            case MYSQL -> "CURRENT_TIMESTAMP(3)";
            case POSTGRESQL -> "CURRENT_TIMESTAMP";
        };
    }

    private ScopeContext scopeOf(MemoryId memoryId) {
        return new ScopeContext(
                memoryId.getAttribute("userId"),
                memoryId.getAttribute("agentId"),
                memoryId.toIdentifier());
    }

    private static void accumulateCounts(
            Set<String> entitySet,
            Set<String> affectedEntityKeys,
            Map<CooccurrenceIdentity, Integer> counts) {
        List<String> sortedKeys = entitySet.stream().filter(Objects::nonNull).sorted().toList();
        for (int i = 0; i < sortedKeys.size(); i++) {
            for (int j = i + 1; j < sortedKeys.size(); j++) {
                String leftEntityKey = sortedKeys.get(i);
                String rightEntityKey = sortedKeys.get(j);
                if (!affectedEntityKeys.contains(leftEntityKey)
                        && !affectedEntityKeys.contains(rightEntityKey)) {
                    continue;
                }
                counts.merge(
                        new CooccurrenceIdentity(leftEntityKey, rightEntityKey), 1, Integer::sum);
            }
        }
    }

    private static Set<String> normalizeEntityKeys(Collection<String> entityKeys) {
        if (entityKeys == null || entityKeys.isEmpty()) {
            return Set.of();
        }
        return entityKeys.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static Set<Long> normalizeItemIds(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Set.of();
        }
        return itemIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static List<ItemLinkType> normalizeLinkTypes(Collection<ItemLinkType> linkTypes) {
        if (linkTypes == null || linkTypes.isEmpty()) {
            return List.of();
        }
        return linkTypes.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static List<Object> concat(Collection<?>... collections) {
        List<Object> values = new ArrayList<>();
        for (Collection<?> collection : collections) {
            values.addAll(collection);
        }
        return values;
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.contains("T") ? value : value.replace(' ', 'T') + "Z";
        return Instant.parse(normalized.endsWith("Z") ? normalized : normalized + "Z");
    }

    @FunctionalInterface
    private interface StatementBinder<T> {
        void bind(PreparedStatement statement, T value) throws SQLException;
    }

    @FunctionalInterface
    private interface AliasMapper {
        GraphEntityAlias map(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    private interface MentionMapper {
        ItemEntityMention map(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    private interface ItemLinkMapper {
        ItemLink map(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    private interface CooccurrenceMapper {
        EntityCooccurrence map(ResultSet resultSet) throws SQLException;
    }

    private record ScopeContext(String userId, String agentId, String memoryId) {}

    private record CooccurrenceIdentity(String leftEntityKey, String rightEntityKey) {}
}
