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
package com.openmemind.ai.memory.core.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HashUtils Hash Tool")
class HashUtilsTest {

    @Nested
    @DisplayName("contentHash(content)")
    class ContentHashTests {

        @Test
        @DisplayName("Same content should produce consistent hash")
        void contentHashShouldBeConsistent() {
            var hash1 = HashUtils.contentHash("User likes coffee");
            var hash2 = HashUtils.contentHash("User likes coffee");
            assertThat(hash1).isEqualTo(hash2).hasSize(16);
        }

        @Test
        @DisplayName("Different content should produce different hash")
        void differentContentShouldProduceDifferentHash() {
            var hash1 = HashUtils.contentHash("User likes coffee");
            var hash2 = HashUtils.contentHash("User likes tea");
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }
}
