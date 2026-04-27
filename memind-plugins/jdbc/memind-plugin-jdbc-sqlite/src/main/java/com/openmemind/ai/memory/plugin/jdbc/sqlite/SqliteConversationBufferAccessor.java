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
package com.openmemind.ai.memory.plugin.jdbc.sqlite;

import com.openmemind.ai.memory.plugin.jdbc.internal.buffer.AbstractJdbcConversationBufferAccessor;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javax.sql.DataSource;

public class SqliteConversationBufferAccessor extends AbstractJdbcConversationBufferAccessor {

    private static final DateTimeFormatter SQLITE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SqliteConversationBufferAccessor(DataSource dataSource) {
        this(dataSource, true);
    }

    public SqliteConversationBufferAccessor(DataSource dataSource, boolean createIfNotExist) {
        super(dataSource);
        StoreSchemaBootstrap.ensureSqlite(dataSource, createIfNotExist);
    }

    @Override
    protected String insertSql() {
        return """
        INSERT INTO memory_conversation_buffer
            (session_id, user_id, agent_id, memory_id, role, content, user_name, timestamp, extracted, deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
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
        statement.setString(parameterIndex, instant == null ? null : instant.toString());
    }

    @Override
    protected Instant readTimestamp(ResultSet resultSet, String columnLabel) throws SQLException {
        return parseInstant(resultSet.getString(columnLabel));
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, SQLITE_TIME_FORMATTER).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("Unsupported datetime value: " + value);
    }
}
