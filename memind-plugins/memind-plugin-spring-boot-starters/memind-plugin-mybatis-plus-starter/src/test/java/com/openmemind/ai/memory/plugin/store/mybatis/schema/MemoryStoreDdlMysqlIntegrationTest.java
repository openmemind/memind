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

import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.extension.ddl.DdlScriptErrorHandler.ThrowsErrorHandler;
import com.mysql.cj.jdbc.MysqlDataSource;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MemoryStoreDdlMysqlIntegrationTest {

    @Container private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @BeforeEach
    void cleanDatabase() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        List<String> tables =
                jdbcTemplate.queryForList(
                        """
                        SELECT TABLE_NAME
                        FROM INFORMATION_SCHEMA.TABLES
                        WHERE TABLE_SCHEMA = DATABASE()
                        """,
                        String.class);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String table : tables) {
                jdbcTemplate.execute("DROP TABLE `" + table.replace("`", "``") + "`");
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    @Test
    void mybatisPlusDdlRunnerLoadsMysqlSquashedSchema() throws Exception {
        DataSource dataSource = dataSource();
        MemoryStoreDdl ddl = new MemoryStoreDdl(dataSource, new DatabaseDialectDetector());
        DdlApplicationRunner runner = new DdlApplicationRunner(List.of(ddl));
        runner.setThrowException(true);
        runner.setDdlScriptErrorHandler(ThrowsErrorHandler.INSTANCE);

        runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(columnExists(dataSource, "memory_raw_data", "segment_content")).isTrue();
        assertThat(columnExists(dataSource, "memory_raw_data", "resource_id")).isTrue();
        assertThat(columnExists(dataSource, "memory_raw_data", "mime_type")).isTrue();
        assertThat(indexExists(dataSource, "memory_conversation_buffer", "idx_buf_pending"))
                .isTrue();
        assertThat(indexExists(dataSource, "memory_raw_data", "ft_raw_data_segment_content"))
                .isTrue();
        assertThat(indexExists(dataSource, "memory_raw_data", "idx_raw_data_resource_id")).isTrue();
    }

    @Test
    void runScriptRepairsLegacyMysqlMultimodalRawDataColumnsAndIndexWhenInitScriptIsSkipped() {
        DataSource dataSource = dataSource();
        createLegacyStoreSchema(dataSource);
        MemoryStoreDdl ddl = new MemoryStoreDdl(dataSource, new DatabaseDialectDetector());

        assertThat(columnExists(dataSource, "memory_raw_data", "resource_id")).isFalse();
        assertThat(columnExists(dataSource, "memory_raw_data", "mime_type")).isFalse();
        assertThat(indexExists(dataSource, "memory_raw_data", "idx_raw_data_resource_id"))
                .isFalse();

        ddl.runScript(ignored -> {});

        assertThat(columnExists(dataSource, "memory_raw_data", "resource_id")).isTrue();
        assertThat(columnExists(dataSource, "memory_raw_data", "mime_type")).isTrue();
        assertThat(indexExists(dataSource, "memory_raw_data", "idx_raw_data_resource_id")).isTrue();
    }

    private static void createLegacyStoreSchema(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(
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
        jdbcTemplate.execute(
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
        jdbcTemplate.execute(
                """
                CREATE TABLE memory_insight_type (
                    id   BIGINT       NOT NULL AUTO_INCREMENT,
                    name VARCHAR(128) NOT NULL,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute(
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
        jdbcTemplate.execute(
                """
                CREATE TABLE memory_insight_buffer (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private static boolean columnExists(
            DataSource dataSource, String tableName, String columnName) {
        Integer count =
                new JdbcTemplate(dataSource)
                        .queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM INFORMATION_SCHEMA.COLUMNS
                                WHERE TABLE_SCHEMA = DATABASE()
                                  AND TABLE_NAME = ?
                                  AND COLUMN_NAME = ?
                                """,
                                Integer.class,
                                tableName,
                                columnName);
        return count != null && count > 0;
    }

    private static boolean indexExists(DataSource dataSource, String tableName, String indexName) {
        Integer count =
                new JdbcTemplate(dataSource)
                        .queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM INFORMATION_SCHEMA.STATISTICS
                                WHERE TABLE_SCHEMA = DATABASE()
                                  AND TABLE_NAME = ?
                                  AND INDEX_NAME = ?
                                """,
                                Integer.class,
                                tableName,
                                indexName);
        return count != null && count > 0;
    }

    private static DataSource dataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
    }
}
