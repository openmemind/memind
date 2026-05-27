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
package com.openmemind.ai.memory.plugin.rawdata.agent.caption;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.llm.ChatMessage;
import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AgentCaptionGeneratorTest {

    @Test
    void shouldBuildDeterministicAgentEpisodeCaptionFromMetadata() {
        String caption =
                new AgentCaptionGenerator()
                        .generate(
                                "content",
                                Map.of(
                                        "goal",
                                        "Fix payment tests",
                                        "outcome",
                                        "success",
                                        "files",
                                        List.of("src/payment/calc.ts"),
                                        "commands",
                                        List.of("npm test payment")))
                        .block();

        assertThat(caption)
                .isEqualTo(
                        "Agent episode: Fix payment tests -> success "
                                + "(src/payment/calc.ts; npm test payment)");
    }

    @Test
    void shouldGenerateLlmTurnSummaryCaptionWhenClientIsAvailable() {
        CapturingChatClient client =
                new CapturingChatClient(
                        new AgentCaptionGenerator.AgentCaptionResponse(
                                "Fix rawdata-agent identity",
                                "The turn unified Claude Code and Codex on the shared"
                                        + " `coding-agent` identity while moving projectSlug into"
                                        + " rawdata and item metadata.",
                                "success",
                                List.of(
                                        "Updated Claude Code and Codex identity defaults.",
                                        "Projected projectSlug into agent episode metadata."),
                                List.of(
                                        "Files: identity.py, AgentSegmentFormatter.java",
                                        "Validation: integration tests passed"),
                                "Use projectSlug later for SessionStart ranking without making it"
                                        + " a memory boundary."));

        String caption =
                new AgentCaptionGenerator(client)
                        .generate(
                                "Goal: Fix identity.\nOutcome: success\nEvidence:\n- e1: file_edit",
                                Map.of(
                                        "goal",
                                        "Fix rawdata-agent identity",
                                        "outcome",
                                        "success",
                                        "files",
                                        List.of("identity.py"),
                                        "commands",
                                        List.of("mvn test"),
                                        "eventIds",
                                        List.of("e1", "e2")),
                                "English")
                        .block();

        assertThat(caption)
                .contains(
                        "Task: Fix rawdata-agent identity.",
                        "Outcome: Success. The turn unified Claude Code and Codex",
                        "Key actions:",
                        "- Updated Claude Code and Codex identity defaults.",
                        "Evidence:",
                        "- Files: identity.py, AgentSegmentFormatter.java",
                        "Next: Use projectSlug later for SessionStart ranking");
        assertThat(client.responseType())
                .isEqualTo(AgentCaptionGenerator.AgentCaptionResponse.class);
        assertThat(client.userPrompt())
                .contains("targetLanguage: English", "Goal: Fix identity", "eventIds: [e1, e2]");
    }

    @Test
    void shouldFallbackToDeterministicCaptionWhenLlmFails() {
        String caption =
                new AgentCaptionGenerator(new FailingChatClient())
                        .generate(
                                "content",
                                Map.of(
                                        "goal",
                                        "Fix payment tests",
                                        "outcome",
                                        "success",
                                        "files",
                                        List.of("src/payment/calc.ts"),
                                        "commands",
                                        List.of("npm test payment")))
                        .block();

        assertThat(caption)
                .isEqualTo(
                        "Agent episode: Fix payment tests -> success "
                                + "(src/payment/calc.ts; npm test payment)");
    }

    @Test
    void shouldIncludeKeyLifecycleAwareEpisodeSignals() {
        String caption =
                new AgentCaptionGenerator()
                        .generate(
                                "content",
                                Map.of(
                                        "goal",
                                        "Fix payment tests",
                                        "outcome",
                                        "success",
                                        "files",
                                        List.of("src/payment/calc.ts"),
                                        "commands",
                                        List.of("npm test payment"),
                                        "toolNames",
                                        List.of("Task", "Bash"),
                                        "failureSignals",
                                        List.of("payment rounding mismatch"),
                                        "eventKinds",
                                        List.of(
                                                "user_prompt",
                                                "subagent_stop",
                                                "file_edit",
                                                "test_result",
                                                "stop")))
                        .block();

        assertThat(caption)
                .contains(
                        "Fix payment tests",
                        "success",
                        "src/payment/calc.ts",
                        "npm test payment",
                        "subagent",
                        "payment rounding mismatch");
    }

    @Test
    void shouldMentionCompactBoundaryWhenEpisodeEndsAtCompaction() {
        String caption =
                new AgentCaptionGenerator()
                        .generate(
                                "content",
                                Map.of(
                                        "goal",
                                        "Continue rawdata-agent implementation",
                                        "outcome",
                                        "success",
                                        "commands",
                                        List.of("mvn test"),
                                        "eventKinds",
                                        List.of("user_prompt", "command", "compact_boundary")))
                        .block();

        assertThat(caption)
                .contains("Continue rawdata-agent implementation", "mvn test", "compact");
    }

    private static final class CapturingChatClient implements StructuredChatClient {

        private final AgentCaptionGenerator.AgentCaptionResponse response;
        private List<ChatMessage> messages = List.of();
        private Class<?> responseType;

        private CapturingChatClient(AgentCaptionGenerator.AgentCaptionResponse response) {
            this.response = response;
        }

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("not used"));
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            this.messages = List.copyOf(messages);
            this.responseType = responseType;
            return Mono.just(responseType.cast(response));
        }

        private Class<?> responseType() {
            return responseType;
        }

        private String userPrompt() {
            return messages.getLast().content();
        }
    }

    private static final class FailingChatClient implements StructuredChatClient {

        @Override
        public Mono<String> call(List<ChatMessage> messages) {
            return Mono.error(new UnsupportedOperationException("not used"));
        }

        @Override
        public <T> Mono<T> call(List<ChatMessage> messages, Class<T> responseType) {
            return Mono.error(new RuntimeException("caption failed"));
        }
    }
}
