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
package com.openmemind.ai.memory.core.extraction.insight.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("LlmInsightGenerator Unit Test")
class LlmInsightGeneratorTest {

    @Test
    @DisplayName("generateLeafPointOps should call structured client with ops response type")
    void generateLeafPointOpsCallsStructuredClientWithOpsResponseType() {
        var client = new FakeStructuredChatClient(new InsightPointOpsResponse(List.of()));
        var generator = new LlmInsightGenerator(client);

        StepVerifier.create(
                        generator.generateLeafPointOps(
                                rootInsightType("profile"),
                                "group",
                                List.of(),
                                List.of(),
                                300,
                                null,
                                "English"))
                .expectNextMatches(response -> response.operations().isEmpty())
                .verifyComplete();

        assertThat(client.lastResponseType()).isEqualTo(InsightPointOpsResponse.class);
    }

    @Test
    @DisplayName("generatePoints should keep full rewrite prompt contract")
    void generatePointsShouldKeepFullRewritePromptContract() {
        var client = new FakeStructuredChatClient(new InsightPointGenerateResponse(List.of()));
        var generator = new LlmInsightGenerator(client);

        StepVerifier.create(
                        generator.generatePoints(
                                rootInsightType("profile"),
                                "group",
                                List.of(),
                                List.of(),
                                300,
                                null,
                                "English"))
                .expectNextMatches(response -> response.points().isEmpty())
                .verifyComplete();

        assertThat(client.lastResponseType()).isEqualTo(InsightPointGenerateResponse.class);
        assertThat(client.lastMessages().getFirst().content())
                .contains("\"points\"")
                .doesNotContain("\"operations\"");
    }

    @Test
    @DisplayName("generateLeafPointOps should not convert missing structured response into noop")
    void generateLeafPointOpsDoesNotConvertMissingStructuredResponseIntoEmptyOperations() {
        var client = new FakeStructuredChatClient(null);
        var generator = new LlmInsightGenerator(client);

        StepVerifier.create(
                        generator.generateLeafPointOps(
                                rootInsightType("profile"),
                                "group",
                                List.of(),
                                List.of(),
                                300,
                                null,
                                "English"))
                .verifyComplete();
    }

    @Test
    @DisplayName("generateBranchPointOps should call structured client with ops response type")
    void generateBranchPointOpsCallsStructuredClientWithOpsResponseType() {
        var client = new FakeStructuredChatClient(new InsightPointOpsResponse(List.of()));
        var generator = new LlmInsightGenerator(client);

        StepVerifier.create(
                        generator.generateBranchPointOps(
                                rootInsightType("profile"), List.of(), List.of(), 300, "English"))
                .expectNextMatches(response -> response.operations().isEmpty())
                .verifyComplete();

        assertThat(client.lastResponseType()).isEqualTo(InsightPointOpsResponse.class);
    }

    @Test
    @DisplayName("generateBranchSummary should keep full rewrite prompt contract")
    void generateBranchSummaryShouldKeepFullRewritePromptContract() {
        var client = new FakeStructuredChatClient(new InsightPointGenerateResponse(List.of()));
        var generator = new LlmInsightGenerator(client);

        StepVerifier.create(
                        generator.generateBranchSummary(
                                rootInsightType("profile"), List.of(), List.of(), 300, "English"))
                .expectNextMatches(response -> response.points().isEmpty())
                .verifyComplete();

        assertThat(client.lastResponseType()).isEqualTo(InsightPointGenerateResponse.class);
        assertThat(client.lastMessages().getFirst().content())
                .contains("\"points\"")
                .doesNotContain("\"operations\"");
    }

    @Test
    @DisplayName("generateLeafPointOps should use point-op prompt override")
    void generateLeafPointOpsShouldUsePointOpsOverrideInstruction() {
        var client = new FakeStructuredChatClient(new InsightPointOpsResponse(List.of()));
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(
                                PromptType.INSIGHT_LEAF_POINT_OPS,
                                "Custom insight leaf point-op instruction")
                        .build();
        var generator = new LlmInsightGenerator(client, registry);

        StepVerifier.create(
                        generator.generateLeafPointOps(
                                rootInsightType("profile"),
                                "group",
                                List.of(),
                                List.of(),
                                300,
                                null,
                                "English"))
                .expectNextMatches(response -> response.operations().isEmpty())
                .verifyComplete();

        assertThat(client.lastMessages().getFirst().content())
                .contains("Custom insight leaf point-op instruction");
    }

    @Test
    @DisplayName("generateBranchPointOps should use point-op prompt override")
    void generateBranchPointOpsShouldUsePointOpsOverrideInstruction() {
        var client = new FakeStructuredChatClient(new InsightPointOpsResponse(List.of()));
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(
                                PromptType.BRANCH_AGGREGATION_POINT_OPS,
                                "Custom branch point-op instruction")
                        .build();
        var generator = new LlmInsightGenerator(client, registry);

        StepVerifier.create(
                        generator.generateBranchPointOps(
                                rootInsightType("profile"), List.of(), List.of(), 300, "English"))
                .expectNextMatches(response -> response.operations().isEmpty())
                .verifyComplete();

        assertThat(client.lastMessages().getFirst().content())
                .contains("Custom branch point-op instruction");
    }

