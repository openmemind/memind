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
package com.openmemind.ai.memory.plugin.store.mybatis.sqlite;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;

/**
 * SQLite schema initializer: Executes V1 DDL (CREATE TABLE/VIRTUAL TABLE/TRIGGER IF NOT EXISTS, idempotent) at application startup
 *
 */
public class SqliteSchemaInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SqliteSchemaInitializer.class);

    private final DataSource dataSource;

    public SqliteSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ClassPathResource resource =
                new ClassPathResource("db/migration/V1__init_sqlite_store.sql");
        String sql;
        try (InputStream is = resource.getInputStream()) {
            sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            // SQLite does not support executing multiple statements at once, execute them one by
            // one
            // Note: The BEGIN...END in triggers contains multiple ; and cannot be simply split by ;
            for (String statement : splitStatements(sql)) {
                executeStatement(stmt, statement);
            }
        }
        log.info("SQLite schema initialized");
    }

    /**
     * Execute a single statement, skip "duplicate column name" error for ALTER TABLE ADD COLUMN.
     * SQLite does not support ADD COLUMN IF NOT EXISTS, this method achieves idempotent migration.
     */
    private void executeStatement(Statement stmt, String sql) throws Exception {
        boolean isAlterAddColumn =
                sql.stripLeading().toUpperCase().startsWith("ALTER TABLE")
                        && sql.toUpperCase().contains("ADD COLUMN");
        try {
            stmt.execute(sql);
        } catch (Exception e) {
            if (isAlterAddColumn
                    && e.getMessage() != null
                    && e.getMessage().toLowerCase().contains("duplicate column name")) {
                log.debug("Skipping migration statement for existing column: {}", sql.strip());
            } else {
                throw e;
            }
        }
    }

    /**
     * Split the contents of the SQL file into independent statements, correctly handle BEGIN...END trigger blocks (do not split by ; inside the block).
     * Also remove single-line -- comments, skip empty statements.
     */
    private List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0; // BEGIN...END nesting level

        for (String line : sql.split("\n")) {
            String stripped = line.stripLeading();
            // Skip pure comment lines
            if (stripped.startsWith("--")) {
                continue;
            }
            // Remove inline comments
            int commentIdx = line.indexOf("--");
            String codeLine = commentIdx >= 0 ? line.substring(0, commentIdx) : line;

            String upperCode = codeLine.trim().toUpperCase();
            if (upperCode.equals("BEGIN") || upperCode.endsWith(" BEGIN")) {
                depth++;
            } else if (upperCode.equals("END") || upperCode.equals("END;")) {
                depth--;
            }

            current.append(codeLine).append("\n");

            // Only when depth == 0, split the statement when encountering ;
            if (depth == 0 && codeLine.contains(";")) {
                // A line may contain multiple ; (e.g., CREATE INDEX), directly take the current
                // buffer as a statement
                String stmt = current.toString().strip();
                // Remove the trailing ;
                if (stmt.endsWith(";")) {
                    stmt = stmt.substring(0, stmt.length() - 1).strip();
                }
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current.setLength(0);
            }
        }

        // Handle the case where there may not be a ; at the end
        String remaining = current.toString().strip();
        if (!remaining.isEmpty()) {
            statements.add(remaining);
        }
        return statements;
    }
}
