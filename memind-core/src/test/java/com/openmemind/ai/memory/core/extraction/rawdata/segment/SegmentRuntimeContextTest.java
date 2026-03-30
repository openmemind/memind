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
package com.openmemind.ai.memory.core.extraction.rawdata.segment;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SegmentRuntimeContextTest {

    @Test
    void derivesConversationTimingAndKeepsHelpersConsistent() {
        var messages =
                List.of(
                        Message.user("hello", Instant.parse("2026-03-27T02:17:00Z"), "Alice"),
                        new Message(
                                Message.Role.ASSISTANT,
                                List.of(),
                                Instant.parse("2026-03-27T02:18:00Z"),
                                null));

        var runtimeContext = SegmentRuntimeContext.fromConversationMessages(messages);
        var segment =
                new Segment(
                        "user: hello\nassistant: hi",
                        null,
                        new MessageBoundary(0, 2),
                        Map.of("start_message", 0, "end_message", 2),
                        runtimeContext);

        assertThat(runtimeContext.startTime()).isEqualTo(Instant.parse("2026-03-27T02:17:00Z"));
        assertThat(runtimeContext.observedAt()).isEqualTo(Instant.parse("2026-03-27T02:18:00Z"));
        assertThat(runtimeContext.userName()).isEqualTo("Alice");
        assertThat(segment.withCaption("caption").runtimeContext()).isEqualTo(runtimeContext);
        assertThat(segment.withoutRuntimeContext().runtimeContext()).isNull();
    }

    @Test
    void legacyConstructorsDefaultRuntimeContextToNull() {
        var segment = new Segment("content", null, new CharBoundary(0, 7), Map.of());
        var parsedSegment = new ParsedSegment("content", null, 0, 7, "raw-1", Map.of());

        assertThat(segment.runtimeContext()).isNull();
        assertThat(parsedSegment.runtimeContext()).isNull();
    }
}
