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
package com.openmemind.ai.memory.plugin.store.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.plugin.store.mybatis.handler.InstantTypeHandler;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.MemorySchemaAutoConfiguration;
import java.nio.file.Path;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.sqlite.SQLiteDataSource;

@DisplayName("MybatisPlusBubbleTrackerStore")
class MybatisPlusBubbleTrackerStoreTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("bubble tracker bean persists branch and root dirty count across context restart")
    void bubbleTrackerPersistsDirtyCountAcrossRestart() {
        Path dbPath = tempDir.resolve("bubble.db");

        sqliteContextRunner(dbPath)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(BubbleTrackerStore.class);
                            BubbleTrackerStore bubbleTrackerStore =
                                    context.getBean(BubbleTrackerStore.class);

                            assertThat(bubbleTrackerStore.incrementAndGet("memory-1::identity", 2))
                                    .isEqualTo(2);
                            assertThat(
                                            bubbleTrackerStore.incrementAndGet(
                                                    "memory-1::root::profile", 2))
                                    .isEqualTo(2);
                        });

        sqliteContextRunner(dbPath)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(BubbleTrackerStore.class);
                            BubbleTrackerStore bubbleTrackerStore =
                                    context.getBean(BubbleTrackerStore.class);

                            assertThat(bubbleTrackerStore.getDirtyCount("memory-1::identity"))
                                    .isEqualTo(2);
                            assertThat(bubbleTrackerStore.getDirtyCount("memory-1::root::profile"))
                                    .isEqualTo(2);
                            assertThat(bubbleTrackerStore.incrementAndGet("memory-1::identity", 1))
                                    .isEqualTo(3);
                            assertThat(
                                            bubbleTrackerStore.incrementAndGet(
                                                    "memory-1::root::profile", 1))
                                    .isEqualTo(3);
                            bubbleTrackerStore.reset("memory-1::root::profile");
                            assertThat(bubbleTrackerStore.getDirtyCount("memory-1::root::profile"))
                                    .isZero();
                        });
    }

    private ApplicationContextRunner sqliteContextRunner(Path dbPath) {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                MemoryMybatisPlusAutoConfiguration.class,
                                MemorySchemaAutoConfiguration.class,
                                MybatisPlusAutoConfiguration.class))
                .withUserConfiguration(TestInfrastructureConfig.class)
                .withPropertyValues(
                        "test.sqlite.path=" + dbPath,
                        "memind.store.init-schema=true",
                        "spring.main.web-application-type=none");
    }

    @Configuration(proxyBeanMethods = false)
    @Import({TransactionalTestConfig.class, SqliteTestSupportConfig.class})
    static class TestInfrastructureConfig {}

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionalTestConfig {

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SqliteTestSupportConfig {

        @Bean
        DataSource dataSource(@Value("${test.sqlite.path}") String dbPath) {
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + dbPath);
            return dataSource;
        }

        @Bean
        ConfigurationCustomizer instantTypeHandlerCustomizer() {
            return configuration ->
                    configuration
                            .getTypeHandlerRegistry()
                            .register(Instant.class, InstantTypeHandler.class);
        }

        @Bean
        InitializingBean ddlRunnerInitializer(DdlApplicationRunner ddlApplicationRunner) {
            return () -> ddlApplicationRunner.run(new DefaultApplicationArguments(new String[0]));
        }
    }
}
