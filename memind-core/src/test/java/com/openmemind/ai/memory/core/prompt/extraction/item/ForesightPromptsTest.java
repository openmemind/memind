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
package com.openmemind.ai.memory.core.prompt.extraction.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ForesightPrompts Unit Test")
class ForesightPromptsTest {

    @Test
    @DisplayName(
            "foresight prompt should expose named sections and keep conversation in user payload")
    void shouldExposeNamedSections() {
        var template =
                ForesightPrompts.build(
                        """
                        [2026-03-15 10:00] user: I just finished the Spring Boot 3.2 virtual threads workshop
                        [2026-03-15 10:02] user: The structured concurrency part was really eye-opening
                        """,
                        Instant.parse("2026-03-15T10:30:00Z"));
        var prompt = template.render("English");

        assertThat(template.describeStructure())
                .contains("Sections: objective, guidelines, output, examples, timeContext");
        assertThat(prompt.userPrompt())
                .contains("Spring Boot 3.2 virtual threads workshop")
                .doesNotContain("What to Predict");
    }

    @Test
    @DisplayName("build with registry should replace foresight instructions")
    void buildWithRegistryUsesOverrideInstruction() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.FORESIGHT, "Custom foresight instruction")
                        .build();
        var template =
                ForesightPrompts.build(
                        registry,
                        """
                        [2026-03-15 10:00] user: I just finished the Spring Boot 3.2 virtual threads workshop
                        """,
                        Instant.parse("2026-03-15T10:30:00Z"));
        var prompt = template.render("English");

        assertThat(prompt.systemPrompt()).contains("Custom foresight instruction");
        assertThat(prompt.systemPrompt()).doesNotContain("What to Predict");
    }
}
