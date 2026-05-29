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

import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.store.mybatis.buffer.ConversationBufferAtomicOperations;
import com.openmemind.ai.memory.plugin.store.mybatis.buffer.DialectConversationBufferAtomicOperations;
import com.openmemind.ai.memory.plugin.store.mybatis.converter.ConversationBufferConverter;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferRow;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.ConversationBufferMapper;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

public class MybatisPlusConversationBuffer implements PendingConversationBuffer {

    private final ConversationBufferMapper mapper;
    private final ConversationBufferAtomicOperations atomicOperations;

    /**
     * Compatibility constructor. This fallback preserves the historical mapper-only behavior but
     * cannot guarantee atomic drain semantics across independent application instances.
     */
    @Deprecated(since = "0.2.0", forRemoval = false)
    public MybatisPlusConversationBuffer(ConversationBufferMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.atomicOperations = null;
    }

    public MybatisPlusConversationBuffer(ConversationBufferMapper mapper, DataSource dataSource) {
        this(mapper, new DialectConversationBufferAtomicOperations(dataSource));
    }

    public MybatisPlusConversationBuffer(
            ConversationBufferMapper mapper, ConversationBufferAtomicOperations atomicOperations) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.atomicOperations = Objects.requireNonNull(atomicOperations, "atomicOperations");
    }

    @Override
    public void append(String sessionId, Message message) {
        mapper.insert(
                ConversationBufferConverter.toDO(
                        Objects.requireNonNull(sessionId, "sessionId"),
                        Objects.requireNonNull(message, "message")));
    }

    @Override
    public List<Message> load(String sessionId) {
        return ConversationBufferConverter.toMessagesFromRows(
                mapper.selectPendingRowsBySessionId(
                        Objects.requireNonNull(sessionId, "sessionId")));
    }

    @Override
    public void clear(String sessionId) {
        if (atomicOperations != null) {
            atomicOperations.clearPending(Objects.requireNonNull(sessionId, "sessionId"));
            return;
        }
        List<Long> ids =
                mapper
                        .selectPendingRowsBySessionId(
                                Objects.requireNonNull(sessionId, "sessionId"))
                        .stream()
                        .map(ConversationBufferRow::getId)
                        .toList();
        if (!ids.isEmpty()) {
            mapper.markExtractedByIds(ids);
        }
    }

    @Override
    public List<Message> drain(String sessionId) {
        if (atomicOperations != null) {
            return ConversationBufferConverter.toMessagesFromRows(
                    atomicOperations.drainPending(Objects.requireNonNull(sessionId, "sessionId")));
        }
        var rows =
                mapper.selectPendingRowsBySessionId(Objects.requireNonNull(sessionId, "sessionId"));
        List<Long> ids = rows.stream().map(ConversationBufferRow::getId).toList();
        if (!ids.isEmpty()) {
            mapper.markExtractedByIds(ids);
        }
        return ConversationBufferConverter.toMessagesFromRows(rows);
    }
}
