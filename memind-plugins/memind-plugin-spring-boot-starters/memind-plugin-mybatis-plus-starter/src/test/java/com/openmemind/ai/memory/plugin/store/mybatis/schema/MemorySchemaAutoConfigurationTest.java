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
import com.baomidou.mybatisplus.autoconfigure.DdlAutoConfiguration;
import com.baomidou.mybatisplus.extension.ddl.DdlScriptErrorHandler.ThrowsErrorHandler;
import com.baomidou.mybatisplus.extension.ddl.IDdl;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("Memory schema auto-configuration")
class MemorySchemaAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MemorySchemaAutoConfiguration.class,
                                    DdlAutoConfiguration.class));

    @Test
    @DisplayName("Activate schema init when datasource exists and init-schema is enabled")
    void activatesWhenDataSourceExistsAndInitSchemaEnabled() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class)
                .withPropertyValues("memind.store.init-schema=true")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(DatabaseDialectDetector.class);
                            assertThat(context).hasSingleBean(IDdl.class);
                            assertThat(context).hasSingleBean(DdlApplicationRunner.class);

                            IDdl ddl = context.getBean(IDdl.class);
                            assertThat(ddl.getSqlFiles())
                                    .containsExactly("db/migration/sqlite/V1__init.sql");

                            DdlApplicationRunner runner =
                                    context.getBean(DdlApplicationRunner.class);
                            assertThat(ReflectionTestUtils.getField(runner, "throwException"))
                                    .isEqualTo(true);
                            assertThat(
                                            ReflectionTestUtils.getField(
                                                    runner, "ddlScriptErrorHandler"))
                                    .isSameAs(ThrowsErrorHandler.INSTANCE);
                        });
    }

    @Test
    @DisplayName("Aggregate every available ddl bean into the runner")
    void aggregatesAllAvailableDdlBeansIntoRunner() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class)
                .withBean(
                        "additionalDdl",
                        IDdl.class,
                        () ->
                                new IDdl() {
                                    @Override
                                    public void runScript(
                                            java.util.function.Consumer<DataSource> consumer) {}

                                    @Override
                                    public List<String> getSqlFiles() {
                                        return List.of(
                                                "db/migration/sqlite/V3__other_extension.sql");
                                    }
                                })
                .withPropertyValues("memind.store.init-schema=true")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(DdlApplicationRunner.class);
                            assertThat(context.getBeansOfType(IDdl.class)).hasSize(2);

                            DdlApplicationRunner runner =
                                    context.getBean(DdlApplicationRunner.class);
                            assertThat((List<?>) ReflectionTestUtils.getField(runner, "ddlList"))
                                    .hasSize(2);
                        });
    }

    @Test
    @DisplayName("Back off when init-schema is disabled")
    void backsOffWhenInitSchemaDisabled() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class)
                .withPropertyValues("memind.store.init-schema=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(DatabaseDialectDetector.class);
                            assertThat(context).doesNotHaveBean(IDdl.class);
                            assertThat(context).doesNotHaveBean(DdlApplicationRunner.class);
                        });
    }

    @Test
    @DisplayName("Back off when datasource is missing")
    void backsOffWhenDataSourceMissing() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(DatabaseDialectDetector.class);
                    assertThat(context).doesNotHaveBean(IDdl.class);
                    assertThat(context).doesNotHaveBean(DdlApplicationRunner.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class DataSourceConfig {

        @Bean
        DataSource dataSource() {
            return new MetadataDataSource("SQLite", "jdbc:sqlite::memory:");
        }
    }

    private static final class MetadataDataSource implements DataSource {

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

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {}

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
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
