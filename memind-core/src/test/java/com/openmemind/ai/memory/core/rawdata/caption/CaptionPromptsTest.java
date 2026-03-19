package com.openmemind.ai.memory.core.rawdata.caption;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.prompt.extraction.rawdata.CaptionPrompts;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CaptionPrompts Unit Test")
class CaptionPromptsTest {

    @Nested
    @DisplayName("Basic Build")
    class BasicBuildTests {

        @Test
        @DisplayName("Should include CONTENT block when there is no metadata")
        void shouldIncludeContentBlock() {
            var result = CaptionPrompts.build("Test content", Map.of()).render("English");

            assertThat(result.userPrompt()).contains("<CONTENT>").contains("Test content");
        }

        @Test
        @DisplayName("Should not include previous episode paragraph")
        void shouldNotIncludePreviousEpisode() {
            var result = CaptionPrompts.build("Test content", Map.of()).render("English");

            assertThat(result.userPrompt()).doesNotContain("Previous Episode");
        }

        @Test
        @DisplayName("Should inject time when metadata contains content_start_time")
        void shouldInjectStartTime() {
            var result =
                    CaptionPrompts.build(
                                    "Test content",
                                    Map.of("content_start_time", "2024-03-15T10:00:00"))
                            .render("English");

            assertThat(result.userPrompt()).contains("2024-03-15T10:00:00");
        }
    }
}
