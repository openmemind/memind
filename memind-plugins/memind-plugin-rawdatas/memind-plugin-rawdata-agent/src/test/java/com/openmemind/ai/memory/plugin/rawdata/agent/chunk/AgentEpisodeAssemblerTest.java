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

import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentChunkingOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEpisode;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventKind;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventStatus;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentOutcome;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentEpisodeAssemblerTest {

    @Test
    void shouldAssembleSuccessfulPaymentEpisodeWithStableEvidence() {
        var timeline =
                AgentEpisodeTestSupport.paymentTimeline(AgentEpisodeTestSupport.paymentEvents());

        List<AgentEpisode> episodes = new AgentEpisodeAssembler().assemble(timeline);

        assertThat(episodes).hasSize(1);
        AgentEpisode episode = episodes.getFirst();
        assertThat(episode.goal()).isEqualTo("Fix payment tests");
        assertThat(episode.outcome()).isEqualTo(AgentOutcome.SUCCESS);
        assertThat(episode.eventIds()).containsExactly("e1", "e2", "e3", "e4", "e5");
        assertThat(episode.files()).containsExactly("src/payment/calc.ts");
        assertThat(episode.commands()).containsExactly("npm test payment");
        assertThat(episode.failureSignals()).contains("rounding mismatch");
        assertThat(episode.id())
                .isEqualTo(new AgentEpisodeAssembler().assemble(timeline).getFirst().id());
    }

    @Test
    void shouldUseNormalizedFileAndTestEventsInEpisodeMetadata() {
        List<AgentEvent> events =
                List.of(
                        AgentEpisodeTestSupport.event(
                                "e1",
                                1,
                                AgentEventKind.USER_PROMPT,
                                "2026-05-24T10:00:00Z",
                                "Fix payment tests",
                                null,
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                null,
                                null,
                                null),
                        AgentEpisodeTestSupport.event(
                                "e2",
                                2,
                                AgentEventKind.FILE_READ,
                                "2026-05-24T10:01:00Z",
                                null,
                                "Read",
                                null,
                                AgentEventStatus.SUCCESS,
                                "src/payment/calc.ts",
                                "read",
                                null,
                                null),
                        AgentEpisodeTestSupport.event(
                                "e3",
                                3,
                                AgentEventKind.FILE_EDIT,
                                "2026-05-24T10:02:00Z",
                                null,
                                "MultiEdit",
                                null,
                                AgentEventStatus.SUCCESS,
                                "src/payment/calc.ts",
                                "multi_edit",
                                null,
                                null),
                        AgentEpisodeTestSupport.event(
                                "e4",
                                4,
                                AgentEventKind.TEST_RESULT,
                                "2026-05-24T10:03:00Z",
                                null,
                                "Bash",
                                "rounding mismatch",
                                AgentEventStatus.FAILED,
                                null,
                                "run",
                                "npm test payment",
                                1),
                        AgentEpisodeTestSupport.event(
                                "e5",
                                5,
                                AgentEventKind.TEST_RESULT,
                                "2026-05-24T10:04:00Z",
                                null,
                                "Bash",
                                "passed",
                                AgentEventStatus.SUCCESS,
                                null,
                                "run",
                                "npm test payment",
                                0),
                        AgentEpisodeTestSupport.event(
                                "e6",
                                6,
                                AgentEventKind.STOP,
                                "2026-05-24T10:05:00Z",
                                null,
                                null,
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                null,
                                null,
                                null));

        List<AgentEpisode> episodes =
                new AgentEpisodeAssembler()
                        .assemble(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(episodes).hasSize(1);
        AgentEpisode episode = episodes.getFirst();
        assertThat(episode.outcome()).isEqualTo(AgentOutcome.SUCCESS);
        assertThat(episode.files()).containsExactly("src/payment/calc.ts");
        assertThat(episode.fileReferences())
                .extracting("eventId", "path", "operation")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("e2", "src/payment/calc.ts", "read"),
                        org.assertj.core.groups.Tuple.tuple(
                                "e3", "src/payment/calc.ts", "multi_edit"));
        assertThat(episode.commands()).containsExactly("npm test payment");
        assertThat(episode.commandEvents()).hasSize(2);
        assertThat(episode.failureSignals()).contains("rounding mismatch");
    }

    @Test
    void shouldClosePreviousEpisodeWhenNewUserPromptAppears() {
        var events = new ArrayList<>(AgentEpisodeTestSupport.paymentEvents());
        events.add(
                AgentEpisodeTestSupport.event(
                        "e6",
                        6,
                        AgentEventKind.USER_PROMPT,
                        "2026-05-24T10:05:00Z",
                        "Fix auth tests",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
        events.add(
                AgentEpisodeTestSupport.event(
                        "e7",
                        7,
                        AgentEventKind.STOP,
                        "2026-05-24T10:06:00Z",
                        null,
                        null,
                        null,
                        AgentEventStatus.SUCCESS,
                        null,
                        null,
                        null,
                        null));

        List<AgentEpisode> episodes =
                new AgentEpisodeAssembler()
                        .assemble(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(episodes)
                .extracting(AgentEpisode::goal)
                .containsExactly("Fix payment tests", "Fix auth tests");
        assertThat(episodes.getFirst().eventIds()).containsExactly("e1", "e2", "e3", "e4", "e5");
        assertThat(episodes.get(1).eventIds()).containsExactly("e6", "e7");
    }

    @Test
    void shouldKeepExplicitTurnTogetherAcrossLongGap() {
        List<AgentEvent> events =
                List.of(
                        AgentEpisodeTestSupport.event(
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
                        AgentEpisodeTestSupport.event(
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
                        AgentEpisodeTestSupport.event(
                                "e3",
                                3,
                                AgentEventKind.COMMAND,
                                "2026-05-24T10:32:01Z",
                                null,
                                "Bash",
                                "passed",
                                AgentEventStatus.SUCCESS,
                                null,
                                null,
                                "npm test payment",
                                0),
                        AgentEpisodeTestSupport.event(
                                "e4",
                                4,
                                AgentEventKind.STOP,
                                "2026-05-24T10:33:00Z",
                                "done",
                                null,
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                null,
                                null,
                                null));

        List<AgentEpisode> episodes =
                new AgentEpisodeAssembler()
                        .assemble(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(episodes).hasSize(1);
        assertThat(episodes.getFirst().eventIds()).containsExactly("e1", "e2", "e3", "e4");
    }

    @Test
    void shouldSplitFallbackEpisodesOnLongGapWhenNoPromptBoundaryExists() {
        List<AgentEvent> events =
                List.of(
                        AgentEpisodeTestSupport.event(
                                "e1",
                                1,
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
                        AgentEpisodeTestSupport.event(
                                "e2",
                                2,
                                AgentEventKind.COMMAND,
                                "2026-05-24T10:32:01Z",
                                null,
                                "Bash",
                                "passed",
                                AgentEventStatus.SUCCESS,
                                null,
                                null,
                                "npm test payment",
                                0));

        List<AgentEpisode> episodes =
                new AgentEpisodeAssembler()
                        .assemble(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(episodes).hasSize(2);
        assertThat(episodes.getFirst().eventIds()).containsExactly("e1");
        assertThat(episodes.get(1).eventIds()).containsExactly("e2");
    }

    @Test
    void shouldNotSplitExplicitTurnWhenEventCountExceedsMax() {
        AgentChunkingOptions options =
                new AgentChunkingOptions(2_000, 4_000, 2, Duration.ofMinutes(30));
        List<AgentEvent> events = AgentEpisodeTestSupport.paymentEvents();

        List<AgentEpisode> episodes =
                new AgentEpisodeAssembler(options)
                        .assemble(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(episodes).hasSize(1);
        assertThat(episodes.getFirst().eventIds()).containsExactly("e1", "e2", "e3", "e4", "e5");
    }

    @Test
    void shouldNotSplitExplicitTurnWhenEpisodeExceedsTargetTokens() {
        AgentChunkingOptions options = new AgentChunkingOptions(20, 40, 80, Duration.ofMinutes(30));

        List<AgentEpisode> episodes =
                new AgentEpisodeAssembler(options)
                        .assemble(
                                AgentEpisodeTestSupport.paymentTimeline(
                                        AgentEpisodeTestSupport.paymentEvents()));

        assertThat(episodes).hasSize(1);
        assertThat(episodes.getFirst().phase()).isEqualTo("full");
        assertThat(episodes.getFirst().goal()).isEqualTo("Fix payment tests");
        assertThat(episodes.getFirst().metadata()).doesNotContainKey("phaseSplit");
    }

    @Test
    void shouldSplitEpisodesWhenTaskMetadataChanges() {
        List<AgentEvent> events =
                List.of(
                        eventWithMetadata(
                                "e1", 1, AgentEventKind.USER_PROMPT, "Fix payment tests", "task-a"),
                        eventWithMetadata("e2", 2, AgentEventKind.COMMAND, null, "task-a"),
                        eventWithMetadata("e3", 3, AgentEventKind.COMMAND, null, "task-b"));

        List<AgentEpisode> episodes =
                new AgentEpisodeAssembler()
                        .assemble(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(episodes).hasSize(2);
        assertThat(episodes.getFirst().eventIds()).containsExactly("e1", "e2");
        assertThat(episodes.get(1).eventIds()).containsExactly("e3");
    }

    @Test
    void shouldSplitEpisodesWhenTurnMetadataChanges() {
        List<AgentEvent> events =
                List.of(
                        eventWithTurnMetadata(
                                "e1", 1, AgentEventKind.USER_PROMPT, "Fix payment tests", "turn-a"),
                        eventWithTurnMetadata("e2", 2, AgentEventKind.COMMAND, null, "turn-a"),
                        eventWithTurnMetadata("e3", 3, AgentEventKind.COMMAND, null, "turn-b"));

        List<AgentEpisode> episodes =
                new AgentEpisodeAssembler()
                        .assemble(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(episodes).hasSize(2);
        assertThat(episodes.getFirst().eventIds()).containsExactly("e1", "e2");
        assertThat(episodes.get(1).eventIds()).containsExactly("e3");
    }

    @Test
    void shouldCloseEpisodesOnStopButKeepCompactBoundaryInsideTurn() {
        List<AgentEvent> events =
                List.of(
                        AgentEpisodeTestSupport.event(
                                "e1",
                                1,
                                AgentEventKind.USER_PROMPT,
                                "2026-05-24T10:00:00Z",
                                "Review rawdata-agent",
                                null,
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                null,
                                null,
                                null),
                        AgentEpisodeTestSupport.event(
                                "e2",
                                2,
                                AgentEventKind.FILE_READ,
                                "2026-05-24T10:01:00Z",
                                null,
                                "Read",
                                null,
                                AgentEventStatus.SUCCESS,
                                "src/main/java/App.java",
                                "read",
                                null,
                                null),
                        AgentEpisodeTestSupport.event(
                                "e3",
                                3,
                                AgentEventKind.SUBAGENT_STOP,
                                "2026-05-24T10:02:00Z",
                                "Explorer found parser edge cases",
                                "explorer",
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                null,
                                null,
                                null),
                        AgentEpisodeTestSupport.event(
                                "e4",
                                4,
                                AgentEventKind.STOP,
                                "2026-05-24T10:03:00Z",
                                "done",
                                null,
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                null,
                                null,
                                null),
                        AgentEpisodeTestSupport.event(
                                "e5",
                                5,
                                AgentEventKind.USER_PROMPT,
                                "2026-05-24T10:04:00Z",
                                "Continue after compaction",
                                null,
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                null,
                                null,
                                null),
                        AgentEpisodeTestSupport.event(
                                "e6",
                                6,
                                AgentEventKind.COMMAND,
                                "2026-05-24T10:05:00Z",
                                null,
                                "Bash",
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                "run",
                                "mvn test",
                                0),
                        AgentEpisodeTestSupport.event(
                                "e7",
                                7,
                                AgentEventKind.COMPACT_BOUNDARY,
                                "2026-05-24T10:06:00Z",
                                "compact",
                                null,
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                "compact",
                                null,
                                null));

        List<AgentEpisode> episodes =
                new AgentEpisodeAssembler()
                        .assemble(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(episodes).hasSize(2);
        assertThat(episodes.get(0).eventIds()).containsExactly("e1", "e2", "e3", "e4");
        assertThat(episodes.get(1).eventIds()).containsExactly("e5", "e6", "e7");
        assertThat(episodes.get(0).phase()).isEqualTo("full");
        assertThat(episodes.get(1).phase()).isEqualTo("full");
    }

    @Test
    void shouldKeepSameTurnEventsTogetherAcrossCompactBoundaryUntilStop() {
        List<AgentEvent> events =
                List.of(
                        eventWithTurnMetadata(
                                "e1", 1, AgentEventKind.USER_PROMPT, "Fix payment tests", "turn-a"),
                        eventWithTurnMetadata("e2", 2, AgentEventKind.COMMAND, null, "turn-a"),
                        eventWithTurnMetadata(
                                "e3", 3, AgentEventKind.COMPACT_BOUNDARY, "compact", "turn-a"),
                        eventWithTurnMetadata("e4", 4, AgentEventKind.COMMAND, null, "turn-a"),
                        eventWithTurnMetadata("e5", 5, AgentEventKind.STOP, "done", "turn-a"));

        List<AgentEpisode> episodes =
                new AgentEpisodeAssembler()
                        .assemble(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(episodes).hasSize(1);
        assertThat(episodes.getFirst().eventIds()).containsExactly("e1", "e2", "e3", "e4", "e5");
        assertThat(episodes.getFirst().outcome()).isEqualTo(AgentOutcome.SUCCESS);
    }

    @Test
    void shouldKeepLifecycleAndNotificationEventsInFullTurn() {
        AgentChunkingOptions options = new AgentChunkingOptions(1, 2, 80, Duration.ofMinutes(30));
        List<AgentEvent> events =
                List.of(
                        AgentEpisodeTestSupport.event(
                                "e1",
                                1,
                                AgentEventKind.USER_PROMPT,
                                "2026-05-24T10:00:00Z",
                                "Investigate flaky tests",
                                null,
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                null,
                                null,
                                null),
                        AgentEpisodeTestSupport.event(
                                "e2",
                                2,
                                AgentEventKind.NOTIFICATION,
                                "2026-05-24T10:01:00Z",
                                "Permission required for Bash",
                                null,
                                null,
                                AgentEventStatus.FAILED,
                                null,
                                "blocked",
                                null,
                                null),
                        AgentEpisodeTestSupport.event(
                                "e3",
                                3,
                                AgentEventKind.SUBAGENT_STOP,
                                "2026-05-24T10:02:00Z",
                                "Explorer checked parser ownership",
                                "explorer",
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                null,
                                null,
                                null),
                        AgentEpisodeTestSupport.event(
                                "e4",
                                4,
                                AgentEventKind.SYNTHETIC_BOUNDARY,
                                "2026-05-24T10:03:00Z",
                                "flush",
                                null,
                                null,
                                AgentEventStatus.SUCCESS,
                                null,
                                "flush",
                                null,
                                null));

        List<AgentEpisode> episodes =
                new AgentEpisodeAssembler(options)
                        .assemble(AgentEpisodeTestSupport.paymentTimeline(events));

        assertThat(episodes).hasSize(1);
        assertThat(episodes.getFirst().phase()).isEqualTo("full");
        assertThat(episodes.getFirst().eventIds()).containsExactly("e1", "e2", "e3", "e4");
        assertThat(episodes.getFirst().failureSignals()).contains("Permission required for Bash");
    }

    private static AgentEvent eventWithMetadata(
            String id, int seq, AgentEventKind kind, String text, String taskId) {
        AgentEvent base =
                AgentEpisodeTestSupport.event(
                        id,
                        seq,
                        kind,
                        "2026-05-24T10:0" + seq + ":00Z",
                        text,
                        "Bash",
                        null,
                        AgentEventStatus.SUCCESS,
                        null,
                        null,
                        "npm test payment",
                        0);
        return new AgentEvent(
                base.eventId(),
                base.seq(),
                base.kind(),
                base.occurredAt(),
                base.text(),
                base.toolName(),
                base.input(),
                base.output(),
                base.status(),
                base.durationMs(),
                base.inputTokens(),
                base.outputTokens(),
                base.contentHash(),
                base.path(),
                base.operation(),
                base.command(),
                base.exitCode(),
                Map.of("taskId", taskId));
    }

    private static AgentEvent eventWithTurnMetadata(
            String id, int seq, AgentEventKind kind, String text, String turnId) {
        AgentEvent base =
                AgentEpisodeTestSupport.event(
                        id,
                        seq,
                        kind,
                        "2026-05-24T10:1" + seq + ":00Z",
                        text,
                        "Bash",
                        null,
                        AgentEventStatus.SUCCESS,
                        null,
                        null,
                        "npm test payment",
                        0);
        return new AgentEvent(
                base.eventId(),
                base.seq(),
                base.kind(),
                base.occurredAt(),
                base.text(),
                base.toolName(),
                base.input(),
                base.output(),
                base.status(),
                base.durationMs(),
                base.inputTokens(),
                base.outputTokens(),
                base.contentHash(),
                base.path(),
                base.operation(),
                base.command(),
                base.exitCode(),
                Map.of("turnId", turnId));
    }
}
