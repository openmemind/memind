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

import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcExecutor;
import com.openmemind.ai.memory.plugin.jdbc.internal.support.JdbcPluginException;
import javax.sql.DataSource;

public final class StoreSchemaBootstrap {

    private StoreSchemaBootstrap() {}

    public static StoreSchemaInitResult ensureSqlite(
            DataSource dataSource, boolean createIfNotExist) {
        boolean hadInsightTypeTable =
                SchemaVerifier.hasSqliteTable(dataSource, "memory_insight_type");
        boolean createdStoreSchema = false;
        if (!hasRequiredSqliteStoreTables(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required SQLite store table: memory_raw_data");
            }
            JdbcExecutor.execute(dataSource, "PRAGMA journal_mode = WAL");
            JdbcSchemaBootstrap.executeSqliteInit(dataSource);
            createdStoreSchema = true;
        }
        if (!hasRequiredSqliteMultimodalSchema(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required SQLite multimodal schema: memory_resource");
            }
            ensureSqliteMultimodalSchema(dataSource);
        }
        ensureSqliteBubbleStateSchema(dataSource, createIfNotExist);
        ensureSqliteItemTemporalSchema(dataSource, createIfNotExist);
        ensureSqliteItemTemporalLookupSchema(dataSource, createIfNotExist);
        ensureSqliteInsightConfidenceRemoved(dataSource, createIfNotExist);
        boolean hasInsightTypeTable =
                SchemaVerifier.hasSqliteTable(dataSource, "memory_insight_type");
        return new StoreSchemaInitResult(
                createdStoreSchema, !hadInsightTypeTable && hasInsightTypeTable);
    }

    public static StoreSchemaInitResult ensureMysql(
            DataSource dataSource, boolean createIfNotExist) {
        boolean hadInsightTypeTable =
                SchemaVerifier.hasMysqlTable(dataSource, "memory_insight_type");
        boolean createdStoreSchema = false;
        if (!hasRequiredMysqlStoreTables(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required MySQL store table: memory_raw_data");
            }
            JdbcSchemaBootstrap.executeMysqlInit(dataSource);
            createdStoreSchema = true;
        }
        if (!hasRequiredMysqlMultimodalSchema(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required MySQL multimodal schema: memory_resource");
            }
            ensureMysqlMultimodalSchema(dataSource);
        }
        ensureMysqlBubbleStateSchema(dataSource, createIfNotExist);
        ensureMysqlItemTemporalSchema(dataSource, createIfNotExist);
        ensureMysqlItemTemporalLookupSchema(dataSource, createIfNotExist);
        ensureMysqlInsightConfidenceRemoved(dataSource, createIfNotExist);
        boolean hasInsightTypeTable =
                SchemaVerifier.hasMysqlTable(dataSource, "memory_insight_type");
        return new StoreSchemaInitResult(
                createdStoreSchema, !hadInsightTypeTable && hasInsightTypeTable);
    }

    public static StoreSchemaInitResult ensurePostgresql(
            DataSource dataSource, boolean createIfNotExist) {
        boolean hadInsightTypeTable =
                SchemaVerifier.hasPostgresqlTable(dataSource, "memory_insight_type");
        boolean createdStoreSchema = false;
        if (!hasRequiredPostgresqlStoreTables(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required PostgreSQL store table: memory_raw_data");
            }
            JdbcSchemaBootstrap.executePostgresqlInit(dataSource);
            createdStoreSchema = true;
        }
        if (!hasRequiredPostgresqlMultimodalSchema(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required PostgreSQL multimodal schema: memory_resource");
            }
            ensurePostgresqlMultimodalSchema(dataSource);
        }
        ensurePostgresqlBubbleStateSchema(dataSource, createIfNotExist);
        ensurePostgresqlItemTemporalSchema(dataSource, createIfNotExist);
        ensurePostgresqlItemTemporalLookupSchema(dataSource, createIfNotExist);
        ensurePostgresqlInsightConfidenceRemoved(dataSource, createIfNotExist);
        boolean hasInsightTypeTable =
                SchemaVerifier.hasPostgresqlTable(dataSource, "memory_insight_type");
        return new StoreSchemaInitResult(
                createdStoreSchema, !hadInsightTypeTable && hasInsightTypeTable);
    }

