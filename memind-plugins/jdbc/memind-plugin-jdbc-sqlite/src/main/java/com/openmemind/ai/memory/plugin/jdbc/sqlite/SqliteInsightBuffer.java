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
package com.openmemind.ai.memory.plugin.jdbc.sqlite;

import com.openmemind.ai.memory.plugin.jdbc.internal.buffer.AbstractJdbcInsightBuffer;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import javax.sql.DataSource;

public class SqliteInsightBuffer extends AbstractJdbcInsightBuffer {

    public SqliteInsightBuffer(DataSource dataSource) {
        this(dataSource, true);
    }

    public SqliteInsightBuffer(DataSource dataSource, boolean createIfNotExist) {
        super(dataSource);
        StoreSchemaBootstrap.ensureSqlite(dataSource, createIfNotExist);
    }

    @Override
    protected String appendSql() {
        return """
        INSERT OR IGNORE INTO memory_insight_buffer
            (user_id, agent_id, memory_id, insight_type_name, item_id, built, deleted)
        VALUES (?, ?, ?, ?, ?, 0, 0)
        """;
    }

    @Override
    protected String falseLiteral() {
        return "0";
    }

    @Override
    protected String trueLiteral() {
        return "1";
    }
}
