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

import com.openmemind.ai.memory.plugin.store.mybatis.mapper.sql.MemoryItemQuerySqlProvider;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialect;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Memory item query SQL provider")
class MemoryItemQuerySqlProviderTest {

    @Test
    @DisplayName("temporal item lookup uses occurrence-derived semantic bounds")
    void temporalItemLookupUsesOccurrenceDerivedSemanticBounds() {
        var provider = new MemoryItemQuerySqlProvider();

        String sqliteSql =
                provider.selectTemporalItemLookupMatches(Map.of("dialect", DatabaseDialect.SQLITE));
        String mysqlSql =
                provider.selectTemporalItemLookupMatches(Map.of("dialect", DatabaseDialect.MYSQL));
        String postgresqlSql =
                provider.selectTemporalItemLookupMatches(
                        Map.of("dialect", DatabaseDialect.POSTGRESQL));

        assertThat(sqliteSql)
                .contains("COALESCE(occurred_start, occurred_at) AS semantic_start")
                .contains("strftime('%Y-%m-%dT%H:%M:%fZ'")
                .contains("julianday(semantic_start)")
                .contains("ORDER BY ABS(julianday(semantic_anchor)");
        assertThat(mysqlSql)
                .contains("DATE_ADD(semantic_start, INTERVAL 1000 MICROSECOND)")
                .contains("TIMESTAMPDIFF(MICROSECOND, semantic_anchor");
        assertThat(postgresqlSql)
                .contains("semantic_start + INTERVAL '1 millisecond'")
                .contains("EXTRACT(EPOCH FROM (semantic_anchor - CAST(");
        assertThat(sqliteSql).doesNotContain("temporal_start");
        assertThat(mysqlSql).doesNotContain("temporal_start");
        assertThat(postgresqlSql).doesNotContain("temporal_start");
        assertThat(sqliteSql).doesNotContain("observed_at");
        assertThat(mysqlSql).doesNotContain("observed_at");
        assertThat(postgresqlSql).doesNotContain("observed_at");
    }

    @Test
    @DisplayName("temporal overlap candidate lookup expands point windows by dialect")
    void temporalOverlapCandidateLookupExpandsPointWindowsByDialect() {
        var provider = new MemoryItemQuerySqlProvider();

        String sqliteSql =
                provider.selectTemporalOverlapCandidates(Map.of("dialect", DatabaseDialect.SQLITE));
        String mysqlSql =
                provider.selectTemporalOverlapCandidates(Map.of("dialect", DatabaseDialect.MYSQL));
        String postgresqlSql =
                provider.selectTemporalOverlapCandidates(
                        Map.of("dialect", DatabaseDialect.POSTGRESQL));

        assertThat(sqliteSql)
                .contains("julianday(temporal_start)")
                .contains("julianday(#{sourceEndOrAnchor})")
                .contains("strftime('%Y-%m-%dT%H:%M:%fZ', temporal_start, '+0.001 seconds')");
        assertThat(mysqlSql)
                .contains("temporal_start <![CDATA[<]]> #{sourceEndOrAnchor}")
                .contains("DATE_ADD(temporal_start, INTERVAL 1000 MICROSECOND)");
        assertThat(postgresqlSql)
                .contains("temporal_start <![CDATA[<]]> #{sourceEndOrAnchor}")
                .contains("temporal_start + INTERVAL '1 millisecond'");
        assertThat(sqliteSql).contains("WHERE deleted = 0");
        assertThat(mysqlSql).contains("WHERE deleted = 0");
        assertThat(postgresqlSql).contains("WHERE deleted = FALSE");
        assertThat(
                        provider.selectTemporalBeforeCandidates(
                                Map.of("dialect", DatabaseDialect.POSTGRESQL)))
                .contains("WHERE deleted = FALSE");
        assertThat(
                        provider.selectTemporalAfterCandidates(
                                Map.of("dialect", DatabaseDialect.POSTGRESQL)))
                .contains("WHERE deleted = FALSE");
        assertThat(sqliteSql).doesNotContain("#{sourceStart} <![CDATA[<]]> temporal_end_or_anchor");
        assertThat(mysqlSql).doesNotContain("#{sourceStart} <![CDATA[<]]> temporal_end_or_anchor");
        assertThat(postgresqlSql)
                .doesNotContain("#{sourceStart} <![CDATA[<]]> temporal_end_or_anchor");
    }
}
