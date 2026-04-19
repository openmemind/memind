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
package com.openmemind.ai.memory.plugin.store.mybatis.mapper.sql;

import com.openmemind.ai.memory.plugin.store.mybatis.schema.DatabaseDialect;
import java.util.Map;

public final class MemoryItemQuerySqlProvider {

    public String selectTemporalOverlapCandidates(Map<String, Object> params) {
        DatabaseDialect dialect = (DatabaseDialect) params.get("dialect");
        return """
        <script>
        SELECT *
        FROM memory_item
        WHERE deleted = 0
          AND memory_id = #{memoryId}
          AND type = #{itemType}
          %s
          <if test="excludeItemIds != null and !excludeItemIds.isEmpty()">
            AND biz_id NOT IN
            <foreach collection="excludeItemIds" item="itemId" open="(" separator="," close=")">
              #{itemId}
            </foreach>
          </if>
          AND temporal_start IS NOT NULL
          AND temporal_end_or_anchor IS NOT NULL
          AND temporal_anchor IS NOT NULL
          AND temporal_start <![CDATA[<]]> #{sourceEndOrAnchor}
          AND #{sourceStart} <![CDATA[<]]> temporal_end_or_anchor
        ORDER BY %s ASC, biz_id ASC
        LIMIT #{limit}
        </script>
        """
                .formatted(categoryPredicate(), anchorDistanceExpression(dialect));
    }

    public String selectTemporalBeforeCandidates(Map<String, Object> params) {
        DatabaseDialect dialect = (DatabaseDialect) params.get("dialect");
        return """
        <script>
        SELECT *
        FROM memory_item
        WHERE deleted = 0
          AND memory_id = #{memoryId}
          AND type = #{itemType}
          %s
          <if test="excludeItemIds != null and !excludeItemIds.isEmpty()">
            AND biz_id NOT IN
            <foreach collection="excludeItemIds" item="itemId" open="(" separator="," close=")">
              #{itemId}
            </foreach>
          </if>
          AND temporal_anchor IS NOT NULL
          AND temporal_anchor <![CDATA[<]]> #{sourceAnchor}
        ORDER BY %s ASC, biz_id ASC
        LIMIT #{limit}
        </script>
        """
                .formatted(categoryPredicate(), anchorDistanceExpression(dialect));
    }

    public String selectTemporalAfterCandidates(Map<String, Object> params) {
        DatabaseDialect dialect = (DatabaseDialect) params.get("dialect");
        return """
        <script>
        SELECT *
        FROM memory_item
        WHERE deleted = 0
          AND memory_id = #{memoryId}
          AND type = #{itemType}
          %s
          <if test="excludeItemIds != null and !excludeItemIds.isEmpty()">
            AND biz_id NOT IN
            <foreach collection="excludeItemIds" item="itemId" open="(" separator="," close=")">
              #{itemId}
            </foreach>
          </if>
          AND temporal_anchor IS NOT NULL
          AND temporal_anchor <![CDATA[>]]> #{sourceAnchor}
        ORDER BY %s ASC, biz_id ASC
        LIMIT #{limit}
        </script>
        """
                .formatted(categoryPredicate(), anchorDistanceExpression(dialect));
    }

    private static String categoryPredicate() {
        return """
          AND (
                (#{category} IS NULL AND category IS NULL)
                OR category = #{category}
              )
        """;
    }

    private static String anchorDistanceExpression(DatabaseDialect dialect) {
        DatabaseDialect effectiveDialect = dialect != null ? dialect : DatabaseDialect.SQLITE;
        return switch (effectiveDialect) {
            case SQLITE -> "ABS(unixepoch(temporal_anchor) - unixepoch(#{sourceAnchor}))";
            case MYSQL -> "ABS(TIMESTAMPDIFF(MICROSECOND, temporal_anchor, #{sourceAnchor}))";
            case POSTGRESQL ->
                    "ABS(EXTRACT(EPOCH FROM (temporal_anchor - CAST(#{sourceAnchor} AS"
                            + " TIMESTAMPTZ))))";
        };
    }
}
