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
package com.openmemind.ai.memory.plugin.jdbc.internal.jdbi;

import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcPluginException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.Jdbi;

public final class JdbiExecutor {

    private static final int SQLITE_BUSY_MAX_RETRIES = 5;
    private static final long SQLITE_BUSY_RETRY_BASE_DELAY_MS = 25L;

    private final Jdbi jdbi;

    public JdbiExecutor(DataSource dataSource) {
        this(JdbiFactory.create(dataSource));
    }

    public JdbiExecutor(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public <T> T withHandle(HandleCallback<T, RuntimeException> callback) {
        return jdbi.withHandle(callback);
    }

    public <T> T inTransaction(HandleCallback<T, RuntimeException> callback) {
        return jdbi.inTransaction(callback);
    }

    public int update(String sql, Map<String, ?> params) {
        return withHandle(handle -> handle.createUpdate(sql).bindMap(params).execute());
    }

    public <T> Optional<T> queryOne(
            String sql, Map<String, ?> params, org.jdbi.v3.core.mapper.RowMapper<T> rowMapper) {
        return withHandle(
                handle -> handle.createQuery(sql).bindMap(params).map(rowMapper).findOne());
    }

    public <T> List<T> queryList(
            String sql, Map<String, ?> params, org.jdbi.v3.core.mapper.RowMapper<T> rowMapper) {
        return withHandle(handle -> handle.createQuery(sql).bindMap(params).map(rowMapper).list());
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection connection) throws Exception;
    }

    public static <T> List<T> queryList(
            DataSource dataSource, String sql, RowMapper<T> rowMapper, Object... params) {
        return new JdbiExecutor(dataSource)
                .withHandle(handle -> queryList(handle.getConnection(), sql, rowMapper, params));
    }

    public static <T> T queryOne(
            DataSource dataSource, String sql, RowMapper<T> rowMapper, Object... params) {
        List<T> results = queryList(dataSource, sql, rowMapper, params);
        return results.isEmpty() ? null : results.get(0);
    }

    public static long queryCount(DataSource dataSource, String sql, Object... params) {
        Long count = queryOne(dataSource, sql, resultSet -> resultSet.getLong(1), params);
        return count == null ? 0L : count;
    }

    public static int update(DataSource dataSource, String sql, Object... params) {
        return new JdbiExecutor(dataSource)
                .withHandle(handle -> update(handle.getConnection(), sql, params));
    }

    public static void execute(DataSource dataSource, String sql) {
        new JdbiExecutor(dataSource)
                .withHandle(
                        handle -> {
                            try (Statement statement = handle.getConnection().createStatement()) {
                                statement.execute(sql);
                                return null;
                            } catch (SQLException e) {
                                throw new JdbcPluginException(e);
                            }
                        });
    }

    public static <T> T inTransaction(DataSource dataSource, TransactionCallback<T> callback) {
        int retryCount = 0;
        while (true) {
            try {
                return inTransactionOnce(dataSource, callback);
            } catch (RuntimeException e) {
                if (!isRetryableSqliteBusy(e) || retryCount >= SQLITE_BUSY_MAX_RETRIES) {
                    throw e;
                }
                retryCount++;
                if (!sleepBeforeRetry(retryCount)) {
                    throw e;
                }
            }
        }
    }

    private static <T> T inTransactionOnce(DataSource dataSource, TransactionCallback<T> callback) {
        return new JdbiExecutor(dataSource)
                .inTransaction(
                        handle -> {
                            try {
                                return callback.execute(handle.getConnection());
                            } catch (RuntimeException e) {
                                throw e;
                            } catch (SQLException e) {
                                throw new JdbcPluginException(e);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    private static <T> List<T> queryList(
            Connection connection, String sql, RowMapper<T> rowMapper, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(rowMapper.map(resultSet));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private static int update(Connection connection, String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParams(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    private static boolean isRetryableSqliteBusy(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (!(current instanceof SQLException sqlException)) {
                continue;
            }
            String message = sqlException.getMessage();
            if (message == null) {
                continue;
            }
            String normalized = message.toUpperCase(Locale.ROOT);
            if (normalized.contains("SQLITE_BUSY")
                    || normalized.contains("SQLITE_BUSY_SNAPSHOT")
                    || normalized.contains("DATABASE IS LOCKED")) {
                return true;
            }
        }
        return false;
    }

    private static boolean sleepBeforeRetry(int retryCount) {
        long delayMs = SQLITE_BUSY_RETRY_BASE_DELAY_MS * retryCount;
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void setParams(PreparedStatement statement, Object... params)
            throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }
}
