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
package com.openmemind.ai.memory.core.llm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatMessage")
class ChatMessageTest {

    @Test
    @DisplayName("rejects null role")
    void rejectsNullRole() {
        assertThatThrownBy(() -> new ChatMessage(null, "content"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("role must not be null");
    }

    @Test
    @DisplayName("rejects null content")
    void rejectsNullContent() {
        assertThatThrownBy(() -> new ChatMessage(ChatRole.USER, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("content must not be null");
    }

    @Test
    @DisplayName("rejects blank content")
    void rejectsBlankContent() {
        assertThatThrownBy(() -> new ChatMessage(ChatRole.USER, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content must not be blank");
    }
}
