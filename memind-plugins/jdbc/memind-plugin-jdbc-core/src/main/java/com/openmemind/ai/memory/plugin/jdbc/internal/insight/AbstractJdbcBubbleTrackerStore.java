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
package com.openmemind.ai.memory.plugin.jdbc.internal.insight;

import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.plugin.jdbc.internal.jdbi.JdbiFactory;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcPluginException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlStatement;

public abstract class AbstractJdbcBubbleTrackerStore implements BubbleTrackerStore {

    private final Jdbi jdbi;

    protected AbstractJdbcBubbleTrackerStore(DataSource dataSource) {
        this.jdbi = JdbiFactory.create(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public int incrementAndGet(String key, int delta) {
        BubbleStateKey parsed = BubbleStateKey.parse(key);
        return inTransaction(
                connection -> {
                    try (var statement = connection.prepareStatement(upsertIncrementSql())) {
                        statement.setString(1, parsed.memoryId());
                        statement.setString(2, parsed.tier());
                        statement.setString(3, parsed.insightType());
                        statement.setInt(4, delta);
                        statement.executeUpdate();
                    }
                    return queryDirtyCount(connection, parsed);
                });
    }

    @Override
    public int getDirtyCount(String key) {
        BubbleStateKey parsed = BubbleStateKey.parse(key);
        Integer count =
                queryOne(
                        """
                        SELECT dirty_count
                        FROM memory_insight_bubble_state
                        WHERE memory_id = ? AND tier = ? AND insight_type = ?
                        """,
                        resultSet -> resultSet.getInt(1),
                        parsed.memoryId(),
                        parsed.tier(),
                        parsed.insightType());
        return count == null ? 0 : count;
    }

    @Override
    public void reset(String key) {
        BubbleStateKey parsed = BubbleStateKey.parse(key);
        update(
                """
                UPDATE memory_insight_bubble_state
                SET dirty_count = 0, updated_at = CURRENT_TIMESTAMP
                WHERE memory_id = ? AND tier = ? AND insight_type = ?
                """,
                parsed.memoryId(),
                parsed.tier(),
                parsed.insightType());
    }

    protected abstract String upsertIncrementSql();

    private int queryDirtyCount(Connection connection, BubbleStateKey parsed) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                        SELECT dirty_count
                        FROM memory_insight_bubble_state
                        WHERE memory_id = ? AND tier = ? AND insight_type = ?
                        """)) {
            statement.setString(1, parsed.memoryId());
            statement.setString(2, parsed.tier());
            statement.setString(3, parsed.insightType());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private <T> T inTransaction(TransactionCallback<T> callback) {
        return jdbi.inTransaction(
                handle -> {
                    try {
                        return callback.execute(handle.getConnection());
                    } catch (SQLException e) {
                        throw new JdbcPluginException(e);
                    }
                });
    }

    private <T> T queryOne(String sql, ResultSetMapper<T> mapper, Object... params) {
        return jdbi.withHandle(
                handle -> {
                    var query = handle.createQuery(sql);
                    bind(query, params);
                    return query.map((resultSet, context) -> mapper.map(resultSet))
                            .findOne()
                            .orElse(null);
                });
    }

    private int update(String sql, Object... params) {
        return jdbi.withHandle(
                handle -> {
                    var update = handle.createUpdate(sql);
                    bind(update, params);
                    return update.execute();
                });
    }

    private static <StatementT extends SqlStatement<StatementT>> StatementT bind(
            StatementT statement, Object... params) {
        for (int i = 0; i < params.length; i++) {
            statement.bind(i, params[i]);
        }
        return statement;
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(java.sql.ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    private interface TransactionCallback<T> {
        T execute(Connection connection) throws SQLException;
    }

    private record BubbleStateKey(String memoryId, String tier, String insightType) {

        private static BubbleStateKey parse(String key) {
            String[] parts = Objects.requireNonNull(key, "key").split("::");
            if (parts.length == 2) {
                return new BubbleStateKey(parts[0], "BRANCH", parts[1]);
            }
            if (parts.length == 3 && "root".equals(parts[1])) {
                return new BubbleStateKey(parts[0], "ROOT", parts[2]);
            }
            throw new IllegalArgumentException("Unsupported bubble tracker key: " + key);
        }
    }
}