    private static boolean hasRequiredSqliteStoreTables(DataSource dataSource) {
        return SchemaVerifier.hasSqliteTable(dataSource, "memory_raw_data")
                && SchemaVerifier.hasSqliteTable(dataSource, "memory_item")
                && SchemaVerifier.hasSqliteTable(dataSource, "memory_insight_type")
                && SchemaVerifier.hasSqliteTable(dataSource, "memory_insight")
                && SchemaVerifier.hasSqliteTable(dataSource, "memory_insight_buffer");
    }

    private static boolean hasRequiredSqliteMultimodalSchema(DataSource dataSource) {
        return SchemaVerifier.hasSqliteTable(dataSource, "memory_resource")
                && SchemaVerifier.hasSqliteColumn(dataSource, "memory_raw_data", "resource_id")
                && SchemaVerifier.hasSqliteColumn(dataSource, "memory_raw_data", "mime_type");
    }

    private static boolean hasRequiredMysqlStoreTables(DataSource dataSource) {
        return SchemaVerifier.hasMysqlTable(dataSource, "memory_raw_data")
                && SchemaVerifier.hasMysqlTable(dataSource, "memory_item")
                && SchemaVerifier.hasMysqlTable(dataSource, "memory_insight_type")
                && SchemaVerifier.hasMysqlTable(dataSource, "memory_insight")
                && SchemaVerifier.hasMysqlTable(dataSource, "memory_insight_buffer");
    }

    private static boolean hasRequiredMysqlMultimodalSchema(DataSource dataSource) {
        return SchemaVerifier.hasMysqlTable(dataSource, "memory_resource")
                && SchemaVerifier.hasMysqlColumn(dataSource, "memory_raw_data", "resource_id")
                && SchemaVerifier.hasMysqlColumn(dataSource, "memory_raw_data", "mime_type");
    }

    private static boolean hasRequiredPostgresqlStoreTables(DataSource dataSource) {
        return SchemaVerifier.hasPostgresqlTable(dataSource, "memory_raw_data")
                && SchemaVerifier.hasPostgresqlTable(dataSource, "memory_item")
                && SchemaVerifier.hasPostgresqlTable(dataSource, "memory_insight_type")
                && SchemaVerifier.hasPostgresqlTable(dataSource, "memory_insight")
                && SchemaVerifier.hasPostgresqlTable(dataSource, "memory_insight_buffer");
    }

    private static boolean hasRequiredPostgresqlMultimodalSchema(DataSource dataSource) {
        return SchemaVerifier.hasPostgresqlTable(dataSource, "memory_resource")
                && SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_raw_data", "resource_id")
                && SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_raw_data", "mime_type");
    }

