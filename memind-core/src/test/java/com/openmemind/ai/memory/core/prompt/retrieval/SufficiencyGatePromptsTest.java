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
import com.openmemind.ai.memory.core.retrieval.query.QueryContext;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult.SourceType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SufficiencyGatePrompts")
class SufficiencyGatePromptsTest {

    @Test
    @DisplayName("Build named system sections and keep query plus results in user payload")
    void builds_named_sections_and_keeps_query_plus_results_in_user_payload() {
        var context =
                new QueryContext(
                        null,
                        "When did the user start their current job?",
                        null,
                        List.of("We discussed the user's current role."),
                        Map.of(),
                        null,
                        null);
        var results =
                List.of(
                        new ScoredResult(
                                SourceType.ITEM,
                                "item-1",
                                "User works at TechCorp as a senior engineer",
                                0.91f,
                                0.87));

        var template = SufficiencyGatePrompts.build(context, results);
        var prompt = template.render("English");

        assertThat(template.describeStructure())
                .contains("Sections: objective, context, workflow, output, examples");
        assertThat(prompt.userPrompt())
                .contains("# Query")
                .contains("When did the user start their current job?")
                .contains("# Retrieved Results")
                .contains("TechCorp")
                .doesNotContain("# Content Types");
    }

    @Test
    @DisplayName("override should collapse sufficiency gate to a single system section")
    void overrideCollapsesSufficiencyGateToSingleSystemSection() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.SUFFICIENCY_GATE, "Custom sufficiency instruction")
                        .build();
        var context =
                new QueryContext(
                        null,
                        "When did the user start their current job?",
                        null,
                        List.of("We discussed the user's current role."),
                        Map.of(),
                        null,
                        null);
        var results =
                List.of(
                        new ScoredResult(
                                SourceType.ITEM,
                                "item-1",
                                "User works at TechCorp as a senior engineer",
                                0.91f,
                                0.87));

        var template = SufficiencyGatePrompts.build(registry, context, results);

        assertThat(template.describeStructure()).contains("Sections: system");
        assertThat(template.render("English").systemPrompt())
                .contains("Custom sufficiency instruction");
    }
}
