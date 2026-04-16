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

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.sqlite.SQLiteDataSource;

@DisplayName("Memory store DDL")
class MemoryStoreDdlTest {

    private final DatabaseDialectDetector detector = new DatabaseDialectDetector();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "db/migration/sqlite/V1__init_store.sql",
                "db/migration/mysql/V1__init_store.sql",
                "db/migration/postgresql/V1__init_store.sql"
            })
    @DisplayName("Fresh V1 store scripts should not define confidence column")
    void freshStoreScriptsShouldNotDefineInsightConfidenceColumn(String classpathResource) {
        assertThat(normalizedSqlResource(classpathResource)).doesNotContain(" confidence ");
    }

    @Test
    @DisplayName("Load SQLite store and text search scripts")
    void loadsSqliteScripts() {
        assertThat(
                        new MemoryStoreDdl(dataSource("SQLite", "jdbc:sqlite::memory:"), detector)
                                .getSqlFiles())
                .containsExactly(
                        "db/migration/sqlite/V1__init_store.sql",
                        "db/migration/sqlite/V2__init_text_search.sql",
                        "db/migration/sqlite/V3__multimodal.sql",
                        "db/migration/sqlite/V4__bubble_state.sql",
                        "db/migration/sqlite/V5__item_temporal_fields.sql");
    }

    @Test
    @DisplayName("Load MySQL store and text search scripts")
    void loadsMysqlScripts() {
        assertThat(
                        new MemoryStoreDdl(
                                        dataSource("MySQL", "jdbc:mysql://localhost:3306/memind"),
                                        detector)
                                .getSqlFiles())
                .containsExactly(
                        "db/migration/mysql/V1__init_store.sql",
                        "db/migration/mysql/V2__init_text_search.sql",
                        "db/migration/mysql/V3__multimodal.sql",
                        "db/migration/mysql/V4__bubble_state.sql",
                        "db/migration/mysql/V5__item_temporal_fields.sql");
    }

    @Test
    @DisplayName("Load PostgreSQL store and text search scripts")
    void loadsPostgresqlScripts() {
        assertThat(
                        new MemoryStoreDdl(
                                        dataSource(
                                                "PostgreSQL",
                                                "jdbc:postgresql://localhost:5432/memind"),
                                        detector)
                                .getSqlFiles())
                .containsExactly(
                        "db/migration/postgresql/V1__init_store.sql",
                        "db/migration/postgresql/V2__init_text_search.sql",
                        "db/migration/postgresql/V3__multimodal.sql",
                        "db/migration/postgresql/V4__bubble_state.sql",
                        "db/migration/postgresql/V5__item_temporal_fields.sql");
    }

    @Test
    @DisplayName("Seed default taxonomy only when insight type table is first created")
    void seedsDefaultsWhenInsightTypeTableIsFirstCreated(@TempDir Path tempDir) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("fresh.db"));

        MemoryStoreDdl ddl = new MemoryStoreDdl(dataSource, detector);
        ddl.runScript(
                ds -> {
                    ResourceDatabasePopulator populator =
                            new ResourceDatabasePopulator(
                                    new ClassPathResource(
                                            "db/migration/sqlite/V1__init_store.sql"));
                    populator.execute(ds);
                });

        Integer count =
                new JdbcTemplate(dataSource)
                        .queryForObject("SELECT COUNT(*) FROM memory_insight_type", Integer.class);

        assertThat(count).isEqualTo(DefaultInsightTypes.all().size());
        assertThat(columnExists(dataSource, "memory_insight", "confidence")).isFalse();
    }

    @Test
    @DisplayName("Do not seed default taxonomy when insight type table already exists")
    void doesNotSeedDefaultsWhenInsightTypeTableAlreadyExists(@TempDir Path tempDir) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("existing.db"));

        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(
                        new ClassPathResource("db/migration/sqlite/V1__init_store.sql"));
        populator.execute(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM memory_insight_type");

        MemoryStoreDdl ddl = new MemoryStoreDdl(dataSource, detector);
        ddl.runScript(ignored -> {});

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM memory_insight_type", Integer.class);

        assertThat(count).isZero();
        assertThat(columnExists(dataSource, "memory_insight", "confidence")).isFalse();
    }

    private DataSource dataSource(String productName, String url) {
        return new MetadataDataSource(productName, url);
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

    private String normalizedSqlResource(String classpathResource) {
        try (InputStream inputStream =
                MemoryStoreDdlTest.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + classpathResource);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class MetadataDataSource extends AbstractDataSource {

        private final String productName;
        private final String url;

        private MetadataDataSource(String productName, String url) {
            this.productName = productName;
            this.url = url;
        }

        @Override
        public Connection getConnection() {
            DatabaseMetaData metadata =
                    (DatabaseMetaData)
                            Proxy.newProxyInstance(
                                    DatabaseMetaData.class.getClassLoader(),
                                    new Class<?>[] {DatabaseMetaData.class},
                                    (proxy, method, args) -> {
                                        return switch (method.getName()) {
                                            case "getDatabaseProductName" -> productName;
                                            case "getURL" -> url;
                                            default -> defaultValue(method.getReturnType());
                                        };
                                    });
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> {
                                return switch (method.getName()) {
                                    case "getMetaData" -> metadata;
                                    case "close" -> null;
                                    default -> defaultValue(method.getReturnType());
                                };
                            });
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }
    }
}
