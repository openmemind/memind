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

@DisplayName("TypedQueryExpandPrompts Unit Test")
class TypedQueryExpandPromptsTest {

    @Test
    @DisplayName("typed query expand should keep strategies in system prompt")
    void shouldKeepExpansionStrategiesInSystemPrompt() {
        var prompt =
                TypedQueryExpandPrompts.build(
                                "When did the user start the migration?",
                                List.of("missing job start date"),
                                List.of("User works on a migration"),
                                List.of("We discussed the migration timeline"),
                                3)
                        .render("English");

        assertThat(prompt.systemPrompt())
                .contains("Query Types")
                .contains("Output Format")
                .contains("Time-Aware Expansion");
        assertThat(prompt.userPrompt())
                .contains("When did the user start the migration?")
                .contains("missing job start date")
                .contains("User works on a migration")
                .contains("We discussed the migration timeline")
                .doesNotContain("Query Types")
                .doesNotContain("Output Format");
    }

    @Test
    @DisplayName("build default should show the most complete typed expansion skeleton")
    void buildDefaultShowsMostCompleteTypedQueryExpandSkeleton() {
        String preview = TypedQueryExpandPrompts.buildDefault().previewSystemPrompt("English");

        assertThat(preview).contains("fill the identified gaps");
        assertThat(preview).contains("# Time-Aware Expansion");
        assertThat(preview).contains("# Output Format");
    }

    @Test
    @DisplayName("build with registry should replace typed expansion instructions")
    void buildWithRegistryUsesOverrideInstruction() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(
                                PromptType.TYPED_QUERY_EXPAND, "Custom typed expansion instruction")
                        .build();

        var prompt =
                TypedQueryExpandPrompts.build(
                                registry,
                                "When did the user start the migration?",
                                List.of("missing job start date"),
                                List.of("User works on a migration"),
                                List.of(),
                                3)
                        .render("English");

        assertThat(prompt.systemPrompt()).contains("Custom typed expansion instruction");
        assertThat(prompt.systemPrompt()).doesNotContain("Query Types");
    }
}
