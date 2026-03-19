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
package com.openmemind.ai.memory.plugin.store.mybatis.converter;

import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferDO;
import java.util.List;

/**
 * ConversationBuffer Converter (Single Message)
 *
 */
public final class ConversationBufferConverter {

    private ConversationBufferConverter() {}

    /**
     * Convert a single message to DO
     */
    public static ConversationBufferDO toDO(MemoryId memoryId, String sessionId, Message message) {
        ConversationBufferDO dataObject = new ConversationBufferDO();
        dataObject.setSessionId(sessionId);
        dataObject.setUserId(memoryId.getAttribute("userId"));
        dataObject.setAgentId(memoryId.getAttribute("agentId"));
        dataObject.setMemoryId(memoryId.toIdentifier());
        dataObject.setRole(message.role().name());
        dataObject.setContent(message.textContent());
        dataObject.setUserName(message.userName());
        dataObject.setTimestamp(message.timestamp());
        return dataObject;
    }

    /**
     * Batch convert message list to DO list
     */
    public static List<ConversationBufferDO> toDOList(
            MemoryId memoryId, String sessionId, List<Message> messages) {
        return messages.stream().map(msg -> toDO(memoryId, sessionId, msg)).toList();
    }

    /**
     * Convert DO to Message
     */
    public static Message toMessage(ConversationBufferDO dataObject) {
        Message.Role role = Message.Role.valueOf(dataObject.getRole());
        String userName = dataObject.getUserName();
        if (userName != null) {
            return Message.user(dataObject.getContent(), dataObject.getTimestamp(), userName);
        }
        return role == Message.Role.USER
                ? Message.user(dataObject.getContent(), dataObject.getTimestamp())
                : Message.assistant(dataObject.getContent(), dataObject.getTimestamp());
    }

    /**
     * Convert DO list to Message list
     */
    public static List<Message> toMessages(List<ConversationBufferDO> dataObjects) {
        return dataObjects.stream().map(ConversationBufferConverter::toMessage).toList();
    }
}