    @Test
    @DisplayName("generateBranchPointOps should not convert missing structured response into noop")
    void generateBranchPointOpsDoesNotConvertMissingStructuredResponseIntoEmptyOperations() {
        var client = new FakeStructuredChatClient(null);
        var generator = new LlmInsightGenerator(client);

        StepVerifier.create(
                        generator.generateBranchPointOps(
                                rootInsightType("profile"), List.of(), List.of(), 300, "English"))
                .verifyComplete();
    }

    @Test
    @DisplayName("generateBranchPointOps should append additional context to the user prompt")
    void generateBranchPointOpsShouldAppendAdditionalContextToUserPrompt() {
        var client = new FakeStructuredChatClient(new InsightPointOpsResponse(List.of()));
        var generator = new LlmInsightGenerator(client);

        StepVerifier.create(
                        generator.generateBranchPointOps(
                                rootInsightType("profile"),
                                List.of(),
                                List.of(memoryInsight()),
                                300,
                                "GraphBranchHints: shared entity project-x",
                                "English"))
                .expectNextMatches(response -> response.operations().isEmpty())
                .verifyComplete();

        assertThat(client.lastMessages().getLast().content())
                .contains("<AdditionalContext>")
                .contains("GraphBranchHints: shared entity project-x");
    }

    @Test
    @DisplayName("generateBranchSummary should append additional context to the user prompt")
    void generateBranchSummaryShouldAppendAdditionalContextToUserPrompt() {
        var client = new FakeStructuredChatClient(new InsightPointGenerateResponse(List.of()));
        var generator = new LlmInsightGenerator(client);

        StepVerifier.create(
                        generator.generateBranchSummary(
                                rootInsightType("profile"),
                                List.of(),
                                List.of(memoryInsight()),
                                300,
                                "GraphBranchHints: shared entity project-x",
                                "English"))
                .expectNextMatches(response -> response.points().isEmpty())
                .verifyComplete();

        assertThat(client.lastMessages().getLast().content())
                .contains("<AdditionalContext>")
                .contains("GraphBranchHints: shared entity project-x");
    }

    @Test
    @DisplayName("generateRootSynthesis should use root synthesis override instruction")
    void generateRootSynthesisShouldUseRootSynthesisOverrideInstruction() {
        var client = new FakeStructuredChatClient(new InsightPointGenerateResponse(List.of()));
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.ROOT_SYNTHESIS, "Custom root synthesis instruction")
                        .build();
        var generator = new LlmInsightGenerator(client, registry);

        StepVerifier.create(
                        generator.generateRootSynthesis(
                                rootInsightType("profile"), List.of(), List.of(), 300, "English"))
                .expectNextMatches(response -> response.points().isEmpty())
                .verifyComplete();

        assertThat(client.lastMessages().getFirst().content())
                .contains("Custom root synthesis instruction");
    }

    @Test
    @DisplayName("generateRootSynthesis should use interaction guide override instruction")
    void generateRootSynthesisShouldUseInteractionGuideOverrideInstruction() {
        var client = new FakeStructuredChatClient(new InsightPointGenerateResponse(List.of()));
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(
                                PromptType.INTERACTION_GUIDE_SYNTHESIS,
                                "Custom interaction guide instruction")
                        .build();
        var generator = new LlmInsightGenerator(client, registry);

        StepVerifier.create(
                        generator.generateRootSynthesis(
                                rootInsightType("interaction"),
                                List.of(),
                                List.of(),
                                300,
                                "English"))
                .expectNextMatches(response -> response.points().isEmpty())
                .verifyComplete();

        assertThat(client.lastMessages().getFirst().content())
                .contains("Custom interaction guide instruction");
    }

    @Test
    @DisplayName("generateRootSynthesis should append additional context to the user prompt")
    void generateRootSynthesisShouldAppendAdditionalContextToUserPrompt() {
        var client = new FakeStructuredChatClient(new InsightPointGenerateResponse(List.of()));
        var generator = new LlmInsightGenerator(client);

        StepVerifier.create(
                        generator.generateRootSynthesis(
                                rootInsightType("interaction"),
                                List.of(),
                                List.of(memoryInsight()),
                                300,
                                "GraphRootHints: weak bridge between branches",
                                "English"))
                .expectNextMatches(response -> response.points().isEmpty())
                .verifyComplete();

        assertThat(client.lastMessages().getLast().content())
                .contains("<AdditionalContext>")
                .contains("GraphRootHints: weak bridge between branches");
    }

    private static MemoryInsightType rootInsightType(String name) {
        return new MemoryInsightType(
                1L,
                name,
                "Root synthesis",
                null,
                List.of("directive"),
                300,
                null,
                null,
                null,
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.AGENT);
    }

    private static MemoryInsight memoryInsight() {
        return new MemoryInsight(
                1L,
                "memory-1",
                "profile",
                MemoryScope.AGENT,
                "group",
                List.of("directive"),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                0);
    }

    private static final class FakeStructuredChatClient implements StructuredChatClient {

        private final Object response;
        private List<ChatMessage> lastMessages = List.of();
        private Class<?> lastResponseType;

        private FakeStructuredChatClient(Object response) {
            this.response = response;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("Not used in this test"));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            lastMessages = List.copyOf(messages);
            lastResponseType = responseType;
            return Mono.justOrEmpty((T) response);
        }

        private List<ChatMessage> lastMessages() {
            return lastMessages;
        }

        private Class<?> lastResponseType() {
            return lastResponseType;
        }
    }
}
