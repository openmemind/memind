package com.openmemind.ai.memory.plugin.store.mybatis.sqlite;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.plugin.store.mybatis.MemoryMybatisPlusAutoConfiguration;
import com.openmemind.ai.memory.plugin.store.mybatis.handler.InstantTypeHandler;
import com.openmemind.ai.memory.plugin.store.mybatis.sqlite.textsearch.SqliteFulltextTextSearch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

/**
 * SQLite storage auto-configuration.
 *
 * <p>Automatically activated when the SQLite JDBC driver is on the classpath: DataSource, Schema initialization, FTS5 full-text search.
 * MyBatis-Plus framework Beans are provided by {@link MemoryMybatisPlusAutoConfiguration}.
 *
 */
@AutoConfiguration(after = MemoryMybatisPlusAutoConfiguration.class)
@ConditionalOnClass(name = "org.sqlite.SQLiteDataSource")
@EnableConfigurationProperties(MemorySqliteProperties.class)
public class MemorySqliteAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DataSource sqliteDataSource(MemorySqliteProperties properties) {
        String path = properties.path();
        if (!":memory:".equals(path)) {
            Path parent = Path.of(path).toAbsolutePath().getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException e) {
                    throw new IllegalStateException(
                            "Failed to create SQLite database directory: " + parent, e);
                }
            }
        }
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + path);
        return ds;
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplate sqliteJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnBean(name = "sqliteDataSource")
    public ConfigurationCustomizer instantTypeHandlerCustomizer() {
        return configuration ->
                configuration
                        .getTypeHandlerRegistry()
                        .register(Instant.class, InstantTypeHandler.class);
    }

    @Bean
    @ConditionalOnProperty(
            name = "memind.store.init-schema",
            havingValue = "true",
            matchIfMissing = true)
    public SqliteSchemaInitializer sqliteSchemaInitializer(DataSource dataSource) {
        return new SqliteSchemaInitializer(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryTextSearch.class)
    public SqliteFulltextTextSearch sqliteFulltextTextSearch(JdbcTemplate jdbcTemplate) {
        return new SqliteFulltextTextSearch(jdbcTemplate);
    }
}
