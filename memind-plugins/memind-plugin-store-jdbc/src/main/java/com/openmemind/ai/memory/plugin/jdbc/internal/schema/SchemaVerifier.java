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

import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcPluginException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

public final class SchemaVerifier {

    private SchemaVerifier() {}

    public static boolean hasSqliteTable(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT name FROM sqlite_master WHERE type='table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new JdbcPluginException(
                    "Failed to verify SQLite table existence: " + tableName, e);
        }
    }

    public static boolean hasMysqlTable(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT TABLE_NAME
                                FROM INFORMATION_SCHEMA.TABLES
                                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new JdbcPluginException(
                    "Failed to verify MySQL table existence: " + tableName, e);
        }
    }

    public static boolean hasPostgresqlTable(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT TABLE_NAME
                                FROM INFORMATION_SCHEMA.TABLES
                                WHERE TABLE_SCHEMA = current_schema() AND TABLE_NAME = ?
                                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new JdbcPluginException(
                    "Failed to verify PostgreSQL table existence: " + tableName, e);
        }
    }

    public static boolean hasPostgresqlIndex(
            DataSource dataSource, String tableName, String indexName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT indexname
                                FROM pg_indexes
                                WHERE schemaname = current_schema() AND tablename = ? AND indexname = ?
                                """)) {
            statement.setString(1, tableName);
            statement.setString(2, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new JdbcPluginException(
                    "Failed to verify PostgreSQL index existence: " + tableName + "." + indexName,
                    e);
        }
    }

    public static boolean hasPostgresqlExtension(DataSource dataSource, String extensionName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT extname FROM pg_extension WHERE extname = ?")) {
            statement.setString(1, extensionName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new JdbcPluginException(
                    "Failed to verify PostgreSQL extension existence: " + extensionName, e);
        }
    }

    public static boolean hasMysqlColumn(
            DataSource dataSource, String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT COLUMN_NAME
                                FROM INFORMATION_SCHEMA.COLUMNS
                                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new JdbcPluginException(
                    "Failed to verify MySQL column existence: " + tableName + "." + columnName, e);
        }
    }

    public static boolean hasMysqlIndex(DataSource dataSource, String tableName, String indexName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT INDEX_NAME
                                FROM INFORMATION_SCHEMA.STATISTICS
                                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                                """)) {
            statement.setString(1, tableName);
            statement.setString(2, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new JdbcPluginException(
                    "Failed to verify MySQL index existence: " + tableName + "." + indexName, e);
        }
    }
}
