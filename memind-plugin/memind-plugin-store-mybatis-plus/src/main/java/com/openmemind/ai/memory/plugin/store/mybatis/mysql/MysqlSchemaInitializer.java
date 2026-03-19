package com.openmemind.ai.memory.plugin.store.mybatis.mysql;

import java.sql.Connection;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * MySQL database initializer: automatically create the database at startup (if it does not exist).
 *
 */
public class MysqlSchemaInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MysqlSchemaInitializer.class);
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("jdbc:[^:]+://[^/]+/([^?]+)");

    private final DataSource dataSource;

    public MysqlSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        String jdbcUrl;
        try (Connection conn = dataSource.getConnection()) {
            jdbcUrl = conn.getMetaData().getURL();
        }

        String dbName = extractDatabaseName(jdbcUrl);
        if (dbName == null) {
            log.warn(
                    "Unable to parse database name from JDBC URL, skipping automatic database"
                            + " creation: {}",
                    jdbcUrl);
            return;
        }

        log.info("Checking if database exists: {}", dbName);
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE DATABASE IF NOT EXISTS `"
                            + dbName
                            + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            log.info("Database is ready: {}", dbName);
        }
    }

    static String extractDatabaseName(String jdbcUrl) {
        Matcher matcher = DB_NAME_PATTERN.matcher(jdbcUrl);
        return matcher.find() ? matcher.group(1) : null;
    }
}
