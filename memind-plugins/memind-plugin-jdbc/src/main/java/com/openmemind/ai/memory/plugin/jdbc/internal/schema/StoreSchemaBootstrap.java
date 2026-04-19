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
import com.openmemind.ai.memory.plugin.jdbc.internal.support.SqlScriptRunner;
import javax.sql.DataSource;

public final class StoreSchemaBootstrap {

    private static final String SQLITE_STORE_RESOURCE = "db/jdbc/sqlite/store/V1__init.sql";
    private static final String SQLITE_MULTIMODAL_RESOURCE =
            "db/jdbc/sqlite/store/V2__multimodal.sql";
    private static final String SQLITE_BUBBLE_STATE_RESOURCE =
            "db/jdbc/sqlite/store/V3__bubble_state.sql";
    private static final String SQLITE_ITEM_TEMPORAL_RESOURCE =
            "db/jdbc/sqlite/store/V4__item_temporal_fields.sql";
    private static final String SQLITE_ITEM_TEMPORAL_LOOKUP_RESOURCE =
            "db/jdbc/sqlite/store/V5__item_temporal_lookup.sql";
    private static final String MYSQL_STORE_RESOURCE = "db/jdbc/mysql/store/V1__init.sql";
    private static final String MYSQL_MULTIMODAL_RESOURCE =
            "db/jdbc/mysql/store/V2__multimodal.sql";
    private static final String MYSQL_BUBBLE_STATE_RESOURCE =
            "db/jdbc/mysql/store/V3__bubble_state.sql";
    private static final String MYSQL_ITEM_TEMPORAL_RESOURCE =
            "db/jdbc/mysql/store/V4__item_temporal_fields.sql";
    private static final String MYSQL_ITEM_TEMPORAL_LOOKUP_RESOURCE =
            "db/jdbc/mysql/store/V5__item_temporal_lookup.sql";
    private static final String POSTGRESQL_STORE_RESOURCE = "db/jdbc/postgresql/store/V1__init.sql";
    private static final String POSTGRESQL_MULTIMODAL_RESOURCE =
            "db/jdbc/postgresql/store/V2__multimodal.sql";
    private static final String POSTGRESQL_BUBBLE_STATE_RESOURCE =
            "db/jdbc/postgresql/store/V3__bubble_state.sql";
    private static final String POSTGRESQL_ITEM_TEMPORAL_RESOURCE =
            "db/jdbc/postgresql/store/V4__item_temporal_fields.sql";
    private static final String POSTGRESQL_ITEM_TEMPORAL_LOOKUP_RESOURCE =
            "db/jdbc/postgresql/store/V5__item_temporal_lookup.sql";

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
            SqlScriptRunner.execute(dataSource, SQLITE_STORE_RESOURCE);
            createdStoreSchema = true;
        }
        if (!hasRequiredSqliteMultimodalSchema(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required SQLite multimodal schema: memory_resource");
            }
            SqlScriptRunner.execute(dataSource, SQLITE_MULTIMODAL_RESOURCE);
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
            SqlScriptRunner.execute(dataSource, MYSQL_STORE_RESOURCE);
            createdStoreSchema = true;
        }
        if (!hasRequiredMysqlMultimodalSchema(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required MySQL multimodal schema: memory_resource");
            }
            SqlScriptRunner.execute(dataSource, MYSQL_MULTIMODAL_RESOURCE);
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
            SqlScriptRunner.execute(dataSource, POSTGRESQL_STORE_RESOURCE);
            createdStoreSchema = true;
        }
        if (!hasRequiredPostgresqlMultimodalSchema(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required PostgreSQL multimodal schema: memory_resource");
            }
            SqlScriptRunner.execute(dataSource, POSTGRESQL_MULTIMODAL_RESOURCE);
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
        SqlScriptRunner.execute(dataSource, SQLITE_BUBBLE_STATE_RESOURCE);
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
        if (hasAnySqliteItemTemporalColumn(dataSource)) {
            ensureSqliteItemTemporalColumn(
                    dataSource,
                    "occurred_start",
                    "ALTER TABLE memory_item ADD COLUMN occurred_start TEXT");
            ensureSqliteItemTemporalColumn(
                    dataSource,
                    "occurred_end",
                    "ALTER TABLE memory_item ADD COLUMN occurred_end TEXT");
            ensureSqliteItemTemporalColumn(
                    dataSource,
                    "time_granularity",
                    "ALTER TABLE memory_item ADD COLUMN time_granularity TEXT");
            return;
        }
        SqlScriptRunner.execute(dataSource, SQLITE_ITEM_TEMPORAL_RESOURCE);
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
        SqlScriptRunner.execute(dataSource, MYSQL_BUBBLE_STATE_RESOURCE);
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
        if (hasAnyMysqlItemTemporalColumn(dataSource)) {
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
            return;
        }
        SqlScriptRunner.execute(dataSource, MYSQL_ITEM_TEMPORAL_RESOURCE);
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
        SqlScriptRunner.execute(dataSource, POSTGRESQL_BUBBLE_STATE_RESOURCE);
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
        if (hasAnyPostgresqlItemTemporalColumn(dataSource)) {
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
            return;
        }
        SqlScriptRunner.execute(dataSource, POSTGRESQL_ITEM_TEMPORAL_RESOURCE);
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
            if (!hasAnySqliteItemTemporalLookupColumn(dataSource)) {
                SqlScriptRunner.execute(dataSource, SQLITE_ITEM_TEMPORAL_LOOKUP_RESOURCE);
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
            if (!hasAnyMysqlItemTemporalLookupColumn(dataSource)) {
                SqlScriptRunner.execute(dataSource, MYSQL_ITEM_TEMPORAL_LOOKUP_RESOURCE);
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
            if (!hasAnyPostgresqlItemTemporalLookupColumn(dataSource)) {
                SqlScriptRunner.execute(dataSource, POSTGRESQL_ITEM_TEMPORAL_LOOKUP_RESOURCE);
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

    private static boolean hasAnySqliteItemTemporalColumn(DataSource dataSource) {
        return SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", "occurred_start")
                || SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", "occurred_end")
                || SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", "time_granularity");
    }

    private static void ensureSqliteItemTemporalColumn(
            DataSource dataSource, String columnName, String sql) {
        if (SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", columnName)) {
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

    private static boolean hasAnySqliteItemTemporalLookupColumn(DataSource dataSource) {
        return SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", "temporal_start")
                || SchemaVerifier.hasSqliteColumn(
                        dataSource, "memory_item", "temporal_end_or_anchor")
                || SchemaVerifier.hasSqliteColumn(dataSource, "memory_item", "temporal_anchor");
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

    private static boolean hasAnyMysqlItemTemporalColumn(DataSource dataSource) {
        return SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", "occurred_start")
                || SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", "occurred_end")
                || SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", "time_granularity");
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

    private static boolean hasAnyMysqlItemTemporalLookupColumn(DataSource dataSource) {
        return SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", "temporal_start")
                || SchemaVerifier.hasMysqlColumn(
                        dataSource, "memory_item", "temporal_end_or_anchor")
                || SchemaVerifier.hasMysqlColumn(dataSource, "memory_item", "temporal_anchor");
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

    private static boolean hasAnyPostgresqlItemTemporalColumn(DataSource dataSource) {
        return SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_item", "occurred_start")
                || SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_item", "occurred_end")
                || SchemaVerifier.hasPostgresqlColumn(
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

    private static boolean hasAnyPostgresqlItemTemporalLookupColumn(DataSource dataSource) {
        return SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_item", "temporal_start")
                || SchemaVerifier.hasPostgresqlColumn(
                        dataSource, "memory_item", "temporal_end_or_anchor")
                || SchemaVerifier.hasPostgresqlColumn(dataSource, "memory_item", "temporal_anchor");
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
