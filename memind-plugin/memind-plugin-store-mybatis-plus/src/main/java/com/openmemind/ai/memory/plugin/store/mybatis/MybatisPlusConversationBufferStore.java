package com.openmemind.ai.memory.plugin.store.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.streaming.ConversationBufferStore;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.ConversationBufferConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.ConversationBufferMapper;
import java.util.List;

/**
 * MyBatis Plus based implementation of session buffer persistence (single message storage)
 *
 */
public class MybatisPlusConversationBufferStore implements ConversationBufferStore {

    private final ConversationBufferMapper mapper;
    private final MemoryId memoryId;

    public MybatisPlusConversationBufferStore(ConversationBufferMapper mapper, MemoryId memoryId) {
        this.mapper = mapper;
        this.memoryId = memoryId;
    }

    @Override
    public void save(String sessionId, List<Message> buffer) {
        long existingCount = mapper.selectCount(sessionQuery(sessionId));

        List<Message> newMessages = buffer.subList((int) existingCount, buffer.size());
        if (newMessages.isEmpty()) {
            return;
        }

        List<ConversationBufferDO> rows =
                ConversationBufferConverter.toDOList(memoryId, sessionId, newMessages);
        rows.forEach(mapper::insert);
    }

    @Override
    public List<Message> load(String sessionId) {
        List<ConversationBufferDO> rows =
                mapper.selectList(sessionQuery(sessionId).orderByAsc("id"));
        return ConversationBufferConverter.toMessages(rows);
    }

    @Override
    public void clear(String sessionId) {
        mapper.delete(new QueryWrapper<ConversationBufferDO>().eq("session_id", sessionId));
    }

    @Override
    public int loadMessageCount(String sessionId) {
        return mapper.selectCount(sessionQuery(sessionId)).intValue();
    }

    @Override
    public List<String> listActiveSessions(MemoryId memoryId) {
        return mapper.selectList(memoryQuery(memoryId)).stream()
                .map(ConversationBufferDO::getSessionId)
                .distinct()
                .toList();
    }

    private QueryWrapper<ConversationBufferDO> sessionQuery(String sessionId) {
        return new QueryWrapper<ConversationBufferDO>().eq("session_id", sessionId);
    }

    private QueryWrapper<ConversationBufferDO> memoryQuery(MemoryId id) {
        return new QueryWrapper<ConversationBufferDO>()
                .eq("user_id", id.getAttribute("userId"))
                .eq("agent_id", id.getAttribute("agentId"));
    }
}
