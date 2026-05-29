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
package com.openmemind.ai.memory.plugin.rawdata.agent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.Memory;
import com.openmemind.ai.memory.core.buffer.InMemoryConversationBuffer;
import com.openmemind.ai.memory.core.buffer.InMemoryInsightBuffer;
import com.openmemind.ai.memory.core.buffer.InMemoryRecentConversationBuffer;
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.builder.ExtractionOptions;
import com.openmemind.ai.memory.core.builder.InsightExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.ItemGraphOptions;
import com.openmemind.ai.memory.core.builder.MemoryBuildOptions;
import com.openmemind.ai.memory.core.builder.PromptBudgetOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.data.MemoryItem;
import com.openmemind.ai.memory.core.data.MemoryRawData;
import com.openmemind.ai.memory.core.data.enums.MemoryCategory;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.insight.scheduler.InsightBuildConfig;
import com.openmemind.ai.memory.core.extraction.item.support.MemoryItemExtractionResponse;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.core.vector.VectorSearchResult;
import com.openmemind.ai.memory.plugin.rawdata.agent.caption.AgentCaptionGenerator;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentRawDataOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.content.AgentTimelineContent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEvent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventKind;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEventStatus;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentProject;
import com.openmemind.ai.memory.plugin.rawdata.agent.plugin.AgentRawDataPlugin;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class AgentExtractionPipelineIntegrationTest {

    private static final int TEST_EMBEDDING_DIMENSION = 8;

    private static final MemoryId MEMORY_ID = DefaultMemoryId.of("user-1", "agent-1");

    @Test
    void successfulTimelineProducesAgentToolItemAndAgentEpisodeRawData() {
        var fixture = fixture(new ScriptedStructuredChatClient(response()));

        ExtractionResult result = extract(fixture, paymentTimeline(paymentEvents()));

        assertThat(result.isSuccess())
                .withFailMessage("status=%s error=%s", result.status(), result.errorMessage())
                .isTrue();
        assertThat(items(fixture))
                .anySatisfy(
                        item -> {
                            assertThat(item.category()).isEqualTo(MemoryCategory.TOOL);
                            assertThat(item.scope()).isEqualTo(MemoryScope.AGENT);
                            assertThat(item.metadata().get("insightTypes"))
                                    .asList()
                                    .containsExactly("tools");
                        });
        assertThat(rawData(fixture))
                .singleElement()
                .satisfies(
                        rawData -> {
                            assertThat(rawData.metadata())
                                    .containsEntry("segmentType", "agent_episode");
                            assertThat(rawData.segment().metadata())
                                    .containsEntry("segmentType", "agent_episode");
                        });
        assertThat(fixture.vector().embed("agent episode caption").block())
                .isNotNull()
                .hasSize(TEST_EMBEDDING_DIMENSION);
        assertThat(fixture.vector().embedAll(List.of("tool", "resolution")).block())
                .isNotNull()
                .hasSize(2)
                .allSatisfy(embedding -> assertThat(embedding).hasSize(TEST_EMBEDDING_DIMENSION));
    }

    @Test
    void failureEditAndSuccessfulValidationProducesResolutionItem() {
        var fixture = fixture(new ScriptedStructuredChatClient(response()));

        extract(fixture, paymentTimeline(paymentEvents()));

        assertThat(items(fixture))
                .anySatisfy(
                        item -> {
                            assertThat(item.category()).isEqualTo(MemoryCategory.RESOLUTION);
                            assertThat(item.scope()).isEqualTo(MemoryScope.AGENT);
                            assertThat(item.content())
                                    .contains(
                                            "rounding mismatch",
                                            "src/payment/calc.ts",
                                            "npm test payment");
                        });
    }

    @Test
    void complexSuccessfulEpisodeCanProducePlaybookFromLlm() {
        var client =
                new ScriptedStructuredChatClient(
                        response(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "When payment tests fail with rounding mismatch, inspect"
                                            + " policy, edit calc.ts, then run npm test payment.",
                                        0.86f,
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
                                        "playbook")));
        var fixture = fixture(client);

        extract(fixture, paymentTimeline(paymentEvents()));

        assertThat(client.structuredCalls()).isEqualTo(2);
        assertThat(client.captionCalls()).isEqualTo(1);
        assertThat(client.itemExtractionCalls()).isEqualTo(1);
        assertThat(items(fixture))
                .anySatisfy(
                        item -> {
                            assertThat(item.category()).isEqualTo(MemoryCategory.PLAYBOOK);
                            assertThat(item.scope()).isEqualTo(MemoryScope.AGENT);
                            assertThat(item.metadata().get("insightTypes"))
                                    .asList()
                                    .containsExactly("playbooks");
                        });
    }

    @Test
    void failedUnresolvedEpisodeDoesNotProducePlaybook() {
        var client =
                new ScriptedStructuredChatClient(
                        response(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "Never accepted because the episode failed",
                                        0.9f,
                                        null,
                                        List.of("playbooks"),
                                        Map.of(
                                                "trigger",
                                                "payment tests fail",
                                                "steps",
                                                List.of("Inspect", "Edit"),
                                                "expectedOutcome",
                                                "tests pass",
                                                "evidenceEventIds",
                                                List.of("e2", "e3")),
                                        "playbook")));
        var fixture = fixture(client);

        extract(fixture, paymentTimeline(failedUnresolvedEvents()));

        assertThat(items(fixture)).noneMatch(item -> item.category() == MemoryCategory.PLAYBOOK);
    }

    @Test
    void agentPipelineAllowsUserAndAgentCategoriesFromTimeline() {
        var client =
                new ScriptedStructuredChatClient(
                        response(
                                new MemoryItemExtractionResponse.ExtractedItem(
                                        "User likes concise test output",
                                        0.9f,
                                        null,
                                        List.of("preferences"),
                                        Map.of("evidenceEventIds", List.of("e3")),
                                        "profile")));
        var fixture = fixture(client);

        extract(fixture, paymentTimeline(paymentEvents()));

        assertThat(items(fixture)).isNotEmpty();
        assertThat(items(fixture))
                .anySatisfy(
                        item -> {
                            assertThat(item.category()).isEqualTo(MemoryCategory.PROFILE);
                            assertThat(item.scope()).isEqualTo(MemoryScope.USER);
                            assertThat(item.metadata().get("insightTypes"))
                                    .asList()
                                    .containsExactly("preferences");
                        });
        assertThat(items(fixture))
                .extracting(MemoryItem::category)
                .contains(MemoryCategory.TOOL, MemoryCategory.RESOLUTION, MemoryCategory.PROFILE);
    }

    @Test
    void exactDuplicateTimelineWindowDoesNotDuplicateDurableItems() {
        var fixture = fixture(new ScriptedStructuredChatClient(response()));
        AgentTimelineContent timeline = paymentTimeline(paymentEvents());

        extract(fixture, timeline);
        extract(fixture, timeline);

        assertThat(items(fixture))
                .extracting(MemoryItem::category)
                .containsExactlyInAnyOrder(MemoryCategory.TOOL, MemoryCategory.RESOLUTION);
        assertThat(rawData(fixture)).hasSize(1);
    }

    @Test
    void redactionRunsBeforeRawDataPersistence() {
        var fixture = fixture(new ScriptedStructuredChatClient(response()));

        extract(fixture, paymentTimeline(secretEvents()));

        assertThat(rawData(fixture))
                .singleElement()
                .satisfies(
                        rawData -> {
                            assertThat(rawData.segment().content())
                                    .doesNotContain("sk-live-1234567890abcdef");
                            assertThat(rawData.segment().content()).contains("[REDACTED");
                        });
    }

    private static ExtractionResult extract(Fixture fixture, AgentTimelineContent timeline) {
        return fixture.memory()
                .extract(ExtractionRequest.of(MEMORY_ID, timeline).withConfig(agentConfig()))
                .block();
    }

    private static ExtractionConfig agentConfig() {
        return ExtractionConfig.agentOnly().withEnableInsight(false);
    }

    private static Fixture fixture(StructuredChatClient client) {
        var store = new InMemoryMemoryStore();
        var vector = new RecordingMemoryVector();
        var memory =
                Memory.builder()
                        .chatClient(client)
                        .store(store)
                        .buffer(
                                MemoryBuffer.of(
                                        new InMemoryInsightBuffer(),
                                        new InMemoryConversationBuffer(),
                                        new InMemoryRecentConversationBuffer()))
                        .vector(vector)
                        .rawDataPlugin(new AgentRawDataPlugin(AgentRawDataOptions.defaults()))
                        .options(memoryOptions())
                        .build();
        return new Fixture(memory, store, vector);
    }

    private static MemoryBuildOptions memoryOptions() {
        return MemoryBuildOptions.builder()
                .extraction(
                        new ExtractionOptions(
                                com.openmemind.ai.memory.core.builder.ExtractionCommonOptions
                                        .defaults(),
                                RawDataExtractionOptions.defaults(),
                                new ItemExtractionOptions(
                                        false,
                                        PromptBudgetOptions.defaults(),
                                        ItemGraphOptions.defaults().withEnabled(false)),
                                new InsightExtractionOptions(
                                        false, new InsightBuildConfig(100, 100, 100, 100))))
                .build();
    }

    private static List<MemoryItem> items(Fixture fixture) {
        return fixture.store().itemOperations().listItems(MEMORY_ID);
    }

    private static List<MemoryRawData> rawData(Fixture fixture) {
        return fixture.store().rawDataOperations().listRawData(MEMORY_ID);
    }

    private static MemoryItemExtractionResponse response(
            MemoryItemExtractionResponse.ExtractedItem... items) {
        return new MemoryItemExtractionResponse(List.of(items));
    }

    private static AgentTimelineContent paymentTimeline(List<AgentEvent> events) {
        return new AgentTimelineContent(
                "codex",
                "1.0",
                "session-123",
                "session-123-agent-turn-1-5",
                "timeline-123",
                new AgentProject("payments-api", "/Users/alice/work/payments-api", null, Map.of()),
                events);
    }

    private static List<AgentEvent> paymentEvents() {
        return List.of(
                event(
                        "e1",
                        1,
                        AgentEventKind.USER_PROMPT,
                        "Fix payment tests",
                        null,
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
                        null,
                        "Bash",
                        "rounding mismatch",
                        AgentEventStatus.FAILED,
                        null,
                        null,
                        "npm test payment",
                        1,
                        Map.of("failureSignal", "rounding mismatch")),
                event(
                        "e3",
                        3,
                        AgentEventKind.FILE_EDIT,
                        null,
                        "Edit",
                        "changed rounding logic",
                        AgentEventStatus.SUCCESS,
                        "src/payment/calc.ts",
                        "edit",
                        null,
                        null,
                        null),
                event(
                        "e4",
                        4,
                        AgentEventKind.COMMAND,
                        null,
                        "Bash",
                        "passed",
                        AgentEventStatus.SUCCESS,
                        null,
                        null,
                        "npm test payment",
                        0,
                        null),
                event(
                        "e5",
                        5,
                        AgentEventKind.STOP,
                        "done",
                        null,
                        null,
                        AgentEventStatus.SUCCESS,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    private static List<AgentEvent> failedUnresolvedEvents() {
        return List.of(
                event(
                        "e1",
                        1,
                        AgentEventKind.USER_PROMPT,
                        "Fix payment tests",
                        null,
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
                        null,
                        "Bash",
                        "rounding mismatch",
                        AgentEventStatus.FAILED,
                        null,
                        null,
                        "npm test payment",
                        1,
                        Map.of("failureSignal", "rounding mismatch")),
                event(
                        "e3",
                        3,
                        AgentEventKind.FILE_EDIT,
                        null,
                        "Edit",
                        "partial change",
                        AgentEventStatus.SUCCESS,
                        "src/payment/calc.ts",
                        "edit",
                        null,
                        null,
                        null));
    }

    private static List<AgentEvent> secretEvents() {
        return List.of(
                event(
                        "e1",
                        1,
                        AgentEventKind.USER_PROMPT,
                        "Run deployment validation",
                        null,
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
                        null,
                        "Bash",
                        "Authorization: Bearer sk-live-1234567890abcdef",
                        AgentEventStatus.SUCCESS,
                        null,
                        null,
                        "curl https://api.example.test",
                        0,
                        null),
                event(
                        "e3",
                        3,
                        AgentEventKind.STOP,
                        "done",
                        null,
                        null,
                        AgentEventStatus.SUCCESS,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    private static AgentEvent event(
            String id,
            int seq,
            AgentEventKind kind,
            String text,
            String toolName,
            String output,
            AgentEventStatus status,
            String path,
            String operation,
            String command,
            Integer exitCode,
            Map<String, Object> metadata) {
        return new AgentEvent(
                id,
                seq,
                kind,
                Instant.parse("2026-05-24T10:00:00Z").plusSeconds(seq * 60L),
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
                metadata == null ? Map.of() : metadata);
    }

    private record Fixture(Memory memory, MemoryStore store, RecordingMemoryVector vector) {}

    private static final class ScriptedStructuredChatClient implements StructuredChatClient {

        private final MemoryItemExtractionResponse response;
        private final AgentCaptionGenerator.AgentCaptionResponse captionResponse;
        private int structuredCalls;
        private int captionCalls;
        private int itemExtractionCalls;

        private ScriptedStructuredChatClient(MemoryItemExtractionResponse response) {
            this.response = response;
            this.captionResponse =
                    new AgentCaptionGenerator.AgentCaptionResponse(
                            "Fix payment tests",
                            "The turn investigated payment test failures, changed payment"
                                    + " calculation logic, and validated the fix.",
                            "success",
                            List.of(
                                    "Ran npm test payment and captured the rounding mismatch.",
                                    "Edited src/payment/calc.ts.",
                                    "Reran npm test payment successfully."),
                            List.of(
                                    "Command: npm test payment",
                                    "File: src/payment/calc.ts",
                                    "Validation: npm test payment passed"),
                            "");
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("not used by this test"));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            structuredCalls++;
            if (responseType == AgentCaptionGenerator.AgentCaptionResponse.class) {
                captionCalls++;
                return Mono.just((T) captionResponse);
            }
            if (responseType == MemoryItemExtractionResponse.class) {
                itemExtractionCalls++;
                return Mono.just((T) response);
            }
            return Mono.empty();
        }

        private int structuredCalls() {
            return structuredCalls;
        }

        private int captionCalls() {
            return captionCalls;
        }

        private int itemExtractionCalls() {
            return itemExtractionCalls;
        }
    }

    private static final class RecordingMemoryVector implements MemoryVector {

        private final AtomicInteger sequence = new AtomicInteger();
        private final List<String> storedTexts = new ArrayList<>();

        @Override
        public Mono<String> store(MemoryId memoryId, String text, Map<String, Object> metadata) {
            storedTexts.add(text);
            return Mono.just("vec-" + sequence.getAndIncrement());
        }

        @Override
        public Mono<List<String>> storeBatch(
                MemoryId memoryId, List<String> texts, List<Map<String, Object>> metadataList) {
            storedTexts.addAll(texts);
            return Mono.just(
                    IntStream.range(0, texts.size())
                            .mapToObj(i -> "vec-" + sequence.getAndIncrement())
                            .toList());
        }

        @Override
        public Mono<Void> delete(MemoryId memoryId, String vectorId) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteBatch(MemoryId memoryId, List<String> vectorIds) {
            return Mono.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(MemoryId memoryId, String query, int topK) {
            return Flux.empty();
        }

        @Override
        public Flux<VectorSearchResult> search(
                MemoryId memoryId, String query, int topK, Map<String, Object> filter) {
            return Flux.empty();
        }

        @Override
        public Mono<List<Float>> embed(String text) {
            return Mono.just(testEmbedding(text));
        }

        @Override
        public Mono<List<List<Float>>> embedAll(List<String> texts) {
            return Mono.just(texts.stream().map(RecordingMemoryVector::testEmbedding).toList());
        }

        private static List<Float> testEmbedding(String text) {
            int seed = text == null ? 0 : text.hashCode();
            return IntStream.range(0, TEST_EMBEDDING_DIMENSION)
                    .mapToObj(i -> ((seed + i * 31) & 0xff) / 255.0f)
                    .toList();
        }
    }
}
