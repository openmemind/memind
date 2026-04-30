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
package com.openmemind.ai.memory.plugin.jdbc.postgresql;

import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.plugin.jdbc.internal.jdbi.JdbiFactory;
import com.openmemind.ai.memory.plugin.jdbc.internal.schema.StoreSchemaBootstrap;
import java.util.Objects;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlStatement;

public final class PostgresqlBubbleTrackerStore implements BubbleTrackerStore {

    private static final String UPSERT_INCREMENT_SQL =
            """
            INSERT INTO memory_insight_bubble_state (memory_id, tier, insight_type, dirty_count)
            VALUES (:memoryId, :tier, :insightType, :delta)
            ON CONFLICT(memory_id, tier, insight_type)
            DO UPDATE SET dirty_count = memory_insight_bubble_state.dirty_count + excluded.dirty_count,
                          updated_at = CURRENT_TIMESTAMP
            """;

    private static final String SELECT_DIRTY_COUNT_SQL =
            """
            SELECT dirty_count
            FROM memory_insight_bubble_state
            WHERE memory_id = :memoryId
              AND tier = :tier
              AND insight_type = :insightType
            """;

    private static final String RESET_SQL =
            """
            UPDATE memory_insight_bubble_state
            SET dirty_count = 0, updated_at = CURRENT_TIMESTAMP
            WHERE memory_id = :memoryId
              AND tier = :tier
              AND insight_type = :insightType
            """;

    private final Jdbi jdbi;

    public PostgresqlBubbleTrackerStore(DataSource dataSource) {
        this(dataSource, true);
    }

    public PostgresqlBubbleTrackerStore(DataSource dataSource, boolean createIfNotExist) {
        DataSource checkedDataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbi = JdbiFactory.create(checkedDataSource);
        StoreSchemaBootstrap.ensurePostgresql(checkedDataSource, createIfNotExist);
    }

    @Override
    public int incrementAndGet(String key, int delta) {
        BubbleStateKey parsed = BubbleStateKey.parse(key);
        return jdbi.inTransaction(
                handle -> {
                    bindKey(handle.createUpdate(UPSERT_INCREMENT_SQL), parsed)
                            .bind("delta", delta)
                            .execute();
                    return bindKey(handle.createQuery(SELECT_DIRTY_COUNT_SQL), parsed)
                            .mapTo(Integer.class)
                            .findOne()
                            .orElse(0);
                });
    }

    @Override
    public int getDirtyCount(String key) {
        BubbleStateKey parsed = BubbleStateKey.parse(key);
        return jdbi.withHandle(
                handle ->
                        bindKey(handle.createQuery(SELECT_DIRTY_COUNT_SQL), parsed)
                                .mapTo(Integer.class)
                                .findOne()
                                .orElse(0));
    }

    @Override
    public void reset(String key) {
        BubbleStateKey parsed = BubbleStateKey.parse(key);
        jdbi.useHandle(handle -> bindKey(handle.createUpdate(RESET_SQL), parsed).execute());
    }

    private static <StatementT extends SqlStatement<StatementT>> StatementT bindKey(
            StatementT statement, BubbleStateKey key) {
        return statement
                .bind("memoryId", key.memoryId())
                .bind("tier", key.tier())
                .bind("insightType", key.insightType());
    }

    private record BubbleStateKey(String memoryId, String tier, String insightType) {

        private static BubbleStateKey parse(String key) {
            String[] parts = Objects.requireNonNull(key, "key").split("::");
            if (parts.length == 2) {
                return new BubbleStateKey(parts[0], "BRANCH", parts[1]);
            }
            if (parts.length == 3 && "root".equals(parts[1])) {
                return new BubbleStateKey(parts[0], "ROOT", parts[2]);
            }
            throw new IllegalArgumentException("Unsupported bubble tracker key: " + key);
        }
    }
}
