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
package com.openmemind.ai.memory.plugin.jdbc.internal.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.plugin.jdbc.internal.jdbi.JdbiFactory;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SqlScriptRunnerTest {

    @TempDir Path tempDir;

    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        SQLiteDataSource sqliteDataSource = new SQLiteDataSource();
        sqliteDataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("sql-script-runner.db"));
        dataSource = sqliteDataSource;
    }

    @Test
    void splitStatementsIgnoresCommentsQuotesAndBeginEndBlocks() {
        List<String> statements =
                SqlScriptRunner.splitStatements(
                        """
                        -- leading comment
                        CREATE TABLE sample (name TEXT DEFAULT 'a;b');
                        /* block ; comment */
                        CREATE TRIGGER sample_ai AFTER INSERT ON sample BEGIN
                            INSERT INTO sample(name) VALUES ('trigger;value');
                        END;
                        INSERT INTO sample(name) VALUES ('hello;world');
                        """);

        assertThat(statements)
                .containsExactly(
                        "CREATE TABLE sample (name TEXT DEFAULT 'a;b')",
                        "CREATE TRIGGER sample_ai AFTER INSERT ON sample BEGIN\n"
                                + "    INSERT INTO sample(name) VALUES ('trigger;value');\n"
                                + "END",
                        "INSERT INTO sample(name) VALUES ('hello;world')");
    }

    @Test
    void executeRunsClasspathSqlScript() {
        SqlScriptRunner.execute(dataSource, "db/jdbc/test/V1__sample.sql");

        String tableName =
                JdbiFactory.create(dataSource)
                        .withHandle(
                                handle ->
                                        handle.createQuery(
                                                        "SELECT name FROM sqlite_master WHERE"
                                                                + " type='table' AND"
                                                                + " name='sample_table'")
                                                .mapTo(String.class)
                                                .one());

        assertThat(tableName).isEqualTo("sample_table");
    }

    @Test
    void throwsUsefulErrorWhenResourceIsMissing() {
        assertThatThrownBy(() -> SqlScriptRunner.execute(dataSource, "missing/V1__none.sql"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource not found")
                .hasMessageContaining("missing/V1__none.sql");
    }
}
