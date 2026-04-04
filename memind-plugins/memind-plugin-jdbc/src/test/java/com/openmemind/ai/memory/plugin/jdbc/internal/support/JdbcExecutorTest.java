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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcExecutorTest {

    @Test
    void retriesSqliteBusyErrorsUntilTransactionSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        DataSource dataSource = testDataSource();

        String result =
                JdbcExecutor.inTransaction(
                        dataSource,
                        connection -> {
                            int attempt = attempts.incrementAndGet();
                            if (attempt == 1) {
                                throw new SQLException(
                                        "[SQLITE_BUSY] The database file is locked"
                                                + " (database is locked)");
                            }
                            if (attempt == 2) {
                                throw new SQLException(
                                        "[SQLITE_BUSY_SNAPSHOT] Another database connection has"
                                                + " already written to the database (database is"
                                                + " locked)");
                            }
                            return "ok";
                        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryNonBusySqlErrors() {
        AtomicInteger attempts = new AtomicInteger();
        DataSource dataSource = testDataSource();

        assertThatThrownBy(
                        () ->
                                JdbcExecutor.inTransaction(
                                        dataSource,
                                        connection -> {
                                            attempts.incrementAndGet();
                                            throw new SQLException(
                                                    "UNIQUE constraint failed: memory_item.biz_id");
                                        }))
                .isInstanceOf(JdbcPluginException.class)
                .hasMessageContaining("UNIQUE constraint failed");

        assertThat(attempts).hasValue(1);
    }

    private static DataSource testDataSource() {
        return (DataSource)
                Proxy.newProxyInstance(
                        DataSource.class.getClassLoader(),
                        new Class<?>[] {DataSource.class},
                        (proxy, method, args) -> {
                            return switch (method.getName()) {
                                case "getConnection" -> testConnection();
                                case "unwrap" -> null;
                                case "isWrapperFor" -> false;
                                case "getLoginTimeout" -> 0;
                                case "getParentLogger" -> java.util.logging.Logger.getGlobal();
                                default -> null;
                            };
                        });
    }

    private static Connection testConnection() {
        AtomicInteger autoCommit = new AtomicInteger(1);
        return (Connection)
                Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            return switch (method.getName()) {
                                case "getAutoCommit" -> autoCommit.get() == 1;
                                case "setAutoCommit" -> {
                                    autoCommit.set(Boolean.TRUE.equals(args[0]) ? 1 : 0);
                                    yield null;
                                }
                                case "commit", "rollback", "close" -> null;
                                case "isClosed" -> false;
                                case "unwrap" -> null;
                                case "isWrapperFor" -> false;
                                case "toString" -> "TestConnection";
                                default ->
                                        throw new UnsupportedOperationException(
                                                "Unexpected method: " + method.getName());
                            };
                        });
    }
}
