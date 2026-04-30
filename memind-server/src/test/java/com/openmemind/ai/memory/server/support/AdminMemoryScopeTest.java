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
package com.openmemind.ai.memory.server.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AdminMemoryScopeTest {

    @Test
    void parsesUserOnlyMemoryId() {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId("user-1");

        assertThat(scope.present()).isTrue();
        assertThat(scope.userId()).isEqualTo("user-1");
        assertThat(scope.agentId()).isNull();
        assertThat(scope.agentScoped()).isFalse();
        assertThat(scope.databaseAgentId()).isEqualTo("");
        assertThat(scope.memoryId()).isEqualTo("user-1");
    }

    @Test
    void parsesUserAgentMemoryId() {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId("user-1:agent-1");

        assertThat(scope.present()).isTrue();
        assertThat(scope.userId()).isEqualTo("user-1");
        assertThat(scope.agentId()).isEqualTo("agent-1");
        assertThat(scope.agentScoped()).isTrue();
        assertThat(scope.databaseAgentId()).isEqualTo("agent-1");
        assertThat(scope.memoryId()).isEqualTo("user-1:agent-1");
    }

    @Test
    void emptyMemoryIdMeansGlobalScope() {
        AdminMemoryScope scope = AdminMemoryScope.fromMemoryId(" ");

        assertThat(scope.present()).isFalse();
        assertThat(scope.userId()).isNull();
        assertThat(scope.agentId()).isNull();
        assertThat(scope.memoryId()).isNull();
    }

    @Test
    void rejectsBlankUserPart() {
        assertThatThrownBy(() -> AdminMemoryScope.fromMemoryId(":agent-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memoryId");
    }

    @Test
    void rejectsBlankAgentPart() {
        assertThatThrownBy(() -> AdminMemoryScope.fromMemoryId("user-1:"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memoryId");
    }
}
