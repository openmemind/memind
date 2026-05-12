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

    public String selectTemporalItemLookupMatches(Map<String, Object> params) {
        DatabaseDialect dialect = (DatabaseDialect) params.get("dialect");
        return """
        <script>
        SELECT *
        FROM (
            SELECT base_items.*,
                   CASE
                       WHEN occurred_end IS NOT NULL
                            AND %s
                       THEN occurred_end
                       ELSE %s
                   END AS semantic_end,
                   COALESCE(occurred_at, semantic_start) AS semantic_anchor
            FROM (
                SELECT memory_item.*,
                       COALESCE(occurred_start, occurred_at) AS semantic_start
                FROM memory_item
                WHERE %s
                  AND memory_id = #{memoryId}
                  AND biz_id IS NOT NULL
                  <if test="scope != null">
                    AND scope = #{scope}
                  </if>
                  <if test="categories != null and !categories.isEmpty()">
                    AND category IN
                    <foreach collection="categories" item="category" open="(" separator="," close=")">
                      #{category}
                    </foreach>
                  </if>
                  <if test="itemTypes != null and !itemTypes.isEmpty()">
                    AND type IN
                    <foreach collection="itemTypes" item="itemTypeName" open="(" separator="," close=")">
                      #{itemTypeName}
                    </foreach>
                  </if>
                  <if test="excludeItemIds != null and !excludeItemIds.isEmpty()">
                    AND biz_id NOT IN
                    <foreach collection="excludeItemIds" item="itemId" open="(" separator="," close=")">
                      #{itemId}
                    </foreach>
                  </if>
            ) base_items
            WHERE semantic_start IS NOT NULL
        ) semantic_items
        WHERE %s
        ORDER BY %s ASC, biz_id ASC
        LIMIT #{limit}
        </script>
        """
                .formatted(
                        semanticEndValidityExpression(dialect),
                        semanticPointEndExpression(dialect),
                        deletedPredicate(dialect),
                        temporalItemOverlapPredicate(dialect),
                        semanticAnchorDistanceExpression(dialect));
    }

    public String selectTemporalOverlapCandidates(Map<String, Object> params) {
        DatabaseDialect dialect = (DatabaseDialect) params.get("dialect");
        return """
        <script>
        SELECT *
        FROM memory_item
        WHERE %s
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
          AND %s
        ORDER BY %s ASC, biz_id ASC
        LIMIT #{limit}
        </script>
        """
                .formatted(
                        deletedPredicate(dialect),
                        categoryPredicate(),
                        temporalCandidateOverlapPredicate(dialect),
                        anchorDistanceExpression(dialect));
    }

    public String selectTemporalBeforeCandidates(Map<String, Object> params) {
        DatabaseDialect dialect = (DatabaseDialect) params.get("dialect");
        return """
        <script>
        SELECT *
        FROM memory_item
        WHERE %s
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
                .formatted(
                        deletedPredicate(dialect),
                        categoryPredicate(),
                        anchorDistanceExpression(dialect));
    }

    public String selectTemporalAfterCandidates(Map<String, Object> params) {
        DatabaseDialect dialect = (DatabaseDialect) params.get("dialect");
        return """
        <script>
        SELECT *
        FROM memory_item
        WHERE %s
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
                .formatted(
                        deletedPredicate(dialect),
                        categoryPredicate(),
                        anchorDistanceExpression(dialect));
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

    private static String deletedPredicate(DatabaseDialect dialect) {
        return effectiveDialect(dialect) == DatabaseDialect.POSTGRESQL
                ? "deleted = FALSE"
                : "deleted = 0";
    }

    private static String semanticEndValidityExpression(DatabaseDialect dialect) {
        return switch (effectiveDialect(dialect)) {
            case SQLITE -> "julianday(semantic_start) <![CDATA[<]]> julianday(occurred_end)";
            case MYSQL, POSTGRESQL -> "semantic_start <![CDATA[<]]> occurred_end";
        };
    }

    private static String semanticPointEndExpression(DatabaseDialect dialect) {
        return switch (effectiveDialect(dialect)) {
            case SQLITE -> "strftime('%Y-%m-%dT%H:%M:%fZ', semantic_start, '+0.001 seconds')";
            case MYSQL -> "DATE_ADD(semantic_start, INTERVAL 1000 MICROSECOND)";
            case POSTGRESQL -> "semantic_start + INTERVAL '1 millisecond'";
        };
    }

    private static String temporalItemOverlapPredicate(DatabaseDialect dialect) {
        return switch (effectiveDialect(dialect)) {
            case SQLITE ->
                    """
                    julianday(semantic_start) <![CDATA[<]]> julianday(#{endExclusive})
                      AND julianday(#{startInclusive}) <![CDATA[<]]> julianday(semantic_end)
                    """;
            case MYSQL, POSTGRESQL ->
                    """
                    semantic_start <![CDATA[<]]> #{endExclusive}
                      AND #{startInclusive} <![CDATA[<]]> semantic_end
                    """;
        };
    }

    private static String temporalCandidateEffectiveEndExpression(DatabaseDialect dialect) {
        return switch (effectiveDialect(dialect)) {
            case SQLITE ->
                    """
                    CASE
                        WHEN julianday(temporal_start) <![CDATA[<]]> julianday(temporal_end_or_anchor)
                        THEN temporal_end_or_anchor
                        ELSE strftime('%Y-%m-%dT%H:%M:%fZ', temporal_start, '+0.001 seconds')
                    END
                    """;
            case MYSQL ->
                    """
                    CASE
                        WHEN temporal_start <![CDATA[<]]> temporal_end_or_anchor
                        THEN temporal_end_or_anchor
                        ELSE DATE_ADD(temporal_start, INTERVAL 1000 MICROSECOND)
                    END
                    """;
            case POSTGRESQL ->
                    """
                    CASE
                        WHEN temporal_start <![CDATA[<]]> temporal_end_or_anchor
                        THEN temporal_end_or_anchor
                        ELSE temporal_start + INTERVAL '1 millisecond'
                    END
                    """;
        };
    }

    private static String temporalCandidateOverlapPredicate(DatabaseDialect dialect) {
        return switch (effectiveDialect(dialect)) {
            case SQLITE ->
                    """
                    julianday(temporal_start) <![CDATA[<]]> julianday(#{sourceEndOrAnchor})
                      AND julianday(#{sourceStart}) <![CDATA[<]]> julianday(%s)
                    """
                            .formatted(temporalCandidateEffectiveEndExpression(dialect));
            case MYSQL, POSTGRESQL ->
                    """
                    temporal_start <![CDATA[<]]> #{sourceEndOrAnchor}
                      AND #{sourceStart} <![CDATA[<]]> %s
                    """
                            .formatted(temporalCandidateEffectiveEndExpression(dialect));
        };
    }

    private static String semanticAnchorDistanceExpression(DatabaseDialect dialect) {
        return switch (effectiveDialect(dialect)) {
            case SQLITE -> "ABS(julianday(semantic_anchor) - julianday(#{midpoint}))";
            case MYSQL -> "ABS(TIMESTAMPDIFF(MICROSECOND, semantic_anchor, #{midpoint}))";
            case POSTGRESQL ->
                    "ABS(EXTRACT(EPOCH FROM (semantic_anchor - CAST(#{midpoint} AS"
                            + " TIMESTAMPTZ))))";
        };
    }

    private static DatabaseDialect effectiveDialect(DatabaseDialect dialect) {
        return dialect != null ? dialect : DatabaseDialect.SQLITE;
    }
}
