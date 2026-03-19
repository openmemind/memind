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
