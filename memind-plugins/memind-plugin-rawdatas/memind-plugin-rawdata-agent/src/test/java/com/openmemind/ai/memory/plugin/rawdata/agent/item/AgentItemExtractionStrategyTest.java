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
                                    .containsEntry("command", "npm test payment");
                            assertThat(entry.metadata()).containsEntry("successCount", 1);
                            assertThat(entry.metadata()).containsEntry("failCount", 1);
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
                        Map.entry(
                                "fileEvents", List.of(fileEvent("e3", 3, "src/payment/calc.ts")))));
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
        return new ParsedSegment(
                "Goal: Fix payment tests",
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
