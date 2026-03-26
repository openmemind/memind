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

import com.openmemind.ai.memory.plugin.jdbc.internal.buffer.AbstractJdbcConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;

public class MysqlConversationBuffer extends AbstractJdbcConversationBuffer {

    public MysqlConversationBuffer(DataSource dataSource) {
        this(dataSource, true);
    }

    public MysqlConversationBuffer(DataSource dataSource, boolean createIfNotExist) {
        super(dataSource);
        StoreSchemaBootstrap.ensureMysql(dataSource, createIfNotExist);
    }

    @Override
    protected String insertSql() {
        return """
        INSERT INTO memory_conversation_buffer
            (session_id, user_id, agent_id, memory_id, role, content, user_name, timestamp, deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
        """;
    }

    @Override
    protected String falseLiteral() {
        return "0";
    }

    @Override
    protected String trueLiteral() {
        return "1";
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
