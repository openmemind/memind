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

@DisplayName("QueryRewritePrompts Unit Test")
class QueryRewritePromptsTest {

    @Test
    @DisplayName("query rewrite should keep rewrite rules in system prompt")
    void shouldKeepRewriteRulesInSystemPrompt() {
        var prompt =
                QueryRewritePrompts.build(
                                "What about the logging part?",
                                List.of("We discussed Spring Boot configuration"))
                        .render("English");

        assertThat(prompt.systemPrompt())
                .contains("Core Principles")
                .contains("Workflow")
                .contains("Resolve ALL pronouns and references");
        assertThat(prompt.userPrompt())
                .contains("What about the logging part?")
                .contains("We discussed Spring Boot configuration")
                .doesNotContain("Core Principles")
                .doesNotContain("Workflow");
    }

    @Test
    @DisplayName("build with registry should replace query rewrite instructions")
    void buildWithRegistryUsesOverrideInstruction() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.QUERY_REWRITE, "Custom query rewrite instruction")
                        .build();

        var prompt =
                QueryRewritePrompts.build(registry, "What about the logging part?", List.of())
                        .render("English");

        assertThat(prompt.systemPrompt()).contains("Custom query rewrite instruction");
        assertThat(prompt.systemPrompt()).doesNotContain("Core Principles");
    }
}
