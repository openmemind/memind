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
package com.openmemind.ai.client.model.common;

import java.util.List;
import java.util.Objects;

public final class ConversationContent extends RawContent {

    private final List<Message> messages;

    private ConversationContent(List<Message> messages) {
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
    }

    public static ConversationContent of(List<Message> messages) {
        return new ConversationContent(messages);
    }

    @Override
    public String type() {
        return "conversation";
    }

    public List<Message> getMessages() {
        return messages;
    }
}
