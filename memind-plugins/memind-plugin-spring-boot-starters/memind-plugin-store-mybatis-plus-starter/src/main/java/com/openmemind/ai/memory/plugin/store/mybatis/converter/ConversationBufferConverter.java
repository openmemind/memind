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

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferDO;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.ConversationBufferRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * ConversationBuffer Converter (Single Message)
 *
 */
public final class ConversationBufferConverter {

    private static final DateTimeFormatter SQLITE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ConversationBufferConverter() {}

    /**
     * Convert a single message to DO
     */
    public static ConversationBufferDO toDO(String sessionId, Message message) {
        SessionScope scope = SessionScope.from(sessionId);
        ConversationBufferDO dataObject = new ConversationBufferDO();
        dataObject.setSessionId(sessionId);
        dataObject.setUserId(scope.userId());
        dataObject.setAgentId(scope.agentId());
        dataObject.setMemoryId(scope.memoryId());
        dataObject.setRole(message.role().name());
        dataObject.setContent(message.textContent());
        dataObject.setUserName(message.userName());
        dataObject.setTimestamp(message.timestamp());
        return dataObject;
    }

    /**
     * Batch convert message list to DO list
     */
    public static List<ConversationBufferDO> toDOList(String sessionId, List<Message> messages) {
        return messages.stream().map(msg -> toDO(sessionId, msg)).toList();
    }

    /**
     * Convert DO to Message
     */
    public static Message toMessage(ConversationBufferDO dataObject) {
        return toMessage(
                dataObject.getRole(),
                dataObject.getContent(),
                dataObject.getUserName(),
                dataObject.getTimestamp());
    }

    /**
     * Convert DO list to Message list
     */
    public static List<Message> toMessages(List<ConversationBufferDO> dataObjects) {
        return dataObjects.stream().map(ConversationBufferConverter::toMessage).toList();
    }

    public static Message toMessage(ConversationBufferRow row) {
        return toMessage(row.getRole(), row.getContent(), row.getUserName(), row.getTimestamp());
    }

    public static List<Message> toMessagesFromRows(List<ConversationBufferRow> rows) {
        return rows.stream().map(ConversationBufferConverter::toMessage).toList();
    }

    private record SessionScope(String userId, String agentId, String memoryId) {

        private static SessionScope from(String sessionId) {
            int separator = sessionId.indexOf(':');
            if (separator < 0) {
                return new SessionScope(sessionId, "", sessionId);
            }
            return new SessionScope(
                    sessionId.substring(0, separator),
                    sessionId.substring(separator + 1),
                    sessionId);
        }
    }

    private static Message toMessage(
            String roleValue, String content, String userName, Object timestamp) {
        Message.Role role = Message.Role.valueOf(roleValue);
        Instant instant = parseTimestamp(timestamp);
        if (role == Message.Role.USER) {
            return userName == null
                    ? Message.user(content, instant)
                    : Message.user(content, instant, userName);
        }
        return Message.assistant(content, instant);
    }

    private static Instant parseTimestamp(Object timestamp) {
        if (timestamp == null) {
            return null;
        }
        if (timestamp instanceof Instant instant) {
            return instant;
        }
        if (timestamp instanceof Timestamp sqlTimestamp) {
            return sqlTimestamp.toInstant();
        }
        if (timestamp instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        if (timestamp instanceof String value) {
            if (value.isBlank()) {
                return null;
            }
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDateTime.parse(value, SQLITE_TIME_FORMATTER).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return Instant.ofEpochMilli(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
            }
        }
        throw new IllegalArgumentException("Unsupported timestamp value: " + timestamp);
    }
}
