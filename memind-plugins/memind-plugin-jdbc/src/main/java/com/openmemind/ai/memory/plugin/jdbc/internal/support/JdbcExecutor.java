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
package com.openmemind.ai.memory.plugin.jdbc.internal.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;

public final class JdbcExecutor {

    private static final int SQLITE_BUSY_MAX_RETRIES = 5;
    private static final long SQLITE_BUSY_RETRY_BASE_DELAY_MS = 25L;

    private JdbcExecutor() {}

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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            setParams(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
    }

    public static void execute(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        }
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
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = callback.execute(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (JdbcPluginException e) {
            throw e;
        } catch (SQLException e) {
            throw new JdbcPluginException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
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
