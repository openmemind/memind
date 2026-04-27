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
package com.openmemind.ai.memory.plugin.jdbc.mysql;

import com.openmemind.ai.memory.plugin.jdbc.internal.insight.AbstractJdbcBubbleTrackerStore;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import javax.sql.DataSource;

public final class MysqlBubbleTrackerStore extends AbstractJdbcBubbleTrackerStore {

    public MysqlBubbleTrackerStore(DataSource dataSource) {
        this(dataSource, true);
    }

    public MysqlBubbleTrackerStore(DataSource dataSource, boolean createIfNotExist) {
        super(dataSource);
        StoreSchemaBootstrap.ensureMysql(dataSource, createIfNotExist);
    }

    @Override
    protected String upsertIncrementSql() {
        return """
        INSERT INTO memory_insight_bubble_state (memory_id, tier, insight_type, dirty_count)
        VALUES (?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE dirty_count = dirty_count + VALUES(dirty_count),
                                updated_at = CURRENT_TIMESTAMP
        """;
    }
}
