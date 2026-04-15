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
import com.openmemind.ai.memory.core.data.InsightPoint.PointType;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InsightLeafPrompts Unit Test")
class InsightLeafPromptsTest {

    @Test
    @DisplayName("full rewrite prompt should keep points contract")
    void leafPromptKeepsFullRewriteContract() {
        var prompt =
                InsightLeafPrompts.build(
                                createBranchType("directives"),
                                "response rules",
                                List.of(
                                        new InsightPoint(
                                                PointType.SUMMARY,
                                                "Existing point",
                                                0.8f,
                                                List.of("1", "2"))),
                                List.of(),
                                200)
                        .render("English");

        assertThat(prompt.systemPrompt())
                .contains("\"points\"")
                .doesNotContain("\"operations\"")
                .contains("Full Replacement");
    }

    @Test
    @DisplayName("point-op prompt should enumerate existing points with stable point ids")
    void leafPointOpsPromptEnumeratesExistingPointsWithStablePointIds() {
        var prompt =
                InsightLeafPrompts.buildPointOps(
                                createBranchType("directives"),
                                "response rules",
                                List.of(
                                        new InsightPoint(
                                                PointType.SUMMARY,
                                                "Existing point",
                                                0.8f,
                                                List.of("1", "2"))),
                                List.of(),
                                200)
                        .render("English");

        assertThat(prompt.userPrompt()).contains("P1.").contains("sourceItemIds");
        assertThat(prompt.systemPrompt())
                .contains("\"operations\"")
                .doesNotContain("\"points\": [")
                .contains("Return ONLY a raw JSON object");
    }

    @Test
    @DisplayName("system prompt should describe new agent dimension examples")
    void shouldDescribeNewAgentDimensionExamples() {
        var prompt =
                InsightLeafPrompts.build(
                                createBranchType("directives"),
                                "response rules",
                                List.of(),
                                List.of(),
                                200)
                        .render("English");

        assertThat(prompt.systemPrompt())
                .contains("\"directives\" = durable collaboration rules")
                .contains("\"playbooks\" = reusable handling methods")
                .contains("\"resolutions\" = resolved problem knowledge")
                .doesNotContain("\"proc" + "edural\" = how things are done");
    }

    @Test
    @DisplayName("build with registry should collapse leaf synthesis prompt to a system section")
    void buildWithRegistryUsesOverrideInstruction() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.INSIGHT_LEAF, "Custom insight leaf instruction")
                        .build();
        var template =
                InsightLeafPrompts.build(
                        registry,
                        createBranchType("directives"),
                        "response rules",
                        List.of(),
                        List.of(),
                        200);

        assertThat(template.describeStructure()).contains("Sections: system");
        assertThat(template.render("English").systemPrompt())
                .contains("Custom insight leaf instruction");
    }

    private static MemoryInsightType createBranchType(String name) {
        return new MemoryInsightType(
                1L,
                name,
                "Branch synthesis",
                null,
                List.of("directive"),
                200,
                null,
                null,
                null,
                InsightAnalysisMode.BRANCH,
                null,
                MemoryScope.AGENT);
    }
}
