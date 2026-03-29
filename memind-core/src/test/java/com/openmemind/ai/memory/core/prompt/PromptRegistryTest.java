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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PromptRegistry")
class PromptRegistryTest {

    @Test
    @DisplayName("EMPTY should never return overrides")
    void emptyRegistryNeverReturnsOverrides() {
        assertThat(PromptRegistry.EMPTY.getOverride(PromptType.INTENT_ROUTING)).isNull();
        assertThat(PromptRegistry.EMPTY.hasOverride(PromptType.INTENT_ROUTING)).isFalse();
    }
}
