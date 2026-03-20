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

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.insight.buffer.BufferEntry;
import com.openmemind.ai.memory.core.extraction.insight.buffer.InsightBufferStore;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.plugin.store.mybatis.handler.DefaultDBFieldHandler;
import com.openmemind.ai.memory.plugin.store.mybatis.mysql.MemoryMysqlAutoConfiguration;
import com.openmemind.ai.memory.plugin.store.mybatis.sqlite.MemorySqliteAutoConfiguration;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("Memory store auto-configuration")
class MemoryStoreAutoConfigurationTest {

    @Nested
    @DisplayName("Storage type selection")
    class StoreTypeSelection {

        private final ApplicationContextRunner sqliteContextRunner =
                new ApplicationContextRunner()
                        .withConfiguration(
                                AutoConfigurations.of(MemorySqliteAutoConfiguration.class))
                        .withPropertyValues(
                                "memind.store.sqlite.path=:memory:",
                                "memind.store.init-schema=false");

        private final ApplicationContextRunner mysqlContextRunner =
                new ApplicationContextRunner()
                        .withConfiguration(
                                AutoConfigurations.of(
                                        MemoryMysqlAutoConfiguration.class,
                                        MemorySqliteAutoConfiguration.class))
                        .withPropertyValues(
                                "memind.store.type=mysql", "memind.store.init-schema=false")
                        .withUserConfiguration(MysqlSupportConfig.class);

        @Test
        @DisplayName("Default to SQLite when store type is not configured")
        void defaultsToSqliteWhenStoreTypeMissing() {
            sqliteContextRunner.run(
                    context -> {
                        assertThat(context).hasSingleBean(DataSource.class);
                        assertThat(context).hasSingleBean(MemoryTextSearch.class);
                        assertThat(context).doesNotHaveBean("mysqlFulltextTextSearch");
                    });
        }

        @Test
        @DisplayName("Activate MySQL path when store type is mysql even if SQLite classes exist")
        void activatesMysqlWhenStoreTypeIsMysql() {
            mysqlContextRunner.run(
                    context -> {
                        assertThat(context).hasSingleBean(MemoryTextSearch.class);
                        assertThat(context).hasBean("mysqlFulltextTextSearch");
                        assertThat(context).doesNotHaveBean("sqliteDataSource");
                    });
        }
    }

    @Nested
    @DisplayName("User bean override")
    class UserBeanOverride {

        private final ApplicationContextRunner contextRunner =
                new ApplicationContextRunner()
                        .withConfiguration(
                                AutoConfigurations.of(MemoryMybatisPlusAutoConfiguration.class))
                        .withPropertyValues(
                                "memind.store.init-schema=false",
                                "mybatis.lazy-initialization=true")
                        .withUserConfiguration(CustomBeansConfig.class);

        @Test
        @DisplayName(
                "Back off when user provides MemoryStore, interceptor, and meta object handler")
        void backsOffForUserProvidedBeans() {
            contextRunner.run(
                    context -> {
                        assertThat(context).hasSingleBean(MemoryStore.class);
                        assertThat(context.getBean(MemoryStore.class))
                                .isSameAs(context.getBean("customMemoryStore"));

                        assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
                        assertThat(context.getBean(MybatisPlusInterceptor.class))
                                .isSameAs(context.getBean("customMybatisPlusInterceptor"));

                        assertThat(context).hasSingleBean(MetaObjectHandler.class);
                        assertThat(context.getBean(MetaObjectHandler.class))
                                .isSameAs(context.getBean("customMetaObjectHandler"));

                        assertThat(context).doesNotHaveBean(MybatisPlusMemoryStore.class);
                        assertThat(context.getBeansOfType(DefaultDBFieldHandler.class)).isEmpty();
                    });
        }
    }

    @Configuration
    static class MysqlSupportConfig {
        @Bean
        DataSource dataSource() {
            return new StubDataSource();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    @Configuration
    static class CustomBeansConfig {
        @Bean
        MemoryStore customMemoryStore() {
            return new InMemoryMemoryStore();
        }

        @Bean
        MybatisPlusInterceptor customMybatisPlusInterceptor() {
            return new MybatisPlusInterceptor();
        }

        @Bean
        MetaObjectHandler customMetaObjectHandler() {
            return new MetaObjectHandler() {
                @Override
                public void insertFill(MetaObject metaObject) {}

                @Override
                public void updateFill(MetaObject metaObject) {}
            };
        }

        @Bean
        InsightBufferStore customInsightBufferStore() {
            return new InsightBufferStore() {
                @Override
                public void append(MemoryId memoryId, String insightTypeName, List<Long> itemIds) {}

                @Override
                public List<BufferEntry> getUnGrouped(MemoryId memoryId, String insightTypeName) {
                    return List.of();
                }

                @Override
                public int countUnGrouped(MemoryId memoryId, String insightTypeName) {
                    return 0;
                }

                @Override
                public List<BufferEntry> getGroupUnbuilt(
                        MemoryId memoryId, String insightTypeName, String groupName) {
                    return List.of();
                }

                @Override
                public int countGroupUnbuilt(
                        MemoryId memoryId, String insightTypeName, String groupName) {
                    return 0;
                }

                @Override
                public void assignGroup(
                        MemoryId memoryId,
                        String insightTypeName,
                        List<Long> itemIds,
                        String groupName) {}

                @Override
                public void markBuilt(
                        MemoryId memoryId, String insightTypeName, List<Long> itemIds) {}

                @Override
                public Set<String> listGroups(MemoryId memoryId, String insightTypeName) {
                    return Set.of();
                }

                @Override
                public Map<String, List<BufferEntry>> getUnbuiltByGroup(
                        MemoryId memoryId, String insightTypeName) {
                    return Map.of();
                }
            };
        }
    }

    static final class StubDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("Not used in auto-configuration tests");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("Not used in auto-configuration tests");
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
    }
}
