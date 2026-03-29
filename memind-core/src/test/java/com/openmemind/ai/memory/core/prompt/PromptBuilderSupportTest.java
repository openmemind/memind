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
package com.openmemind.ai.memory.core.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.MemoryInsightType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptBuilderSupportTest {

    @Test
    @DisplayName("coreSections should preserve the canonical five-section order")
    void coreSectionsShouldPreserveCanonicalFiveSectionOrder() {
        var template =
                PromptBuilderSupport.coreSections(
                                "sample", "objective", "context", "workflow", "output", "examples")
                        .userPrompt("user")
                        .build();

        assertThat(template.describeStructure())
                .contains("Sections: objective, context, workflow, output, examples");
    }

    @Test
    @DisplayName("builder should preserve the provided custom section order")
    void builderShouldPreserveCustomSectionOrder() {
        var template =
                PromptBuilderSupport.builder(
                                "custom",
                                PromptBuilderSupport.section("system", "system"),
                                PromptBuilderSupport.section("examples", "examples"))
                        .userPrompt("user")
                        .build();

        assertThat(template.describeStructure()).contains("Sections: system, examples");
    }

    @Test
    @DisplayName("descriptionOrName should fall back to name when description is missing")
    void descriptionOrNameShouldFallbackToName() {
        var insightType =
                new MemoryInsightType(
                        1L,
                        "identity",
                        null,
                        null,
                        List.of(),
                        300,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThat(PromptBuilderSupport.descriptionOrName(insightType)).isEqualTo("identity");
    }
}
