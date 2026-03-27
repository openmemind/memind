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
package com.openmemind.ai.memory.example.springboot.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.example.springboot.support.ExampleDataLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgentScopeExampleCatalogTest {

    @Test
    @DisplayName("agent scope example data should be discoverable")
    void agentScopeExampleData_shouldBeDiscoverable() {
        var loader = new ExampleDataLoader();

        assertThat(loader.loadMessages("agent/messages-1.json")).hasSizeGreaterThan(0);
        assertThat(loader.loadMessages("agent/messages-2.json")).hasSizeGreaterThan(0);
    }

    @Test
    @DisplayName("agent scope example main class should exist")
    void agentScopeExampleMainClass_shouldExist() {
        assertThat(AgentScopeMemoryExample.class).isNotNull();
    }
}
