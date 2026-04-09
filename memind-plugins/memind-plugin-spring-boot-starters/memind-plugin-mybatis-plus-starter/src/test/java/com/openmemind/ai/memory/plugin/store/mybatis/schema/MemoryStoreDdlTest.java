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

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;

@DisplayName("Memory store DDL")
class MemoryStoreDdlTest {

    private final DatabaseDialectDetector detector = new DatabaseDialectDetector();

    @Test
    @DisplayName("Load SQLite store and text search scripts")
    void loadsSqliteScripts() {
        assertThat(
                        new MemoryStoreDdl(dataSource("SQLite", "jdbc:sqlite::memory:"), detector)
                                .getSqlFiles())
                .containsExactly(
                        "db/migration/sqlite/V1__init_store.sql",
                        "db/migration/sqlite/V2__init_text_search.sql",
                        "db/migration/sqlite/V3__multimodal.sql");
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
                        "db/migration/mysql/V3__multimodal.sql");
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
                        "db/migration/postgresql/V3__multimodal.sql");
    }

    private DataSource dataSource(String productName, String url) {
        return new MetadataDataSource(productName, url);
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
