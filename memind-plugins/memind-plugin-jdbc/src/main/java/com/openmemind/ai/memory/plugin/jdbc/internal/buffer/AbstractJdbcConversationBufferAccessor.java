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

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcExecutor;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * Shared JDBC accessor for single-table conversation runtime storage.
 */
public abstract class AbstractJdbcConversationBufferAccessor {

    private final DataSource dataSource;

    protected AbstractJdbcConversationBufferAccessor(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public void append(String sessionId, Message message) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(message, "message");

        SessionScope scope = scopeOfSession(sessionId);
        JdbcExecutor.inTransaction(
                dataSource,
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement(insertSql())) {
                        bindInsert(statement, scope, message);
                        statement.executeUpdate();
                    }
                    return null;
                });
    }

    public List<ConversationBufferRow> selectPending(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        String sql =
                "SELECT id, session_id, role, content, user_name, timestamp "
                        + "FROM memory_conversation_buffer "
                        + "WHERE session_id = ? AND extracted = "
                        + falseLiteral()
                        + " AND deleted = "
                        + falseLiteral()
                        + " ORDER BY id ASC";
        return JdbcExecutor.queryList(dataSource, sql, this::mapRecord, sessionId);
    }

    public List<ConversationBufferRow> selectRecent(String sessionId, int limit) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        String sql =
                "SELECT id, session_id, role, content, user_name, timestamp "
                        + "FROM memory_conversation_buffer "
                        + "WHERE session_id = ? AND deleted = "
                        + falseLiteral()
                        + " ORDER BY id DESC LIMIT ?";
        List<ConversationBufferRow> descending =
                JdbcExecutor.queryList(dataSource, sql, this::mapRecord, sessionId, limit);
        java.util.Collections.reverse(descending);
        return descending;
    }

    public void markExtractedByIds(List<Long> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        String sql =
                "UPDATE memory_conversation_buffer SET extracted = "
                        + trueLiteral()
                        + ", updated_at = CURRENT_TIMESTAMP WHERE deleted = "
                        + falseLiteral()
                        + " AND id IN ("
                        + placeholders
                        + ")";
        JdbcExecutor.update(dataSource, sql, ids.toArray());
    }

    protected abstract String insertSql();

    protected abstract String falseLiteral();

    protected abstract String trueLiteral();

    protected abstract void bindTimestamp(
            PreparedStatement statement, int parameterIndex, Instant instant) throws SQLException;

    protected abstract Instant readTimestamp(ResultSet resultSet, String columnLabel)
            throws SQLException;

    private void bindInsert(PreparedStatement statement, SessionScope scope, Message message)
            throws SQLException {
        statement.setString(1, scope.sessionId());
        statement.setString(2, scope.userId());
        statement.setString(3, scope.agentId());
        statement.setString(4, scope.memoryId());
        statement.setString(5, message.role().name());
        statement.setString(6, message.textContent());
        statement.setString(7, message.userName());
        bindTimestamp(statement, 8, message.timestamp());
    }

    private ConversationBufferRow mapRecord(ResultSet resultSet) throws SQLException {
        Message.Role role = Message.Role.valueOf(resultSet.getString("role"));
        String content = resultSet.getString("content");
        String userName = resultSet.getString("user_name");
        Instant timestamp = readTimestamp(resultSet, "timestamp");
        Message message;
        if (role == Message.Role.USER) {
            message =
                    userName == null
                            ? Message.user(content, timestamp)
                            : Message.user(content, timestamp, userName);
        } else {
            message = Message.assistant(content, timestamp);
        }
        return new ConversationBufferRow(
                resultSet.getLong("id"), resultSet.getString("session_id"), message);
    }

    private SessionScope scopeOfSession(String sessionId) {
        int separator = sessionId.indexOf(':');
        if (separator < 0) {
            return new SessionScope(sessionId, sessionId, "", sessionId);
        }
        return new SessionScope(
                sessionId,
                sessionId.substring(0, separator),
                normalizeAgentId(sessionId.substring(separator + 1)),
                sessionId);
    }

    private String normalizeAgentId(String agentId) {
        return agentId == null ? "" : agentId;
    }

    private record SessionScope(String sessionId, String userId, String agentId, String memoryId) {}
}
