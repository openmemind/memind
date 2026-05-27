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
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.item.ItemExtractionConfig;
import com.openmemind.ai.memory.core.extraction.item.support.ExtractedMemoryEntry;
import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.content.AgentTimelineContent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AgentItemExtractionStrategyLlmTest {

    @Test
    void shouldMergeValidLlmPlaybookWithDeterministicMetadataAndGraphHints() {
        var client =
                new StubStructuredChatClient(
                        response(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "When payment tests fail with rounding mismatch, inspect"
                                            + " policy, edit calc.ts, then run npm test payment.",
                                        0.86f,
                                        null,
                                        null,
                                        List.of("playbooks"),
                                        Map.of(
                                                "trigger",
                                                "payment tests fail with rounding mismatch",
                                                "steps",
                                                List.of(
                                                        "Inspect policy",
                                                        "Edit calc.ts",
                                                        "Run npm test payment"),
                                                "expectedOutcome",
                                                "payment tests pass",
                                                "evidenceEventIds",
                                                List.of("e3", "e4", "e5")),
                                        "playbook",
                                        List.of(
                                                new MemoryItemExtractionResponse.ExtractedEntity(
                                                        "src/payment/calc.ts", "object", 0.9f),
                                                new MemoryItemExtractionResponse.ExtractedEntity(
                                                        "rounding mismatch", "concept", 0.8f)),
                                        List.of())));
        AgentItemExtractionStrategy strategy = strategy(client);

        List<ExtractedMemoryEntry> entries =
                strategy.extract(
                                List.of(successfulEpisode()),
                                DefaultInsightTypes.all(),
                                agentConfig())
                        .block();

        assertThat(client.calls()).isEqualTo(1);
        assertThat(client.lastMessages())
                .anySatisfy(
                        message ->
                                assertThat(message.content())
                                        .contains(
                                                "Categories are limited to",
                                                "evidenceEventIds",
                                                "caused_by",
                                                "enabled_by",
                                                "motivated_by"));
        assertThat(entries)
                .anySatisfy(
                        entry -> {
                            assertThat(entry.category()).isEqualTo("playbook");
                            assertThat(entry.insightTypes()).containsExactly("playbooks");
                            assertThat(entry.metadata())
                                    .containsEntry("episodeId", "episode-123")
                                    .containsEntry("sessionId", "session-123")
                                    .containsEntry("timelineId", "timeline-123")
                                    .containsEntry("sourceClient", "codex")
                                    .containsEntry("projectId", "payments-api-remote")
                                    .containsEntry("projectSlug", "payments-api-remote")
                                    .containsEntry("projectName", "payments-api");
                            assertThat(entry.metadata().get("evidenceEventIds"))
                                    .asList()
                                    .containsExactly("e3", "e4", "e5");
                            assertThat(entry.graphHints().entities())
                                    .extracting(
                                            com.openmemind.ai.memory.core.extraction.item.support
                                                            .ExtractedGraphHints.ExtractedEntityHint
                                                    ::name)
                                    .contains("src/payment/calc.ts", "rounding mismatch");
                        });
    }

    @Test
    void shouldDropInvalidLlmItems() {
        var client =
                new StubStructuredChatClient(
                        response(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "Too thin playbook",
                                        0.9f,
                                        null,
                                        List.of("playbooks"),
                                        Map.of(
                                                "trigger",
                                                "payment tests fail",
                                                "steps",
                                                List.of("Run tests"),
                                                "expectedOutcome",
                                                "tests pass",
                                                "evidenceEventIds",
                                                List.of("e3")),
                                        "playbook"),
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "Problem only",
                                        0.9f,
                                        null,
                                        List.of("resolutions"),
                                        Map.of(
                                                "problem",
                                                "rounding mismatch",
                                                "evidenceEventIds",
                                                List.of("e3")),
                                        "resolution"),
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "User likes test output",
                                        0.9f,
                                        null,
                                        List.of("preferences"),
                                        Map.of("evidenceEventIds", List.of("e3")),
                                        "profile"),
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "Evidence outside this episode",
                                        0.9f,
                                        null,
                                        List.of("directives"),
                                        Map.of("evidenceEventIds", List.of("missing")),
                                        "directive")));
        AgentItemExtractionStrategy strategy = strategy(client);

        List<ExtractedMemoryEntry> entries =
                strategy.extract(
                                List.of(successfulEpisode()),
                                DefaultInsightTypes.all(),
                                agentConfig())
                        .block();

        assertThat(entries)
                .noneMatch(
                        entry ->
                                "playbook".equals(entry.category())
                                        || "directive".equals(entry.category()));
        assertThat(entries.stream().filter(entry -> "resolution".equals(entry.category())).count())
                .isEqualTo(1);
    }

    @Test
    void shouldAllowUserScopeMemoryCategoriesFromAgentTimeline() {
        var client =
                new StubStructuredChatClient(
                        response(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "User is currently refining Memind rawdata-agent"
                                                + " extraction.",
                                        0.88f,
                                        null,
                                        List.of("experiences"),
                                        Map.of("evidenceEventIds", List.of("e1")),
                                        "event")));
        AgentItemExtractionStrategy strategy = strategy(client);

        List<ExtractedMemoryEntry> entries =
                strategy.extract(
                                List.of(successfulEpisode()),
                                DefaultInsightTypes.all(),
                                allScopeConfig())
                        .block();

        assertThat(client.calls()).isEqualTo(1);
        assertThat(client.lastMessages())
                .anySatisfy(
                        message ->
                                assertThat(message.content())
                                        .contains("event")
                                        .contains("profile: stable facts")
                                        .contains("event: time-bound user/project situations"));
        assertThat(entries)
                .anySatisfy(
                        entry -> {
                            assertThat(entry.category()).isEqualTo("event");
                            assertThat(entry.insightTypes()).containsExactly("experiences");
                            assertThat(entry.metadata().get("evidenceEventIds"))
                                    .asList()
                                    .containsExactly("e1");
                        });
    }

    @Test
    void shouldAcceptSubagentBackedPlaybookWhenEvidenceBelongsToEpisode() {
        var client =
                new StubStructuredChatClient(
                        response(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "When payment test failures are unclear, ask an explorer"
                                                + " subagent to inspect the failing resolver before"
                                                + " editing calc.ts.",
                                        0.86f,
                                        null,
                                        null,
                                        List.of("playbooks"),
                                        Map.of(
                                                "trigger",
                                                "payment test failures are unclear",
                                                "steps",
                                                List.of(
                                                        "Ask explorer subagent to inspect resolver",
                                                        "Use the finding before editing calc.ts"),
                                                "expectedOutcome",
                                                "edits are based on the diagnosed resolver issue",
                                                "evidenceEventIds",
                                                List.of("subagent-1")),
                                        "playbook")));
        AgentItemExtractionStrategy strategy = strategy(client);

        List<ExtractedMemoryEntry> entries =
                strategy.extract(
                                List.of(subagentEpisode()),
                                DefaultInsightTypes.all(),
                                agentConfig())
                        .block();

        assertThat(entries)
                .filteredOn(entry -> "playbook".equals(entry.category()))
                .singleElement()
                .satisfies(
                        entry ->
                                assertThat(entry.metadata().get("evidenceEventIds"))
                                        .asList()
                                        .containsExactly("subagent-1"));
    }

    @Test
    void shouldRejectSubagentPlaybookWhenEvidenceIsOutsideEpisode() {
        var client =
                new StubStructuredChatClient(
                        response(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "When payment test failures are unclear, ask an explorer"
                                                + " subagent first.",
                                        0.86f,
                                        null,
                                        null,
                                        List.of("playbooks"),
                                        Map.of(
                                                "trigger",
                                                "payment test failures are unclear",
                                                "steps",
                                                List.of(
                                                        "Ask explorer subagent",
                                                        "Use the finding before editing"),
                                                "expectedOutcome",
                                                "edits are based on diagnosis",
                                                "evidenceEventIds",
                                                List.of("outside-event")),
                                        "playbook")));
        AgentItemExtractionStrategy strategy = strategy(client);

        List<ExtractedMemoryEntry> entries =
                strategy.extract(
                                List.of(subagentEpisode()),
                                DefaultInsightTypes.all(),
                                agentConfig())
                        .block();

        assertThat(entries).noneMatch(entry -> "playbook".equals(entry.category()));
    }

    @Test
    void shouldSkipLlmWhenEpisodeDoesNotMeetMinimumEventThreshold() {
        var client =
                new StubStructuredChatClient(
                        response(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "Never called",
                                        0.9f,
                                        null,
                                        List.of("directives"),
                                        Map.of("evidenceEventIds", List.of("e1")),
                                        "directive")));
        AgentItemExtractionStrategy strategy = strategy(client);

        List<ExtractedMemoryEntry> entries =
                strategy.extract(List.of(shortEpisode()), DefaultInsightTypes.all(), agentConfig())
                        .block();

        assertThat(client.calls()).isZero();
        assertThat(entries).anyMatch(entry -> "tool".equals(entry.category()));
    }

    private static AgentItemExtractionStrategy strategy(StructuredChatClient client) {
        return new AgentItemExtractionStrategy(
                client,
                PromptRegistry.EMPTY,
                AgentExtractionOptions.defaults(),
                new AgentMemoryItemFactory());
    }

    private static MemoryItemExtractionResponse response(
            MemoryItemExtractionResponse.ExtractedItem... items) {
        return new MemoryItemExtractionResponse(List.of(items));
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
                        Map.entry(
                                "fileEvents", List.of(fileEvent("e3", 3, "src/payment/calc.ts")))));
    }

    private static ParsedSegment subagentEpisode() {
        return segment(
                Map.ofEntries(
                        Map.entry("segmentType", "agent_episode"),
                        Map.entry("episodeId", "episode-subagent"),
                        Map.entry("sourceClient", "claude-code"),
                        Map.entry("sessionId", "session-123"),
                        Map.entry("timelineId", "timeline-123"),
                        Map.entry("outcome", "success"),
                        Map.entry("files", List.of("src/payment/calc.ts")),
                        Map.entry("commands", List.of("npm test payment")),
                        Map.entry("toolNames", List.of("Task")),
                        Map.entry("failureSignals", List.of()),
                        Map.entry("eventIds", List.of("prompt-1", "subagent-1", "stop-1"))));
    }

    private static ParsedSegment shortEpisode() {
        return segment(
                Map.ofEntries(
                        Map.entry("segmentType", "agent_episode"),
                        Map.entry("episodeId", "episode-short"),
                        Map.entry("sourceClient", "codex"),
                        Map.entry("sessionId", "session-123"),
                        Map.entry("timelineId", "timeline-123"),
                        Map.entry("outcome", "success"),
                        Map.entry("commands", List.of("npm test payment")),
                        Map.entry("toolNames", List.of("Bash")),
                        Map.entry("eventIds", List.of("e1", "e2")),
                        Map.entry(
                                "commandEvents",
                                List.of(
                                        commandEvent(
                                                "e2",
                                                2,
                                                "npm test payment",
                                                "success",
                                                "passed")))));
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

    private static Map<String, Object> commandEvent(
            String eventId, int seq, String command, String status, String output) {
        return Map.of(
                "eventId", eventId, "seq", seq, "command", command, "status", status, "output",
                output);
    }

    private static Map<String, Object> fileEvent(String eventId, int seq, String path) {
        return Map.of("eventId", eventId, "seq", seq, "path", path, "operation", "edit");
    }

    private static ItemExtractionConfig agentConfig() {
        return new ItemExtractionConfig(
                MemoryScope.AGENT,
                AgentTimelineContent.TYPE,
                MemoryCategory.agentCategories(),
                false,
                "en");
    }

    private static ItemExtractionConfig allScopeConfig() {
        return new ItemExtractionConfig(
                MemoryScope.USER,
                AgentTimelineContent.TYPE,
                java.util.EnumSet.allOf(MemoryCategory.class),
                false,
                "en");
    }

    private static final class StubStructuredChatClient implements StructuredChatClient {

        private final MemoryItemExtractionResponse response;
        private int calls;
        private List<ChatMessage> lastMessages = List.of();

        private StubStructuredChatClient(MemoryItemExtractionResponse response) {
            this.response = response;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("not used by this test"));
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            calls++;
            lastMessages = List.copyOf(messages);
            return Mono.just(responseType.cast(response));
        }

        int calls() {
            return calls;
        }

        List<ChatMessage> lastMessages() {
            return lastMessages;
        }
    }
}
