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
package com.openmemind.ai.memory.core.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ObservationContextTest {

    @Nested
    @DisplayName("of() factory method")
    class OfTests {

        @Test
        @DisplayName("only spanName — attributes are empty, extractor is none")
        void spanNameOnly() {
            ObservationContext<String> ctx = ObservationContext.of("test.span");
            assertThat(ctx.spanName()).isEqualTo("test.span");
            assertThat(ctx.requestAttributes()).isEmpty();
            assertThat(ctx.resultExtractor().extract("x")).isEmpty();
        }

        @Test
        @DisplayName("spanName + attrs")
        void withAttrs() {
            var attrs = Map.<String, Object>of("key", "value");
            ObservationContext<String> ctx = ObservationContext.of("test.span", attrs);
            assertThat(ctx.requestAttributes()).containsEntry("key", "value");
        }
    }

    @Nested
    @DisplayName("withResultExtractor()")
    class WithExtractorTests {

        @Test
        @DisplayName("after replacing extractor, can extract result attributes")
        void replacesExtractor() {
            ObservationContext<String> ctx =
                    ObservationContext.<String>of("test.span")
                            .withResultExtractor(s -> Map.of("len", s.length()));
            assertThat(ctx.resultExtractor().extract("hi")).containsEntry("len", 2);
        }

        @Test
        @DisplayName("original context is immutable")
        void immutable() {
            ObservationContext<String> original = ObservationContext.of("test.span");
            ObservationContext<String> derived =
                    original.withResultExtractor(s -> Map.of("len", s.length()));
            assertThat(original.resultExtractor().extract("hi")).isEmpty();
            assertThat(derived.resultExtractor().extract("hi")).containsEntry("len", 2);
        }
    }
}
