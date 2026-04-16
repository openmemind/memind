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
package com.openmemind.ai.memory.core.prompt.extraction.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.InsightPoint;
import com.openmemind.ai.memory.core.data.InsightPointRef;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InteractionGuideSynthesisPrompts Unit Test")
class InteractionGuideSynthesisPromptsTest {

    @Test
    @DisplayName("system prompt should describe new agent branch taxonomy")
    void shouldDescribeNewAgentBranchTaxonomy() {
        var template =
                InteractionGuideSynthesisPrompts.build(
                        createRootType("interaction"),
                        List.of(
                                new InsightPoint(
                                        "pt_root_interaction",
                                        InsightPoint.PointType.REASONING,
                                        "Keep replies concise",
                                        List.of(),
                                        List.of(new InsightPointRef(11L, "pt_branch_comm")),
                                        Map.of("dimension", "communication_style"))),
                        List.of(),
                        300);
        var prompt = template.render("English");

        assertThat(template.describeStructure())
                .contains("Sections: objective, context, workflow, output, examples");
        assertThat(prompt.systemPrompt())
                .contains("The 8 BRANCH dimensions you may receive:")
                .contains("directives")
                .contains("playbooks")
                .contains("resolutions")
                .contains("directly stated by user in a directives BRANCH")
                .doesNotContain("proc" + "edural BRANCH");
        assertThat(prompt.userPrompt())
                .contains("# Existing Directives")
                .contains("Keep replies concise")
                .contains("sourcePointRefs")
                .doesNotContain("confidence")
                .doesNotContain("# Dimension Decision Logic");
    }

    @Test
    @DisplayName(
            "build with registry should collapse interaction guide synthesis to a system section")
    void buildWithRegistryUsesOverrideInstruction() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(
                                PromptType.INTERACTION_GUIDE_SYNTHESIS,
                                "Custom interaction guide instruction")
                        .build();
        var template =
                InteractionGuideSynthesisPrompts.build(
                        registry, createRootType("interaction"), List.of(), List.of(), 300);

        assertThat(template.describeStructure()).contains("Sections: system");
        assertThat(template.render("English").systemPrompt())
                .contains("Custom interaction guide instruction");
    }

    private static MemoryInsightType createRootType(String name) {
        return new MemoryInsightType(
                1L,
                name,
                "Interaction synthesis",
                null,
                List.of(),
                300,
                null,
                null,
                null,
                InsightAnalysisMode.ROOT,
                null,
                null);
    }
}
