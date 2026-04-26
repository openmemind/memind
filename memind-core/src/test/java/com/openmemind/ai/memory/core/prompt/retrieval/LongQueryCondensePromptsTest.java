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

import com.openmemind.ai.memory.core.prompt.PromptRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class LongQueryCondensePromptsTest {

    @Test
    void shouldBuildRetrievalFocusedPrompt() {
        var prompt =
                LongQueryCondensePrompts.build(
                                PromptRegistry.EMPTY,
                                "very long query about Alice and budget",
                                List.of("Alice discussed Q2 budget"),
                                64)
                        .render("English");

        assertThat(prompt.systemPrompt()).contains("retrieval-focused");
        assertThat(prompt.userPrompt()).contains("very long query about Alice and budget");
        assertThat(prompt.userPrompt()).contains("Alice discussed Q2 budget");
        assertThat(prompt.userPrompt()).contains("64");
    }
}
