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
package com.openmemind.ai.memory.core.extraction.rawdata.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.utils.HashUtils;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Conversation content
 *
 * <p>Holds a structured list of messages for raw data of conversation type.
 *
 */
public class ConversationContent extends RawContent {

    public static final String TYPE = "CONVERSATION";

    @Override
    public String contentType() {
        return TYPE;
    }

    private final List<Message> messages;

    @JsonCreator
    public ConversationContent(@JsonProperty("messages") List<Message> messages) {
        this.messages = messages != null ? List.copyOf(messages) : List.of();
    }

    public List<Message> getMessages() {
        return messages;
    }

    public int getMessageCount() {
        return messages.size();
    }

    @Override
    public String toContentString() {
        return messages.stream()
                .map(ConversationContent::formatMessage)
                .collect(Collectors.joining("\n"));
    }

    public static String formatMessage(Message m) {
        String prefix =
                (m.userName() != null && !m.userName().isBlank())
                        ? m.userName()
                        : m.role().name().toLowerCase();

        if (m.timestamp() != null) {
            String ts = TIMESTAMP_FMT.format(m.timestamp());
            return "[" + ts + "] " + prefix + ": " + m.textContent();
        }
        return prefix + ": " + m.textContent();
    }

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    @Override
    public String getContentId() {
        return HashUtils.sampledSha256(toContentString());
    }

    @Override
    public String toString() {
        return toContentString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<Message> messages = new ArrayList<>();

        public Builder addMessage(Message message) {
            messages.add(message);
            return this;
        }

        public Builder addUserMessage(String content) {
            return addMessage(Message.user(content));
        }

        public Builder addAssistantMessage(String content) {
            return addMessage(Message.assistant(content));
        }

        public ConversationContent build() {
            return new ConversationContent(messages);
        }
    }
}
