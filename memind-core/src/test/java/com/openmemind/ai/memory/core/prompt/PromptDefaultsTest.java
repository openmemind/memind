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

@DisplayName("PromptDefaults")
class PromptDefaultsTest {

    @Test
    @DisplayName("build should support every prompt type")
    void buildSupportsEveryPromptType() {
        for (PromptType type : PromptType.values()) {
            assertThat(PromptDefaults.build(type)).as("default template for %s", type).isNotNull();
        }
    }

    @Test
    @DisplayName("buildPreview should support every prompt type")
    void buildPreviewSupportsEveryPromptType() {
        for (PromptType type : PromptType.values()) {
            assertThat(PromptDefaults.buildPreview(type))
                    .as("preview template for %s", type)
                    .isNotNull();
        }
    }
}
