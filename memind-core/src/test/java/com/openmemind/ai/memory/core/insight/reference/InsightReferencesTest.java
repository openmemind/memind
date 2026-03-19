package com.openmemind.ai.memory.core.insight.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.insight.reference.InsightReferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InsightReferences Unit Test")
class InsightReferencesTest {

    @Nested
    @DisplayName("extractReferences Method")
    class ExtractReferencesTests {

        @Test
        @DisplayName("Should extract a single reference")
        void shouldExtractSingleReference() {
            var text = "User likes coffee [ref:abc123]";
            var refs = InsightReferences.extractReferences(text);
            assertThat(refs).containsExactly("abc123");
        }

        @Test
        @DisplayName("Should extract multiple references")
        void shouldExtractMultipleReferences() {
            var text = "Name: Alice [ref:abc123]\nLocation: Shanghai [ref:def456]";
            var refs = InsightReferences.extractReferences(text);
            assertThat(refs).containsExactly("abc123", "def456");
        }

        @Test
        @DisplayName("Should expand comma-separated multiple references")
        void shouldExpandCommaSeparatedReferences() {
            var text = "Works at Startup X [ref:abc123,def456]";
            var refs = InsightReferences.extractReferences(text);
            assertThat(refs).containsExactly("abc123", "def456");
        }

        @Test
        @DisplayName("null or empty string should return an empty list")
        void shouldReturnEmptyForNullOrEmpty() {
            assertThat(InsightReferences.extractReferences(null)).isEmpty();
            assertThat(InsightReferences.extractReferences("")).isEmpty();
        }

        @Test
        @DisplayName("Text without references should return an empty list")
        void shouldReturnEmptyWhenNoReferences() {
            var text = "User likes coffee and tea";
            assertThat(InsightReferences.extractReferences(text)).isEmpty();
        }

        @Test
        @DisplayName("Should support IDs containing hyphens")
        void shouldSupportHyphenatedIds() {
            var text = "Info [ref:abc-123]";
            var refs = InsightReferences.extractReferences(text);
            assertThat(refs).containsExactly("abc-123");
        }

        @Test
        @DisplayName("Should support IDs containing underscores")
        void shouldSupportUnderscoreIds() {
            var text = "Info [ref:abc_123]";
            var refs = InsightReferences.extractReferences(text);
            assertThat(refs).containsExactly("abc_123");
        }
    }

    @Nested
    @DisplayName("stripReferences Method")
    class StripReferencesTests {

        @Test
        @DisplayName("Should remove all reference tags")
        void shouldStripAllReferences() {
            var text = "Name: Alice [ref:abc123]\nLocation: Shanghai [ref:def456]";
            var result = InsightReferences.stripReferences(text);
            assertThat(result).isEqualTo("Name: Alice \nLocation: Shanghai");
        }

        @Test
        @DisplayName("Should remove comma-separated multiple references")
        void shouldStripCommaSeparatedReferences() {
            var text = "Works at Startup X [ref:abc123,def456]";
            var result = InsightReferences.stripReferences(text);
            assertThat(result).isEqualTo("Works at Startup X");
        }

        @Test
        @DisplayName("null should return null")
        void shouldReturnNullForNull() {
            assertThat(InsightReferences.stripReferences(null)).isNull();
        }

        @Test
        @DisplayName("Empty string should return an empty string")
        void shouldReturnEmptyForEmpty() {
            assertThat(InsightReferences.stripReferences("")).isEmpty();
        }

        @Test
        @DisplayName("Text without references should return as is")
        void shouldReturnOriginalWhenNoReferences() {
            var text = "User likes coffee";
            assertThat(InsightReferences.stripReferences(text)).isEqualTo("User likes coffee");
        }
    }

    @Nested
    @DisplayName("buildShortRefId Method")
    class BuildShortRefIdTests {

        @Test
        @DisplayName("Should return the first 6 characters (after removing hyphens)")
        void shouldReturnFirst6CharsWithoutHyphens() {
            var result = InsightReferences.buildShortRefId("abc-def-ghi-jkl");
            assertThat(result).isEqualTo("abcdef");
        }

        @Test
        @DisplayName("Pure numeric ID should return the first 6 characters")
        void shouldHandleNumericId() {
            var result = InsightReferences.buildShortRefId("1234567890");
            assertThat(result).isEqualTo("123456");
        }

        @Test
        @DisplayName("Shorter than 6 characters should return as is")
        void shouldReturnAsIsWhenShorterThan6() {
            var result = InsightReferences.buildShortRefId("abc");
            assertThat(result).isEqualTo("abc");
        }

        @Test
        @DisplayName("null should return an empty string")
        void shouldReturnEmptyForNull() {
            assertThat(InsightReferences.buildShortRefId(null)).isEmpty();
        }

        @Test
        @DisplayName("Empty string should return an empty string")
        void shouldReturnEmptyForEmpty() {
            assertThat(InsightReferences.buildShortRefId("")).isEmpty();
        }
    }
}
