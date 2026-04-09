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
    private static final String MYSQL_STORE_RESOURCE = "db/jdbc/mysql/store/V1__init.sql";
    private static final String MYSQL_MULTIMODAL_RESOURCE =
            "db/jdbc/mysql/store/V2__multimodal.sql";
    private static final String POSTGRESQL_STORE_RESOURCE = "db/jdbc/postgresql/store/V1__init.sql";
    private static final String POSTGRESQL_MULTIMODAL_RESOURCE =
            "db/jdbc/postgresql/store/V2__multimodal.sql";

    private StoreSchemaBootstrap() {}

    public static void ensureSqlite(DataSource dataSource, boolean createIfNotExist) {
        if (!hasRequiredSqliteStoreTables(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required SQLite store table: memory_raw_data");
            }
            JdbcExecutor.execute(dataSource, "PRAGMA journal_mode = WAL");
            SqlScriptRunner.execute(dataSource, SQLITE_STORE_RESOURCE);
        }
        if (hasRequiredSqliteMultimodalSchema(dataSource)) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Missing required SQLite multimodal schema: memory_resource");
        }
        SqlScriptRunner.execute(dataSource, SQLITE_MULTIMODAL_RESOURCE);
    }

    public static void ensureMysql(DataSource dataSource, boolean createIfNotExist) {
        if (!hasRequiredMysqlStoreTables(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required MySQL store table: memory_raw_data");
            }
            SqlScriptRunner.execute(dataSource, MYSQL_STORE_RESOURCE);
        }
        if (hasRequiredMysqlMultimodalSchema(dataSource)) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Missing required MySQL multimodal schema: memory_resource");
        }
        SqlScriptRunner.execute(dataSource, MYSQL_MULTIMODAL_RESOURCE);
    }

    public static void ensurePostgresql(DataSource dataSource, boolean createIfNotExist) {
        if (!hasRequiredPostgresqlStoreTables(dataSource)) {
            if (!createIfNotExist) {
                throw new JdbcPluginException(
                        "Missing required PostgreSQL store table: memory_raw_data");
            }
            SqlScriptRunner.execute(dataSource, POSTGRESQL_STORE_RESOURCE);
        }
        if (hasRequiredPostgresqlMultimodalSchema(dataSource)) {
            return;
        }
        if (!createIfNotExist) {
            throw new JdbcPluginException(
                    "Missing required PostgreSQL multimodal schema: memory_resource");
        }
        SqlScriptRunner.execute(dataSource, POSTGRESQL_MULTIMODAL_RESOURCE);
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
}
