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

import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.buffer.InMemoryRecentConversationBuffer;
import com.openmemind.ai.memory.core.store.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.store.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlInsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlMemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.mysql.MysqlMemoryTextSearch;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlInsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlMemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.postgresql.PostgresqlMemoryTextSearch;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteConversationBuffer;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteInsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteMemoryStore;
import com.openmemind.ai.memory.plugin.jdbc.sqlite.SqliteMemoryTextSearch;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "com.openmemind.ai.memory.plugin.store.mybatis.MemoryMybatisPlusAutoConfiguration"
        },
        beforeName =
                "com.openmemind.ai.memory.autoconfigure.extraction.MemoryExtractionAutoConfiguration")
@ConditionalOnBean(DataSource.class)
public class JdbcPluginAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MemoryStore.class)
    public MemoryStore memoryStore(DataSource dataSource, Environment environment) {
        boolean createIfNotExist =
                environment.getProperty("memind.store.init-schema", Boolean.class, true);
        return switch (detectDialect(dataSource)) {
            case SQLITE -> {
                var store = new SqliteMemoryStore(dataSource, createIfNotExist);
                yield MemoryStore.of(store, store, store);
            }
            case MYSQL -> {
                var store = new MysqlMemoryStore(dataSource, createIfNotExist);
                yield MemoryStore.of(store, store, store);
            }
            case POSTGRESQL -> {
                var store = new PostgresqlMemoryStore(dataSource, createIfNotExist);
                yield MemoryStore.of(store, store, store);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(RecentConversationBuffer.class)
    public RecentConversationBuffer recentConversationBuffer() {
        return new InMemoryRecentConversationBuffer();
    }

    @Bean
    @ConditionalOnMissingBean(MemoryBuffer.class)
    public MemoryBuffer memoryBuffer(
            DataSource dataSource,
            Environment environment,
            RecentConversationBuffer recentConversationBuffer) {
        boolean createIfNotExist =
                environment.getProperty("memind.store.init-schema", Boolean.class, true);
        return switch (detectDialect(dataSource)) {
            case SQLITE ->
                    MemoryBuffer.of(
                            new SqliteInsightBuffer(dataSource, createIfNotExist),
                            new SqliteConversationBuffer(dataSource, createIfNotExist),
                            recentConversationBuffer);
            case MYSQL ->
                    MemoryBuffer.of(
                            new MysqlInsightBuffer(dataSource, createIfNotExist),
                            new MysqlConversationBuffer(dataSource, createIfNotExist),
                            recentConversationBuffer);
            case POSTGRESQL ->
                    MemoryBuffer.of(
                            new PostgresqlInsightBuffer(dataSource, createIfNotExist),
                            new PostgresqlConversationBuffer(dataSource, createIfNotExist),
                            recentConversationBuffer);
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
}
