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
package com.openmemind.ai.memory.plugin.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteInsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteMemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteMemoryTextSearch;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcStoreTest {

    @Test
    void sqliteHelperCreatesMemoryStoreAndTextSearch(@TempDir Path tempDir) {
        JdbcMemoryAccess jdbc = JdbcStore.sqlite(tempDir.resolve("memind.db").toString());

        assertThat(jdbc.store()).isInstanceOf(MemoryStore.class);
        assertThat(jdbc.buffer()).isInstanceOf(MemoryBuffer.class);
        assertThat(jdbc.store().rawDataOperations()).isInstanceOf(SqliteMemoryStore.class);
        assertThat(jdbc.store().itemOperations()).isInstanceOf(SqliteMemoryStore.class);
        assertThat(jdbc.store().insightOperations()).isInstanceOf(SqliteMemoryStore.class);
        assertThat(jdbc.buffer().insightBuffer()).isInstanceOf(SqliteInsightBuffer.class);
        assertThat(jdbc.buffer().pendingConversationBuffer())
                .isInstanceOf(SqliteConversationBuffer.class);
        assertThat(jdbc.textSearch()).isInstanceOf(SqliteMemoryTextSearch.class);
    }

    @Test
    void sqliteDataSourceHelperUsesProvidedDataSource(@TempDir Path tempDir) {
        TrackingDataSource dataSource =
                new TrackingDataSource("jdbc:sqlite:" + tempDir.resolve("memind-ds.db"));

        JdbcMemoryAccess jdbc = JdbcStore.sqlite(dataSource);

        assertThat(jdbc.dataSource()).isSameAs(dataSource);
        assertThat(dataSource.connectionCount()).isGreaterThan(0);
    }

    @Test
    void mysqlHelperRequiresUrlUsernameAndPassword() {
        assertThatThrownBy(() -> JdbcStore.mysql("jdbc:mysql://localhost:3306/memind", "", "pwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");

        assertThatThrownBy(() -> JdbcStore.mysql("", "root", "pwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");

        assertThatThrownBy(() -> JdbcStore.mysql("jdbc:mysql://localhost:3306/memind", "root", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    @Test
    void postgresqlHelperRequiresUrlUsernameAndPassword() {
        assertThatThrownBy(
                        () ->
                                JdbcStore.postgresql(
                                        "jdbc:postgresql://localhost:5432/memind", "", "pwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");

        assertThatThrownBy(() -> JdbcStore.postgresql("", "postgres", "pwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");

        assertThatThrownBy(
                        () ->
                                JdbcStore.postgresql(
                                        "jdbc:postgresql://localhost:5432/memind", "postgres", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    private static final class TrackingDataSource implements DataSource {

        private final String jdbcUrl;
        private final AtomicInteger connectionCount = new AtomicInteger();

        private TrackingDataSource(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Failed to load SQLite JDBC driver", e);
            }
        }

        @Override
        public Connection getConnection() throws SQLException {
            connectionCount.incrementAndGet();
            return DriverManager.getConnection(jdbcUrl);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        int connectionCount() {
            return connectionCount.get();
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {}

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("TrackingDataSource does not support unwrap");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
