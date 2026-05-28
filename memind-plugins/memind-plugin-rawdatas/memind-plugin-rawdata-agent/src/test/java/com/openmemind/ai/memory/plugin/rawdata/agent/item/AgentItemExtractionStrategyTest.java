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
package com.openmemind.ai.memory.plugin.rawdata.agent.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.DefaultInsightTypes;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryItemType;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedGraphHints;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.plugin.rawdata.agent.content.AgentTimelineContent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentItemExtractionStrategyTest {

    private final AgentItemExtractionStrategy strategy = new AgentItemExtractionStrategy();

    @Test
    void shouldExtractDeterministicToolAndResolutionFromSuccessfulEpisode() {
        List<ExtractedMemoryEntry> entries =
                strategy.extract(
                                List.of(successfulEpisode()),
                                DefaultInsightTypes.all(),
                                agentConfig())
                        .block();

        assertThat(entries)
                .anySatisfy(
                        entry -> {
                            assertThat(entry.category()).isEqualTo("tool");
                            assertThat(entry.type()).isEqualTo(MemoryItemType.FACT);
                            assertThat(entry.insightTypes()).containsExactly("tools");
                            assertThat(entry.metadata()).containsEntry("episodeId", "episode-123");
                            assertThat(entry.metadata())
                                    .containsEntry("projectId", "payments-api-remote")
                                    .containsEntry("projectSlug", "payments-api-remote")
                                    .containsEntry("projectName", "payments-api");
                            assertThat(entry.metadata())
                                    .containsEntry("command", "npm test payment");
                            assertThat(entry.metadata())
                                    .containsKeys("toolStats", "toolRecords", "toolGroups")
                                    .containsEntry("successCount", 1)
                                    .containsEntry("failCount", 1);
                            assertThat(entry.content())
                                    .contains("npm test payment")
                                    .contains("failed once")
                                    .contains("passed once")
                                    .contains("src/payment/calc.ts");
                            assertThat(entry.graphHints().entities())
                                    .extracting(ExtractedGraphHints.ExtractedEntityHint::name)
                                    .contains("Bash", "npm test payment", "src/payment/calc.ts");
                        });
        assertThat(entries)
                .anySatisfy(
                        entry -> {
                            assertThat(entry.category()).isEqualTo("resolution");
                            assertThat(entry.insightTypes()).containsExactly("resolutions");
                            assertThat(entry.metadata()).containsKey("evidenceEventIds");
                            assertThat(entry.metadata().get("evidenceEventIds"))
                                    .asList()
                                    .containsExactly("e2", "e3", "e4");
                            assertThat(entry.content())
                                    .contains(
                                            "rounding mismatch",
                                            "src/payment/calc.ts",
                                            "npm test payment");
                            assertThat(entry.graphHints().entities())
                                    .extracting(ExtractedGraphHints.ExtractedEntityHint::entityType)
                                    .contains("object", "concept");
                        });
    }

    @Test
    void shouldNotExtractResolutionOrPlaybookFromFailedUnresolvedEpisode() {
        List<ExtractedMemoryEntry> entries =
                strategy.extract(List.of(failedEpisode()), DefaultInsightTypes.all(), agentConfig())
                        .block();

        assertThat(entries).anyMatch(entry -> "tool".equals(entry.category()));
        assertThat(entries).noneMatch(entry -> "playbook".equals(entry.category()));
        assertThat(entries).noneMatch(entry -> "resolution".equals(entry.category()));
    }

    @Test
    void shouldIgnoreUnrelatedSuccessfulCommandsForResolutionValidation() {
        ParsedSegment segment =
                segment(
                        Map.ofEntries(
                                Map.entry("segmentType", "agent_episode"),
                                Map.entry("episodeId", "episode-123"),
                                Map.entry("sourceClient", "codex"),
                                Map.entry("sessionId", "session-123"),
                                Map.entry("timelineId", "timeline-123"),
                                Map.entry("outcome", "success"),
                                Map.entry("files", List.of("src/payment/calc.ts")),
                                Map.entry("commands", List.of("npm test payment", "git status")),
                                Map.entry("toolNames", List.of("Bash", "Edit")),
                                Map.entry("failureSignals", List.of("rounding mismatch")),
                                Map.entry("eventIds", List.of("e1", "e2", "e3", "e4")),
                                Map.entry(
                                        "commandEvents",
                                        List.of(
                                                commandEvent(
                                                        "e2",
                                                        2,
                                                        "npm test payment",
                                                        "failed",
                                                        "rounding mismatch"),
                                                commandEvent(
                                                        "e4",
                                                        4,
                                                        "git status",
                                                        "success",
                                                        "clean")))));

        List<ExtractedMemoryEntry> entries =
                strategy.extract(List.of(segment), DefaultInsightTypes.all(), agentConfig())
                        .block();

        assertThat(entries).noneMatch(entry -> "resolution".equals(entry.category()));
    }

    @Test
    void shouldUseLaterMatchingValidationAndIntermediateFileEvidenceForResolution() {
        ParsedSegment segment =
                segment(
                        Map.ofEntries(
                                Map.entry("segmentType", "agent_episode"),
                                Map.entry("episodeId", "episode-resolution"),
                                Map.entry("sourceClient", "claude-code"),
                                Map.entry("sessionId", "session-123"),
                                Map.entry("timelineId", "timeline-123"),
                                Map.entry("outcome", "success"),
                                Map.entry("files", List.of("src/payment/calc.ts")),
                                Map.entry("commands", List.of("npm test payment")),
                                Map.entry("toolNames", List.of("Bash", "Edit")),
                                Map.entry("failureSignals", List.of("payment rounding mismatch")),
                                Map.entry(
                                        "eventIds",
                                        List.of(
                                                "prompt",
                                                "failed-test",
                                                "early-pass",
                                                "edit-calc",
                                                "passed-test",
                                                "outside-edit")),
                                Map.entry(
                                        "commandEvents",
                                        List.of(
                                                commandEvent(
                                                        "early-pass",
                                                        2,
                                                        "npm test payment",
                                                        "success",
                                                        "passed before failure"),
                                                commandEvent(
                                                        "failed-test",
                                                        3,
                                                        "npm test payment",
                                                        "failed",
                                                        "payment rounding mismatch"),
                                                commandEvent(
                                                        "passed-test",
                                                        8,
                                                        "npm test payment",
                                                        "success",
                                                        "passed"))),
                                Map.entry(
                                        "fileEvents",
                                        List.of(
                                                fileEvent("edit-calc", 5, "src/payment/calc.ts"),
                                                fileEvent("outside-edit", 9, "README.md")))));

        List<ExtractedMemoryEntry> entries =
                strategy.extract(List.of(segment), DefaultInsightTypes.all(), agentConfig())
                        .block();

        assertThat(entries)
                .filteredOn(entry -> "resolution".equals(entry.category()))
                .singleElement()
                .satisfies(
                        resolution -> {
                            assertThat(resolution.metadata())
                                    .containsEntry("validatedBy", "npm test payment");
                            assertThat(resolution.metadata().get("evidenceEventIds"))
                                    .asList()
                                    .containsExactly("failed-test", "edit-calc", "passed-test");
                        });
    }

    @Test
    void shouldNotCreateDeterministicItemFromWeakNotificationOnlyEpisode() {
        ParsedSegment segment =
                segment(
                        "Claude is waiting for input.",
                        Map.of(
                                "segmentType",
                                "agent_episode",
                                "episodeId",
                                "episode-notification",
                                "eventIds",
                                List.of("notice-1"),
                                "files",
                                List.of(),
                                "commands",
                                List.of(),
                                "toolNames",
                                List.of(),
                                "failureSignals",
                                List.of(),
                                "outcome",
                                "unknown"));

        List<ExtractedMemoryEntry> entries =
                strategy.extract(List.of(segment), DefaultInsightTypes.all(), agentConfig())
                        .block();

        assertThat(entries).isEmpty();
    }

    @Test
    void shouldProduceStableCanonicalContentForDuplicateExtraction() {
        var first =
                strategy.extract(
                                List.of(successfulEpisode()),
                                DefaultInsightTypes.all(),
                                agentConfig())
                        .block();
        var second =
                strategy.extract(
                                List.of(successfulEpisode()),
                                DefaultInsightTypes.all(),
                                agentConfig())
                        .block();

        assertThat(first).isNotEmpty();
        assertThat(first.getFirst().content()).isEqualTo(second.getFirst().content());
    }

    private static ParsedSegment successfulEpisode() {
        return segment(
                Map.ofEntries(
                        Map.entry("segmentType", "agent_episode"),
                        Map.entry("episodeId", "episode-123"),
                        Map.entry("sourceClient", "codex"),
                        Map.entry("sessionId", "session-123"),
                        Map.entry("timelineId", "timeline-123"),
                        Map.entry("projectId", "payments-api-remote"),
                        Map.entry("projectSlug", "payments-api-remote"),
                        Map.entry("projectName", "payments-api"),
                        Map.entry("outcome", "success"),
                        Map.entry("files", List.of("src/payment/calc.ts")),
                        Map.entry("commands", List.of("npm test payment")),
                        Map.entry("toolNames", List.of("Bash", "Edit")),
                        Map.entry("failureSignals", List.of("rounding mismatch")),
                        Map.entry("eventIds", List.of("e1", "e2", "e3", "e4", "e5")),
                        Map.entry(
                                "commandEvents",
                                List.of(
                                        commandEvent(
                                                "e2",
                                                2,
                                                "npm test payment",
                                                "failed",
                                                "rounding mismatch"),
                                        commandEvent(
                                                "e4", 4, "npm test payment", "success", "passed"))),
                        Map.entry("fileEvents", List.of(fileEvent("e3", 3, "src/payment/calc.ts"))),
                        Map.entry(
                                "toolStats",
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
                                                10L))),
                        Map.entry(
                                "toolRecords",
                                List.of(
                                        Map.of(
                                                "eventId",
                                                "e2",
                                                "seq",
                                                2,
                                                "toolName",
                                                "Bash",
                                                "status",
                                                "failed",
                                                "command",
                                                "npm test payment",
                                                "outputPreview",
                                                "rounding mismatch"),
                                        Map.of(
                                                "eventId",
                                                "e4",
                                                "seq",
                                                4,
                                                "toolName",
                                                "Bash",
                                                "status",
                                                "success",
                                                "command",
                                                "npm test payment",
                                                "outputPreview",
                                                "passed"))),
                        Map.entry(
                                "toolGroups",
                                List.of(
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
                                                List.of("npm test payment"))))));
    }

    private static ParsedSegment failedEpisode() {
        return segment(
                Map.ofEntries(
                        Map.entry("segmentType", "agent_episode"),
                        Map.entry("episodeId", "episode-456"),
                        Map.entry("sourceClient", "codex"),
                        Map.entry("sessionId", "session-123"),
                        Map.entry("timelineId", "timeline-123"),
                        Map.entry("outcome", "failed"),
                        Map.entry("files", List.of("src/payment/calc.ts")),
                        Map.entry("commands", List.of("npm test payment")),
                        Map.entry("toolNames", List.of("Bash")),
                        Map.entry("failureSignals", List.of("rounding mismatch")),
                        Map.entry("eventIds", List.of("e1", "e2")),
                        Map.entry(
                                "commandEvents",
                                List.of(
                                        commandEvent(
                                                "e2",
                                                2,
                                                "npm test payment",
                                                "failed",
                                                "rounding mismatch")))));
    }

    private static Map<String, Object> commandEvent(
            String eventId, int seq, String command, String status, String output) {
        return Map.of(
                "eventId", eventId, "seq", seq, "command", command, "status", status, "output",
                output);
    }

    private static Map<String, Object> fileEvent(String eventId, int seq, String path) {
        return Map.of("eventId", eventId, "seq", seq, "path", path, "operation", "edit");
    }

    private static ParsedSegment segment(Map<String, Object> metadata) {
        return segment("Goal: Fix payment tests", metadata);
    }

    private static ParsedSegment segment(String text, Map<String, Object> metadata) {
        return new ParsedSegment(
                text,
                null,
                0,
                23,
                "raw-123",
                metadata,
                new SegmentRuntimeContext(
                        Instant.parse("2026-05-24T10:00:00Z"),
                        Instant.parse("2026-05-24T10:04:00Z"),
                        null,
                        "codex"));
    }

    private static ItemExtractionConfig agentConfig() {
        return new ItemExtractionConfig(
                MemoryScope.AGENT,
                AgentTimelineContent.TYPE,
                MemoryCategory.agentCategories(),
                false,
                "en");
    }
}
