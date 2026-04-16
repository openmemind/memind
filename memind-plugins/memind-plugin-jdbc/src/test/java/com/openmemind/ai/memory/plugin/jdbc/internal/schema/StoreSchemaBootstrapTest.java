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
import java.util.Locale;
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
                "db/jdbc/sqlite/store/V1__init.sql",
                "db/jdbc/mysql/store/V1__init.sql",
                "db/jdbc/postgresql/store/V1__init.sql"
            })
    void freshStoreScriptsShouldNotDefineInsightConfidenceColumn(String classpathResource) {
        assertThat(normalizedSqlResource(classpathResource)).doesNotContain(" confidence ");
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

    private DataSource dataSource(Path dbPath) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        return dataSource;
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

    private String normalizedSqlResource(String classpathResource) {
        try (InputStream inputStream =
                StoreSchemaBootstrapTest.class
                        .getClassLoader()
                        .getResourceAsStream(classpathResource)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + classpathResource);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
