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

import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlConversationBufferAccessor;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlInsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlMemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlMemoryTextSearch;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlRecentConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlConversationBufferAccessor;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlInsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlMemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlMemoryTextSearch;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlRecentConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteConversationBufferAccessor;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteInsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteMemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteMemoryTextSearch;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteRecentConversationBuffer;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * User-facing helper for creating JDBC-backed memind runtime components.
 */
public final class JdbcStore {

    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";

    private JdbcStore() {}

    public static JdbcMemoryAccess sqlite(String path) {
        return sqlite(path, true);
    }

    public static JdbcMemoryAccess sqlite(String path, boolean createIfNotExist) {
        requireText(path, "path");
        return create(
                Dialect.SQLITE,
                new DriverManagerDataSource(SQLITE_DRIVER, "jdbc:sqlite:" + path, null, null),
                createIfNotExist);
    }

    public static JdbcMemoryAccess sqlite(DataSource dataSource) {
        return sqlite(dataSource, true);
    }

    public static JdbcMemoryAccess sqlite(DataSource dataSource, boolean createIfNotExist) {
        return create(
                Dialect.SQLITE, Objects.requireNonNull(dataSource, "dataSource"), createIfNotExist);
    }

    public static JdbcMemoryAccess mysql(String url, String username, String password) {
        return mysql(url, username, password, true);
    }

    public static JdbcMemoryAccess mysql(
            String url, String username, String password, boolean createIfNotExist) {
        return create(
                Dialect.MYSQL,
                new DriverManagerDataSource(
                        MYSQL_DRIVER,
                        requireText(url, "url"),
                        requireText(username, "username"),
                        requireText(password, "password")),
                createIfNotExist);
    }

    public static JdbcMemoryAccess mysql(DataSource dataSource) {
        return mysql(dataSource, true);
    }

    public static JdbcMemoryAccess mysql(DataSource dataSource, boolean createIfNotExist) {
        return create(
                Dialect.MYSQL, Objects.requireNonNull(dataSource, "dataSource"), createIfNotExist);
    }

    public static JdbcMemoryAccess postgresql(String url, String username, String password) {
        return postgresql(url, username, password, true);
    }

    public static JdbcMemoryAccess postgresql(
            String url, String username, String password, boolean createIfNotExist) {
        return create(
                Dialect.POSTGRESQL,
                new DriverManagerDataSource(
                        POSTGRESQL_DRIVER,
                        requireText(url, "url"),
                        requireText(username, "username"),
                        requireText(password, "password")),
                createIfNotExist);
    }

    public static JdbcMemoryAccess postgresql(DataSource dataSource) {
        return postgresql(dataSource, true);
    }

    public static JdbcMemoryAccess postgresql(DataSource dataSource, boolean createIfNotExist) {
        return create(
                Dialect.POSTGRESQL,
                Objects.requireNonNull(dataSource, "dataSource"),
                createIfNotExist);
    }

    private static JdbcMemoryAccess create(
            Dialect dialect, DataSource dataSource, boolean createIfNotExist) {
        return switch (dialect) {
            case SQLITE -> {
                var store = new SqliteMemoryStore(dataSource, createIfNotExist);
                var insightBufferStore = new SqliteInsightBuffer(dataSource, createIfNotExist);
                var conversationBufferAccessor =
                        new SqliteConversationBufferAccessor(dataSource, createIfNotExist);
                var memoryBuffer =
                        MemoryBuffer.of(
                                insightBufferStore,
                                new SqliteConversationBuffer(conversationBufferAccessor),
                                new SqliteRecentConversationBuffer(conversationBufferAccessor));
                yield new DefaultJdbcMemoryAccess(
                        store,
                        memoryBuffer,
                        new SqliteMemoryTextSearch(dataSource, createIfNotExist),
                        dataSource);
            }
            case MYSQL -> {
                var store = new MysqlMemoryStore(dataSource, createIfNotExist);
                var insightBufferStore = new MysqlInsightBuffer(dataSource, createIfNotExist);
                var conversationBufferAccessor =
                        new MysqlConversationBufferAccessor(dataSource, createIfNotExist);
                var memoryBuffer =
                        MemoryBuffer.of(
                                insightBufferStore,
                                new MysqlConversationBuffer(conversationBufferAccessor),
                                new MysqlRecentConversationBuffer(conversationBufferAccessor));
                yield new DefaultJdbcMemoryAccess(
                        store,
                        memoryBuffer,
                        new MysqlMemoryTextSearch(dataSource, createIfNotExist),
                        dataSource);
            }
            case POSTGRESQL -> {
                var store = new PostgresqlMemoryStore(dataSource, createIfNotExist);
                var insightBufferStore = new PostgresqlInsightBuffer(dataSource, createIfNotExist);
                var conversationBufferAccessor =
                        new PostgresqlConversationBufferAccessor(dataSource, createIfNotExist);
                var memoryBuffer =
                        MemoryBuffer.of(
                                insightBufferStore,
                                new PostgresqlConversationBuffer(conversationBufferAccessor),
                                new PostgresqlRecentConversationBuffer(conversationBufferAccessor));
                yield new DefaultJdbcMemoryAccess(
                        store,
                        memoryBuffer,
                        new PostgresqlMemoryTextSearch(dataSource, createIfNotExist),
                        dataSource);
            }
        };
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private enum Dialect {
        SQLITE,
        MYSQL,
        POSTGRESQL
    }

    private record DefaultJdbcMemoryAccess(
            MemoryStore store,
            MemoryBuffer buffer,
            MemoryTextSearch textSearch,
            DataSource dataSource)
            implements JdbcMemoryAccess {

        private DefaultJdbcMemoryAccess {
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(buffer, "buffer");
            Objects.requireNonNull(textSearch, "textSearch");
            Objects.requireNonNull(dataSource, "dataSource");
        }

        @Override
        public void close() throws Exception {
            RuntimeException closeFailure = null;
            for (AutoCloseable closeable : uniqueCloseables(textSearch, buffer, store)) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    if (closeFailure == null) {
                        closeFailure =
                                new IllegalStateException("Failed to close JDBC memory access", e);
                    } else {
                        closeFailure.addSuppressed(e);
                    }
                }
            }
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    private static List<AutoCloseable> uniqueCloseables(Object... candidates) {
        List<AutoCloseable> ordered = new ArrayList<>();
        IdentityHashMap<AutoCloseable, Boolean> seen = new IdentityHashMap<>();
        for (Object candidate : candidates) {
            if (!(candidate instanceof AutoCloseable closeable)) {
                continue;
            }
            if (seen.put(closeable, Boolean.TRUE) == null) {
                ordered.add(closeable);
            }
        }
        return ordered;
    }

    private static final class DriverManagerDataSource implements DataSource {

        private final String driverClassName;
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private PrintWriter logWriter;
        private int loginTimeout;

        private DriverManagerDataSource(
                String driverClassName, String jdbcUrl, String username, String password) {
            this.driverClassName = Objects.requireNonNull(driverClassName, "driverClassName");
            this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
            this.username = username;
            this.password = password;
            try {
                Class.forName(driverClassName);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                        "Failed to load JDBC driver: " + driverClassName, e);
            }
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (username == null || username.isBlank()) {
                return DriverManager.getConnection(jdbcUrl);
            }
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            this.logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            this.loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("DriverManagerDataSource does not support unwrap");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
