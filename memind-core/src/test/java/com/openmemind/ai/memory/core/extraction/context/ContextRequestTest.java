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
package com.openmemind.ai.memory.core.extraction.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ContextRequest")
class ContextRequestTest {

    private final MemoryId memoryId = DefaultMemoryId.of("user1", "agent1");

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("of(memoryId, maxTokens) defaults to SIMPLE with includeMemories=true")
        void defaults_to_simple_with_memories() {
            var request = ContextRequest.of(memoryId, 80000);

            assertThat(request.memoryId()).isEqualTo(memoryId);
            assertThat(request.maxTokens()).isEqualTo(80000);
            assertThat(request.includeMemories()).isTrue();
            assertThat(request.strategy()).isEqualTo(RetrievalConfig.Strategy.SIMPLE);
        }

        @Test
        @DisplayName("of(memoryId, maxTokens, strategy) uses specified strategy")
        void uses_specified_strategy() {
            var request = ContextRequest.of(memoryId, 50000, RetrievalConfig.Strategy.DEEP);

            assertThat(request.strategy()).isEqualTo(RetrievalConfig.Strategy.DEEP);
            assertThat(request.includeMemories()).isTrue();
        }

        @Test
        @DisplayName("bufferOnly disables memory retrieval")
        void buffer_only_disables_memories() {
            var request = ContextRequest.bufferOnly(memoryId, 80000);

            assertThat(request.includeMemories()).isFalse();
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("Rejects null memoryId")
        void rejects_null_memory_id() {
            assertThatThrownBy(() -> ContextRequest.of(null, 80000))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("memoryId");
        }

        @Test
        @DisplayName("Rejects zero maxTokens")
        void rejects_zero_max_tokens() {
            assertThatThrownBy(() -> ContextRequest.of(memoryId, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxTokens");
        }

        @Test
        @DisplayName("Rejects negative maxTokens")
        void rejects_negative_max_tokens() {
            assertThatThrownBy(() -> ContextRequest.of(memoryId, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
