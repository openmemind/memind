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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.resource.ResourceStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlConversationBufferAccessor;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlInsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlMemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlMemoryTextSearch;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlRecentConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlConversationBufferAccessor;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlInsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlMemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlMemoryTextSearch;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlRecentConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteConversationBufferAccessor;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteInsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteMemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteMemoryTextSearch;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteRecentConversationBuffer;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
            "com.openmemind.ai.memory.plugin.store.mybatis.MemoryMybatisPlusAutoConfiguration"
        })
@ConditionalOnBean(DataSource.class)
public class JdbcPluginAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MemoryStore.class)
    public MemoryStore memoryStore(
            DataSource dataSource,
            Environment environment,
            ObjectProvider<ResourceStore> resourceStoreProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        boolean createIfNotExist =
                environment.getProperty("memind.store.init-schema", Boolean.class, true);
        ResourceStore resourceStore = resourceStoreProvider.getIfAvailable();
        ObjectMapper objectMapper = resolveObjectMapper(objectMapperProvider);
        return switch (detectDialect(dataSource)) {
            case SQLITE -> {
                yield new SqliteMemoryStore(
                        dataSource, resourceStore, objectMapper, createIfNotExist);
            }
            case MYSQL -> {
                yield new MysqlMemoryStore(
                        dataSource, resourceStore, objectMapper, createIfNotExist);
            }
            case POSTGRESQL -> {
                yield new PostgresqlMemoryStore(
                        dataSource, resourceStore, objectMapper, createIfNotExist);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(MemoryBuffer.class)
    public MemoryBuffer memoryBuffer(DataSource dataSource, Environment environment) {
        boolean createIfNotExist =
                environment.getProperty("memind.store.init-schema", Boolean.class, true);
        return switch (detectDialect(dataSource)) {
            case SQLITE -> {
                var conversationBufferAccessor =
                        new SqliteConversationBufferAccessor(dataSource, createIfNotExist);
                yield MemoryBuffer.of(
                        new SqliteInsightBuffer(dataSource, createIfNotExist),
                        new SqliteConversationBuffer(conversationBufferAccessor),
                        new SqliteRecentConversationBuffer(conversationBufferAccessor));
            }
            case MYSQL -> {
                var conversationBufferAccessor =
                        new MysqlConversationBufferAccessor(dataSource, createIfNotExist);
                yield MemoryBuffer.of(
                        new MysqlInsightBuffer(dataSource, createIfNotExist),
                        new MysqlConversationBuffer(conversationBufferAccessor),
                        new MysqlRecentConversationBuffer(conversationBufferAccessor));
            }
            case POSTGRESQL -> {
                var conversationBufferAccessor =
                        new PostgresqlConversationBufferAccessor(dataSource, createIfNotExist);
                yield MemoryBuffer.of(
                        new PostgresqlInsightBuffer(dataSource, createIfNotExist),
                        new PostgresqlConversationBuffer(conversationBufferAccessor),
                        new PostgresqlRecentConversationBuffer(conversationBufferAccessor));
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(MemoryTextSearch.class)
    public MemoryTextSearch memoryTextSearch(DataSource dataSource, Environment environment) {
        boolean createIfNotExist =
                environment.getProperty("memind.store.init-schema", Boolean.class, true);
        return switch (detectDialect(dataSource)) {
            case SQLITE -> new SqliteMemoryTextSearch(dataSource, createIfNotExist);
            case MYSQL -> new MysqlMemoryTextSearch(dataSource, createIfNotExist);
            case POSTGRESQL -> new PostgresqlMemoryTextSearch(dataSource, createIfNotExist);
        };
    }

    private JdbcDialect detectDialect(DataSource dataSource) {
        return new JdbcDialectDetector().detect(dataSource);
    }

    private ObjectMapper resolveObjectMapper(ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable();
        return objectMapper != null ? objectMapper.copy() : JsonUtils.mapper().copy();
    }
}
