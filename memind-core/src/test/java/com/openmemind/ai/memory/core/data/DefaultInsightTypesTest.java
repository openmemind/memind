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
package com.openmemind.ai.memory.core.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultInsightTypesTest {

    @Test
    @DisplayName("all() should expose the new agent branch insight types")
    void allShouldExposeNewAgentBranchInsightTypes() {
        assertThat(DefaultInsightTypes.all())
                .extracting(MemoryInsightType::name)
                .contains("directives", "playbooks", "resolutions")
                .doesNotContain("proc" + "edural");
    }

    @Test
    @DisplayName("agent branch insight types should map 1:1 to their categories")
    void agentBranchTypesShouldMapToTheirCategories() {
        assertThat(DefaultInsightTypes.directives().categories()).containsExactly("directive");
        assertThat(DefaultInsightTypes.playbooks().categories()).containsExactly("playbook");
        assertThat(DefaultInsightTypes.resolutions().categories()).containsExactly("resolution");
    }
}
