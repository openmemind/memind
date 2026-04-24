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
package com.openmemind.ai.memory.plugin.store.mybatis.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.sqlite.SQLiteDataSource;

@DisplayName("Memory store DDL")
class MemoryStoreDdlTest {

    private final DatabaseDialectDetector detector = new DatabaseDialectDetector();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "db/migration/sqlite/V1__init.sql",
                "db/migration/mysql/V1__init.sql",
                "db/migration/postgresql/V1__init.sql"
            })
    @DisplayName("Fresh init scripts should not define confidence column")
    void freshStoreScriptsShouldNotDefineInsightConfidenceColumn(String classpathResource) {
        assertThat(normalizedCreateTableStatement(classpathResource, "memory_insight"))
                .doesNotContain(" confidence ");
    }

    @Test
    @DisplayName("Load SQLite squashed init script")
    void loadsSqliteSquashedInitScript() {
        assertThat(
                        new MemoryStoreDdl(dataSource("SQLite", "jdbc:sqlite::memory:"), detector)
                                .getSqlFiles())
                .containsExactly("db/migration/sqlite/V1__init.sql");
    }

    @Test
    @DisplayName("Load MySQL squashed init script")
    void loadsMysqlSquashedInitScript() {
        assertThat(
                        new MemoryStoreDdl(
                                        dataSource("MySQL", "jdbc:mysql://localhost:3306/memind"),
                                        detector)
                                .getSqlFiles())
                .containsExactly("db/migration/mysql/V1__init.sql");
    }

    @Test
    @DisplayName("Load PostgreSQL squashed init script")
    void loadsPostgresqlSquashedInitScript() {
        assertThat(
                        new MemoryStoreDdl(
                                        dataSource(
                                                "PostgreSQL",
                                                "jdbc:postgresql://localhost:5432/memind"),
                                        detector)
                                .getSqlFiles())
                .containsExactly("db/migration/postgresql/V1__init.sql");
    }

    @Test
    @DisplayName("graph schema migration creates bounded-read indexes on SQLite")
    void graphSchemaMigrationCreatesBoundedReadIndexesOnSqlite(@TempDir Path tempDir) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("graph-indexes.db"));
        MemoryStoreDdl ddl = new MemoryStoreDdl(dataSource, detector);

        ddl.runScript(ds -> executeSqlResource(ds, "db/migration/sqlite/V1__init.sql"));

        assertThat(indexExists(dataSource, "uk_item_entity_mention_identity")).isTrue();
        assertThat(indexExists(dataSource, "idx_item_entity_mention_item")).isTrue();
        assertThat(indexExists(dataSource, "idx_item_entity_mention_entity_key")).isTrue();
        assertThat(indexExists(dataSource, "uk_item_link_identity")).isTrue();
        assertThat(indexExists(dataSource, "idx_item_link_source_type")).isTrue();
        assertThat(indexExists(dataSource, "idx_item_link_target_type")).isTrue();
    }

    @Test
    @DisplayName("memory thread migration creates bounded-read tables and indexes on SQLite")
    void memoryThreadSchemaMigrationCreatesTablesAndIndexesOnSqlite(@TempDir Path tempDir) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("memory-thread.db"));
        MemoryStoreDdl ddl = new MemoryStoreDdl(dataSource, detector);

        ddl.runScript(ds -> executeSqlResource(ds, "db/migration/sqlite/V1__init.sql"));

        assertThat(tableExists(dataSource, "memory_thread")).isTrue();
        assertThat(tableExists(dataSource, "memory_thread_membership")).isTrue();
        assertThat(tableExists(dataSource, "memory_thread_runtime")).isTrue();
        assertThat(tableExists(dataSource, "thread_intake_outbox")).isTrue();
        assertThat(indexExists(dataSource, "uk_memory_thread_key")).isTrue();
        assertThat(indexExists(dataSource, "uk_memory_thread_membership")).isTrue();
    }

    @Test
    @DisplayName("SQLite DDL upgrade repairs partial temporal lookup backfill and creates indexes")
    void sqliteDdlUpgradeRepairsPartialTemporalLookupColumnsAndIndexes(@TempDir Path tempDir) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("temporal-upgrade.db"));
        createLegacyTemporalStoreSchema(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("ALTER TABLE memory_item ADD COLUMN temporal_start TEXT");
        jdbcTemplate.execute("ALTER TABLE memory_item ADD COLUMN temporal_end_or_anchor TEXT");
        jdbcTemplate.execute("ALTER TABLE memory_item ADD COLUMN temporal_anchor TEXT");
        jdbcTemplate.update(
                """
                INSERT INTO memory_item
                    (biz_id, user_id, agent_id, memory_id, content, scope, category, occurred_at,
                     observed_at, type, raw_data_type, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                12L,
                "user-1",
                "agent-1",
                "user-1:agent-1",
                "legacy temporal item",
                "LONG_TERM",
                "EVENT",
                "2026-03-19T23:30:00Z",
                "2026-03-19T23:30:00Z",
                "FACT",
                "CONVERSATION",
                "2026-03-19T23:30:00Z",
                "2026-03-19T23:30:00Z");
        jdbcTemplate.update(
                """
                UPDATE memory_item
                SET temporal_start = NULL,
                    temporal_end_or_anchor = NULL,
                    temporal_anchor = ?
                WHERE biz_id = ?
                """,
                "2026-03-19T23:30:00Z",
                12L);

        MemoryStoreDdl ddl = new MemoryStoreDdl(dataSource, detector);
        ddl.runScript(ignored -> {});

        assertThat(columnExists(dataSource, "memory_item", "temporal_start")).isTrue();
        assertThat(columnExists(dataSource, "memory_item", "temporal_end_or_anchor")).isTrue();
        assertThat(columnExists(dataSource, "memory_item", "temporal_anchor")).isTrue();
        assertThat(indexExists(dataSource, "idx_item_temporal_anchor_scope")).isTrue();
        assertThat(indexExists(dataSource, "idx_item_temporal_start_scope")).isTrue();
        assertThat(indexExists(dataSource, "idx_item_temporal_end_scope")).isTrue();
        var row =
                jdbcTemplate.queryForMap(
                        "SELECT temporal_start, temporal_end_or_anchor, temporal_anchor FROM"
                                + " memory_item WHERE biz_id = ?",
                        12L);
        assertThat(row.get("temporal_start").toString()).contains("2026-03-19T23:30:00Z");
        assertThat(row.get("temporal_end_or_anchor").toString()).contains("2026-03-19T23:30:00Z");
        assertThat(row.get("temporal_anchor").toString()).contains("2026-03-19T23:30:00Z");
    }

    @Test
    @DisplayName("Seed default taxonomy only when insight type table is first created")
    void seedsDefaultsWhenInsightTypeTableIsFirstCreated(@TempDir Path tempDir) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("fresh.db"));

        MemoryStoreDdl ddl = new MemoryStoreDdl(dataSource, detector);
        ddl.runScript(ds -> executeSqlResource(ds, "db/migration/sqlite/V1__init.sql"));

        Integer count =
                new JdbcTemplate(dataSource)
                        .queryForObject("SELECT COUNT(*) FROM memory_insight_type", Integer.class);

        assertThat(count).isEqualTo(DefaultInsightTypes.all().size());
        assertThat(columnExists(dataSource, "memory_insight", "confidence")).isFalse();
    }

    @Test
    @DisplayName("Do not seed default taxonomy when insight type table already exists")
    void doesNotSeedDefaultsWhenInsightTypeTableAlreadyExists(@TempDir Path tempDir) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("existing.db"));

        executeSqlResource(dataSource, "db/migration/sqlite/V1__init.sql");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM memory_insight_type");

        MemoryStoreDdl ddl = new MemoryStoreDdl(dataSource, detector);
        ddl.runScript(ignored -> {});

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM memory_insight_type", Integer.class);

        assertThat(count).isZero();
        assertThat(columnExists(dataSource, "memory_insight", "confidence")).isFalse();
    }

    private void createLegacyTemporalStoreSchema(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(
                """
                CREATE TABLE memory_item (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    biz_id INTEGER NOT NULL,
                    user_id TEXT NOT NULL,
                    agent_id TEXT NOT NULL,
                    memory_id TEXT NOT NULL,
                    content TEXT NOT NULL,
                    scope TEXT NOT NULL,
                    category TEXT,
                    vector_id TEXT,
                    raw_data_id TEXT,
                    content_hash TEXT,
                    occurred_at TEXT,
                    occurred_start TEXT,
                    occurred_end TEXT,
                    time_granularity TEXT,
                    observed_at TEXT,
                    type TEXT NOT NULL DEFAULT 'FACT',
                    raw_data_type TEXT NOT NULL DEFAULT 'CONVERSATION',
                    metadata TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    deleted INTEGER NOT NULL DEFAULT 0
                )
                """);
    }

    private void executeSqlResource(DataSource dataSource, String classpathResource) {
        String script = loadSqlResource(classpathResource);
        try (Connection connection = dataSource.getConnection();
                java.sql.Statement statement = connection.createStatement()) {
            for (String sql : splitStatements(script)) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> splitStatements(String script) {
        List<String> statements = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inLineComment = false;
        int beginEndDepth = 0;
        for (int i = 0; i < script.length(); i++) {
            char currentChar = script.charAt(i);
            char nextChar = i + 1 < script.length() ? script.charAt(i + 1) : 0;
            if (inLineComment) {
                if (currentChar == '\n') {
                    inLineComment = false;
                    current.append(currentChar);
                }
                continue;
            }
            if (!inSingleQuote && currentChar == '-' && nextChar == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (currentChar == '\'') {
                inSingleQuote = !inSingleQuote;
            }
            if (!inSingleQuote) {
                String remaining = script.substring(i);
                if (remaining.length() >= 5
                        && remaining.substring(0, 5).equalsIgnoreCase("BEGIN")
                        && (remaining.length() == 5
                                || !Character.isLetterOrDigit(remaining.charAt(5)))) {
                    beginEndDepth++;
                } else if (remaining.length() >= 3
                        && remaining.substring(0, 3).equalsIgnoreCase("END")
                        && (remaining.length() == 3
                                || !Character.isLetterOrDigit(remaining.charAt(3)))
                        && beginEndDepth > 0) {
                    beginEndDepth--;
                }
            }
            if (currentChar == ';' && !inSingleQuote && beginEndDepth == 0) {
                String statement = current.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        return statements;
    }

    private DataSource dataSource(String productName, String url) {
        return new MetadataDataSource(productName, url);
    }

    private boolean columnExists(DataSource dataSource, String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("PRAGMA table_info(" + tableName + ")")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                        return true;
                    }
                }
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean tableExists(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT name FROM sqlite_master WHERE type = 'table' AND name ="
                                        + " ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean indexExists(DataSource dataSource, String indexName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT name FROM sqlite_master WHERE type = 'index' AND name ="
                                        + " ?")) {
            statement.setString(1, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String normalizedSqlResource(String classpathResource) {
        return loadSqlResource(classpathResource).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizedCreateTableStatement(String classpathResource, String tableName) {
        return java.util.Arrays.stream(normalizedSqlResource(classpathResource).split(";"))
                .filter(statement -> statement.contains("create table if not exists " + tableName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableName));
    }

    private String loadSqlResource(String classpathResource) {
        try (InputStream inputStream =
                MemoryStoreDdlTest.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + classpathResource);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class MetadataDataSource extends AbstractDataSource {

        private final String productName;
        private final String url;

        private MetadataDataSource(String productName, String url) {
            this.productName = productName;
            this.url = url;
        }

        @Override
        public Connection getConnection() {
            DatabaseMetaData metadata =
                    (DatabaseMetaData)
                            Proxy.newProxyInstance(
                                    DatabaseMetaData.class.getClassLoader(),
                                    new Class<?>[] {DatabaseMetaData.class},
                                    (proxy, method, args) -> {
                                        return switch (method.getName()) {
                                            case "getDatabaseProductName" -> productName;
                                            case "getURL" -> url;
                                            default -> defaultValue(method.getReturnType());
                                        };
                                    });
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> {
                                return switch (method.getName()) {
                                    case "getMetaData" -> metadata;
                                    case "close" -> null;
                                    default -> defaultValue(method.getReturnType());
                                };
                            });
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }
    }
}
