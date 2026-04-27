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
package com.openmemind.ai.memory.plugin.jdbc.mysql;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.jdbc.internal.buffer.ConversationBufferRow;
import com.openmemind.ai.memory.plugin.jdbc.internal.jdbi.JdbiFactory;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;

public class MysqlConversationBufferAccessor {

    private static final String INSERT_SQL =
            """
            INSERT INTO memory_conversation_buffer
                (session_id, user_id, agent_id, memory_id, role, content, user_name, timestamp, extracted, deleted)
            VALUES (:sessionId, :userId, :agentId, :memoryId, :role, :content, :userName, :timestamp, 0, 0)
            """;

    private static final String SELECT_PENDING_SQL =
            """
            SELECT id, session_id, role, content, user_name, timestamp
            FROM memory_conversation_buffer
            WHERE session_id = :sessionId
              AND extracted = 0
              AND deleted = 0
            ORDER BY id ASC
            """;

    private static final String SELECT_RECENT_SQL =
            """
            SELECT id, session_id, role, content, user_name, timestamp
            FROM memory_conversation_buffer
            WHERE session_id = :sessionId
              AND deleted = 0
            ORDER BY id DESC
            LIMIT :limit
            """;

    private static final String MARK_EXTRACTED_SQL =
            """
            UPDATE memory_conversation_buffer
            SET extracted = 1, updated_at = CURRENT_TIMESTAMP
            WHERE deleted = 0
              AND id IN (<ids>)
            """;

    private final Jdbi jdbi;

    public MysqlConversationBufferAccessor(DataSource dataSource) {
        this(dataSource, true);
    }

    public MysqlConversationBufferAccessor(DataSource dataSource, boolean createIfNotExist) {
        DataSource checkedDataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbi = JdbiFactory.create(checkedDataSource);
        StoreSchemaBootstrap.ensureMysql(checkedDataSource, createIfNotExist);
    }

    public void append(String sessionId, Message message) {
        Objects.requireNonNull(message, "message");
        SessionScope scope = scopeOfSession(sessionId);
        jdbi.useHandle(
                handle ->
                        bindScope(handle.createUpdate(INSERT_SQL), scope)
                                .bind("role", message.role().name())
                                .bind("content", message.textContent())
                                .bind("userName", message.userName())
                                .bind("timestamp", timestampValue(message.timestamp()))
                                .execute());
    }

    public List<ConversationBufferRow> selectPending(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return jdbi.withHandle(
                handle ->
                        handle.createQuery(SELECT_PENDING_SQL)
                                .bind("sessionId", sessionId)
                                .map(MysqlConversationBufferAccessor::mapRecord)
                                .list());
    }

    public List<ConversationBufferRow> selectRecent(String sessionId, int limit) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<ConversationBufferRow> descending =
                jdbi.withHandle(
                        handle ->
                                handle.createQuery(SELECT_RECENT_SQL)
                                        .bind("sessionId", sessionId)
                                        .bind("limit", limit)
                                        .map(MysqlConversationBufferAccessor::mapRecord)
                                        .list());
        Collections.reverse(descending);
        return descending;
    }

    public void markExtractedByIds(List<Long> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) {
            return;
        }
        jdbi.useHandle(
                handle -> handle.createUpdate(MARK_EXTRACTED_SQL).bindList("ids", ids).execute());
    }

    private static <StatementT extends SqlStatement<StatementT>> StatementT bindScope(
            StatementT statement, SessionScope scope) {
        return statement
                .bind("sessionId", scope.sessionId())
                .bind("userId", scope.userId())
                .bind("agentId", scope.agentId())
                .bind("memoryId", scope.memoryId());
    }

    private static ConversationBufferRow mapRecord(ResultSet resultSet, StatementContext context)
            throws SQLException {
        Message.Role role = Message.Role.valueOf(resultSet.getString("role"));
        String content = resultSet.getString("content");
        String userName = resultSet.getString("user_name");
        Timestamp timestamp = resultSet.getTimestamp("timestamp");
        Instant instant = timestamp == null ? null : timestamp.toInstant();
        Message message =
                role == Message.Role.USER
                        ? userMessage(content, instant, userName)
                        : Message.assistant(content, instant);
        return new ConversationBufferRow(
                resultSet.getLong("id"), resultSet.getString("session_id"), message);
    }

    private static Message userMessage(String content, Instant timestamp, String userName) {
        return userName == null
                ? Message.user(content, timestamp)
                : Message.user(content, timestamp, userName);
    }

    private static Timestamp timestampValue(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static SessionScope scopeOfSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
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

    private static String normalizeAgentId(String agentId) {
        return agentId == null ? "" : agentId;
    }

    private record SessionScope(String sessionId, String userId, String agentId, String memoryId) {}
}
