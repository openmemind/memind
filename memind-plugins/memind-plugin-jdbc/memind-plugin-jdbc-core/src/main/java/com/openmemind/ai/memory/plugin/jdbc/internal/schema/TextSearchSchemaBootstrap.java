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

import com.openmemind.ai.memory.plugin.jdbc.internal.jdbi.JdbiFactory;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcPluginException;
import javax.sql.DataSource;

public final class TextSearchSchemaBootstrap {

    private TextSearchSchemaBootstrap() {}

    public static void ensureSqlite(DataSource dataSource, boolean createIfNotExist) {
        StoreSchemaBootstrap.ensureSqlite(dataSource, createIfNotExist);
        if (hasRequiredSqliteTextSearchTables(dataSource)) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException("Missing required SQLite text-search table: item_fts");
        }
        ensureSqliteTextSearchSchema(dataSource);
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
        ensureMysqlTextSearchSchema(dataSource);
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
            ensurePostgresqlTextSearchSchema(dataSource);
        } catch (RuntimeException e) {
            throw new JdbcPluginException(
                    "Failed to initialize PostgreSQL text-search schema. Ensure pg_trgm "
                            + "extension can be created.",
                    e);
        }
    }

    private static void ensureSqliteTextSearchSchema(DataSource dataSource) {
        execute(
                dataSource,
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS item_fts USING fts5(
                    biz_id    UNINDEXED,
                    memory_id UNINDEXED,
                    content,
                    tokenize  = 'trigram'
                )
                """);
        execute(
                dataSource,
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS insight_fts USING fts5(
                    biz_id    UNINDEXED,
                    memory_id UNINDEXED,
                    content,
                    tokenize  = 'trigram'
                )
                """);
        execute(
                dataSource,
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS raw_data_fts USING fts5(
                    biz_id    UNINDEXED,
                    memory_id UNINDEXED,
                    content,
                    tokenize  = 'trigram'
                )
                """);
        execute(
                dataSource,
                "CREATE TRIGGER IF NOT EXISTS item_fts_ai AFTER INSERT ON memory_item BEGIN "
                        + "INSERT INTO item_fts(rowid, biz_id, memory_id, content) VALUES "
                        + "(new.id, new.biz_id, new.memory_id, new.content); END");
        execute(
                dataSource,
                "CREATE TRIGGER IF NOT EXISTS item_fts_au AFTER UPDATE ON memory_item "
                        + "WHEN new.deleted = 0 BEGIN DELETE FROM item_fts WHERE rowid = old.id; "
                        + "INSERT INTO item_fts(rowid, biz_id, memory_id, content) VALUES "
                        + "(new.id, new.biz_id, new.memory_id, new.content); END");
        execute(
                dataSource,
                "CREATE TRIGGER IF NOT EXISTS item_fts_ad AFTER UPDATE ON memory_item "
                        + "WHEN new.deleted = 1 AND old.deleted = 0 BEGIN DELETE FROM item_fts "
                        + "WHERE rowid = old.id; END");
        execute(
                dataSource,
                "CREATE TRIGGER IF NOT EXISTS insight_fts_ai AFTER INSERT ON memory_insight BEGIN "
                        + "INSERT INTO insight_fts(rowid, biz_id, memory_id, content) VALUES "
                        + "(new.id, new.biz_id, new.memory_id, new.content); END");
        execute(
                dataSource,
                "CREATE TRIGGER IF NOT EXISTS insight_fts_au AFTER UPDATE ON memory_insight WHEN"
                    + " new.deleted = 0 BEGIN DELETE FROM insight_fts WHERE rowid = old.id; INSERT"
                    + " INTO insight_fts(rowid, biz_id, memory_id, content) VALUES (new.id,"
                    + " new.biz_id, new.memory_id, new.content); END");
        execute(
                dataSource,
                "CREATE TRIGGER IF NOT EXISTS insight_fts_ad AFTER UPDATE ON memory_insight "
                        + "WHEN new.deleted = 1 AND old.deleted = 0 BEGIN DELETE FROM insight_fts "
                        + "WHERE rowid = old.id; END");
        execute(
                dataSource,
                "CREATE TRIGGER IF NOT EXISTS raw_data_fts_ai AFTER INSERT ON memory_raw_data BEGIN"
                    + " INSERT INTO raw_data_fts(rowid, biz_id, memory_id, content) VALUES (new.id,"
                    + " new.biz_id, new.memory_id, json_extract(new.segment, '$.content')); END");
        execute(
                dataSource,
                "CREATE TRIGGER IF NOT EXISTS raw_data_fts_au AFTER UPDATE ON memory_raw_data WHEN"
                    + " new.deleted = 0 BEGIN DELETE FROM raw_data_fts WHERE rowid = old.id; INSERT"
                    + " INTO raw_data_fts(rowid, biz_id, memory_id, content) VALUES (new.id,"
                    + " new.biz_id, new.memory_id, json_extract(new.segment, '$.content')); END");
        execute(
                dataSource,
                "CREATE TRIGGER IF NOT EXISTS raw_data_fts_ad AFTER UPDATE ON memory_raw_data "
                        + "WHEN new.deleted = 1 AND old.deleted = 0 BEGIN DELETE FROM raw_data_fts "
                        + "WHERE rowid = old.id; END");
        execute(
                dataSource,
                """
                INSERT OR REPLACE INTO item_fts(rowid, biz_id, memory_id, content)
                SELECT id, biz_id, memory_id, content FROM memory_item WHERE deleted = 0
                """);
        execute(
                dataSource,
                """
                INSERT OR REPLACE INTO insight_fts(rowid, biz_id, memory_id, content)
                SELECT id, biz_id, memory_id, content FROM memory_insight WHERE deleted = 0
                """);
        execute(
                dataSource,
                """
                INSERT OR REPLACE INTO raw_data_fts(rowid, biz_id, memory_id, content)
                SELECT id, biz_id, memory_id, json_extract(segment, '$.content')
                FROM memory_raw_data
                WHERE deleted = 0
                  AND json_extract(segment, '$.content') IS NOT NULL
                """);
    }

    private static void ensureMysqlTextSearchSchema(DataSource dataSource) {
        if (!SchemaVerifier.hasMysqlColumn(dataSource, "memory_raw_data", "segment_content")) {
            execute(
                    dataSource,
                    """
                    ALTER TABLE memory_raw_data
                        ADD COLUMN segment_content TEXT GENERATED ALWAYS AS
                            (JSON_UNQUOTE(JSON_EXTRACT(segment, '$.content'))) STORED
                    """);
        }
        ensureMysqlTextSearchIndex(
                dataSource,
                "memory_item",
                "ft_item_content",
                "CREATE FULLTEXT INDEX ft_item_content ON memory_item(content) WITH PARSER ngram");
        ensureMysqlTextSearchIndex(
                dataSource,
                "memory_insight",
                "ft_insight_content",
                "CREATE FULLTEXT INDEX ft_insight_content ON memory_insight(content) WITH PARSER"
                        + " ngram");
        ensureMysqlTextSearchIndex(
                dataSource,
                "memory_raw_data",
                "ft_raw_data_segment_content",
                "CREATE FULLTEXT INDEX ft_raw_data_segment_content ON"
                        + " memory_raw_data(segment_content) WITH PARSER ngram");
    }

    private static void ensureMysqlTextSearchIndex(
            DataSource dataSource, String tableName, String indexName, String sql) {
        if (SchemaVerifier.hasMysqlIndex(dataSource, tableName, indexName)) {
            return;
        }
        execute(dataSource, sql);
    }

    private static void ensurePostgresqlTextSearchSchema(DataSource dataSource) {
        execute(dataSource, "CREATE EXTENSION IF NOT EXISTS pg_trgm");
        ensurePostgresqlTextSearchIndex(
                dataSource,
                "memory_item",
                "idx_item_content_trgm",
                "CREATE INDEX IF NOT EXISTS idx_item_content_trgm ON memory_item "
                        + "USING GIN (content gin_trgm_ops) WHERE deleted = FALSE");
        ensurePostgresqlTextSearchIndex(
                dataSource,
                "memory_insight",
                "idx_insight_content_trgm",
                "CREATE INDEX IF NOT EXISTS idx_insight_content_trgm ON memory_insight "
                        + "USING GIN ((COALESCE(content, '')) gin_trgm_ops) "
                        + "WHERE deleted = FALSE");
        ensurePostgresqlTextSearchIndex(
                dataSource,
                "memory_raw_data",
                "idx_raw_data_segment_content_trgm",
                "CREATE INDEX IF NOT EXISTS idx_raw_data_segment_content_trgm ON memory_raw_data "
                        + "USING GIN ((COALESCE(segment ->> 'content', '')) gin_trgm_ops) "
                        + "WHERE deleted = FALSE");
    }

    private static void ensurePostgresqlTextSearchIndex(
            DataSource dataSource, String tableName, String indexName, String sql) {
        if (SchemaVerifier.hasPostgresqlIndex(dataSource, tableName, indexName)) {
            return;
        }
        execute(dataSource, sql);
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

    private static void execute(DataSource dataSource, String sql) {
        try {
            JdbiFactory.create(dataSource).useHandle(handle -> handle.execute(sql));
        } catch (RuntimeException e) {
            throw new JdbcPluginException(e);
        }
    }
}
