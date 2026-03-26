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
package com.openmemind.ai.memory.core.store.buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryRecentConversationBuffer")
class InMemoryRecentConversationBufferTest {

    @Test
    @DisplayName("append preserves message order")
    void appendPreservesMessageOrder() {
        var buffer = new InMemoryRecentConversationBuffer();

        buffer.append("session-1", Message.user("one"));
        buffer.append("session-1", Message.assistant("two"));

        assertThat(buffer.loadRecent("session-1", 10))
                .extracting(Message::textContent)
                .containsExactly("one", "two");
    }

    @Test
    @DisplayName("loadRecent returns only the newest messages up to the limit")
    void loadRecentReturnsNewestMessages() {
        var buffer = new InMemoryRecentConversationBuffer();

        IntStream.rangeClosed(1, 12)
                .forEach(i -> buffer.append("session-1", Message.user("message-" + i)));

        assertThat(buffer.loadRecent("session-1", 10))
                .extracting(Message::textContent)
                .containsExactly(
                        "message-3",
                        "message-4",
                        "message-5",
                        "message-6",
                        "message-7",
                        "message-8",
                        "message-9",
                        "message-10",
                        "message-11",
                        "message-12");
    }

    @Test
    @DisplayName("loadRecent returns all messages when the limit exceeds the buffer size")
    void loadRecentReturnsAllMessagesWhenLimitExceedsBufferSize() {
        var buffer = new InMemoryRecentConversationBuffer();

        buffer.append("session-1", Message.user("one"));
        buffer.append("session-1", Message.user("two"));

        assertThat(buffer.loadRecent("session-1", 10))
                .extracting(Message::textContent)
                .containsExactly("one", "two");
    }

    @Test
    @DisplayName("clear removes only the requested session")
    void clearRemovesOnlyRequestedSession() {
        var buffer = new InMemoryRecentConversationBuffer();

        buffer.append("session-1", Message.user("one"));
        buffer.append("session-2", Message.user("two"));

        buffer.clear("session-1");

        assertThat(buffer.loadRecent("session-1", 10)).isEmpty();
        assertThat(buffer.loadRecent("session-2", 10))
                .extracting(Message::textContent)
                .containsExactly("two");
    }

    @Test
    @DisplayName("constructor rejects non-positive retention")
    void constructorRejectsNonPositiveRetention() {
        assertThatThrownBy(() -> new InMemoryRecentConversationBuffer(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retention");
    }
}
