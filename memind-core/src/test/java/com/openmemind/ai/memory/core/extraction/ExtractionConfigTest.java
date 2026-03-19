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
package com.openmemind.ai.memory.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExtractionConfig")
class ExtractionConfigTest {

    @Test
    @DisplayName("defaults should enable insight and use USER scope")
    void defaultsShouldHaveCorrectSettings() {
        var config = ExtractionConfig.defaults();
        assertThat(config.enableInsight()).isTrue();
        assertThat(config.scope()).isEqualTo(MemoryScope.USER);
    }

    @Test
    @DisplayName("agentOnly should use AGENT scope")
    void agentOnlyShouldUseAgentScope() {
        var config = ExtractionConfig.agentOnly();
        assertThat(config.scope()).isEqualTo(MemoryScope.AGENT);
    }
}
