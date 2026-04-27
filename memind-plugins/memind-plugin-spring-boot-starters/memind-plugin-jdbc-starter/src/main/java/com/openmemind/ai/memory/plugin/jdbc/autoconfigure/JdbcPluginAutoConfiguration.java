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
package com.openmemind.ai.memory.plugin.jdbc.autoconfigure;

import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.graph.GraphOperations;
import com.openmemind.ai.memory.core.store.graph.GraphOperationsCapabilities;
import com.openmemind.ai.memory.core.store.graph.ItemGraphCommitOperations;
import com.openmemind.ai.memory.core.store.thread.ThreadEnrichmentInputStore;
import com.openmemind.ai.memory.core.store.thread.ThreadProjectionStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import com.openmemind.ai.memory.plugin.jdbc.JdbcMemoryAccess;
import com.openmemind.ai.memory.plugin.jdbc.JdbcPluginOptions;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlJdbcPlugin;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlJdbcPlugin;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteJdbcPlugin;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
            "com.openmemind.ai.memory.plugin.store.mybatis.MemoryMybatisPlusAutoConfiguration"
        })
@ConditionalOnBean(HikariDataSource.class)
public class JdbcPluginAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(
            value = {
                JdbcMemoryAccess.class,
                MemoryStore.class,
                MemoryBuffer.class,
                MemoryTextSearch.class,
                BubbleTrackerStore.class
            })
    public JdbcMemoryAccess jdbcMemoryAccess(
            HikariDataSource dataSource,
            Environment environment,
            ObjectProvider<ResourceStore> resourceStoreProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        JdbcPluginOptions options =
                JdbcPluginOptions.externalDataSource()
                        .withCreateIfNotExist(
                                environment.getProperty(
                                        "memind.store.init-schema", Boolean.class, true))
                        .withResourceStore(resourceStoreProvider.getIfAvailable())
                        .withObjectMapper(resolveObjectMapper(objectMapperProvider));
        return switch (detectDialect(dataSource)) {
            case SQLITE -> SqliteJdbcPlugin.create(dataSource, options);
            case MYSQL -> MysqlJdbcPlugin.create(dataSource, options);
            case POSTGRESQL -> PostgresqlJdbcPlugin.create(dataSource, options);
        };
    }

    @Bean
    @ConditionalOnBean(JdbcMemoryAccess.class)
    @ConditionalOnMissingBean(MemoryStore.class)
    public MemoryStore memoryStore(JdbcMemoryAccess jdbcMemoryAccess) {
        return jdbcMemoryAccess.store();
    }

    @Bean
    @ConditionalOnMissingBean(GraphOperations.class)
    public GraphOperations graphOperations(MemoryStore memoryStore) {
        return memoryStore.graphOperations();
    }

    @Bean
    @ConditionalOnMissingBean(GraphOperationsCapabilities.class)
    public GraphOperationsCapabilities graphOperationsCapabilities(MemoryStore memoryStore) {
        return memoryStore.graphOperationsCapabilities();
    }

    @Bean
    @ConditionalOnMissingBean(ItemGraphCommitOperations.class)
    public ItemGraphCommitOperations itemGraphCommitOperations(MemoryStore memoryStore) {
        return memoryStore.itemGraphCommitOperations();
    }

    @Bean
    @ConditionalOnMissingBean(ThreadProjectionStore.class)
    public ThreadProjectionStore threadProjectionStore(MemoryStore memoryStore) {
        return narrowInterface(ThreadProjectionStore.class, memoryStore.threadOperations());
    }

    @Bean
    @ConditionalOnMissingBean(ThreadEnrichmentInputStore.class)
    public ThreadEnrichmentInputStore threadEnrichmentInputStore(MemoryStore memoryStore) {
        return narrowInterface(
                ThreadEnrichmentInputStore.class, memoryStore.threadEnrichmentInputStore());
    }

    @Bean
    @ConditionalOnBean(JdbcMemoryAccess.class)
    @ConditionalOnMissingBean(MemoryBuffer.class)
    public MemoryBuffer memoryBuffer(JdbcMemoryAccess jdbcMemoryAccess) {
        return jdbcMemoryAccess.buffer();
    }

    @Bean
    @ConditionalOnBean(JdbcMemoryAccess.class)
    @ConditionalOnMissingBean(MemoryTextSearch.class)
    public MemoryTextSearch memoryTextSearch(JdbcMemoryAccess jdbcMemoryAccess) {
        return jdbcMemoryAccess.textSearch();
    }

    @Bean
    @ConditionalOnBean(JdbcMemoryAccess.class)
    @ConditionalOnMissingBean(BubbleTrackerStore.class)
    public BubbleTrackerStore bubbleTrackerStore(JdbcMemoryAccess jdbcMemoryAccess) {
        return jdbcMemoryAccess.bubbleTrackerStore();
    }

    private <T> T narrowInterface(Class<T> interfaceType, T delegate) {
        Object proxy =
                Proxy.newProxyInstance(
                        interfaceType.getClassLoader(),
                        new Class<?>[] {interfaceType},
                        (proxyInstance, method, args) -> {
                            try {
                                return method.invoke(delegate, args);
                            } catch (InvocationTargetException e) {
                                throw e.getTargetException();
                            }
                        });
        return interfaceType.cast(proxy);
    }

    private JdbcDialect detectDialect(HikariDataSource dataSource) {
        return new JdbcDialectDetector().detect(dataSource);
    }

    private ObjectMapper resolveObjectMapper(ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable();
        return objectMapper != null ? objectMapper.rebuild().build() : JsonUtils.newMapper();
    }
}
