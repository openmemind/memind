package com.openmemind.ai.memory.plugin.store.mybatis.mysql;

import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.plugin.store.mybatis.MemoryMybatisPlusAutoConfiguration;
import com.openmemind.ai.memory.plugin.store.mybatis.mysql.textsearch.MysqlFulltextTextSearch;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MySQL storage auto-configuration.
 *
 * <p>Automatically activated when the MySQL JDBC driver is on the classpath and the SQLite driver does not exist: create database, create table, full-text search Bean.
 * MyBatis-Plus framework Beans (Interceptor, MetaHandler, Store) are provided by
 * {@link MemoryMybatisPlusAutoConfiguration}.
 *
 */
@AutoConfiguration(after = MemoryMybatisPlusAutoConfiguration.class)
@ConditionalOnClass(name = "com.mysql.cj.jdbc.Driver")
@ConditionalOnMissingClass("org.sqlite.SQLiteDataSource")
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
