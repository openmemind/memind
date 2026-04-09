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

import com.openmemind.ai.memory.plugin.jdbc.internal.support.SqlScriptRunner;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class StoreSchemaBootstrapTest {

    @Test
    void ensureSqliteUpgradesLegacyStoreSchemaWithMultimodalTablesAndColumns(
            @TempDir Path tempDir) {
        DataSource dataSource = dataSource(tempDir.resolve("legacy-store.db"));
        SqlScriptRunner.execute(dataSource, "db/jdbc/sqlite/store/V1__init.sql");

        assertThat(tableExists(dataSource, "memory_resource")).isFalse();
        assertThat(columnExists(dataSource, "memory_raw_data", "resource_id")).isFalse();
        assertThat(columnExists(dataSource, "memory_raw_data", "mime_type")).isFalse();

        StoreSchemaBootstrap.ensureSqlite(dataSource, true);

        assertThat(tableExists(dataSource, "memory_resource")).isTrue();
        assertThat(columnExists(dataSource, "memory_raw_data", "resource_id")).isTrue();
        assertThat(columnExists(dataSource, "memory_raw_data", "mime_type")).isTrue();
    }

    private DataSource dataSource(Path dbPath) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        return dataSource;
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
}
