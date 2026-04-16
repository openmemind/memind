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
package com.openmemind.ai.memory.plugin.store.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryInsightBubbleStateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MemoryInsightBubbleStateMapper extends BaseMapper<MemoryInsightBubbleStateDO> {

    @Insert(
            """
            INSERT INTO memory_insight_bubble_state (memory_id, tier, insight_type, dirty_count)
            VALUES (#{memoryId}, #{tier}, #{insightType}, #{delta})
            ON CONFLICT(memory_id, tier, insight_type)
            DO UPDATE SET dirty_count = memory_insight_bubble_state.dirty_count + excluded.dirty_count,
                          updated_at = CURRENT_TIMESTAMP
            """)
    int upsertIncrementSqlite(
            @Param("memoryId") String memoryId,
            @Param("tier") String tier,
            @Param("insightType") String insightType,
            @Param("delta") int delta);

    @Insert(
            """
            INSERT INTO memory_insight_bubble_state (memory_id, tier, insight_type, dirty_count)
            VALUES (#{memoryId}, #{tier}, #{insightType}, #{delta})
            ON DUPLICATE KEY UPDATE dirty_count = dirty_count + VALUES(dirty_count),
                                    updated_at = CURRENT_TIMESTAMP
            """)
    int upsertIncrementMysql(
            @Param("memoryId") String memoryId,
            @Param("tier") String tier,
            @Param("insightType") String insightType,
            @Param("delta") int delta);

    @Insert(
            """
            INSERT INTO memory_insight_bubble_state (memory_id, tier, insight_type, dirty_count)
            VALUES (#{memoryId}, #{tier}, #{insightType}, #{delta})
            ON CONFLICT(memory_id, tier, insight_type)
            DO UPDATE SET dirty_count = memory_insight_bubble_state.dirty_count + excluded.dirty_count,
                          updated_at = CURRENT_TIMESTAMP
            """)
    int upsertIncrementPostgresql(
            @Param("memoryId") String memoryId,
            @Param("tier") String tier,
            @Param("insightType") String insightType,
            @Param("delta") int delta);

    @Select(
            """
            SELECT id,
                   memory_id AS memoryId,
                   tier,
                   insight_type AS insightType,
                   dirty_count AS dirtyCount,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   deleted
            FROM memory_insight_bubble_state
            WHERE memory_id = #{memoryId}
              AND tier = #{tier}
              AND insight_type = #{insightType}
              AND deleted = FALSE
            """)
    MemoryInsightBubbleStateDO selectByUniqueKey(
            @Param("memoryId") String memoryId,
            @Param("tier") String tier,
            @Param("insightType") String insightType);

    @Update(
            """
            UPDATE memory_insight_bubble_state
            SET dirty_count = 0, updated_at = CURRENT_TIMESTAMP
            WHERE memory_id = #{memoryId}
              AND tier = #{tier}
              AND insight_type = #{insightType}
              AND deleted = FALSE
            """)
    int resetDirtyCount(
            @Param("memoryId") String memoryId,
            @Param("tier") String tier,
            @Param("insightType") String insightType);
}
