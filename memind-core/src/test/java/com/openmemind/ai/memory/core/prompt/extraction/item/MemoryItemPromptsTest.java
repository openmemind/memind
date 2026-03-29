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
package com.openmemind.ai.memory.core.prompt.extraction.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.prompt.InMemoryPromptRegistry;
import com.openmemind.ai.memory.core.prompt.PromptType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MemoryItemPrompts")
class MemoryItemPromptsTest {

    @Test
    @DisplayName("build unified with registry should delegate to the unified prompt type")
    void buildUnifiedWithRegistryDelegatesToUnifiedPromptType() {
        var registry =
                InMemoryPromptRegistry.builder()
                        .override(
                                PromptType.MEMORY_ITEM_UNIFIED,
                                "Custom unified extraction instruction")
                        .build();

        var prompt =
                MemoryItemPrompts.buildUnified(
                                registry,
                                List.of(),
                                "user: hello",
                                Instant.parse("2026-03-29T00:00:00Z"),
                                null,
                                Set.of())
                        .render("English");

        assertThat(prompt.systemPrompt()).contains("Custom unified extraction instruction");
    }
}
