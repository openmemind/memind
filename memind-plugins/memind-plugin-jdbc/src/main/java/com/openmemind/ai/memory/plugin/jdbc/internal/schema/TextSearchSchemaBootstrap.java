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
package com.openmemind.ai.memory.plugin.jdbc.internal.schema;

import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcPluginException;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.SqlScriptRunner;
import javax.sql.DataSource;

public final class TextSearchSchemaBootstrap {

    private static final String SQLITE_TEXT_SEARCH_RESOURCE =
            "db/jdbc/sqlite/textsearch/V1__init.sql";
    private static final String MYSQL_TEXT_SEARCH_RESOURCE =
            "db/jdbc/mysql/textsearch/V1__init.sql";
    private static final String POSTGRESQL_TEXT_SEARCH_RESOURCE =
            "db/jdbc/postgresql/textsearch/V1__init.sql";

    private TextSearchSchemaBootstrap() {}

    public static void ensureSqlite(DataSource dataSource, boolean createIfNotExist) {
        StoreSchemaBootstrap.ensureSqlite(dataSource, createIfNotExist);
        if (hasRequiredSqliteTextSearchTables(dataSource)) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException("Missing required SQLite text-search table: item_fts");
        }
        SqlScriptRunner.execute(dataSource, SQLITE_TEXT_SEARCH_RESOURCE);
    }

    public static void ensureMysql(DataSource dataSource, boolean createIfNotExist) {
        StoreSchemaBootstrap.ensureMysql(dataSource, createIfNotExist);
        if (hasRequiredMysqlTextSearchObjects(dataSource)) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Missing required MySQL text-search object: ft_item_content");
        }
        SqlScriptRunner.execute(dataSource, MYSQL_TEXT_SEARCH_RESOURCE);
    }

    public static void ensurePostgresql(DataSource dataSource, boolean createIfNotExist) {
        StoreSchemaBootstrap.ensurePostgresql(dataSource, createIfNotExist);
        if (hasRequiredPostgresqlTextSearchObjects(dataSource)) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Missing required PostgreSQL text-search object: idx_item_content_trgm");
        }
        try {
            SqlScriptRunner.execute(dataSource, POSTGRESQL_TEXT_SEARCH_RESOURCE);
        } catch (RuntimeException e) {
            throw new JdbcPluginException(
                    "Failed to initialize PostgreSQL text-search schema. Ensure pg_trgm "
                            + "extension can be created.",
                    e);
        }
    }

    private static boolean hasRequiredSqliteTextSearchTables(DataSource dataSource) {
        return SchemaVerifier.hasSqliteTable(dataSource, "item_fts")
                && SchemaVerifier.hasSqliteTable(dataSource, "insight_fts")
                && SchemaVerifier.hasSqliteTable(dataSource, "raw_data_fts");
    }

    private static boolean hasRequiredMysqlTextSearchObjects(DataSource dataSource) {
        return SchemaVerifier.hasMysqlColumn(dataSource, "memory_raw_data", "segment_content")
                && SchemaVerifier.hasMysqlIndex(dataSource, "memory_item", "ft_item_content")
                && SchemaVerifier.hasMysqlIndex(dataSource, "memory_insight", "ft_insight_content")
                && SchemaVerifier.hasMysqlIndex(
                        dataSource, "memory_raw_data", "ft_raw_data_segment_content");
    }

    private static boolean hasRequiredPostgresqlTextSearchObjects(DataSource dataSource) {
        return SchemaVerifier.hasPostgresqlExtension(dataSource, "pg_trgm")
                && SchemaVerifier.hasPostgresqlIndex(
                        dataSource, "memory_item", "idx_item_content_trgm")
                && SchemaVerifier.hasPostgresqlIndex(
                        dataSource, "memory_insight", "idx_insight_content_trgm")
                && SchemaVerifier.hasPostgresqlIndex(
                        dataSource, "memory_raw_data", "idx_raw_data_segment_content_trgm");
    }
}
