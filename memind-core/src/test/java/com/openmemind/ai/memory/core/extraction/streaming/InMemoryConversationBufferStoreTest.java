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
package com.openmemind.ai.memory.core.extraction.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryConversationBufferStore Unit Test")
class InMemoryConversationBufferStoreTest {

    private InMemoryConversationBufferStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryConversationBufferStore();
    }

    @Nested
    @DisplayName("Basic Buffer Operations")
    class BufferTests {

        @Test
        @DisplayName("save + load Normal Access")
        void shouldSaveAndLoadBuffer() {
            store.save("session-1", List.of(Message.user("Message")));

            assertThat(store.load("session-1")).hasSize(1);
            assertThat(store.load("session-1").getFirst().textContent()).isEqualTo("Message");
        }

        @Test
        @DisplayName("load Returns Empty List When Not Exists")
        void shouldReturnEmptyWhenNotExists() {
            assertThat(store.load("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("clear Should Clear Buffer and Message Count")
        void shouldClearBufferAndMessageCount() {
            store.save("session-1", List.of(Message.user("Message")));
            store.saveMessageCount("session-1", 5);

            store.clear("session-1");

            assertThat(store.load("session-1")).isEmpty();
            assertThat(store.loadMessageCount("session-1")).isZero();
        }
    }
}
