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
