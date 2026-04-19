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

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.sql.GraphQuerySqlProvider;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialect;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Graph query SQL provider")
class GraphQuerySqlProviderTest {

    @Test
    @DisplayName("graph query sql provider uses MySQL fallback without window functions")
    void mysqlReverseMentionSqlUsesUnionAllFallback() {
        var provider = new GraphQuerySqlProvider();

        String sql =
                provider.selectMentionsByEntityKeysLimited(
                        Map.of(
                                "memoryId",
                                DefaultMemoryId.of("user-1", "agent-1").toIdentifier(),
                                "entityKeys",
                                List.of("organization:openai", "person:sam_altman"),
                                "perEntityLimitPlusOne",
                                4,
                                "dialect",
                                DatabaseDialect.MYSQL));

        assertThat(sql).contains("UNION ALL");
        assertThat(sql).doesNotContain("ROW_NUMBER() OVER");
    }

    @Test
    @DisplayName("graph query sql provider uses windowed branch for SQLite and PostgreSQL")
    void windowCapableDialectsUseWindowedReverseMentionSql() {
        var provider = new GraphQuerySqlProvider();

        assertThat(
                        provider.selectMentionsByEntityKeysLimited(
                                Map.of(
                                        "memoryId",
                                        DefaultMemoryId.of("user-1", "agent-1").toIdentifier(),
                                        "entityKeys",
                                        List.of("organization:openai"),
                                        "perEntityLimitPlusOne",
                                        4,
                                        "dialect",
                                        DatabaseDialect.SQLITE)))
                .contains("ROW_NUMBER() OVER");
        assertThat(
                        provider.selectMentionsByEntityKeysLimited(
                                Map.of(
                                        "memoryId",
                                        DefaultMemoryId.of("user-1", "agent-1").toIdentifier(),
                                        "entityKeys",
                                        List.of("organization:openai"),
                                        "perEntityLimitPlusOne",
                                        4,
                                        "dialect",
                                        DatabaseDialect.POSTGRESQL)))
                .contains("ROW_NUMBER() OVER");
    }

    @Test
    @DisplayName("graph query sql provider renders bounded entity key lookup sql")
    void graphQuerySqlProviderShouldRenderBoundedEntityKeyLookupSql() {
        String sql =
                new GraphQuerySqlProvider()
                        .selectEntitiesByKeys(
                                Map.of(
                                        "memoryId",
                                        DefaultMemoryId.of("user-1", "agent-1").toIdentifier(),
                                        "entityKeys",
                                        List.of("organization:openai", "person:sam_altman")));

        assertThat(sql).contains("FROM memory_graph_entity");
        assertThat(sql).contains("memory_id = #{memoryId}");
        assertThat(sql).contains("entity_key IN");
    }
}
