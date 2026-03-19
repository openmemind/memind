package com.openmemind.ai.memory.plugin.store.mybatis.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * MyBatis TypeHandler: Converts Java {@link Instant} to and from SQLite TEXT column.
 *
 * <p>Writing: {@code Instant} → ISO-8601 string (e.g. {@code 2024-01-01T12:00:00Z})<br>
 * Reading: ISO-8601 string → {@code Instant}
 *
 * <p>Solves the problem that SQLite JDBC cannot directly parse epoch milliseconds to Timestamp.
 *
 */
@MappedTypes(Instant.class)
public class InstantTypeHandler extends BaseTypeHandler<Instant> {

    @Override
    public void setNonNullParameter(
            PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.toString());
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
        if (value == null || value.isBlank()) return null;
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
