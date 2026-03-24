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

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ContextWindow")
class ContextWindowTest {

    @Nested
    @DisplayName("bufferOnly")
    class BufferOnly {

        @Test
        @DisplayName("Has no memories")
        void has_no_memories() {
            var messages = List.of(Message.user("Hello"), Message.assistant("Hi"));
            var window = ContextWindow.bufferOnly(messages, 100);

            assertThat(window.hasMemories()).isFalse();
            assertThat(window.memories()).isNull();
            assertThat(window.recentMessages()).hasSize(2);
            assertThat(window.totalTokens()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("With memories")
    class WithMemories {

        @Test
        @DisplayName("hasMemories returns true when non-empty")
        void has_memories_when_non_empty() {
            var messages = List.of(Message.user("Hello"));
            var memories = buildRetrievalResult("User likes Java");
            var window = new ContextWindow(messages, memories, 200);

            assertThat(window.hasMemories()).isTrue();
        }

        @Test
        @DisplayName("hasMemories returns false for empty retrieval result")
        void has_memories_false_when_empty() {
            var messages = List.of(Message.user("Hello"));
            var emptyMemories = RetrievalResult.empty("SIMPLE", "test");
            var window = new ContextWindow(messages, emptyMemories, 100);

            assertThat(window.hasMemories()).isFalse();
        }
    }

    @Nested
    @DisplayName("formattedContext")
    class FormattedContext {

        @Test
        @DisplayName("Buffer-only context contains only messages")
        void buffer_only_contains_messages() {
            var messages = List.of(Message.user("Hello"), Message.assistant("Hi there"));
            var window = ContextWindow.bufferOnly(messages, 100);

            String formatted = window.formattedContext();

            assertThat(formatted).contains("<recent-messages>");
            assertThat(formatted).contains("[USER] Hello");
            assertThat(formatted).contains("[ASSISTANT] Hi there");
            assertThat(formatted).doesNotContain("<related-memories>");
        }

        @Test
        @DisplayName("Full context contains memories and messages")
        void full_context_contains_both() {
            var messages = List.of(Message.user("What's my name?"));
            var memories = buildRetrievalResult("User's name is Alice");
            var window = new ContextWindow(messages, memories, 300);

            String formatted = window.formattedContext();

            assertThat(formatted).contains("<related-memories>");
            assertThat(formatted).contains("User's name is Alice");
            assertThat(formatted).contains("<recent-messages>");
            assertThat(formatted).contains("[USER] What's my name?");
        }

        @Test
        @DisplayName("Empty buffer produces empty string")
        void empty_buffer_produces_empty() {
            var window = ContextWindow.bufferOnly(List.of(), 0);

            assertThat(window.formattedContext()).isEmpty();
        }
    }

    private static RetrievalResult buildRetrievalResult(String itemText) {
        var scored =
                new ScoredResult(
                        ScoredResult.SourceType.ITEM, "item-1", itemText, 0.9f, 0.9, Instant.now());
        return new RetrievalResult(
                List.of(scored), List.of(), List.of(), List.of(), "SIMPLE", "test");
    }
}
