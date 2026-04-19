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
package com.openmemind.ai.memory.plugin.store.mybatis.schema;

import com.baomidou.mybatisplus.extension.ddl.IDdl;
import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

public class MemoryStoreDdl implements IDdl, Ordered {

    private final DataSource dataSource;
    private final DatabaseDialectDetector databaseDialectDetector;

    public MemoryStoreDdl(DataSource dataSource, DatabaseDialectDetector databaseDialectDetector) {
        this.dataSource = dataSource;
        this.databaseDialectDetector = databaseDialectDetector;
    }

    @Override
    public List<String> getSqlFiles() {
        return databaseDialectDetector.detect(dataSource).scriptPaths();
    }

    @Override
    public void runScript(Consumer<DataSource> consumer) {
        boolean hadInsightTypeTable = tableExists("memory_insight_type");
        consumer.accept(dataSource);
        dropInsightConfidenceIfPresent();
        ensureItemTemporalColumnsPresent();
        ensureItemTemporalLookupSchemaPresent();
        if (!hadInsightTypeTable && tableExists("memory_insight_type")) {
            seedDefaultInsightTypes();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            return tableExists(metadata, tableName)
                    || tableExists(metadata, tableName.toUpperCase())
                    || tableExists(metadata, tableName.toLowerCase());
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to inspect table existence for " + tableName, e);
        }
    }

    private boolean tableExists(DatabaseMetaData metadata, String tableName) throws SQLException {
        try (ResultSet resultSet =
                metadata.getTables(null, null, tableName, new String[] {"TABLE"})) {
            return resultSet.next();
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            return columnExists(metadata, tableName, columnName)
                    || columnExists(metadata, tableName.toUpperCase(), columnName)
                    || columnExists(metadata, tableName.toLowerCase(), columnName);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to inspect column existence for " + tableName + "." + columnName, e);
        }
    }

    private boolean columnExists(DatabaseMetaData metadata, String tableName, String columnName)
            throws SQLException {
        try (ResultSet resultSet = metadata.getColumns(null, null, tableName, columnName)) {
            return resultSet.next();
        }
    }

