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
package com.openmemind.ai.memory.plugin.store.mybatis.buffer;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferRow;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialect;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DialectConversationBufferAtomicOperationsTest {

    @Container private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void sqliteDrainClaimsPreexistingBatchOnceConcurrently(@TempDir Path tempDir) throws Exception {
        assertConcurrentDrainClaimsPreexistingBatchOnce(
                sqliteDataSource(tempDir.resolve("conversation.db")),
                DatabaseDialect.SQLITE,
                "sqlite-atomic-user:agent",
                TimestampMode.STRING);
    }

    @Test
    void postgresqlDrainClaimsPreexistingBatchOnceConcurrently() throws Exception {
        assertConcurrentDrainClaimsPreexistingBatchOnce(
                postgresqlDataSource(),
                DatabaseDialect.POSTGRESQL,
                "postgres-atomic-user:agent",
                TimestampMode.TIMESTAMP);
    }

    @Test
    void mysqlDrainClaimsPreexistingBatchOnceConcurrently() throws Exception {
        assertConcurrentDrainClaimsPreexistingBatchOnce(
                mysqlDataSource(),
                DatabaseDialect.MYSQL,
                "mysql-atomic-user:agent",
                TimestampMode.TIMESTAMP);
    }

    private static void assertConcurrentDrainClaimsPreexistingBatchOnce(
            DataSource dataSource,
            DatabaseDialect dialect,
            String sessionId,
            TimestampMode timestampMode)
            throws Exception {
        createConversationBufferTable(dataSource, dialect);
        int messageCount = 20;
        seedMessages(dataSource, sessionId, messageCount, timestampMode);

        var first = new DialectConversationBufferAtomicOperations(dataSource);
        var second = new DialectConversationBufferAtomicOperations(dataSource);
        List<List<ConversationBufferRow>> drained = drainConcurrently(first, second, sessionId);

        assertAtomicDrainResult(drained, messageCount);
        Integer pendingCount =
                new JdbcTemplate(dataSource)
                        .queryForObject(
                                "SELECT COUNT(*) FROM memory_conversation_buffer WHERE session_id"
                                        + " = ? AND "
                                        + pendingPredicate(dialect),
                                Integer.class,
                                sessionId);
        assertThat(pendingCount).isZero();
    }

    private static void createConversationBufferTable(
            DataSource dataSource, DatabaseDialect dialect) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        switch (dialect) {
            case SQLITE ->
                    jdbcTemplate.execute(
                            """
                            CREATE TABLE memory_conversation_buffer (
                                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                                session_id TEXT NOT NULL,
                                user_id TEXT NOT NULL,
                                agent_id TEXT NOT NULL,
                                memory_id TEXT NOT NULL,
                                role TEXT NOT NULL,
                                content TEXT NOT NULL,
                                user_name TEXT,
                                source_client TEXT,
                                timestamp TEXT,
                                extracted INTEGER NOT NULL DEFAULT 0,
                                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                                deleted INTEGER NOT NULL DEFAULT 0
                            )
                            """);
            case POSTGRESQL ->
                    jdbcTemplate.execute(
                            """
                            CREATE TABLE memory_conversation_buffer (
                                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                                session_id VARCHAR(128) NOT NULL,
                                user_id VARCHAR(64) NOT NULL,
                                agent_id VARCHAR(64) NOT NULL,
                                memory_id VARCHAR(200) NOT NULL,
                                role VARCHAR(16) NOT NULL,
                                content TEXT NOT NULL,
                                user_name VARCHAR(64),
                                source_client VARCHAR(64),
                                timestamp TIMESTAMPTZ,
                                extracted BOOLEAN NOT NULL DEFAULT FALSE,
                                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                deleted BOOLEAN NOT NULL DEFAULT FALSE
                            )
                            """);
            case MYSQL ->
                    jdbcTemplate.execute(
                            """
                            CREATE TABLE memory_conversation_buffer (
                                id BIGINT NOT NULL AUTO_INCREMENT,
                                session_id VARCHAR(128) NOT NULL,
                                user_id VARCHAR(64) NOT NULL,
                                agent_id VARCHAR(64) NOT NULL,
                                memory_id VARCHAR(200) NOT NULL,
                                role VARCHAR(16) NOT NULL,
                                content TEXT NOT NULL,
                                user_name VARCHAR(64),
                                source_client VARCHAR(64),
                                timestamp DATETIME(3),
                                extracted TINYINT NOT NULL DEFAULT 0,
                                updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                    ON UPDATE CURRENT_TIMESTAMP(3),
                                deleted TINYINT NOT NULL DEFAULT 0,
                                PRIMARY KEY (id)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                            """);
        }
        jdbcTemplate.execute(
                """
                CREATE INDEX idx_buf_pending
                ON memory_conversation_buffer(session_id, extracted, deleted, id)
                """);
    }

    private static List<List<ConversationBufferRow>> drainConcurrently(
            ConversationBufferAtomicOperations first,
            ConversationBufferAtomicOperations second,
            String sessionId)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<List<ConversationBufferRow>> firstDrain = () -> first.drainPending(sessionId);
            Callable<List<ConversationBufferRow>> secondDrain =
                    () -> second.drainPending(sessionId);
            Future<List<ConversationBufferRow>> firstResult = executor.submit(firstDrain);
            Future<List<ConversationBufferRow>> secondResult = executor.submit(secondDrain);
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertAtomicDrainResult(
            List<List<ConversationBufferRow>> drained, int messageCount) {
        List<ConversationBufferRow> combined = drained.stream().flatMap(List::stream).toList();

        assertThat(combined).hasSize(messageCount);
        assertThat(combined)
                .extracting(ConversationBufferRow::getContent)
                .containsExactlyInAnyOrderElementsOf(expectedMessages(messageCount));
        assertThat(combined).extracting(ConversationBufferRow::getSourceClient).containsOnly("web");
        assertThat(drained.stream().map(List::size).sorted().toList())
                .containsExactly(0, messageCount);
    }

    private static void seedMessages(
            DataSource dataSource, String sessionId, int messageCount, TimestampMode timestampMode)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO memory_conversation_buffer
                                    (session_id, user_id, agent_id, memory_id, role, content,
                                     user_name, source_client, timestamp)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """)) {
            for (int i = 0; i < messageCount; i++) {
                Instant timestamp = Instant.parse("2026-04-01T00:00:00Z").plusSeconds(i);
                statement.setString(1, sessionId);
                statement.setString(2, "user");
                statement.setString(3, "agent");
                statement.setString(4, sessionId);
                statement.setString(5, "USER");
                statement.setString(6, "message-" + i);
                statement.setString(7, null);
                statement.setString(8, "web");
                if (timestampMode == TimestampMode.STRING) {
                    statement.setString(9, timestamp.toString());
                } else {
                    statement.setTimestamp(9, Timestamp.from(timestamp));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static String pendingPredicate(DatabaseDialect dialect) {
        return switch (dialect) {
            case POSTGRESQL -> "extracted = FALSE AND deleted = FALSE";
            case MYSQL, SQLITE -> "extracted = 0 AND deleted = 0";
        };
    }

    private static List<String> expectedMessages(int messageCount) {
        List<String> expected = new ArrayList<>(messageCount);
        for (int i = 0; i < messageCount; i++) {
            expected.add("message-" + i);
        }
        return expected;
    }

    private static DataSource sqliteDataSource(Path path) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + path + "?busy_timeout=5000");
        return dataSource;
    }

    private static DataSource postgresqlDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static DataSource mysqlDataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
    }

    private enum TimestampMode {
        STRING,
        TIMESTAMP
    }
}
