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
package com.openmemind.ai.memory.plugin.jdbc.internal.jdbi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class JdbiExecutorTest {

    @TempDir private Path tempDir;

    private JdbiExecutor executor;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("jdbi-executor-test.db"));
        executor = new JdbiExecutor(dataSource);
        executor.withHandle(
                handle -> {
                    handle.execute(
                            """
                            CREATE TABLE sample (
                                id INTEGER PRIMARY KEY,
                                name TEXT NOT NULL
                            )
                            """);
                    return null;
                });
    }

    @Test
    void queryOneReturnsEmptyWhenNoRowExists() {
        Optional<String> result =
                executor.queryOne(
                        "SELECT name FROM sample WHERE id = :id",
                        Map.of("id", 404),
                        (resultSet, context) -> resultSet.getString("name"));

        assertThat(result).isEmpty();
    }

    @Test
    void queryListBindsNamedParameters() {
        executor.update(
                "INSERT INTO sample(id, name) VALUES (:id, :name)",
                Map.of("id", 1, "name", "alpha"));
        executor.update(
                "INSERT INTO sample(id, name) VALUES (:id, :name)",
                Map.of("id", 2, "name", "beta"));

        List<String> result =
                executor.queryList(
                        "SELECT name FROM sample WHERE id >= :minId ORDER BY id",
                        Map.of("minId", 2),
                        (resultSet, context) -> resultSet.getString("name"));

        assertThat(result).containsExactly("beta");
    }

    @Test
    void updateReturnsAffectedRowCount() {
        int rows =
                executor.update(
                        "INSERT INTO sample(id, name) VALUES (:id, :name)",
                        Map.of("id", 1, "name", "alpha"));

        assertThat(rows).isEqualTo(1);
    }

    @Test
    void inTransactionRollsBackOnException() {
        assertThatThrownBy(
                        () ->
                                executor.inTransaction(
                                        handle -> {
                                            handle.createUpdate(
                                                            "INSERT INTO sample(id, name) VALUES"
                                                                    + " (:id, :name)")
                                                    .bind("id", 1)
                                                    .bind("name", "alpha")
                                                    .execute();
                                            throw new IllegalStateException("boom");
                                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        Optional<Long> count =
                executor.queryOne(
                        "SELECT COUNT(*) FROM sample",
                        Map.of(),
                        (resultSet, context) -> resultSet.getLong(1));

        assertThat(count).contains(0L);
    }

    @Test
    void preparedBatchInsertsMultipleRowsWithoutManualPreparedStatement() {
        int[] counts =
                executor.withHandle(
                        handle -> {
                            var batch =
                                    handle.prepareBatch(
                                            "INSERT INTO sample(id, name) VALUES (:id, :name)");
                            batch.bind("id", 1).bind("name", "alpha").add();
                            batch.bind("id", 2).bind("name", "beta").add();
                            return batch.execute();
                        });

        List<String> names =
                executor.queryList(
                        "SELECT name FROM sample ORDER BY id",
                        Map.of(),
                        (resultSet, context) -> resultSet.getString("name"));

        assertThat(counts).containsExactly(1, 1);
        assertThat(names).containsExactly("alpha", "beta");
    }
}
