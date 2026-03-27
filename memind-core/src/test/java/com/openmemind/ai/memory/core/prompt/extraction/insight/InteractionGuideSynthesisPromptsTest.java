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

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import com.openmemind.ai.memory.core.data.enums.InsightAnalysisMode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InteractionGuideSynthesisPrompts Unit Test")
class InteractionGuideSynthesisPromptsTest {

    @Test
    @DisplayName("system prompt should describe new agent branch taxonomy")
    void shouldDescribeNewAgentBranchTaxonomy() {
        var prompt =
                InteractionGuideSynthesisPrompts.build(
                                createRootType("interaction"), null, List.of(), 300)
                        .render("English");

        assertThat(prompt.systemPrompt())
                .contains("The 8 BRANCH dimensions you may receive:")
                .contains("directives")
                .contains("playbooks")
                .contains("resolutions")
                .contains("directly stated by user in a directives BRANCH")
                .doesNotContain("proc" + "edural BRANCH");
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
                null,
                InsightAnalysisMode.ROOT,
                null,
                null,
                null);
    }
}
