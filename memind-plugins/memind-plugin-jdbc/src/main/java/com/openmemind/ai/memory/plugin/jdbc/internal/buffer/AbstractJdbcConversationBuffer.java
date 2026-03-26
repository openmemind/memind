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

import com.openmemind.ai.memory.core.buffer.ConversationBuffer;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcExecutor;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

public abstract class AbstractJdbcConversationBuffer implements ConversationBuffer {

    private final DataSource dataSource;

    protected AbstractJdbcConversationBuffer(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void save(String sessionId, List<Message> buffer) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (buffer == null || buffer.isEmpty()) {
            clear(sessionId);
            return;
        }

        SessionScope scope = scopeOfSession(sessionId);
        JdbcExecutor.inTransaction(
                dataSource,
                connection -> {
                    int existingCount = activeMessageCount(sessionId);
                    if (existingCount >= buffer.size()) {
                        return null;
                    }

                    try (PreparedStatement statement = connection.prepareStatement(insertSql())) {
                        for (Message message : buffer.subList(existingCount, buffer.size())) {
                            if (message == null) {
                                continue;
                            }
                            bindInsert(statement, scope, message);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    return null;
                });
    }

    @Override
    public List<Message> load(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        String sql =
                "SELECT role, content, user_name, timestamp "
                        + "FROM memory_conversation_buffer "
                        + "WHERE session_id = ? AND deleted = "
                        + falseLiteral()
                        + " "
                        + "ORDER BY id ASC";
        return JdbcExecutor.queryList(dataSource, sql, this::mapMessage, sessionId);
    }

    @Override
    public void clear(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        String sql =
                "UPDATE memory_conversation_buffer "
                        + "SET deleted = "
                        + trueLiteral()
                        + ", updated_at = CURRENT_TIMESTAMP "
                        + "WHERE session_id = ? AND deleted = "
                        + falseLiteral();
        JdbcExecutor.update(dataSource, sql, sessionId);
    }

    @Override
    public List<Message> drain(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        String selectSql =
                "SELECT role, content, user_name, timestamp "
                        + "FROM memory_conversation_buffer "
                        + "WHERE session_id = ? AND deleted = "
                        + falseLiteral()
                        + " "
                        + "ORDER BY id ASC";
        String updateSql =
                "UPDATE memory_conversation_buffer "
                        + "SET deleted = "
                        + trueLiteral()
                        + ", updated_at = CURRENT_TIMESTAMP "
                        + "WHERE session_id = ? AND deleted = "
                        + falseLiteral();
        return JdbcExecutor.inTransaction(
                dataSource,
                connection -> {
                    List<Message> messages;
                    try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
                        ps.setString(1, sessionId);
                        try (ResultSet rs = ps.executeQuery()) {
                            messages = new java.util.ArrayList<>();
                            while (rs.next()) {
                                messages.add(mapMessage(rs));
                            }
                        }
                    }
                    try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                        ps.setString(1, sessionId);
                        ps.executeUpdate();
                    }
                    return messages;
                });
    }

    @Override
    public List<String> listActiveSessions(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        SessionScope scope = scopeOf(memoryId);
        String sql =
                "SELECT DISTINCT session_id "
                        + "FROM memory_conversation_buffer "
                        + "WHERE user_id = ? AND agent_id = ? AND deleted = "
                        + falseLiteral()
                        + " "
                        + "ORDER BY session_id ASC";
        return JdbcExecutor.queryList(
                dataSource,
                sql,
                resultSet -> resultSet.getString(1),
                scope.userId(),
                scope.agentId());
    }

    @Override
    public int loadMessageCount(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return Math.toIntExact(
                JdbcExecutor.queryCount(
                        dataSource,
                        """
                        SELECT COUNT(*)
                        FROM memory_conversation_buffer
                        WHERE session_id = ?
                        """,
                        sessionId));
    }

    protected abstract String insertSql();

    protected abstract String falseLiteral();

    protected abstract String trueLiteral();

    protected abstract void bindTimestamp(
            PreparedStatement statement, int parameterIndex, Instant instant) throws SQLException;

    protected abstract Instant readTimestamp(ResultSet resultSet, String columnLabel)
            throws SQLException;

    private int activeMessageCount(String sessionId) {
        String sql =
                "SELECT COUNT(*) "
                        + "FROM memory_conversation_buffer "
                        + "WHERE session_id = ? AND deleted = "
                        + falseLiteral();
        return Math.toIntExact(JdbcExecutor.queryCount(dataSource, sql, sessionId));
    }

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

    private Message mapMessage(ResultSet resultSet) throws SQLException {
        Message.Role role = Message.Role.valueOf(resultSet.getString("role"));
        String content = resultSet.getString("content");
        String userName = resultSet.getString("user_name");
        Instant timestamp = readTimestamp(resultSet, "timestamp");
        if (role == Message.Role.USER) {
            return userName == null
                    ? Message.user(content, timestamp)
                    : Message.user(content, timestamp, userName);
        }
        return Message.assistant(content, timestamp);
    }

    private SessionScope scopeOf(MemoryId memoryId) {
        return new SessionScope(
                memoryId.toIdentifier(),
                memoryId.getAttribute("userId"),
                normalizeAgentId(memoryId.getAttribute("agentId")),
                memoryId.toIdentifier());
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