    private static void ensureSqliteBubbleStateSchema(
            DataSource dataSource, boolean createIfNotExist) {
        if (SchemaVerifier.hasSqliteTable(dataSource, "memory_insight_bubble_state")) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Missing required SQLite bubble schema: memory_insight_bubble_state");
        }
        JdbcExecutor.execute(
                dataSource,
                """
                CREATE TABLE IF NOT EXISTS memory_insight_bubble_state (
                    id           INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
                    memory_id    TEXT         NOT NULL,
                    tier         TEXT         NOT NULL,
                    insight_type TEXT         NOT NULL,
                    dirty_count  INTEGER      NOT NULL DEFAULT 0,
                    created_at   TEXT         NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at   TEXT         NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(memory_id, tier, insight_type)
                )
                """);
    }

    private static void ensureSqliteMultimodalSchema(DataSource dataSource) {
        JdbcExecutor.execute(
                dataSource,
                """
                CREATE TABLE IF NOT EXISTS memory_resource (
                    id         INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
                    biz_id     TEXT         NOT NULL,
                    user_id    TEXT         NOT NULL,
                    agent_id   TEXT         NOT NULL,
                    memory_id  TEXT         NOT NULL,
                    source_uri TEXT,
                    storage_uri TEXT,
                    file_name  TEXT,
                    mime_type  TEXT,
                    checksum   TEXT,
                    size_bytes INTEGER,
                    metadata   TEXT,
                    created_at TEXT         NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT         NOT NULL DEFAULT (datetime('now')),
                    deleted    INTEGER      NOT NULL DEFAULT 0
                )
                """);
        ensureSqliteIndex(
                dataSource,
                "uk_resource_biz_id",
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_resource_biz_id ON"
                        + " memory_resource(user_id, agent_id, biz_id)");
        ensureSqliteIndex(
                dataSource,
                "idx_resource_memory_id",
                "CREATE INDEX IF NOT EXISTS idx_resource_memory_id ON"
                        + " memory_resource(user_id, agent_id)");
        ensureSqliteTableColumn(
                dataSource,
                "memory_raw_data",
                "resource_id",
                "ALTER TABLE memory_raw_data ADD COLUMN resource_id TEXT");
        ensureSqliteTableColumn(
                dataSource,
                "memory_raw_data",
                "mime_type",
                "ALTER TABLE memory_raw_data ADD COLUMN mime_type TEXT");
    }

    private static void ensureSqliteItemTemporalSchema(
            DataSource dataSource, boolean createIfNotExist) {
        if (hasRequiredSqliteItemTemporalSchema(dataSource)) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Missing required SQLite item temporal schema: occurred_start");
        }
        ensureSqliteItemTemporalColumn(
                dataSource,
                "occurred_start",
                "ALTER TABLE memory_item ADD COLUMN occurred_start TEXT");
        ensureSqliteItemTemporalColumn(
                dataSource, "occurred_end", "ALTER TABLE memory_item ADD COLUMN occurred_end TEXT");
        ensureSqliteItemTemporalColumn(
                dataSource,
                "time_granularity",
                "ALTER TABLE memory_item ADD COLUMN time_granularity TEXT");
    }

    private static void ensureMysqlBubbleStateSchema(
            DataSource dataSource, boolean createIfNotExist) {
        if (SchemaVerifier.hasMysqlTable(dataSource, "memory_insight_bubble_state")) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Missing required MySQL bubble schema: memory_insight_bubble_state");
        }
        JdbcExecutor.execute(
                dataSource,
                """
                CREATE TABLE IF NOT EXISTS memory_insight_bubble_state (
                    id           BIGINT       NOT NULL AUTO_INCREMENT,
                    memory_id    VARCHAR(255) NOT NULL,
                    tier         VARCHAR(32)  NOT NULL,
                    insight_type VARCHAR(255) NOT NULL,
                    dirty_count  INT          NOT NULL DEFAULT 0,
                    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_memory_insight_bubble_state (memory_id, tier, insight_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private static void ensureMysqlMultimodalSchema(DataSource dataSource) {
        JdbcExecutor.execute(
                dataSource,
                """
                CREATE TABLE IF NOT EXISTS memory_resource (
                    id         BIGINT       NOT NULL AUTO_INCREMENT,
                    biz_id     VARCHAR(64)  NOT NULL,
                    user_id    VARCHAR(64)  NOT NULL,
                    agent_id   VARCHAR(64)  NOT NULL,
                    memory_id  VARCHAR(200) NOT NULL,
                    source_uri VARCHAR(1024),
                    storage_uri VARCHAR(1024),
                    file_name  VARCHAR(512),
                    mime_type  VARCHAR(128),
                    checksum   VARCHAR(128),
                    size_bytes BIGINT,
                    metadata   JSON,
                    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    deleted    TINYINT      NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_resource_biz_id (user_id, agent_id, biz_id),
                    KEY idx_resource_memory_id (user_id, agent_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        JdbcExecutor.execute(
                dataSource,
                """
                ALTER TABLE memory_raw_data
                    ADD COLUMN IF NOT EXISTS resource_id VARCHAR(64),
                    ADD COLUMN IF NOT EXISTS mime_type VARCHAR(128)
                """);
    }

    private static void ensureMysqlItemTemporalSchema(
            DataSource dataSource, boolean createIfNotExist) {
        if (hasRequiredMysqlItemTemporalSchema(dataSource)) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Missing required MySQL item temporal schema: occurred_start");
        }
        ensureMysqlItemTemporalColumn(
                dataSource,
                "occurred_start",
                "ALTER TABLE memory_item ADD COLUMN occurred_start DATETIME(3) NULL");
        ensureMysqlItemTemporalColumn(
                dataSource,
                "occurred_end",
                "ALTER TABLE memory_item ADD COLUMN occurred_end DATETIME(3) NULL");
        ensureMysqlItemTemporalColumn(
                dataSource,
                "time_granularity",
                "ALTER TABLE memory_item ADD COLUMN time_granularity VARCHAR(16) NULL");
    }

    private static void ensurePostgresqlBubbleStateSchema(
            DataSource dataSource, boolean createIfNotExist) {
        if (SchemaVerifier.hasPostgresqlTable(dataSource, "memory_insight_bubble_state")) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Missing required PostgreSQL bubble schema: memory_insight_bubble_state");
        }
        JdbcExecutor.execute(
                dataSource,
                """
                CREATE TABLE IF NOT EXISTS memory_insight_bubble_state (
                    id           BIGSERIAL PRIMARY KEY,
                    memory_id    VARCHAR(255) NOT NULL,
                    tier         VARCHAR(32)  NOT NULL,
                    insight_type VARCHAR(255) NOT NULL,
                    dirty_count  INTEGER      NOT NULL DEFAULT 0,
                    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(memory_id, tier, insight_type)
                )
                """);
    }

    private static void ensurePostgresqlMultimodalSchema(DataSource dataSource) {
        JdbcExecutor.execute(
                dataSource,
                """
                CREATE TABLE IF NOT EXISTS memory_resource (
                    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    biz_id     VARCHAR(64)  NOT NULL,
                    user_id    VARCHAR(64)  NOT NULL,
                    agent_id   VARCHAR(64)  NOT NULL,
                    memory_id  VARCHAR(200) NOT NULL,
                    source_uri VARCHAR(1024),
                    storage_uri VARCHAR(1024),
                    file_name  VARCHAR(512),
                    mime_type  VARCHAR(128),
                    checksum   VARCHAR(128),
                    size_bytes BIGINT,
                    metadata   JSONB,
                    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted    BOOLEAN      NOT NULL DEFAULT FALSE
                )
                """);
        ensurePostgresqlIndex(
                dataSource,
                "memory_resource",
                "uk_resource_biz_id",
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_resource_biz_id ON"
                        + " memory_resource(user_id, agent_id, biz_id)");
        ensurePostgresqlIndex(
                dataSource,
                "memory_resource",
                "idx_resource_memory_id",
                "CREATE INDEX IF NOT EXISTS idx_resource_memory_id ON"
                        + " memory_resource(user_id, agent_id)");
        JdbcExecutor.execute(
                dataSource,
                """
                ALTER TABLE memory_raw_data
                    ADD COLUMN IF NOT EXISTS resource_id VARCHAR(64),
                    ADD COLUMN IF NOT EXISTS mime_type VARCHAR(128)
                """);
    }

    private static void ensurePostgresqlItemTemporalSchema(
            DataSource dataSource, boolean createIfNotExist) {
        if (hasRequiredPostgresqlItemTemporalSchema(dataSource)) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Missing required PostgreSQL item temporal schema: occurred_start");
        }
        ensurePostgresqlItemTemporalColumn(
                dataSource,
                "occurred_start",
                "ALTER TABLE memory_item ADD COLUMN occurred_start TIMESTAMPTZ");
        ensurePostgresqlItemTemporalColumn(
                dataSource,
                "occurred_end",
                "ALTER TABLE memory_item ADD COLUMN occurred_end TIMESTAMPTZ");
        ensurePostgresqlItemTemporalColumn(
                dataSource,
                "time_granularity",
                "ALTER TABLE memory_item ADD COLUMN time_granularity VARCHAR(16)");
    }

    private static void ensureSqliteInsightConfidenceRemoved(
            DataSource dataSource, boolean createIfNotExist) {
        if (!SchemaVerifier.hasSqliteColumn(dataSource, "memory_insight", "confidence")) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Legacy SQLite insight schema requires confidence column removal");
        }
        JdbcExecutor.execute(dataSource, "ALTER TABLE memory_insight DROP COLUMN confidence");
    }

    private static void ensureSqliteItemTemporalLookupSchema(
            DataSource dataSource, boolean createIfNotExist) {
        if (!hasRequiredSqliteItemTemporalLookupSchema(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required SQLite item temporal lookup schema: temporal_start");
            }
            ensureSqliteItemTemporalLookupColumn(
                    dataSource,
                    "temporal_start",
                    "ALTER TABLE memory_item ADD COLUMN temporal_start TEXT");
            ensureSqliteItemTemporalLookupColumn(
                    dataSource,
                    "temporal_end_or_anchor",
                    "ALTER TABLE memory_item ADD COLUMN temporal_end_or_anchor TEXT");
            ensureSqliteItemTemporalLookupColumn(
                    dataSource,
                    "temporal_anchor",
                    "ALTER TABLE memory_item ADD COLUMN temporal_anchor TEXT");
        }
        backfillTemporalLookupColumns(dataSource);
        ensureSqliteIndex(
                dataSource,
                "idx_item_temporal_anchor_scope",
                "CREATE INDEX IF NOT EXISTS idx_item_temporal_anchor_scope ON"
                        + " memory_item(memory_id, type, category, temporal_anchor, biz_id)");
        ensureSqliteIndex(
                dataSource,
                "idx_item_temporal_start_scope",
                "CREATE INDEX IF NOT EXISTS idx_item_temporal_start_scope ON"
                        + " memory_item(memory_id, type, category, temporal_start, biz_id)");
        ensureSqliteIndex(
                dataSource,
                "idx_item_temporal_end_scope",
                "CREATE INDEX IF NOT EXISTS idx_item_temporal_end_scope ON"
                        + " memory_item(memory_id, type, category, temporal_end_or_anchor,"
                        + " biz_id)");
    }

    private static void ensureMysqlItemTemporalLookupSchema(
            DataSource dataSource, boolean createIfNotExist) {
        if (!hasRequiredMysqlItemTemporalLookupSchema(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required MySQL item temporal lookup schema: temporal_start");
            }
            ensureMysqlItemTemporalLookupColumn(
                    dataSource,
                    "temporal_start",
                    "ALTER TABLE memory_item ADD COLUMN temporal_start DATETIME(3) NULL");
            ensureMysqlItemTemporalLookupColumn(
                    dataSource,
                    "temporal_end_or_anchor",
                    "ALTER TABLE memory_item ADD COLUMN temporal_end_or_anchor DATETIME(3) NULL");
            ensureMysqlItemTemporalLookupColumn(
                    dataSource,
                    "temporal_anchor",
                    "ALTER TABLE memory_item ADD COLUMN temporal_anchor DATETIME(3) NULL");
        }
        backfillTemporalLookupColumns(dataSource);
        ensureMysqlIndex(
                dataSource,
                "memory_item",
                "idx_item_temporal_anchor_scope",
                "CREATE INDEX idx_item_temporal_anchor_scope ON"
                        + " memory_item(memory_id, type, category, temporal_anchor, biz_id)");
        ensureMysqlIndex(
                dataSource,
                "memory_item",
                "idx_item_temporal_start_scope",
                "CREATE INDEX idx_item_temporal_start_scope ON"
                        + " memory_item(memory_id, type, category, temporal_start, biz_id)");
        ensureMysqlIndex(
                dataSource,
                "memory_item",
                "idx_item_temporal_end_scope",
                "CREATE INDEX idx_item_temporal_end_scope ON"
                        + " memory_item(memory_id, type, category, temporal_end_or_anchor,"
                        + " biz_id)");
    }

    private static void ensurePostgresqlItemTemporalLookupSchema(
            DataSource dataSource, boolean createIfNotExist) {
        if (!hasRequiredPostgresqlItemTemporalLookupSchema(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required PostgreSQL item temporal lookup schema: temporal_start");
            }
            ensurePostgresqlItemTemporalLookupColumn(
                    dataSource,
                    "temporal_start",
                    "ALTER TABLE memory_item ADD COLUMN temporal_start TIMESTAMPTZ");
            ensurePostgresqlItemTemporalLookupColumn(
                    dataSource,
                    "temporal_end_or_anchor",
                    "ALTER TABLE memory_item ADD COLUMN temporal_end_or_anchor TIMESTAMPTZ");
            ensurePostgresqlItemTemporalLookupColumn(
                    dataSource,
                    "temporal_anchor",
                    "ALTER TABLE memory_item ADD COLUMN temporal_anchor TIMESTAMPTZ");
        }
        backfillTemporalLookupColumns(dataSource);
        ensurePostgresqlIndex(
                dataSource,
                "memory_item",
                "idx_item_temporal_anchor_scope",
                "CREATE INDEX idx_item_temporal_anchor_scope ON"
                        + " memory_item(memory_id, type, category, temporal_anchor, biz_id)");
        ensurePostgresqlIndex(
                dataSource,
                "memory_item",
                "idx_item_temporal_start_scope",
                "CREATE INDEX idx_item_temporal_start_scope ON"
                        + " memory_item(memory_id, type, category, temporal_start, biz_id)");
        ensurePostgresqlIndex(
                dataSource,
                "memory_item",
                "idx_item_temporal_end_scope",
                "CREATE INDEX idx_item_temporal_end_scope ON"
                        + " memory_item(memory_id, type, category, temporal_end_or_anchor,"
                        + " biz_id)");
    }

    private static void ensureMysqlInsightConfidenceRemoved(
            DataSource dataSource, boolean createIfNotExist) {
        if (!SchemaVerifier.hasMysqlColumn(dataSource, "memory_insight", "confidence")) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Legacy MySQL insight schema requires confidence column removal");
        }
        JdbcExecutor.execute(dataSource, "ALTER TABLE memory_insight DROP COLUMN confidence");
    }

    private static void ensurePostgresqlInsightConfidenceRemoved(
            DataSource dataSource, boolean createIfNotExist) {
        if (!SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_insight", "confidence")) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Legacy PostgreSQL insight schema requires confidence column removal");
        }
        JdbcExecutor.execute(dataSource, "ALTER TABLE memory_insight DROP COLUMN confidence");
    }

    private static boolean hasRequiredSqliteItemTemporalSchema(DataSource dataSource) {
        return SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", "occurred_start")
                && SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", "occurred_end")
                && SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", "time_granularity");
    }

    private static void ensureSqliteItemTemporalColumn(
            DataSource dataSource, String columnName, String sql) {
        if (SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", columnName)) {
            return;
        }
        JdbcExecutor.execute(dataSource, sql);
    }

    private static void ensureSqliteTableColumn(
            DataSource dataSource, String tableName, String columnName, String sql) {
        if (SchemaVerifier.hasSqliteColumn(dataSource, tableName, columnName)) {
            return;
        }
        JdbcExecutor.execute(dataSource, sql);
    }

    private static boolean hasRequiredSqliteItemTemporalLookupSchema(DataSource dataSource) {
        return SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", "temporal_start")
                && SchemaVerifier.hasSqliteColumn(
                        dataSource, "memory_item", "temporal_end_or_anchor")
                && SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", "temporal_anchor");
    }

    private static void ensureSqliteItemTemporalLookupColumn(
            DataSource dataSource, String columnName, String sql) {
        if (SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", columnName)) {
            return;
        }
        JdbcExecutor.execute(dataSource, sql);
    }

    private static void ensureSqliteIndex(DataSource dataSource, String indexName, String sql) {
        if (SchemaVerifier.hasSqliteIndex(dataSource, indexName)) {
            return;
        }
        JdbcExecutor.execute(dataSource, sql);
    }

    private static boolean hasRequiredMysqlItemTemporalSchema(DataSource dataSource) {
        return SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", "occurred_start")
                && SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", "occurred_end")
                && SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", "time_granularity");
    }

    private static void ensureMysqlItemTemporalColumn(
            DataSource dataSource, String columnName, String sql) {
        if (SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", columnName)) {
            return;
        }
        JdbcExecutor.execute(dataSource, sql);
    }

    private static boolean hasRequiredMysqlItemTemporalLookupSchema(DataSource dataSource) {
        return SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", "temporal_start")
                && SchemaVerifier.hasMysqlColumn(
                        dataSource, "memory_item", "temporal_end_or_anchor")
                && SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", "temporal_anchor");
    }

    private static void ensureMysqlItemTemporalLookupColumn(
            DataSource dataSource, String columnName, String sql) {
        if (SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", columnName)) {
            return;
        }
        JdbcExecutor.execute(dataSource, sql);
    }

    private static void ensureMysqlIndex(
            DataSource dataSource, String tableName, String indexName, String sql) {
        if (SchemaVerifier.hasMysqlIndex(dataSource, tableName, indexName)) {
            return;
        }
        JdbcExecutor.execute(dataSource, sql);
    }

    private static boolean hasRequiredPostgresqlItemTemporalSchema(DataSource dataSource) {
        return SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_item", "occurred_start")
                && SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_item", "occurred_end")
                && SchemaVerifier.hasPostgresqlColumn(
                        dataSource, "memory_item", "time_granularity");
    }

    private static void ensurePostgresqlItemTemporalColumn(
            DataSource dataSource, String columnName, String sql) {
        if (SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_item", columnName)) {
            return;
        }
        JdbcExecutor.execute(dataSource, sql);
    }

    private static boolean hasRequiredPostgresqlItemTemporalLookupSchema(DataSource dataSource) {
        return SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_item", "temporal_start")
                && SchemaVerifier.hasPostgresqlColumn(
                        dataSource, "memory_item", "temporal_end_or_anchor")
                && SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_item", "temporal_anchor");
    }

    private static void ensurePostgresqlItemTemporalLookupColumn(
            DataSource dataSource, String columnName, String sql) {
        if (SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_item", columnName)) {
            return;
        }
        JdbcExecutor.execute(dataSource, sql);
    }

    private static void ensurePostgresqlIndex(
            DataSource dataSource, String tableName, String indexName, String sql) {
        if (SchemaVerifier.hasPostgresqlIndex(dataSource, tableName, indexName)) {
            return;
        }
        JdbcExecutor.execute(dataSource, sql);
    }

    private static void backfillTemporalLookupColumns(DataSource dataSource) {
        JdbcExecutor.execute(
                dataSource,
                """
                UPDATE memory_item
                SET temporal_start = COALESCE(temporal_start, occurred_start, occurred_at, observed_at),
                    temporal_end_or_anchor = COALESCE(
                            temporal_end_or_anchor,
                            occurred_end,
                            occurred_start,
                            occurred_at,
                            observed_at),
                    temporal_anchor = COALESCE(temporal_anchor, occurred_start, occurred_at, observed_at)
                WHERE COALESCE(occurred_start, occurred_at, observed_at) IS NOT NULL
                  AND (
                          temporal_start IS NULL
                          OR temporal_end_or_anchor IS NULL
                          OR temporal_anchor IS NULL
                      )
                """);
    }
}
