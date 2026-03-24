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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.store.buffer.ConversationBuffer;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.ConversationBufferConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.ConversationBufferMapper;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * MyBatis Plus based implementation of session buffer persistence (single message storage)
 *
 */
public class MybatisPlusConversationBuffer implements ConversationBuffer {

    private final ConversationBufferMapper mapper;

    public MybatisPlusConversationBuffer(ConversationBufferMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(String sessionId, List<Message> buffer) {
        if (buffer == null || buffer.isEmpty()) {
            clear(sessionId);
            return;
        }

        long existingCount = mapper.selectCount(sessionQuery(sessionId));

        List<Message> newMessages = buffer.subList((int) existingCount, buffer.size());
        if (newMessages.isEmpty()) {
            return;
        }

        List<ConversationBufferDO> rows =
                ConversationBufferConverter.toDOList(sessionId, newMessages);
        rows.forEach(mapper::insert);
    }

    @Override
    public List<Message> load(String sessionId) {
        return ConversationBufferConverter.toMessagesFromRows(
                mapper.selectActiveRowsBySessionId(sessionId));
    }

    @Override
    public void clear(String sessionId) {
        mapper.delete(new QueryWrapper<ConversationBufferDO>().eq("session_id", sessionId));
    }

    @Transactional
    @Override
    public List<Message> drain(String sessionId) {
        List<Message> messages = load(sessionId);
        clear(sessionId);
        return messages;
    }

    @Override
    public int loadMessageCount(String sessionId) {
        return Math.toIntExact(mapper.countAllBySessionId(sessionId));
    }

    @Override
    public List<String> listActiveSessions(MemoryId memoryId) {
        MemoryScope scope = MemoryScope.from(memoryId);
        return mapper.selectActiveSessionIds(scope.userId(), scope.agentId());
    }

    private QueryWrapper<ConversationBufferDO> sessionQuery(String sessionId) {
        return new QueryWrapper<ConversationBufferDO>().eq("session_id", sessionId);
    }

    private record MemoryScope(String userId, String agentId) {

        private static MemoryScope from(MemoryId memoryId) {
            return new MemoryScope(
                    memoryId.getAttribute("userId"),
                    memoryId.getAttribute("agentId") == null
                            ? ""
                            : memoryId.getAttribute("agentId"));
        }
    }
}
