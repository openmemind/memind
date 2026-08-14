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
package com.openmemind.ai.memory.plugin.store.mybatis.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * MyBatis TypeHandler: Converts Java {@link Instant} to and from SQL temporal columns.
 *
 * <p>Writing: {@link Instant} → {@link Timestamp} (standard JDBC).<br>
 * Reading: accepts ISO-8601 (with {@code Z}), {@code yyyy-MM-dd HH:mm:ss[.SSS]} (space-separated,
 * treated as UTC), or epoch millis.
 *
 * <p>Previously wrote {@code Instant.toString()} (ISO-8601 with {@code Z} + nanosecond precision)
 * as a String — SQLite TEXT and PostgreSQL TIMESTAMPTZ tolerated it, but MySQL {@code DATETIME}
 * rejects it ({@code Incorrect datetime value}). Using {@code setTimestamp} is portable across all
 * three drivers.
 */
@MappedTypes(Instant.class)
public class InstantTypeHandler extends BaseTypeHandler<Instant> {

    @Override
    public void setNonNullParameter(
            PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType) throws SQLException {
        ps.setTimestamp(i, Timestamp.from(parameter));
    }

    @Override
    public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return parse(value);
    }

    @Override
    public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return parse(value);
    }

    @Override
    public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return parse(value);
    }

    private Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.chars().allMatch(Character::isDigit)) {
            return Instant.ofEpochMilli(Long.parseLong(value));
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            // SQLite datetime('now') defaults to generating "yyyy-MM-dd HH:mm:ss" format (UTC)
            try {
                return Instant.parse(value.replace(" ", "T") + "Z");
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
