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
package com.openmemind.ai.memory.plugin.rawdata.agent.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.plugin.rawdata.agent.AgentRawContentTypeRegistrar;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventKind;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventStatus;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentProject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentTimelineContentTest {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        return RawContentJackson.registerAll(mapper, List.of(new AgentRawContentTypeRegistrar()));
    }

    @Test
    void contentShouldExposeDeterministicIdentityAndReadableTimelineText() {
        AgentProject project = new AgentProject("payment-service", "/repo/payment", null, Map.of());
        List<AgentEvent> events =
                List.of(
                        new AgentEvent(
                                "e2",
                                2,
                                AgentEventKind.COMMAND,
                                Instant.parse("2026-05-24T10:01:00Z"),
                                null,
                                "Bash",
                                null,
                                "rounding mismatch",
                                AgentEventStatus.FAILED,
                                1200L,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "npm test payment",
                                1,
                                Map.of()),
                        new AgentEvent(
                                "e1",
                                1,
                                AgentEventKind.USER_PROMPT,
                                Instant.parse("2026-05-24T10:00:00Z"),
                                "Fix payment tests",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Map.of()));

        AgentTimelineContent content =
                new AgentTimelineContent(
                        "claude-code",
                        "1.0",
                        "session-123",
                        "session-123-agent-turn-1-2",
                        "timeline-123",
                        project,
                        events);
        AgentTimelineContent duplicate =
                new AgentTimelineContent(
                        "claude-code",
                        "1.0",
                        "session-123",
                        "session-123-agent-turn-1-2",
                        "timeline-123",
                        project,
                        events);

        assertThat(content.contentType()).isEqualTo("AGENT_TIMELINE");
        assertThat(content.toContentString())
                .contains("Goal:", "Fix payment tests", "npm test payment");
        assertThat(content.getContentId()).isEqualTo(duplicate.getContentId());
        assertThat(content.events()).extracting(AgentEvent::eventId).containsExactly("e1", "e2");
    }

    @Test
    void jacksonRoundTripShouldPreserveSubtypeAndUserPromptText() throws Exception {
        AgentTimelineContent content =
                new AgentTimelineContent(
                        "codex",
                        "1.0",
                        "session-1",
                        "session-1-agent-turn-1-1",
                        "timeline-1",
                        new AgentProject("memind", "/repo/memind", null, Map.of()),
                        List.of(
                                new AgentEvent(
                                        "e1",
                                        1,
                                        AgentEventKind.USER_PROMPT,
                                        Instant.parse("2026-05-24T10:00:00Z"),
                                        "Review rawdata-agent design",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        Map.of())));

        String json = OBJECT_MAPPER.writeValueAsString(content);
        RawContent decoded = OBJECT_MAPPER.readValue(json, RawContent.class);

        assertThat(json).contains("\"type\":\"agent_timeline\"");
        assertThat(json).contains("\"eventId\":\"e1\"");
        assertThat(json).doesNotContain("\"id\":\"e1\"");
        assertThat(decoded).isInstanceOf(AgentTimelineContent.class);
        assertThat(((AgentTimelineContent) decoded).events())
                .singleElement()
                .extracting(AgentEvent::text)
                .isEqualTo("Review rawdata-agent design");
        assertThat(decoded.toContentString()).contains("Goal: Review rawdata-agent design");
    }

    @Test
    void jacksonShouldPreserveAgentTurnAndEventIdFields() throws Exception {
        String json =
                """
                {
                  "type": "agent_timeline",
                  "sourceClient": "claude-code",
                  "sessionId": "session-1",
                  "agentTurnId": "turn-1",
                  "timelineId": "timeline-1",
                  "events": [
                    {
                      "eventId": "event-new",
                      "seq": 1,
                      "kind": "user_prompt",
                      "text": "Fix test"
                    }
                  ]
                }
                """;

        RawContent decoded = OBJECT_MAPPER.readValue(json, RawContent.class);

        AgentTimelineContent timeline = (AgentTimelineContent) decoded;
        assertThat(timeline.agentTurnId()).isEqualTo("turn-1");
        assertThat(timeline.events()).extracting(AgentEvent::eventId).containsExactly("event-new");
    }

    @Test
    void jacksonShouldPreserveToolTelemetryFields() throws Exception {
        String json =
                """
                {
                  "type": "agent_timeline",
                  "sourceClient": "claude-code",
                  "sessionId": "session-1",
                  "agentTurnId": "turn-1",
                  "timelineId": "timeline-1",
                  "events": [
                    {
                      "eventId": "event-tool",
                      "seq": 1,
                      "kind": "command",
                      "toolName": "Bash",
                      "command": "npm test payment",
                      "status": "success",
                      "durationMs": 1234,
                      "inputTokens": 11,
                      "outputTokens": 22,
                      "contentHash": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    }
                  ]
                }
                """;

        RawContent decoded = OBJECT_MAPPER.readValue(json, RawContent.class);

        AgentEvent event = ((AgentTimelineContent) decoded).events().getFirst();
        assertThat(event.durationMs()).isEqualTo(1234L);
        assertThat(event.inputTokens()).isEqualTo(11);
        assertThat(event.outputTokens()).isEqualTo(22);
        assertThat(event.contentHash())
                .isEqualTo(
                        "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    }

    @Test
    void contentIdShouldIncludeTypedToolTelemetryFields() {
        AgentProject project = new AgentProject("payment-service", "/repo/payment", null, Map.of());
        AgentEvent first =
                new AgentEvent(
                        "e1",
                        1,
                        AgentEventKind.COMMAND,
                        Instant.parse("2026-05-24T10:00:00Z"),
                        null,
                        "Bash",
                        null,
                        "passed",
                        AgentEventStatus.SUCCESS,
                        1234L,
                        11,
                        22,
                        "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        null,
                        "run",
                        "npm test payment",
                        0,
                        Map.of());
        AgentEvent second =
                new AgentEvent(
                        "e1",
                        1,
                        AgentEventKind.COMMAND,
                        Instant.parse("2026-05-24T10:00:00Z"),
                        null,
                        "Bash",
                        null,
                        "passed",
                        AgentEventStatus.SUCCESS,
                        1234L,
                        11,
                        23,
                        "sha256:fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
                        null,
                        "run",
                        "npm test payment",
                        0,
                        Map.of());

        AgentTimelineContent firstContent =
                new AgentTimelineContent(
                        "claude-code",
                        "1.0",
                        "session-123",
                        "turn-1",
                        "timeline-1",
                        project,
                        List.of(first));
        AgentTimelineContent secondContent =
                new AgentTimelineContent(
                        "claude-code",
                        "1.0",
                        "session-123",
                        "turn-1",
                        "timeline-1",
                        project,
                        List.of(second));

        assertThat(firstContent.getContentId()).isNotEqualTo(secondContent.getContentId());
    }

    @Test
    void eventKindShouldParseLifecycleWireValues() {
        assertThat(AgentEventKind.fromWireValue("notification"))
                .isEqualTo(AgentEventKind.NOTIFICATION);
        assertThat(AgentEventKind.fromWireValue("subagent_stop"))
                .isEqualTo(AgentEventKind.SUBAGENT_STOP);
        assertThat(AgentEventKind.fromWireValue("compact_boundary"))
                .isEqualTo(AgentEventKind.COMPACT_BOUNDARY);
        assertThat(AgentEventKind.fromWireValue("synthetic_boundary"))
                .isEqualTo(AgentEventKind.SYNTHETIC_BOUNDARY);
    }

    @Test
    void contentStringShouldIncludeLifecycleEventEvidence() {
        AgentTimelineContent content =
                new AgentTimelineContent(
                        "claude-code",
                        "1.0",
                        "session-1",
                        "turn-1",
                        "timeline-1",
                        null,
                        List.of(
                                new AgentEvent(
                                        "notice-1",
                                        1,
                                        AgentEventKind.NOTIFICATION,
                                        Instant.parse("2026-05-24T10:00:00Z"),
                                        "Permission required for Bash",
                                        null,
                                        null,
                                        null,
                                        AgentEventStatus.FAILED,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        "blocked",
                                        null,
                                        null,
                                        Map.of()),
                                new AgentEvent(
                                        "subagent-1",
                                        2,
                                        AgentEventKind.SUBAGENT_STOP,
                                        Instant.parse("2026-05-24T10:01:00Z"),
                                        "Explorer found parser edge cases",
                                        "explorer",
                                        null,
                                        null,
                                        AgentEventStatus.SUCCESS,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        Map.of()),
                                new AgentEvent(
                                        "compact-1",
                                        3,
                                        AgentEventKind.COMPACT_BOUNDARY,
                                        Instant.parse("2026-05-24T10:02:00Z"),
                                        "compact",
                                        null,
                                        null,
                                        null,
                                        AgentEventStatus.SUCCESS,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        "manual",
                                        null,
                                        null,
                                        Map.of())));

        assertThat(content.toContentString())
                .contains("notification", "Permission required for Bash")
                .contains("subagent_stop", "Explorer found parser edge cases", "tool=explorer")
                .contains("compact_boundary", "operation=manual");
    }
}
