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
package com.openmemind.ai.memory.core.extraction.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.buffer.InMemoryConversationBuffer;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryConversationBuffer Unit Test")
class InMemoryConversationBufferTest {

    private InMemoryConversationBuffer store;

    @BeforeEach
    void setUp() {
        store = new InMemoryConversationBuffer();
    }

    @Nested
    @DisplayName("Basic Buffer Operations")
    class BufferTests {

        @Test
        @DisplayName("append + load Normal Access")
        void shouldSaveAndLoadBuffer() {
            store.append("session-1", Message.user("Message"));

            assertThat(store.load("session-1")).hasSize(1);
            assertThat(store.load("session-1").getFirst().textContent()).isEqualTo("Message");
        }

        @Test
        @DisplayName("load Returns Empty List When Not Exists")
        void shouldReturnEmptyWhenNotExists() {
            assertThat(store.load("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("clear Should Clear Buffer")
        void shouldClearBuffer() {
            store.append("session-1", Message.user("Message"));

            store.clear("session-1");

            assertThat(store.load("session-1")).isEmpty();
        }

        @Test
        @DisplayName("append Should Preserve Existing Messages and Increment Count")
        void shouldAppendMessageAndIncrementCount() {
            store.append("session-1", Message.user("first"));

            store.append("session-1", Message.assistant("second"));

            assertThat(store.load("session-1"))
                    .extracting(Message::textContent)
                    .containsExactly("first", "second");
        }
    }
}
