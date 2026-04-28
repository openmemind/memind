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
package com.openmemind.ai.memory.plugin.jdbc.postgresql;

import com.openmemind.ai.memory.plugin.jdbc.internal.buffer.AbstractJdbcConversationBufferAccessor;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;

public class PostgresqlConversationBufferAccessor extends AbstractJdbcConversationBufferAccessor {

    public PostgresqlConversationBufferAccessor(DataSource dataSource) {
        this(dataSource, true);
    }

    public PostgresqlConversationBufferAccessor(DataSource dataSource, boolean createIfNotExist) {
        super(dataSource);
        StoreSchemaBootstrap.ensurePostgresql(dataSource, createIfNotExist);
    }

    @Override
    protected String insertSql() {
        return """
        INSERT INTO memory_conversation_buffer
            (session_id, user_id, agent_id, memory_id, role, content, user_name, source_client,
             timestamp, extracted, deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, FALSE)
        """;
    }

    @Override
    protected String falseLiteral() {
        return "FALSE";
    }

    @Override
    protected String trueLiteral() {
        return "TRUE";
    }

    @Override
    protected void bindTimestamp(PreparedStatement statement, int parameterIndex, Instant instant)
            throws SQLException {
        statement.setTimestamp(parameterIndex, instant == null ? null : Timestamp.from(instant));
    }

    @Override
    protected Instant readTimestamp(ResultSet resultSet, String columnLabel) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnLabel);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
