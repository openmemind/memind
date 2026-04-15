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
package com.openmemind.ai.memory.plugin.store.mybatis;

import com.openmemind.ai.memory.core.extraction.insight.tree.BubbleTrackerStore;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightBubbleStateDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightBubbleStateMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialect;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class MybatisPlusBubbleTrackerStore implements BubbleTrackerStore {

    private final MemoryInsightBubbleStateMapper mapper;
    private final DatabaseDialect dialect;

    public MybatisPlusBubbleTrackerStore(
            MemoryInsightBubbleStateMapper mapper, DatabaseDialect dialect) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    @Override
    @Transactional
    public int incrementAndGet(String key, int delta) {
        BubbleStateKey parsed = BubbleStateKey.parse(key);
        switch (dialect) {
            case SQLITE ->
                    mapper.upsertIncrementSqlite(
                            parsed.memoryId(), parsed.tier(), parsed.insightType(), delta);
            case MYSQL ->
                    mapper.upsertIncrementMysql(
                            parsed.memoryId(), parsed.tier(), parsed.insightType(), delta);
            case POSTGRESQL ->
                    mapper.upsertIncrementPostgresql(
                            parsed.memoryId(), parsed.tier(), parsed.insightType(), delta);
        }
        MemoryInsightBubbleStateDO state =
                mapper.selectByUniqueKey(parsed.memoryId(), parsed.tier(), parsed.insightType());
        return state == null || state.getDirtyCount() == null ? 0 : state.getDirtyCount();
    }

    @Override
    public int getDirtyCount(String key) {
        BubbleStateKey parsed = BubbleStateKey.parse(key);
        MemoryInsightBubbleStateDO state =
                mapper.selectByUniqueKey(parsed.memoryId(), parsed.tier(), parsed.insightType());
        return state == null || state.getDirtyCount() == null ? 0 : state.getDirtyCount();
    }

    @Override
    @Transactional
    public void reset(String key) {
        BubbleStateKey parsed = BubbleStateKey.parse(key);
        mapper.resetDirtyCount(parsed.memoryId(), parsed.tier(), parsed.insightType());
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
