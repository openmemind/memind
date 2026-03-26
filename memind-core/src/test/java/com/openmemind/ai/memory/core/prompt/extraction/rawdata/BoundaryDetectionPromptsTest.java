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
package com.openmemind.ai.memory.core.prompt.extraction.rawdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.context.CommitDetectionContext;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BoundaryDetectionPrompts")
class BoundaryDetectionPromptsTest {

    @Test
    @DisplayName("Build explicit history and new message sections")
    void builds_explicit_history_and_new_message_sections() {
        var history =
                List.of(
                        Message.user("History one", Instant.parse("2024-03-15T10:00:00Z")),
                        Message.assistant("History two", Instant.parse("2024-03-15T10:05:00Z")));
        var incoming = List.of(Message.user("Incoming", Instant.parse("2024-03-15T10:45:00Z")));

        var prompt =
                BoundaryDetectionPrompts.build(
                                history,
                                incoming,
                                new CommitDetectionContext(Duration.ofMinutes(40)))
                        .render(null);

        assertThat(prompt.userPrompt())
                .contains("# Conversation History")
                .contains("History one")
                .contains("History two")
                .contains("# New Messages")
                .contains("Incoming")
                .contains("40 minutes");
    }
}
