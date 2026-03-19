package com.openmemind.ai.memory.core.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResultAttributeExtractorTest {

    @Nested
    @DisplayName("none()")
    class NoneTests {

        @Test
        @DisplayName("returns empty map")
        void returnsEmptyMap() {
            ResultAttributeExtractor<String> extractor = ResultAttributeExtractor.none();
            assertThat(extractor.extract("anything")).isEmpty();
        }

        @Test
        @DisplayName("passing null returns empty map")
        void handlesNull() {
            ResultAttributeExtractor<Object> extractor = ResultAttributeExtractor.none();
            assertThat(extractor.extract(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("custom extractor")
    class CustomTests {

        @Test
        @DisplayName("extract result attributes")
        void extractsAttributes() {
            ResultAttributeExtractor<String> extractor = s -> Map.of("length", s.length());
            assertThat(extractor.extract("hello")).containsEntry("length", 5);
        }
    }
}
