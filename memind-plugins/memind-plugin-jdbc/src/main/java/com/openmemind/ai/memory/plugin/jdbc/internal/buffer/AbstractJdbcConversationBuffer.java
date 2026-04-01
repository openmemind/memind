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
package com.openmemind.ai.memory.plugin.jdbc.internal.buffer;

import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.List;
import java.util.Objects;

public abstract class AbstractJdbcConversationBuffer implements PendingConversationBuffer {

    private final AbstractJdbcConversationBufferAccessor accessor;

    protected AbstractJdbcConversationBuffer(AbstractJdbcConversationBufferAccessor accessor) {
        this.accessor = Objects.requireNonNull(accessor, "accessor");
    }

    @Override
    public void append(String sessionId, Message message) {
        accessor.append(
                Objects.requireNonNull(sessionId, "sessionId"),
                Objects.requireNonNull(message, "message"));
    }

    @Override
    public List<Message> load(String sessionId) {
        return accessor.selectPending(Objects.requireNonNull(sessionId, "sessionId")).stream()
                .map(ConversationBufferRow::message)
                .toList();
    }

    @Override
    public void clear(String sessionId) {
        List<Long> ids =
                accessor.selectPending(Objects.requireNonNull(sessionId, "sessionId")).stream()
                        .map(ConversationBufferRow::id)
                        .toList();
        if (!ids.isEmpty()) {
            accessor.markExtractedByIds(ids);
        }
    }

    @Override
    public List<Message> drain(String sessionId) {
        List<ConversationBufferRow> rows =
                accessor.selectPending(Objects.requireNonNull(sessionId, "sessionId"));
        List<Long> ids = rows.stream().map(ConversationBufferRow::id).toList();
        if (!ids.isEmpty()) {
            accessor.markExtractedByIds(ids);
        }
        return rows.stream().map(ConversationBufferRow::message).toList();
    }
}
