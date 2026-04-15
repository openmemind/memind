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
import com.openmemind.ai.memory.core.data.MemoryInsight;
import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import com.openmemind.ai.memory.core.data.enums.InsightTier;
import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RootSynthesisPrompts Unit Test")
class RootSynthesisPromptsTest {

    @Test
    @DisplayName("system prompt should describe new agent branch taxonomy")
    void shouldDescribeNewAgentBranchTaxonomy() {
        var prompt =
                RootSynthesisPrompts.build(
                                createRootType("profile"),
                                List.of(
                                        new InsightPoint(
                                                "pt_root_existing",
                                                InsightPoint.PointType.REASONING,
                                                "Existing synthesis",
                                                List.of(),
                                                List.of(new InsightPointRef(11L, "pt_branch_1")),
                                                Map.of("dimension", "convergence"))),
                                List.of(branchInsight()),
                                300)
                        .render("English");

        assertThat(prompt.systemPrompt())
                .contains("The 8 BRANCH dimensions you may receive:")
                .contains("directives")
                .contains("playbooks")
                .contains("resolutions")
                .doesNotContain("proc" + "edural: Reusable HOW-TO knowledge");
        assertThat(prompt.userPrompt())
                .contains("insightId=")
                .contains("pointId=")
                .contains("sourcePointRefs")
                .doesNotContain("confidence");
    }

    @Test
    @DisplayName("root synthesis override should use a single system section")
    void rootSynthesisOverrideUsesSingleSystemSection() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.ROOT_SYNTHESIS, "Custom root synthesis instruction")
                        .build();

        var template =
                RootSynthesisPrompts.build(
                        registry, createRootType("interaction"), List.of(), List.of(), 300);

        assertThat(template.describeStructure()).contains("Sections: system");
        assertThat(template.render("English").systemPrompt())
                .contains("Custom root synthesis instruction");
    }

    private static MemoryInsightType createRootType(String name) {
        return new MemoryInsightType(
                1L,
                name,
                "Root synthesis",
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

    private static MemoryInsight branchInsight() {
        Instant now = Instant.parse("2026-04-15T00:00:00Z");
        return new MemoryInsight(
                11L,
                "memory-1",
                "profile",
                null,
                "branch-profile",
                List.of(),
                List.of(
                        new InsightPoint(
                                "pt_branch_1",
                                InsightPoint.PointType.SUMMARY,
                                "User consistently optimizes for async-first work.",
                                List.of(),
                                List.of(
                                        new InsightPointRef(101L, "pt_leaf_remote"),
                                        new InsightPointRef(102L, "pt_leaf_deep_work")),
                                null)),
                null,
                now,
                null,
                now,
                now,
                InsightTier.BRANCH,
                null,
                List.of(101L, 102L),
                1);
    }
}
