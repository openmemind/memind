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
package com.openmemind.ai.memory.core.retrieval.sufficiency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SufficiencyResult Unit Test")
class SufficiencyResultTest {

    @Nested
    @DisplayName("keyInformation Field")
    class KeyInformation {

        @Test
        @DisplayName("Contains keyInformation when insufficient")
        void insufficientWithKeyInformation() {
            var result =
                    new SufficiencyResult(
                            false,
                            "missing date",
                            List.of(),
                            List.of("specific date of event"),
                            List.of("user attended the event"));

            assertThat(result.sufficient()).isFalse();
            assertThat(result.keyInformation()).containsExactly("user attended the event");
            assertThat(result.gaps()).containsExactly("specific date of event");
        }

        @Test
        @DisplayName("keyInformation is empty when sufficient")
        void sufficientWithEmptyKeyInformation() {
            var result =
                    new SufficiencyResult(
                            true, "all found", List.of("evidence1"), List.of(), List.of());

            assertThat(result.sufficient()).isTrue();
            assertThat(result.keyInformation()).isEmpty();
        }

        @Test
        @DisplayName("fallbackInsufficient returns empty keyInformation")
        void fallbackHasEmptyKeyInformation() {
            var result = SufficiencyResult.fallbackInsufficient();
            assertThat(result.keyInformation()).isEmpty();
        }
    }
}
