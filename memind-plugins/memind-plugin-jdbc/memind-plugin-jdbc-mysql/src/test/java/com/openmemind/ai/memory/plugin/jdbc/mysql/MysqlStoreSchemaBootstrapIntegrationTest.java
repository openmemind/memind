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
package com.openmemind.ai.memory.plugin.jdbc.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MysqlStoreSchemaBootstrapIntegrationTest {

    @Container private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void ensureMysqlRepairsLegacyMultimodalRawDataColumnsAndIndex() {
        DataSource dataSource = dataSource();
        createLegacyStoreSchema(dataSource);

        assertThat(columnExists(dataSource, "memory_raw_data", "resource_id")).isFalse();
        assertThat(columnExists(dataSource, "memory_raw_data", "mime_type")).isFalse();
        assertThat(indexNames(dataSource, "memory_raw_data"))
                .doesNotContain("idx_raw_data_resource_id");

        StoreSchemaBootstrap.ensureMysql(dataSource, true);

        assertThat(columnExists(dataSource, "memory_raw_data", "resource_id")).isTrue();
        assertThat(columnExists(dataSource, "memory_raw_data", "mime_type")).isTrue();
        assertThat(indexNames(dataSource, "memory_raw_data")).contains("idx_raw_data_resource_id");
    }

    private static void createLegacyStoreSchema(DataSource dataSource) {
        execute(
                dataSource,
                """
                CREATE TABLE memory_raw_data (
                    id                BIGINT       NOT NULL AUTO_INCREMENT,
                    biz_id            VARCHAR(64)  NOT NULL,
                    user_id           VARCHAR(64)  NOT NULL,
                    agent_id          VARCHAR(64)  NOT NULL,
                    memory_id         VARCHAR(200) NOT NULL,
                    type              VARCHAR(32)  NOT NULL,
                    segment           JSON,
                    caption           TEXT,
                    metadata          JSON,
                    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
                    deleted           TINYINT      NOT NULL DEFAULT 0,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        execute(
                dataSource,
                """
                CREATE TABLE memory_item (
                    id            BIGINT       NOT NULL AUTO_INCREMENT,
                    biz_id        BIGINT       NOT NULL,
                    user_id       VARCHAR(64)  NOT NULL,
                    agent_id      VARCHAR(64)  NOT NULL,
                    memory_id     VARCHAR(200) NOT NULL,
                    content       TEXT         NOT NULL,
                    scope         VARCHAR(16)  NOT NULL,
                    category      VARCHAR(64),
                    occurred_at   DATETIME(3),
                    observed_at   DATETIME(3),
                    type          VARCHAR(16)  NOT NULL DEFAULT 'FACT',
                    raw_data_type VARCHAR(32)  NOT NULL DEFAULT 'CONVERSATION',
                    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
                    deleted       TINYINT      NOT NULL DEFAULT 0,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        execute(
                dataSource,
                """
                CREATE TABLE memory_insight_type (
                    id   BIGINT       NOT NULL AUTO_INCREMENT,
                    name VARCHAR(128) NOT NULL,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        execute(
                dataSource,
                """
                CREATE TABLE memory_insight (
                    id        BIGINT       NOT NULL AUTO_INCREMENT,
                    biz_id    BIGINT       NOT NULL,
                    user_id   VARCHAR(64)  NOT NULL,
                    agent_id  VARCHAR(64)  NOT NULL,
                    memory_id VARCHAR(200) NOT NULL,
                    type      VARCHAR(128) NOT NULL,
                    scope     VARCHAR(16)  NOT NULL,
                    deleted   TINYINT      NOT NULL DEFAULT 0,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        execute(
                dataSource,
                """
                CREATE TABLE memory_insight_buffer (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private static void execute(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute test DDL", e);
        }
    }

    private static boolean columnExists(
            DataSource dataSource, String tableName, String columnName) {
        return exists(
                dataSource,
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
                tableName,
                columnName);
    }

    private static List<String> indexNames(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT DISTINCT INDEX_NAME
                                FROM INFORMATION_SCHEMA.STATISTICS
                                WHERE TABLE_SCHEMA = DATABASE()
                                  AND TABLE_NAME = ?
                                ORDER BY INDEX_NAME
                                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> indexNames = new ArrayList<>();
                while (resultSet.next()) {
                    indexNames.add(resultSet.getString(1));
                }
                return indexNames;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect MySQL indexes", e);
        }
    }

    private static boolean exists(DataSource dataSource, String sql, String first, String second) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, first);
            statement.setString(2, second);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect MySQL schema", e);
        }
    }

    private static DataSource dataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
    }
}
