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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
}
