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
package com.openmemind.ai.memory.plugin.jdbc.internal.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sqlite.SQLiteDataSource;

class StoreSchemaBootstrapTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "db/jdbc/sqlite/V1__init.sql",
                "db/jdbc/mysql/V1__init.sql",
                "db/jdbc/postgresql/V1__init.sql"
            })
    void freshStoreScriptsShouldNotDefineInsightConfidenceColumn(String classpathResource) {
        assertThat(normalizedCreateTableStatement(classpathResource, "memory_insight"))
                .doesNotContain(" confidence ");
    }

    @Test
    void unifiedJdbcSchemaUsesSingleInitScriptPerDialect() {
        assertThat(JdbcDialectSchema.SQLITE.scriptPath()).isEqualTo("db/jdbc/sqlite/V1__init.sql");
        assertThat(JdbcDialectSchema.MYSQL.scriptPath()).isEqualTo("db/jdbc/mysql/V1__init.sql");
        assertThat(JdbcDialectSchema.POSTGRESQL.scriptPath())
                .isEqualTo("db/jdbc/postgresql/V1__init.sql");
    }

    @Test
    void ensureSqliteReportsWhenInsightTypeTableWasCreated(@TempDir Path tempDir) {
        DataSource dataSource = dataSource(tempDir.resolve("fresh.db"));

        StoreSchemaInitResult result = StoreSchemaBootstrap.ensureSqlite(dataSource, true);

        assertThat(result.createdInsightTypeTable()).isTrue();
    }

    @Test
    void ensureSqliteUpgradesLegacyStoreSchemaWithMultimodalTablesAndColumns(
            @TempDir Path tempDir) {
        DataSource dataSource = dataSource(tempDir.resolve("legacy-store.db"));
        createLegacySqliteStoreSchema(dataSource);

        assertThat(tableExists(dataSource, "memory_resource")).isFalse();
        assertThat(columnExists(dataSource, "memory_insight", "confidence")).isTrue();
        assertThat(columnExists(dataSource, "memory_raw_data", "resource_id")).isFalse();
        assertThat(columnExists(dataSource, "memory_raw_data", "mime_type")).isFalse();

        StoreSchemaInitResult result = StoreSchemaBootstrap.ensureSqlite(dataSource, true);

        assertThat(tableExists(dataSource, "memory_resource")).isTrue();
        assertThat(columnExists(dataSource, "memory_insight", "confidence")).isFalse();
        assertThat(columnExists(dataSource, "memory_raw_data", "resource_id")).isTrue();
        assertThat(columnExists(dataSource, "memory_raw_data", "mime_type")).isTrue();
        assertThat(columnExists(dataSource, "memory_item", "occurred_start")).isTrue();
        assertThat(columnExists(dataSource, "memory_item", "occurred_end")).isTrue();
        assertThat(columnExists(dataSource, "memory_item", "time_granularity")).isTrue();
        assertThat(result.createdInsightTypeTable()).isFalse();
    }

    @Test
    void ensureSqliteRepairsPartialTemporalLookupColumnsDuringBootstrap(@TempDir Path tempDir) {
        DataSource dataSource = dataSource(tempDir.resolve("bootstrap-temporal-upgrade.db"));
        createLegacySqliteStoreSchema(dataSource);
        executeStatement(dataSource, "ALTER TABLE memory_item ADD COLUMN temporal_start TEXT");
        executeStatement(
                dataSource, "ALTER TABLE memory_item ADD COLUMN temporal_end_or_anchor TEXT");
        executeStatement(dataSource, "ALTER TABLE memory_item ADD COLUMN temporal_anchor TEXT");
        executeUpdate(
                dataSource,
                """
                INSERT INTO memory_item
                    (biz_id, user_id, agent_id, memory_id, content, scope, category, occurred_at,
                     observed_at, type, raw_data_type, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                13L,
                "user-1",
                "agent-1",
                "user-1:agent-1",
                "legacy bootstrap item",
                "LONG_TERM",
                "EVENT",
                "2026-03-20T08:15:00Z",
                "2026-03-20T08:15:00Z",
                "FACT",
                "CONVERSATION",
                "2026-03-20T08:15:00Z",
                "2026-03-20T08:15:00Z");
        executeUpdate(
                dataSource,
                """
                UPDATE memory_item
                SET temporal_start = NULL,
                    temporal_end_or_anchor = NULL,
                    temporal_anchor = ?
                WHERE biz_id = ?
                """,
                "2026-03-20T08:15:00Z",
                13L);

        StoreSchemaBootstrap.ensureSqlite(dataSource, true);

        assertThat(columnExists(dataSource, "memory_item", "temporal_start")).isTrue();
        assertThat(columnExists(dataSource, "memory_item", "temporal_end_or_anchor")).isTrue();
        assertThat(columnExists(dataSource, "memory_item", "temporal_anchor")).isTrue();
        assertThat(indexExists(dataSource, "idx_item_temporal_anchor_scope")).isTrue();
        assertThat(indexExists(dataSource, "idx_item_temporal_start_scope")).isTrue();
        assertThat(indexExists(dataSource, "idx_item_temporal_end_scope")).isTrue();
        Map<String, Object> row =
                queryForMap(
                        dataSource,
                        "SELECT temporal_start, temporal_end_or_anchor, temporal_anchor FROM"
                                + " memory_item WHERE biz_id = ?",
                        13L);
        assertThat(row.get("temporal_start").toString()).contains("2026-03-20T08:15:00Z");
        assertThat(row.get("temporal_end_or_anchor").toString()).contains("2026-03-20T08:15:00Z");
        assertThat(row.get("temporal_anchor").toString()).contains("2026-03-20T08:15:00Z");
    }

    private DataSource dataSource(Path dbPath) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        return dataSource;
    }

    private void executeSqlResource(DataSource dataSource, String classpathResource) {
        String script = loadSqlResource(classpathResource);
        for (String statement : script.split(";")) {
            String sql = statement.trim();
            if (!sql.isBlank()) {
                executeStatement(dataSource, sql);
            }
        }
    }

    private void executeStatement(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void executeUpdate(DataSource dataSource, String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> queryForMap(DataSource dataSource, String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("No row returned for query: " + sql);
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("temporal_start", resultSet.getObject("temporal_start"));
                row.put("temporal_end_or_anchor", resultSet.getObject("temporal_end_or_anchor"));
                row.put("temporal_anchor", resultSet.getObject("temporal_anchor"));
                return row;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createLegacySqliteStoreSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE memory_raw_data (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        biz_id TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        agent_id TEXT NOT NULL,
                        memory_id TEXT NOT NULL,
                        type TEXT NOT NULL
                    )
                    """);
            statement.execute(
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
                        observed_at TEXT,
                        type TEXT NOT NULL DEFAULT 'FACT',
                        raw_data_type TEXT NOT NULL DEFAULT 'CONVERSATION',
                        metadata TEXT,
                        created_at TEXT NOT NULL DEFAULT (datetime('now')),
                        updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                        deleted INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE memory_insight_type (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE memory_insight (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        biz_id INTEGER NOT NULL,
                        user_id TEXT NOT NULL,
                        agent_id TEXT NOT NULL,
                        memory_id TEXT NOT NULL,
                        type TEXT NOT NULL,
                        scope TEXT NOT NULL,
                        confidence REAL DEFAULT 0
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE memory_insight_buffer (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT
                    )
                    """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean tableExists(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT name FROM sqlite_master WHERE type='table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private boolean indexExists(DataSource dataSource, String indexName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT name FROM sqlite_master WHERE type='index' AND name = ?")) {
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
                StoreSchemaBootstrapTest.class
                        .getClassLoader()
                        .getResourceAsStream(classpathResource)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + classpathResource);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .reduce(
                            new StringBuilder(),
                            (builder, line) -> builder.append(line).append('\n'),
                            StringBuilder::append)
                    .toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
