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

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchId;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchRecord;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ExtractionBatchState;
import com.openmemind.ai.memory.core.extraction.item.graph.commit.ItemGraphCommitReceipt;
import com.openmemind.ai.memory.core.extraction.item.graph.plan.ItemGraphWritePlan;
import com.openmemind.ai.memory.core.store.graph.ItemGraphCommitOperations;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcPluginException;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JsonCodec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

public class JdbcItemGraphCommitOperations implements ItemGraphCommitOperations {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final DataSource dataSource;
    private final JdbcGraphDialect dialect;
    private final JdbcGraphOperations graphOperations;
    private final JsonCodec jsonCodec = new JsonCodec();

    protected JdbcItemGraphCommitOperations(
            DataSource dataSource, JdbcGraphDialect dialect, JdbcGraphOperations graphOperations) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.graphOperations = Objects.requireNonNull(graphOperations, "graphOperations");
    }

    @Override
    public ItemGraphCommitReceipt commit(
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            List<MemoryItem> items,
            ItemGraphWritePlan writePlan) {
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(extractionBatchId, "extractionBatchId");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(writePlan, "writePlan");
        recordPending(memoryId, extractionBatchId);
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertItems(connection, memoryId, extractionBatchId, items);
                graphOperations.applyGraphWritePlan(
                        connection, memoryId, extractionBatchId, writePlan);
                markCommitted(connection, memoryId, extractionBatchId);
                connection.commit();
                return ItemGraphCommitReceipt.success(extractionBatchId);
            } catch (RuntimeException | SQLException error) {
                connection.rollback();
                RuntimeException runtimeError = toRuntimeException(error);
                markRepairRequired(memoryId, extractionBatchId, runtimeError);
                throw runtimeError;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            RuntimeException runtimeError = new JdbcPluginException(e);
            markRepairRequired(memoryId, extractionBatchId, runtimeError);
            throw runtimeError;
        }
    }

    @Override
    public Optional<ExtractionBatchRecord> getBatch(
            MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        try (Connection connection = dataSource.getConnection()) {
            return Optional.ofNullable(loadBatch(connection, memoryId, extractionBatchId));
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    @Override
    public void discardFailedBatch(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        try (Connection connection = dataSource.getConnection()) {
            executeUpdate(
                    connection,
                    "DELETE FROM memory_item_graph_batch WHERE memory_id = ? AND"
                            + " extraction_batch_id = ?",
                    memoryId.toIdentifier(),
                    extractionBatchId.value());
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private void recordPending(MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        try (Connection connection = dataSource.getConnection()) {
            upsertBatch(
                    connection, memoryId, extractionBatchId, ExtractionBatchState.PENDING, null);
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private void markCommitted(
            Connection connection, MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        upsertBatch(connection, memoryId, extractionBatchId, ExtractionBatchState.COMMITTED, null);
    }

    private void markRepairRequired(
            MemoryId memoryId, ExtractionBatchId extractionBatchId, RuntimeException error) {
        try (Connection connection = dataSource.getConnection()) {
            upsertBatch(
                    connection,
                    memoryId,
                    extractionBatchId,
                    ExtractionBatchState.REPAIR_REQUIRED,
                    truncateErrorMessage(error));
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private void insertItems(
            Connection connection,
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            List<MemoryItem> items) {
        if (items.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(insertItemSql())) {
            ScopeContext scope = scopeOf(memoryId);
            Instant now = Instant.now();
            for (MemoryItem item : items) {
                bindItem(statement, scope, extractionBatchId, item, now);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private void bindItem(
            PreparedStatement statement,
            ScopeContext scope,
            ExtractionBatchId extractionBatchId,
            MemoryItem item,
            Instant now)
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
        setTemporal(statement, 11, item.occurredAt());
        setTemporal(statement, 12, item.occurredStart());
        setTemporal(statement, 13, item.occurredEnd());
        statement.setString(14, item.timeGranularity());
        setTemporal(statement, 15, item.observedAt());
        Instant temporalStart =
                firstNonNull(item.occurredStart(), item.occurredAt(), item.observedAt());
        Instant temporalEndOrAnchor =
                firstNonNull(
                        item.occurredEnd(),
                        item.occurredStart(),
                        item.occurredAt(),
                        item.observedAt());
        Instant temporalAnchor =
                firstNonNull(item.occurredStart(), item.occurredAt(), item.observedAt());
        setTemporal(statement, 16, temporalStart);
        setTemporal(statement, 17, temporalEndOrAnchor);
        setTemporal(statement, 18, temporalAnchor);
        statement.setString(19, item.type() != null ? item.type().name() : "FACT");
        statement.setString(20, item.contentType() != null ? item.contentType() : "conversation");
        setJson(statement, 21, jsonCodec.toJson(item.metadata()));
        setTemporal(statement, 22, item.createdAt() != null ? item.createdAt() : now);
        setTemporal(statement, 23, now);
        statement.setString(24, extractionBatchId.value());
    }

    private void upsertBatch(
            Connection connection,
            MemoryId memoryId,
            ExtractionBatchId extractionBatchId,
            ExtractionBatchState state,
            String errorMessage) {
        ScopeContext scope = scopeOf(memoryId);
        try (PreparedStatement statement = connection.prepareStatement(upsertBatchSql())) {
            statement.setString(1, scope.userId());
            statement.setString(2, scope.agentId());
            statement.setString(3, scope.memoryId());
            statement.setString(4, extractionBatchId.value());
            statement.setString(5, state.name());
            statement.setString(6, errorMessage);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private ExtractionBatchRecord loadBatch(
            Connection connection, MemoryId memoryId, ExtractionBatchId extractionBatchId) {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT * FROM memory_item_graph_batch WHERE memory_id = ? AND"
                                + " extraction_batch_id = ? LIMIT 1")) {
            statement.setString(1, memoryId.toIdentifier());
            statement.setString(2, extractionBatchId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new ExtractionBatchRecord(
                        toMemoryId(resultSet.getString("memory_id")),
                        new ExtractionBatchId(resultSet.getString("extraction_batch_id")),
                        ExtractionBatchState.valueOf(resultSet.getString("state")),
                        resultSet.getString("error_message"),
                        booleanColumn(resultSet, "retry_promotion_supported"));
            }
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private String insertItemSql() {
        return "INSERT INTO memory_item (biz_id, user_id, agent_id, memory_id, content, scope,"
                + " category, vector_id, raw_data_id, content_hash, occurred_at, occurred_start,"
                + " occurred_end, time_granularity, observed_at, temporal_start,"
                + " temporal_end_or_anchor, temporal_anchor, type, raw_data_type, metadata,"
                + " created_at, updated_at, extraction_batch_id, deleted) VALUES (?, ?, ?, ?, ?,"
                + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                + falseValue()
                + ")";
    }

    private String upsertBatchSql() {
        return switch (dialect) {
            case SQLITE, POSTGRESQL ->
                    "INSERT INTO memory_item_graph_batch (user_id, agent_id, memory_id,"
                        + " extraction_batch_id, state, error_message, retry_promotion_supported,"
                        + " deleted) VALUES (?, ?, ?, ?, ?, ?, "
                            + falseValue()
                            + ", "
                            + falseValue()
                            + ") ON CONFLICT(memory_id, extraction_batch_id) DO UPDATE SET state ="
                            + " excluded.state, error_message = excluded.error_message,"
                            + " retry_promotion_supported = "
                            + falseValue()
                            + ", updated_at = "
                            + currentTimestamp()
                            + ", deleted = "
                            + falseValue();
            case MYSQL ->
                    "INSERT INTO memory_item_graph_batch (user_id, agent_id, memory_id,"
                        + " extraction_batch_id, state, error_message, retry_promotion_supported,"
                        + " deleted) VALUES (?, ?, ?, ?, ?, ?, 0, 0) ON DUPLICATE KEY UPDATE state"
                        + " = VALUES(state), error_message = VALUES(error_message),"
                        + " retry_promotion_supported = 0, updated_at = "
                            + currentTimestamp()
                            + ", deleted = 0";
        };
    }

    private void setTemporal(PreparedStatement statement, int index, Instant instant)
            throws SQLException {
        if (dialect == JdbcGraphDialect.SQLITE) {
            statement.setString(index, instant == null ? null : instant.toString());
        } else {
            statement.setTimestamp(index, instant == null ? null : Timestamp.from(instant));
        }
    }

    private void setJson(PreparedStatement statement, int index, String json) throws SQLException {
        statement.setString(index, json);
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

    private static int executeUpdate(Connection connection, String sql, Object... params)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            return statement.executeUpdate();
        }
    }

    private static RuntimeException toRuntimeException(Throwable error) {
        return error instanceof RuntimeException runtimeException
                ? runtimeException
                : new JdbcPluginException(error);
    }

    private static String truncateErrorMessage(RuntimeException error) {
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static boolean booleanColumn(ResultSet resultSet, String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return !resultSet.wasNull() && value;
    }

    private static Instant firstNonNull(Instant first, Instant... rest) {
        if (first != null) {
            return first;
        }
        for (Instant candidate : rest) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static MemoryId toMemoryId(String memoryId) {
        String[] parts = memoryId.split(":", 2);
        return parts.length == 2
                ? DefaultMemoryId.of(parts[0], parts[1])
                : DefaultMemoryId.of(memoryId, null);
    }

    private static ScopeContext scopeOf(MemoryId memoryId) {
        return new ScopeContext(
                memoryId.getAttribute("userId"),
                memoryId.getAttribute("agentId"),
                memoryId.toIdentifier());
    }

    private record ScopeContext(String userId, String agentId, String memoryId) {}
}
