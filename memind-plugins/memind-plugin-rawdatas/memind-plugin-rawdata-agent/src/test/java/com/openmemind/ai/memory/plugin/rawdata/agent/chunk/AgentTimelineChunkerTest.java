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
package com.openmemind.ai.memory.plugin.rawdata.agent.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.plugin.rawdata.agent.content.AgentTimelineContent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventKind;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentTimelineChunkerTest {

    @Test
    void shouldRedactAssembleAndFormatAgentTimelineSegments() {
        AgentTimelineContent timeline =
                AgentEpisodeTestSupport.paymentTimeline(
                        List.of(
                                AgentEpisodeTestSupport.paymentEvents().get(0),
                                new AgentEvent(
                                        "e2",
                                        2,
                                        AgentEventKind.COMMAND,
                                        Instant.parse("2026-05-24T10:01:00Z"),
                                        null,
                                        "Bash",
                                        "Authorization: Bearer abc.def.ghi",
                                        "rounding mismatch",
                                        AgentEventStatus.FAILED,
                                        10L,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        "npm test payment",
                                        1,
                                        Map.of()),
                                AgentEpisodeTestSupport.paymentEvents().get(2),
                                AgentEpisodeTestSupport.paymentEvents().get(3),
                                AgentEpisodeTestSupport.paymentEvents().get(4)));

        List<Segment> segments = new AgentTimelineChunker().chunk(timeline);

        assertThat(segments).hasSize(1);
        Segment segment = segments.getFirst();
        assertThat(segment.content()).contains("[REDACTED:bearer_token]");
        assertThat(segment.metadata()).containsEntry("segmentType", "agent_episode");
        assertThat(segment.boundary()).isEqualTo(new CharBoundary(0, segment.content().length()));
        assertThat(segment.runtimeContext().startTime())
                .isEqualTo(Instant.parse("2026-05-24T10:00:00Z"));
        assertThat(segment.runtimeContext().observedAt())
                .isEqualTo(Instant.parse("2026-05-24T10:04:00Z"));
        assertThat(segment.runtimeContext().sourceClient()).isEqualTo("codex");
    }
}
