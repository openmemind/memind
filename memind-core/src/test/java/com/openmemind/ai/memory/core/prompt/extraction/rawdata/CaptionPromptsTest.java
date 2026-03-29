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
package com.openmemind.ai.memory.core.prompt.extraction.rawdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CaptionPrompts")
class CaptionPromptsTest {

    @Test
    @DisplayName("caption prompt should use override instruction")
    void captionPromptUsesOverrideInstruction() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(PromptType.CAPTION, "Custom caption instruction")
                        .build();

        var prompt = CaptionPrompts.build(registry, "content", Map.of()).render("English");

        assertThat(prompt.systemPrompt()).contains("Custom caption instruction");
    }
}
