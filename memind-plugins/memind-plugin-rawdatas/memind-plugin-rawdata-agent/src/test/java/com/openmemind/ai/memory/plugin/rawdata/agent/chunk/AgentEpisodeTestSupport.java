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

import com.openmemind.ai.memory.plugin.rawdata.agent.content.AgentTimelineContent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventKind;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventStatus;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentProject;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class AgentEpisodeTestSupport {

    private AgentEpisodeTestSupport() {}

    static AgentTimelineContent paymentTimeline(List<AgentEvent> events) {
        return new AgentTimelineContent(
                "codex",
                "1.0",
                "session-123",
                "session-123-agent-turn-1-5",
                "timeline-123",
                new AgentProject(
                        "payments-api",
                        "/Users/alice/work/payments-api",
                        null,
                        Map.of("projectSlug", "payments-api-remote")),
                events);
    }

    static List<AgentEvent> paymentEvents() {
        return List.of(
                event(
                        "e1",
                        1,
                        AgentEventKind.USER_PROMPT,
                        "2026-05-24T10:00:00Z",
                        "Fix payment tests",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                event(
                        "e2",
                        2,
                        AgentEventKind.COMMAND,
                        "2026-05-24T10:01:00Z",
                        null,
                        "Bash",
                        "rounding mismatch",
                        AgentEventStatus.FAILED,
                        null,
                        null,
                        "npm test payment",
                        1),
                event(
                        "e3",
                        3,
                        AgentEventKind.FILE_EDIT,
                        "2026-05-24T10:02:00Z",
                        null,
                        "Edit",
                        "changed rounding logic",
                        AgentEventStatus.SUCCESS,
                        "src/payment/calc.ts",
                        "edit",
                        null,
                        null),
                event(
                        "e4",
                        4,
                        AgentEventKind.COMMAND,
                        "2026-05-24T10:03:00Z",
                        null,
                        "Bash",
                        "passed",
                        AgentEventStatus.SUCCESS,
                        null,
                        null,
                        "npm test payment",
                        0),
                event(
                        "e5",
                        5,
                        AgentEventKind.STOP,
                        "2026-05-24T10:04:00Z",
                        "done",
                        null,
                        null,
                        AgentEventStatus.SUCCESS,
                        null,
                        null,
                        null,
                        null));
    }

    static AgentEvent event(
            String id,
            int seq,
            AgentEventKind kind,
            String occurredAt,
            String text,
            String toolName,
            String output,
            AgentEventStatus status,
            String path,
            String operation,
            String command,
            Integer exitCode) {
        return new AgentEvent(
                id,
                seq,
                kind,
                Instant.parse(occurredAt),
                text,
                toolName,
                null,
                output,
                status,
                10L,
                null,
                null,
                null,
                path,
                operation,
                command,
                exitCode,
                Map.of());
    }
}
