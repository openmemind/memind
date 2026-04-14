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
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VectorUtils Vector Mathematics Tool")
class VectorUtilsTest {

    @Nested
    @DisplayName("cosineSimilarity")
    class CosineSimilarityTest {

        @Test
        @DisplayName("Same Direction → 1.0")
        void sameDirection() {
            var a = List.of(1.0f, 2.0f, 3.0f);
            var b = List.of(2.0f, 4.0f, 6.0f);
            assertThat(VectorUtils.cosineSimilarity(a, b)).isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Orthogonal → 0.0")
        void orthogonal() {
            var a = List.of(1.0f, 0.0f);
            var b = List.of(0.0f, 1.0f);
            assertThat(VectorUtils.cosineSimilarity(a, b)).isCloseTo(0.0, within(1e-6));
        }

        @Test
        @DisplayName("Opposite Direction → -1.0")
        void oppositeDirection() {
            var a = List.of(1.0f, 2.0f, 3.0f);
            var b = List.of(-1.0f, -2.0f, -3.0f);
            assertThat(VectorUtils.cosineSimilarity(a, b)).isCloseTo(-1.0, within(1e-6));
        }

        @Test
        @DisplayName("Zero Vector → 0.0")
        void zeroVector() {
            var a = List.of(0.0f, 0.0f, 0.0f);
            var b = List.of(1.0f, 2.0f, 3.0f);
            assertThat(VectorUtils.cosineSimilarity(a, b)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("null Input → 0.0")
        void nullInput() {
            assertThat(VectorUtils.cosineSimilarity(null, List.of(1.0f))).isEqualTo(0.0);
            assertThat(VectorUtils.cosineSimilarity(List.of(1.0f), null)).isEqualTo(0.0);
            assertThat(VectorUtils.cosineSimilarity(null, null)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Size Mismatch → 0.0")
        void sizeMismatch() {
            var a = List.of(1.0f, 2.0f);
            var b = List.of(1.0f, 2.0f, 3.0f);
            assertThat(VectorUtils.cosineSimilarity(a, b)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Empty List → 0.0")
        void emptyList() {
            assertThat(VectorUtils.cosineSimilarity(List.of(), List.of())).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("add")
    class AddTest {

        @Test
        @DisplayName("Normal Addition")
        void normalAdd() {
            var a = List.of(1.0f, 2.0f, 3.0f);
            var b = List.of(4.0f, 5.0f, 6.0f);
            assertThat(VectorUtils.add(a, b)).containsExactly(5.0f, 7.0f, 9.0f);
        }

        @Test
        @DisplayName("null Input Returns a")
        void nullInput() {
            var a = List.of(1.0f, 2.0f);
            assertThat(VectorUtils.add(a, null)).isEqualTo(a);
            assertThat(VectorUtils.add(null, a)).isNull();
        }
    }

    @Nested
    @DisplayName("divide")
    class DivideTest {

        @Test
        @DisplayName("Normal Division")
        void normalDivide() {
            var v = List.of(4.0f, 6.0f, 8.0f);
            assertThat(VectorUtils.divide(v, 2.0f)).containsExactly(2.0f, 3.0f, 4.0f);
        }

        @Test
        @DisplayName("Divide by Zero Returns Original Vector")
        void divideByZero() {
            var v = List.of(1.0f, 2.0f);
            assertThat(VectorUtils.divide(v, 0.0f)).isEqualTo(v);
        }

        @Test
        @DisplayName("null Input Returns null")
        void nullInput() {
            assertThat(VectorUtils.divide(null, 2.0f)).isNull();
        }
    }
}
