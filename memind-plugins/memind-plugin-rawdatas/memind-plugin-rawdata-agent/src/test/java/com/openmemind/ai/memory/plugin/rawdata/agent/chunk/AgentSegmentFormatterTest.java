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

import com.openmemind.ai.memory.plugin.rawdata.agent.content.AgentTimelineContent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEpisode;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventKind;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentSegmentFormatterTest {

    @Test
    void shouldFormatEpisodeTextAndMetadataDeterministically() {
        var timeline =
                AgentEpisodeTestSupport.paymentTimeline(AgentEpisodeTestSupport.paymentEvents());
        AgentEpisode episode = new AgentEpisodeAssembler().assemble(timeline).getFirst();

        AgentSegmentFormatter.FormattedSegment formatted =
                new AgentSegmentFormatter().format(timeline, episode);

        assertThat(formatted.content())
                .contains(
                        "Goal: Fix payment tests.",
                        "Outcome: success",
                        "Project: payments-api",
                        "Files: src/payment/calc.ts",
                        "Commands:",
                        "- npm test payment -> failed: rounding mismatch",
                        "- npm test payment -> success",
                        "Evidence:",
                        "- e2:",
                        "- e4:");
        assertThat(formatted.metadata())
                .containsEntry("segmentType", "agent_episode")
                .containsEntry("episodeId", episode.id())
                .containsEntry("phase", "full")
                .containsEntry("sourceClient", "codex")
                .containsEntry("sessionId", "session-123")
                .containsEntry("timelineId", "timeline-123")
                .containsEntry("projectName", "payments-api")
                .containsEntry("projectId", "payments-api-remote")
                .containsEntry("projectSlug", "payments-api-remote")
                .containsEntry("outcome", "success");
        assertThat(formatted.metadata().get("files"))
                .asList()
                .containsExactly("src/payment/calc.ts");
        assertThat(formatted.metadata().get("commands"))
                .asList()
                .containsExactly("npm test payment");
        assertThat(formatted.metadata().get("toolNames")).asList().containsExactly("Bash", "Edit");
        assertThat(formatted.metadata().get("failureSignals"))
                .asList()
                .contains("rounding mismatch");
        assertThat(formatted.metadata().get("eventIds"))
                .asList()
                .containsExactly("e1", "e2", "e3", "e4", "e5");
        assertThat(formatted.metadata().get("commandEvents"))
                .asList()
                .containsExactly(
                        Map.of(
                                "eventId",
                                "e2",
                                "seq",
                                2,
                                "command",
                                "npm test payment",
                                "status",
                                "failed",
                                "output",
                                "rounding mismatch",
                                "exitCode",
                                1),
                        Map.of(
                                "eventId",
                                "e4",
                                "seq",
                                4,
                                "command",
                                "npm test payment",
                                "status",
                                "success",
                                "output",
                                "passed",
                                "exitCode",
                                0));
        assertThat(formatted.metadata().get("fileEvents"))
                .asList()
                .containsExactly(
                        Map.of(
                                "eventId",
                                "e3",
                                "seq",
                                3,
                                "path",
                                "src/payment/calc.ts",
                                "operation",
                                "edit"));
        assertThat(formatted.metadata().get("toolRecords"))
                .asList()
                .contains(
                        Map.of(
                                "eventId",
                                "e2",
                                "seq",
                                2,
                                "toolName",
                                "Bash",
                                "kind",
                                "command",
                                "status",
                                "failed",
                                "durationMs",
                                10L,
                                "command",
                                "npm test payment",
                                "outputPreview",
                                "rounding mismatch"));
        assertThat(formatted.metadata().get("toolStats"))
                .isEqualTo(
                        Map.of(
                                "Bash",
                                Map.of(
                                        "callCount",
                                        2,
                                        "successCount",
                                        1,
                                        "failCount",
                                        1,
                                        "avgDurationMs",
                                        10L),
                                "Edit",
                                Map.of(
                                        "callCount",
                                        1,
                                        "successCount",
                                        1,
                                        "failCount",
                                        0,
                                        "avgDurationMs",
                                        10L)));
        assertThat(formatted.metadata().get("toolGroups"))
                .asList()
                .contains(
                        Map.of(
                                "toolName",
                                "Bash",
                                "callCount",
                                2,
                                "successCount",
                                1,
                                "failCount",
                                1,
                                "commands",
                                List.of("npm test payment")));
        assertThat(formatted.metadata()).doesNotContainKey("projectRootRaw");
        assertThat(formatted.metadata()).containsKey("projectRootHash");
    }

    @Test
    void shouldCapToolRecordMetadata() {
        var events = new ArrayList<AgentEvent>();
        events.add(
                AgentEpisodeTestSupport.event(
                        "prompt",
                        1,
                        AgentEventKind.USER_PROMPT,
                        "2026-05-24T10:00:00Z",
                        "Run many commands",
                        null,
                        null,
                        AgentEventStatus.SUCCESS,
                        null,
                        null,
                        null,
                        null));
        for (int i = 0; i < 45; i++) {
            events.add(commandEvent("tool-" + i, i + 2, "Bash", "npm test module-" + i, null));
        }

        AgentSegmentFormatter.FormattedSegment formatted =
                formatSingleEpisode(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(formatted.metadata().get("toolRecords")).asList().hasSize(40);
    }

    @Test
    void shouldCapToolStatsGroupsAndGroupValues() {
        var events = new ArrayList<AgentEvent>();
        events.add(
                AgentEpisodeTestSupport.event(
                        "prompt",
                        1,
                        AgentEventKind.USER_PROMPT,
                        "2026-05-24T10:00:00Z",
                        "Run many tools",
                        null,
                        null,
                        AgentEventStatus.SUCCESS,
                        null,
                        null,
                        null,
                        null));
        for (int i = 0; i < 25; i++) {
            events.add(
                    commandEvent(
                            "same-tool-command-" + i,
                            i + 2,
                            "Bash",
                            "npm test module-" + i,
                            "src/module-" + i + ".ts"));
        }
        for (int i = 0; i < 25; i++) {
            events.add(commandEvent("distinct-tool-" + i, i + 40, "Tool" + i, "tool " + i, null));
        }

        AgentSegmentFormatter.FormattedSegment formatted =
                formatSingleEpisode(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(((Map<?, ?>) formatted.metadata().get("toolStats"))).hasSize(20);
        assertThat(formatted.metadata().get("toolGroups")).asList().hasSize(20);
        Map<?, ?> group = (Map<?, ?>) ((List<?>) formatted.metadata().get("toolGroups")).getFirst();
        assertThat(group.get("commands")).asList().hasSize(20);
        assertThat(group.get("paths")).asList().hasSize(20);
    }

    private static AgentSegmentFormatter.FormattedSegment formatSingleEpisode(
            AgentTimelineContent timeline) {
        AgentEpisode episode = new AgentEpisodeAssembler().assemble(timeline).getFirst();
        return new AgentSegmentFormatter().format(timeline, episode);
    }

    private static AgentEvent commandEvent(
            String id, int seq, String toolName, String command, String path) {
        return new AgentEvent(
                id,
                seq,
                AgentEventKind.COMMAND,
                Instant.parse("2026-05-24T10:00:00Z").plusSeconds(seq),
                null,
                toolName,
                null,
                "passed",
                AgentEventStatus.SUCCESS,
                10L,
                null,
                null,
                "sha256:%064d".formatted(seq),
                path,
                "run",
                command,
                0,
                Map.of());
    }
}
