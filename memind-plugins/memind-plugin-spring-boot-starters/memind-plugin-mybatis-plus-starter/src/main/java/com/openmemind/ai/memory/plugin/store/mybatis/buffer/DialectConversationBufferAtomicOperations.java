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
package com.openmemind.ai.memory.plugin.store.mybatis.buffer;

import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferRow;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialect;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialectDetector;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

public class DialectConversationBufferAtomicOperations
        implements ConversationBufferAtomicOperations {

    private static final String POSTGRESQL_DRAIN_PENDING_SQL =
            """
            WITH updated AS (
                UPDATE memory_conversation_buffer AS b
                SET extracted = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE b.session_id = ?
                  AND b.extracted = FALSE
                  AND b.deleted = FALSE
                RETURNING b.id, b.session_id, b.role, b.content, b.user_name,
                          b.source_client, b.timestamp
            )
            SELECT id, session_id, role, content, user_name, source_client, timestamp
            FROM updated
            ORDER BY id ASC
            """;

    private static final String POSTGRESQL_CLEAR_PENDING_SQL =
            """
            UPDATE memory_conversation_buffer
            SET extracted = TRUE, updated_at = CURRENT_TIMESTAMP
            WHERE session_id = ?
              AND extracted = FALSE
              AND deleted = FALSE
            """;

    private static final String MYSQL_LOCK_PENDING_SQL =
            """
            SELECT id, session_id, role, content, user_name, source_client, timestamp
            FROM memory_conversation_buffer
            WHERE session_id = ?
              AND extracted = 0
              AND deleted = 0
            ORDER BY id ASC
            FOR UPDATE
            """;

    private static final String MYSQL_CLEAR_PENDING_SQL =
            """
            UPDATE memory_conversation_buffer
            SET extracted = 1, updated_at = CURRENT_TIMESTAMP
            WHERE session_id = ?
              AND extracted = 0
              AND deleted = 0
            """;

    private static final String SQLITE_DRAIN_PENDING_SQL =
            """
            UPDATE memory_conversation_buffer
            SET extracted = 1, updated_at = CURRENT_TIMESTAMP
            WHERE session_id = ?
              AND extracted = 0
              AND deleted = 0
              AND id IN (
                  SELECT id
                  FROM memory_conversation_buffer
                  WHERE session_id = ?
                    AND extracted = 0
                    AND deleted = 0
                  ORDER BY id ASC
              )
            RETURNING id, session_id, role, content, user_name, source_client, timestamp
            """;

    private static final String SQLITE_CLEAR_PENDING_SQL =
            """
            UPDATE memory_conversation_buffer
            SET extracted = 1, updated_at = CURRENT_TIMESTAMP
            WHERE session_id = ?
              AND extracted = 0
              AND deleted = 0
            """;

    private final DataSource dataSource;
    private final DatabaseDialect dialect;

    public DialectConversationBufferAtomicOperations(DataSource dataSource) {
        this(dataSource, new DatabaseDialectDetector());
    }

    public DialectConversationBufferAtomicOperations(
            DataSource dataSource, DatabaseDialectDetector dialectDetector) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect =
                Objects.requireNonNull(dialectDetector, "dialectDetector").detect(dataSource);
    }

    @Override
    public List<ConversationBufferRow> drainPending(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return switch (dialect) {
            case POSTGRESQL -> drainWithReturning(POSTGRESQL_DRAIN_PENDING_SQL, sessionId, false);
            case MYSQL -> drainMysql(sessionId);
            case SQLITE -> drainWithReturning(SQLITE_DRAIN_PENDING_SQL, sessionId, true);
        };
    }

    @Override
    public void clearPending(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        switch (dialect) {
            case POSTGRESQL -> executeSingleSessionUpdate(POSTGRESQL_CLEAR_PENDING_SQL, sessionId);
            case MYSQL -> executeSingleSessionUpdate(MYSQL_CLEAR_PENDING_SQL, sessionId);
            case SQLITE -> executeSingleSessionUpdate(SQLITE_CLEAR_PENDING_SQL, sessionId);
        }
    }

    private List<ConversationBufferRow> drainWithReturning(
            String sql, String sessionId, boolean bindSessionTwice) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            if (bindSessionTwice) {
                statement.setString(2, sessionId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return readRows(resultSet, dialect).stream()
                        .sorted(Comparator.comparingLong(ConversationBufferRow::getId))
                        .toList();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to drain pending conversation buffer rows", e);
        }
    }

    private List<ConversationBufferRow> drainMysql(String sessionId) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            int originalIsolation = connection.getTransactionIsolation();
            boolean originalReadOnly = connection.isReadOnly();
            Throwable failure = null;
            try {
                connection.setReadOnly(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                connection.setAutoCommit(false);
                List<ConversationBufferRow> rows = lockMysqlPendingRows(connection, sessionId);
                markMysqlLockedRowsExtracted(connection, rows);
                connection.commit();
                return rows;
            } catch (SQLException | RuntimeException ex) {
                failure = ex;
                rollback(connection, ex);
                throw ex;
            } finally {
                restoreConnectionState(
                        connection,
                        originalAutoCommit,
                        originalIsolation,
                        originalReadOnly,
                        failure);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to drain pending conversation buffer rows", e);
        }
    }

    private void executeSingleSessionUpdate(String sql, String sessionId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear pending conversation buffer rows", e);
        }
    }

    private static List<ConversationBufferRow> lockMysqlPendingRows(
            Connection connection, String sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MYSQL_LOCK_PENDING_SQL)) {
            statement.setString(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return readRows(resultSet, DatabaseDialect.MYSQL);
            }
        }
    }

    private static void markMysqlLockedRowsExtracted(
            Connection connection, List<ConversationBufferRow> rows) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        String sql =
                "UPDATE memory_conversation_buffer SET extracted = 1, updated_at ="
                        + " CURRENT_TIMESTAMP WHERE extracted = 0 AND deleted = 0 AND id IN ("
                        + placeholders(rows.size())
                        + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < rows.size(); i++) {
                statement.setLong(i + 1, rows.get(i).getId());
            }
            statement.executeUpdate();
        }
    }

    private static List<ConversationBufferRow> readRows(
            ResultSet resultSet, DatabaseDialect dialect) throws SQLException {
        List<ConversationBufferRow> rows = new ArrayList<>();
        while (resultSet.next()) {
            rows.add(mapCurrentRow(resultSet, dialect));
        }
        return rows;
    }

    private static ConversationBufferRow mapCurrentRow(ResultSet resultSet, DatabaseDialect dialect)
            throws SQLException {
        ConversationBufferRow row = new ConversationBufferRow();
        row.setId(resultSet.getLong("id"));
        row.setSessionId(resultSet.getString("session_id"));
        row.setRole(resultSet.getString("role"));
        row.setContent(resultSet.getString("content"));
        row.setUserName(resultSet.getString("user_name"));
        row.setSourceClient(resultSet.getString("source_client"));
        row.setTimestamp(
                dialect == DatabaseDialect.SQLITE
                        ? resultSet.getString("timestamp")
                        : resultSet.getTimestamp("timestamp"));
        return row;
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            cause.addSuppressed(rollbackException);
        }
    }

    private static void restoreConnectionState(
            Connection connection,
            boolean originalAutoCommit,
            int originalIsolation,
            boolean originalReadOnly,
            Throwable previousFailure)
            throws SQLException {
        try {
            connection.setTransactionIsolation(originalIsolation);
            connection.setReadOnly(originalReadOnly);
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException restoreException) {
            if (previousFailure != null) {
                previousFailure.addSuppressed(restoreException);
                return;
            }
            throw restoreException;
        }
    }
}
