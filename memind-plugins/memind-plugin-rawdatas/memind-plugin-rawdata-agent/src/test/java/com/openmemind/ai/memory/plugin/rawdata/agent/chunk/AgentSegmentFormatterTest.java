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

import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEpisode;
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
        assertThat(formatted.metadata()).doesNotContainKey("projectRootRaw");
        assertThat(formatted.metadata()).containsKey("projectRootHash");
    }
}
