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
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BranchAggregationPromptsTest {

    @Test
    @DisplayName("Rendered prompt should expose named sections and keep branch inputs in user")
    void shouldExposeNamedSectionsAndKeepBranchInputsInUserPrompt() {
        var template =
                BranchAggregationPrompts.build(
                        createInsightType(),
                        List.of(
                                new InsightPoint(
                                        PointType.SUMMARY,
                                        "User values schedule flexibility",
                                        0.85f,
                                        List.of("10", "11"))),
                        List.of(createLeafInsight("career_background")),
                        320);
        var prompt = template.render("English");

        assertThat(template.describeStructure())
                .contains("Sections: objective, context, workflow, output, examples");
        assertThat(prompt.userPrompt())
                .contains("# Existing BRANCH Points")
                .contains("User values schedule flexibility")
                .contains("# LEAF Insights")
                .contains("career_background")
                .doesNotContain("# Core Principles");
    }

    @Test
    @DisplayName("Rendered full rewrite prompt should keep points contract")
    void shouldRenderFullRewritePrompt() {
        var template =
                BranchAggregationPrompts.build(
                        createInsightType(),
                        List.of(
                                new InsightPoint(
                                        PointType.SUMMARY,
                                        "User values schedule flexibility",
                                        0.85f,
                                        List.of("10", "11"))),
                        List.of(createLeafInsight("career_background")),
                        320);
        var prompt = template.render("English");

        assertThat(prompt.systemPrompt())
                .contains("\"points\"")
                .doesNotContain("\"operations\"")
                .contains("Full Replacement");
    }

    @Test
    @DisplayName("Rendered point-op prompt should use point ids for branch points")
    void shouldRenderPointOpsPromptWithPointIds() {
        var template =
                BranchAggregationPrompts.buildPointOps(
                        createInsightType(),
                        List.of(
                                new InsightPoint(
                                        "pt_branch_1",
                                        PointType.SUMMARY,
                                        "User values schedule flexibility",
                                        0.85f,
                                        List.of("10", "11"))),
                        List.of(createLeafInsight("career_background")),
                        320);
        var prompt = template.render("English");

        assertThat(prompt.systemPrompt())
                .contains("Point Operations Only")
                .doesNotContain("Full Replacement");
        assertThat(prompt.userPrompt())
                .contains("pointId: pt_branch_1")
                .contains("G1.P1 [SUMMARY] User has 8 years of backend engineering experience")
                .contains("sourceItemIds: [10, 14]")
                .doesNotContain("P1.");
        assertThat(prompt.systemPrompt())
                .contains("\"targetPointId\"")
                .doesNotContain("\"targetIndex\"");
    }

    @Test
    @DisplayName(
            "build with registry should collapse branch aggregation prompt to a system section")
    void buildWithRegistryUsesOverrideInstruction() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(
                                PromptType.BRANCH_AGGREGATION,
                                "Custom branch aggregation instruction")
                        .build();
        var template =
                BranchAggregationPrompts.build(
                        registry, createInsightType(), List.of(), List.of(), 320);

        assertThat(template.describeStructure()).contains("Sections: system");
        assertThat(template.render("English").systemPrompt())
                .contains("Custom branch aggregation instruction");
    }

    private static MemoryInsightType createInsightType() {
        return new MemoryInsightType(
                1L,
                "identity",
                "Cross-group identity synthesis",
                null,
                List.of("profile"),
                400,
                null,
                null,
                null,
                InsightAnalysisMode.BRANCH,
                null,
                null);
    }

    private static MemoryInsight createLeafInsight(String group) {
        return new MemoryInsight(
                10L,
                "memory-1",
                "identity",
                null,
                "Leaf insight",
                List.of("profile"),
                List.of(
                        new InsightPoint(
                                PointType.SUMMARY,
                                "User has 8 years of backend engineering experience",
                                0.9f,
                                List.of("10", "14"))),
                group,
                0.9f,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                1);
    }
}
