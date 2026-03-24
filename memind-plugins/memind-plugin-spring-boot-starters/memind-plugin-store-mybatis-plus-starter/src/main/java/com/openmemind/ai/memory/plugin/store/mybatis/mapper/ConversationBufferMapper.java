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
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationBufferMapper extends BaseMapper<ConversationBufferDO> {

    @Select(
            """
            SELECT COUNT(*)
            FROM memory_conversation_buffer
            WHERE session_id = #{sessionId}
            """)
    long countAllBySessionId(@Param("sessionId") String sessionId);

    @Select(
            """
            SELECT role, content, user_name AS userName, timestamp
            FROM memory_conversation_buffer
            WHERE session_id = #{sessionId} AND deleted = FALSE
            ORDER BY id ASC
            """)
    List<ConversationBufferRow> selectActiveRowsBySessionId(@Param("sessionId") String sessionId);

    @Select(
            """
            SELECT DISTINCT session_id
            FROM memory_conversation_buffer
            WHERE user_id = #{userId} AND agent_id = #{agentId} AND deleted = FALSE
            ORDER BY session_id ASC
            """)
    List<String> selectActiveSessionIds(
            @Param("userId") String userId, @Param("agentId") String agentId);
}
