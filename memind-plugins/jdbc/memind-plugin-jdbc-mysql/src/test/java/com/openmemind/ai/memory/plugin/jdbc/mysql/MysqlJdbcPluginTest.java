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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.plugin.jdbc.internal.schema.JdbcDialectSchema;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MysqlJdbcPluginTest {

    @Test
    void createRequiresUrlUsernameAndPasswordBeforeOpeningPool() {
        assertThatThrownBy(() -> MysqlJdbcPlugin.create("", "root", "pwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbcUrl");

        assertThatThrownBy(
                        () ->
                                MysqlJdbcPlugin.create(
                                        "jdbc:mysql://localhost:3306/memind", "", "pwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");

        assertThatThrownBy(
                        () ->
                                MysqlJdbcPlugin.create(
                                        "jdbc:mysql://localhost:3306/memind", "root", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    @Test
    void freshStoreScriptShouldNotDefineInsightConfidenceColumn() {
        assertThat(
                        normalizedCreateTableStatement(
                                JdbcDialectSchema.MYSQL.scriptPath(), "memory_insight"))
                .doesNotContain(" confidence ");
    }

    private String normalizedCreateTableStatement(String classpathResource, String tableName) {
        return java.util.Arrays.stream(normalizedSqlResource(classpathResource).split(";"))
                .filter(statement -> statement.contains("create table if not exists " + tableName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableName));
    }

    private String normalizedSqlResource(String classpathResource) {
        return loadSqlResource(classpathResource).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String loadSqlResource(String classpathResource) {
        try (InputStream inputStream =
                MysqlJdbcPluginTest.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + classpathResource);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .reduce(
                            new StringBuilder(),
                            (builder, line) -> builder.append(line).append('\n'),
                            StringBuilder::append)
                    .toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
