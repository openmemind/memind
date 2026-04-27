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

import com.openmemind.ai.memory.core.buffer.BufferEntry;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
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

class SqliteInsightBufferTest {

    @TempDir Path tempDir;

    private final MemoryId memoryId = DefaultMemoryId.of("u1", "a1");

    private DataSource dataSource;
    private SqliteInsightBuffer store;

    @BeforeEach
    void setUp() {
        dataSource = dataSource("insight-buffer.db");
        store = new SqliteInsightBuffer(dataSource);
    }

    @Test
    void constructorInitializesSchemaByDefault() {
        DataSource emptyDataSource = dataSource("insight-buffer-auto-init.db");

        new SqliteInsightBuffer(emptyDataSource);

        assertThat(tableExists(emptyDataSource, "memory_insight_buffer")).isTrue();
    }

    @Test
    void constructorWithCreateIfNotExistFalseFailsOnEmptyDatabase() {
        DataSource emptyDataSource = dataSource("insight-buffer-no-auto-init.db");

        assertThatThrownBy(() -> new SqliteInsightBuffer(emptyDataSource, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("memory_raw_data");
    }

    @Test
    void appendIgnoresDuplicatesAndReturnsUngroupedEntries() {
        store.append(memoryId, "profile", List.of(1L, 2L));
        store.append(memoryId, "profile", List.of(2L, 3L));

        assertThat(store.countUnGrouped(memoryId, "profile")).isEqualTo(3);
        assertThat(store.getUnGrouped(memoryId, "profile"))
                .extracting(BufferEntry::itemId)
                .containsExactly(1L, 2L, 3L);
        assertThat(store.listGroups(memoryId, "profile")).isEmpty();
    }

    @Test
    void assignGroupAndMarkBuiltPersistState() {
        store.append(memoryId, "profile", List.of(1L, 2L, 3L));

        store.assignGroup(memoryId, "profile", List.of(1L, 2L, 3L), "food");

        assertThat(store.countUnGrouped(memoryId, "profile")).isZero();
        assertThat(store.countGroupUnbuilt(memoryId, "profile", "food")).isEqualTo(3);
        assertThat(store.listGroups(memoryId, "profile")).containsExactly("food");

        store.markBuilt(memoryId, "profile", List.of(1L, 2L));

        assertThat(store.getGroupUnbuilt(memoryId, "profile", "food"))
                .extracting(BufferEntry::itemId)
                .containsExactly(3L);
        assertThat(store.getUnbuiltByGroup(memoryId, "profile"))
                .containsOnlyKeys("food")
                .containsEntry("food", List.of(new BufferEntry(3L, "food", false)));
        assertThat(store.hasWork(memoryId, "profile")).isTrue();

        store.markBuilt(memoryId, "profile", List.of(3L));

        assertThat(store.listGroups(memoryId, "profile")).containsExactly("food");
        assertThat(store.getGroupUnbuilt(memoryId, "profile", "food")).isEmpty();
        assertThat(store.hasWork(memoryId, "profile")).isFalse();
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
}