    private boolean indexExists(String tableName, String indexName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            return indexExists(metadata, tableName, indexName)
                    || indexExists(metadata, tableName.toUpperCase(), indexName)
                    || indexExists(metadata, tableName.toLowerCase(), indexName);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to inspect index existence for " + tableName + "." + indexName, e);
        }
    }

    private boolean indexExists(DatabaseMetaData metadata, String tableName, String indexName)
            throws SQLException {
        try (ResultSet resultSet = metadata.getIndexInfo(null, null, tableName, false, false)) {
            while (resultSet.next()) {
                if (indexName.equalsIgnoreCase(resultSet.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void dropInsightConfidenceIfPresent() {
        if (!tableExists("memory_insight") || !columnExists("memory_insight", "confidence")) {
            return;
        }
        String sql =
                switch (databaseDialectDetector.detect(dataSource)) {
                    case SQLITE -> "ALTER TABLE memory_insight DROP COLUMN confidence";
                    case MYSQL -> "ALTER TABLE memory_insight DROP COLUMN confidence";
                    case POSTGRESQL -> "ALTER TABLE memory_insight DROP COLUMN confidence";
                };
        new JdbcTemplate(dataSource).execute(sql);
    }

    private void ensureItemTemporalColumnsPresent() {
        if (!tableExists("memory_item")) {
            return;
        }
        switch (databaseDialectDetector.detect(dataSource)) {
            case SQLITE -> {
                ensureColumn(
                        "memory_item",
                        "occurred_start",
                        "ALTER TABLE memory_item ADD COLUMN occurred_start TEXT");
                ensureColumn(
                        "memory_item",
                        "occurred_end",
                        "ALTER TABLE memory_item ADD COLUMN occurred_end TEXT");
                ensureColumn(
                        "memory_item",
                        "time_granularity",
                        "ALTER TABLE memory_item ADD COLUMN time_granularity TEXT");
                ensureColumn(
                        "memory_item",
                        "temporal_start",
                        "ALTER TABLE memory_item ADD COLUMN temporal_start TEXT");
                ensureColumn(
                        "memory_item",
                        "temporal_end_or_anchor",
                        "ALTER TABLE memory_item ADD COLUMN temporal_end_or_anchor TEXT");
                ensureColumn(
                        "memory_item",
                        "temporal_anchor",
                        "ALTER TABLE memory_item ADD COLUMN temporal_anchor TEXT");
            }
            case MYSQL -> {
                ensureColumn(
                        "memory_item",
                        "occurred_start",
                        "ALTER TABLE memory_item ADD COLUMN occurred_start DATETIME(3) NULL");
                ensureColumn(
                        "memory_item",
                        "occurred_end",
                        "ALTER TABLE memory_item ADD COLUMN occurred_end DATETIME(3) NULL");
                ensureColumn(
                        "memory_item",
                        "time_granularity",
                        "ALTER TABLE memory_item ADD COLUMN time_granularity VARCHAR(16) NULL");
                ensureColumn(
                        "memory_item",
                        "temporal_start",
                        "ALTER TABLE memory_item ADD COLUMN temporal_start DATETIME(3) NULL");
                ensureColumn(
                        "memory_item",
                        "temporal_end_or_anchor",
                        "ALTER TABLE memory_item ADD COLUMN temporal_end_or_anchor DATETIME(3)"
                                + " NULL");
                ensureColumn(
                        "memory_item",
                        "temporal_anchor",
                        "ALTER TABLE memory_item ADD COLUMN temporal_anchor DATETIME(3) NULL");
            }
            case POSTGRESQL -> {
                ensureColumn(
                        "memory_item",
                        "occurred_start",
                        "ALTER TABLE memory_item ADD COLUMN occurred_start TIMESTAMPTZ");
                ensureColumn(
                        "memory_item",
                        "occurred_end",
                        "ALTER TABLE memory_item ADD COLUMN occurred_end TIMESTAMPTZ");
                ensureColumn(
                        "memory_item",
                        "time_granularity",
                        "ALTER TABLE memory_item ADD COLUMN time_granularity VARCHAR(16)");
                ensureColumn(
                        "memory_item",
                        "temporal_start",
                        "ALTER TABLE memory_item ADD COLUMN temporal_start TIMESTAMPTZ");
                ensureColumn(
                        "memory_item",
                        "temporal_end_or_anchor",
                        "ALTER TABLE memory_item ADD COLUMN temporal_end_or_anchor TIMESTAMPTZ");
                ensureColumn(
                        "memory_item",
                        "temporal_anchor",
                        "ALTER TABLE memory_item ADD COLUMN temporal_anchor TIMESTAMPTZ");
            }
        }
    }

    private void ensureItemTemporalLookupSchemaPresent() {
        if (!tableExists("memory_item")) {
            return;
        }
        backfillTemporalLookupColumns();
        ensureIndex(
                "memory_item",
                "idx_item_temporal_anchor_scope",
                "CREATE INDEX idx_item_temporal_anchor_scope ON"
                        + " memory_item(memory_id, type, category, temporal_anchor, biz_id)");
        ensureIndex(
                "memory_item",
                "idx_item_temporal_start_scope",
                "CREATE INDEX idx_item_temporal_start_scope ON"
                        + " memory_item(memory_id, type, category, temporal_start, biz_id)");
        ensureIndex(
                "memory_item",
                "idx_item_temporal_end_scope",
                "CREATE INDEX idx_item_temporal_end_scope ON"
                        + " memory_item(memory_id, type, category, temporal_end_or_anchor,"
                        + " biz_id)");
    }

    private void ensureColumn(String tableName, String columnName, String sql) {
        if (columnExists(tableName, columnName)) {
            return;
        }
        new JdbcTemplate(dataSource).execute(sql);
    }

    private void ensureIndex(String tableName, String indexName, String sql) {
        if (indexExists(tableName, indexName)) {
            return;
        }
        new JdbcTemplate(dataSource).execute(sql);
    }

    private void backfillTemporalLookupColumns() {
        new JdbcTemplate(dataSource)
                .update(
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

    private void seedDefaultInsightTypes() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String sql = insightTypeInsertSql();
        for (MemoryInsightType type : DefaultInsightTypes.all()) {
            jdbcTemplate.update(
                    connection -> {
                        PreparedStatement statement = connection.prepareStatement(sql);
                        bindInsightTypeInsert(statement, type);
                        return statement;
                    });
        }
    }

    private String insightTypeInsertSql() {
        return switch (databaseDialectDetector.detect(dataSource)) {
            case SQLITE ->
                    """
                    INSERT INTO memory_insight_type
                        (biz_id, name, description, description_vector_id, categories,
                         target_tokens, analysis_mode, scope, tree_config)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            case MYSQL ->
                    """
                    INSERT INTO memory_insight_type
                        (biz_id, name, description, description_vector_id, categories,
                         target_tokens, analysis_mode, scope, tree_config)
                    VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, CAST(? AS JSON))
                    """;
            case POSTGRESQL ->
                    """
                    INSERT INTO memory_insight_type
                        (biz_id, name, description, description_vector_id, categories,
                         target_tokens, analysis_mode, scope, tree_config)
                    VALUES (?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, CAST(? AS JSONB))
                    """;
        };
    }

    private void bindInsightTypeInsert(PreparedStatement statement, MemoryInsightType type)
            throws SQLException {
        statement.setObject(1, type.id());
        statement.setString(2, type.name());
        statement.setString(3, type.description());
        statement.setString(4, type.descriptionVectorId());
        statement.setString(5, JsonUtils.toJson(type.categories()));
        statement.setInt(6, type.targetTokens());
        statement.setString(
                7, type.insightAnalysisMode() != null ? type.insightAnalysisMode().name() : null);
        statement.setString(8, type.scope() != null ? type.scope().name() : null);
        statement.setString(9, JsonUtils.toJson(type.treeConfig()));
    }
}
