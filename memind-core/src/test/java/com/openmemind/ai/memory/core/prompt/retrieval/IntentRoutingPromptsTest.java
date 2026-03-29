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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IntentRoutingPrompts Unit Test")
class IntentRoutingPromptsTest {

    @Test
    @DisplayName("intent routing should keep instructions in system prompt")
    void shouldKeepInstructionsInSystemPrompt() {
        var prompt =
                IntentRoutingPrompts.build(
                                "How should I structure my API?",
                                List.of("We discussed a Spring Boot service yesterday"))
                        .render("English");

        assertThat(prompt.systemPrompt()).contains("When to RETRIEVE").contains("When to SKIP");
        assertThat(prompt.userPrompt())
                .contains("How should I structure my API?")
                .contains("We discussed a Spring Boot service yesterday")
                .doesNotContain("When to RETRIEVE")
                .doesNotContain("When to SKIP");
    }

    @Test
    @DisplayName("build with registry should use override instruction")
    void buildWithRegistryUsesOverrideInstruction() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.INTENT_ROUTING, "Custom intent routing instruction")
                        .build();

        var prompt =
                IntentRoutingPrompts.build(registry, "How should I structure my API?", List.of())
                        .render("English");

        assertThat(prompt.systemPrompt()).contains("Custom intent routing instruction");
        assertThat(prompt.systemPrompt()).doesNotContain("When to RETRIEVE");
    }
}
