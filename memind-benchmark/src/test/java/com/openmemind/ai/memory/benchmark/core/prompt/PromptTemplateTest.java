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
package com.openmemind.ai.memory.benchmark.core.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplateTest {

    @Test
    void renderReplacesAllNamedVariables() {
        PromptTemplate template =
                PromptTemplate.fromClasspath("fixtures/prompts/test-template.xml");

        String rendered =
                template.render(
                        Map.of(
                                "question", "Where did we meet?",
                                "context", "We met in Hangzhou."));

        assertThat(rendered).contains("Question: Where did we meet?");
        assertThat(rendered).contains("Context: We met in Hangzhou.");
        assertThat(rendered).doesNotContain("{{question}}");
    }
}
