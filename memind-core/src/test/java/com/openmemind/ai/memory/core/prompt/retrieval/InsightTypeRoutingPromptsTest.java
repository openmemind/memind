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
package com.openmemind.ai.memory.core.prompt.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InsightTypeRoutingPrompts Unit Test")
class InsightTypeRoutingPromptsTest {

    @Test
    @DisplayName("routing prompt should describe new agent insight types")
    void shouldDescribeNewAgentInsightTypes() {
        var prompt =
                InsightTypeRoutingPrompts.build(
                                "How should repository comparisons be handled?",
                                List.of("directives", "playbooks", "resolutions"),
                                Map.of(
                                        "directives", "Durable agent rules",
                                        "playbooks", "Reusable workflows",
                                        "resolutions", "Resolved problems"))
                        .render("English");

        assertThat(prompt.systemPrompt())
                .contains("DURABLE AGENT RULES")
                .contains("REUSABLE WORKFLOWS")
                .contains("RESOLVED PROBLEMS")
                .contains("Show the plan before changes")
                .contains("Compare repositories by scope")
                .doesNotContain("- " + "proc" + "edural: Query asks about HOW-TO");
        assertThat(prompt.userPrompt())
                .contains("How should repository comparisons be handled?")
                .contains("- directives: Durable agent rules")
                .contains("- playbooks: Reusable workflows")
                .contains("- resolutions: Resolved problems")
                .doesNotContain("DURABLE AGENT RULES")
                .doesNotContain("REUSABLE WORKFLOWS")
                .doesNotContain("RESOLVED PROBLEMS");
    }

    @Test
    @DisplayName("build with registry should replace insight type routing instructions")
    void buildWithRegistryUsesOverrideInstruction() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(
                                PromptType.INSIGHT_TYPE_ROUTING,
                                "Custom insight type routing instruction")
                        .build();

        var prompt =
                InsightTypeRoutingPrompts.build(
                                registry,
                                "How should repository comparisons be handled?",
                                List.of("directives"),
                                Map.of("directives", "Durable agent rules"),
                                List.of())
                        .render("English");

        assertThat(prompt.systemPrompt()).contains("Custom insight type routing instruction");
        assertThat(prompt.systemPrompt()).doesNotContain("DURABLE AGENT RULES");
    }
}
