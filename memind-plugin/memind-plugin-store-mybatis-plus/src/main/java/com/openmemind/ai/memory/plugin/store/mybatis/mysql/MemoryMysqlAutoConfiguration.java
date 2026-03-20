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
package com.openmemind.ai.memory.plugin.store.mybatis.mysql;

import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.plugin.store.mybatis.MemoryMybatisPlusAutoConfiguration;
import com.openmemind.ai.memory.plugin.store.mybatis.mysql.textsearch.MysqlFulltextTextSearch;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MySQL storage auto-configuration.
 *
 * <p>Automatically activated when the MySQL JDBC driver is on the classpath and
 * {@code memind.store.type=mysql}: create database, create table, full-text search Bean.
 * MyBatis-Plus framework Beans (Interceptor, MetaHandler, Store) are provided by
 * {@link MemoryMybatisPlusAutoConfiguration}.
 *
 */
@AutoConfiguration(after = MemoryMybatisPlusAutoConfiguration.class)
@ConditionalOnClass(name = "com.mysql.cj.jdbc.Driver")
@ConditionalOnProperty(name = "memind.store.type", havingValue = "mysql")
public class MemoryMysqlAutoConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "memind.store.init-schema",
            havingValue = "true",
            matchIfMissing = true)
    public MysqlSchemaInitializer mysqlSchemaInitializer(DataSource dataSource) {
        return new MysqlSchemaInitializer(dataSource);
    }

    @Bean
    @ConditionalOnProperty(
            name = "memind.store.init-schema",
            havingValue = "true",
            matchIfMissing = true)
    public MemoryDdl memindDdl(DataSource dataSource) {
        return new MemoryDdl(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryTextSearch.class)
    public MysqlFulltextTextSearch mysqlFulltextTextSearch(JdbcTemplate jdbcTemplate) {
        return new MysqlFulltextTextSearch(jdbcTemplate);
    }
}
