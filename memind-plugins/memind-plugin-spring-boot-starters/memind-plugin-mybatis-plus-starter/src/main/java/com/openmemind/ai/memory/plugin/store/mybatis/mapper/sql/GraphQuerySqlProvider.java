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

public final class GraphQuerySqlProvider {

    public String selectLocalSubgraphLinks(Map<String, Object> params) {
        return """
        <script>
        SELECT *
        FROM memory_item_link
        WHERE deleted = 0
          AND memory_id = #{memoryId}
          <if test="linkTypes != null and !linkTypes.isEmpty()">
            AND link_type IN
            <foreach collection="linkTypes" item="type" open="(" separator="," close=")">
              #{type}
            </foreach>
          </if>
          AND source_item_id IN
          <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">
            #{itemId}
          </foreach>
          AND target_item_id IN
          <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">
            #{itemId}
          </foreach>
        ORDER BY source_item_id ASC, target_item_id ASC, link_type ASC
        </script>
        """;
    }

    public String selectAdjacentLinks(Map<String, Object> params) {
        return """
        <script>
        SELECT *
        FROM memory_item_link
        WHERE deleted = 0
          AND memory_id = #{memoryId}
          <if test="linkTypes != null and !linkTypes.isEmpty()">
            AND link_type IN
            <foreach collection="linkTypes" item="type" open="(" separator="," close=")">
              #{type}
            </foreach>
          </if>
          AND (
            source_item_id IN
            <foreach collection="seedItemIds" item="itemId" open="(" separator="," close=")">
              #{itemId}
            </foreach>
            OR
            target_item_id IN
            <foreach collection="seedItemIds" item="itemId" open="(" separator="," close=")">
              #{itemId}
            </foreach>
          )
        ORDER BY source_item_id ASC, target_item_id ASC, link_type ASC
        </script>
        """;
    }

    public String selectMentionsByItemIds(Map<String, Object> params) {
        return """
        <script>
        SELECT *
        FROM memory_item_entity_mention
        WHERE deleted = 0
          AND memory_id = #{memoryId}
          AND item_id IN
          <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">
            #{itemId}
          </foreach>
        ORDER BY item_id ASC, entity_key ASC, created_at ASC
        </script>
        """;
    }

    public String selectMentionsByEntityKeysLimited(Map<String, Object> params) {
        DatabaseDialect dialect = (DatabaseDialect) params.get("dialect");
        return switch (dialect) {
            case MYSQL -> selectMentionsByEntityKeysLimitedUnionAll();
            case SQLITE, POSTGRESQL -> selectMentionsByEntityKeysLimitedWindowed();
        };
    }

    public String deleteCooccurrencesByEntityKeys(Map<String, Object> params) {
        return """
        <script>
        DELETE FROM memory_entity_cooccurrence
        WHERE memory_id = #{memoryId}
          AND (
            left_entity_key IN
            <foreach collection="entityKeys" item="entityKey" open="(" separator="," close=")">
              #{entityKey}
            </foreach>
            OR
            right_entity_key IN
            <foreach collection="entityKeys" item="entityKey" open="(" separator="," close=")">
              #{entityKey}
            </foreach>
          )
        </script>
        """;
    }

    private String selectMentionsByEntityKeysLimitedWindowed() {
        return """
        <script>
        SELECT *
        FROM (
            SELECT *,
                   ROW_NUMBER() OVER (
                       PARTITION BY entity_key
                       ORDER BY item_id ASC, created_at ASC
                   ) AS rn
            FROM memory_item_entity_mention
            WHERE deleted = 0
              AND memory_id = #{memoryId}
              AND entity_key IN
              <foreach collection="entityKeys" item="entityKey" open="(" separator="," close=")">
                #{entityKey}
              </foreach>
        ) bounded
        WHERE bounded.rn <![CDATA[<=]]> #{perEntityLimitPlusOne}
        ORDER BY bounded.entity_key ASC, bounded.item_id ASC
        </script>
        """;
    }

    private String selectMentionsByEntityKeysLimitedUnionAll() {
        return """
        <script>
        <foreach collection="entityKeys" item="entityKey" index="index" separator=" UNION ALL ">
            SELECT * FROM (
                SELECT *
                FROM memory_item_entity_mention
                WHERE deleted = 0
                  AND memory_id = #{memoryId}
                  AND entity_key = #{entityKey}
                ORDER BY item_id ASC, created_at ASC
                LIMIT #{perEntityLimitPlusOne}
            ) bounded_${index}
        </foreach>
        ORDER BY entity_key ASC, item_id ASC
        </script>
        """;
    }
}
