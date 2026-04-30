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
package com.openmemind.ai.memory.plugin.jdbc.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.textsearch.TextSearchResult;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SqliteMemoryTextSearchTest {

    @TempDir Path tempDir;

    private final TestMemoryId memoryId = new TestMemoryId("u1", "a1");

    private DataSource dataSource;
    private MemoryTextSearch textSearch;

    @BeforeEach
    void setUp() {
        dataSource = dataSource("sqlite-textsearch.db");
        textSearch = new SqliteMemoryTextSearch(dataSource);
    }

    @Test
    void constructorInitializesStoreAndTextSearchSchemaByDefault() {
        DataSource emptyDataSource = dataSource("text-search-auto-init.db");

        new SqliteMemoryTextSearch(emptyDataSource);

        assertThat(tableExists(emptyDataSource, "memory_item")).isTrue();
        assertThat(tableExists(emptyDataSource, "item_fts")).isTrue();
        assertThat(tableExists(emptyDataSource, "insight_fts")).isTrue();
        assertThat(tableExists(emptyDataSource, "raw_data_fts")).isTrue();
    }

    @Test
    void constructorWithCreateIfNotExistFalseFailsOnEmptyDatabase() {
        DataSource emptyDataSource = dataSource("text-search-no-auto-init.db");

        assertThatThrownBy(() -> new SqliteMemoryTextSearch(emptyDataSource, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("memory_raw_data");
    }

    @Test
    void searchItemFindsInsertedContent() {
        executeUpdate(
                """
                INSERT INTO memory_item
                    (biz_id, user_id, agent_id, memory_id, content, scope, occurred_at, type, raw_data_type)
                VALUES (?, ?, ?, ?, ?, ?, datetime('now'), 'FACT', 'CONVERSATION')
                """,
                1L,
                memoryId.userId(),
                memoryId.agentId(),
                memoryId.toIdentifier(),
                "I love programming in Java and SQLite",
                "USER");

        List<TextSearchResult> results =
                textSearch
                        .search(memoryId, "programming", 10, MemoryTextSearch.SearchTarget.ITEM)
                        .block();

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).documentId()).isEqualTo("1");
        assertThat(results.get(0).text()).contains("programming");
    }

    @Test
    void searchInsightFindsInsertedContent() {
        executeUpdate(
                """
                INSERT INTO memory_insight
                    (biz_id, user_id, agent_id, memory_id, type, scope, content, points)
                VALUES (?, ?, ?, ?, ?, ?, ?, '[]')
                """,
                9L,
                memoryId.userId(),
                memoryId.agentId(),
                memoryId.toIdentifier(),
                "profile",
                "USER",
                "The user prefers concise engineering communication");

        List<TextSearchResult> results =
                textSearch
                        .search(memoryId, "engineering", 10, MemoryTextSearch.SearchTarget.INSIGHT)
                        .block();

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).documentId()).isEqualTo("9");
        assertThat(results.get(0).text()).contains("engineering");
    }

    @Test
    void searchRawDataKeepsStringDocumentId() {
        executeUpdate(
                """
                INSERT INTO memory_raw_data
                    (biz_id, user_id, agent_id, memory_id, type, content_id, segment)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "raw-1",
                memoryId.userId(),
                memoryId.agentId(),
                memoryId.toIdentifier(),
                "CONVERSATION",
                "content-1",
                "{\"content\":\"The raw text mentions trigram search support\"}");

        List<TextSearchResult> results =
                textSearch
                        .search(memoryId, "trigram", 10, MemoryTextSearch.SearchTarget.RAW_DATA)
                        .block();

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).documentId()).isEqualTo("raw-1");
        assertThat(results.get(0).text()).contains("trigram");
    }

    @Test
    void searchIgnoresSoftDeletedItem() {
        executeUpdate(
                """
                INSERT INTO memory_item
                    (biz_id, user_id, agent_id, memory_id, content, scope, occurred_at, type, raw_data_type)
                VALUES (?, ?, ?, ?, ?, ?, datetime('now'), 'FACT', 'CONVERSATION')
                """,
                3L,
                memoryId.userId(),
                memoryId.agentId(),
                memoryId.toIdentifier(),
                "This item should disappear from the FTS index",
                "USER");
        executeUpdate(
                "UPDATE memory_item SET deleted = 1 WHERE user_id = ? AND agent_id = ? AND biz_id ="
                        + " ?",
                memoryId.userId(),
                memoryId.agentId(),
                3L);

        List<TextSearchResult> results =
                textSearch
                        .search(memoryId, "disappear", 10, MemoryTextSearch.SearchTarget.ITEM)
                        .block();

        assertThat(results).isEmpty();
    }

    @Test
    void searchBlankOrMissingQueryReturnsEmpty() {
        assertThat(textSearch.search(memoryId, " ", 10, MemoryTextSearch.SearchTarget.ITEM).block())
                .isEmpty();
        assertThat(
                        textSearch
                                .search(
                                        memoryId,
                                        "nonexistent",
                                        10,
                                        MemoryTextSearch.SearchTarget.ITEM)
                                .block())
                .isEmpty();
    }

    private DataSource dataSource(String fileName) {
        SQLiteDataSource sqliteDataSource = new SQLiteDataSource();
        sqliteDataSource.setUrl("jdbc:sqlite:" + tempDir.resolve(fileName));
        return sqliteDataSource;
    }

    private boolean tableExists(DataSource targetDataSource, String tableName) {
        try (Connection connection = targetDataSource.getConnection();
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

    private void executeUpdate(String sql, Object... params) {
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

    private record TestMemoryId(String userId, String agentId) implements MemoryId {

        @Override
        public String toIdentifier() {
            return userId + ":" + agentId;
        }

        @Override
        public String getAttribute(String key) {
            return switch (key) {
                case "userId" -> userId;
                case "agentId" -> agentId;
                default -> null;
            };
        }
    }
}
