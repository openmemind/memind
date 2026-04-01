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

import com.openmemind.ai.memory.core.buffer.RecentConversationBuffer;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.ConversationBufferConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.ConversationBufferMapper;
import java.util.List;
import java.util.Objects;

public class MybatisPlusRecentConversationBuffer implements RecentConversationBuffer {

    private final ConversationBufferMapper mapper;

    public MybatisPlusRecentConversationBuffer(ConversationBufferMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void append(String sessionId, Message message) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(message, "message");
    }

    @Override
    public List<Message> loadRecent(String sessionId, int limit) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        var rows = mapper.selectRecentRowsBySessionId(sessionId, limit);
        java.util.Collections.reverse(rows);
        return ConversationBufferConverter.toMessagesFromRows(rows);
    }

    @Override
    public void clear(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
    }
}
