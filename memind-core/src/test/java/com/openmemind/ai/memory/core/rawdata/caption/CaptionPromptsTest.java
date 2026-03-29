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
package com.openmemind.ai.memory.core.rawdata.caption;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.prompt.extraction.rawdata.CaptionPrompts;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CaptionPrompts Unit Test")
class CaptionPromptsTest {

    @Test
    @DisplayName("Should expose named sections and keep content in user payload")
    void shouldExposeNamedSections() {
        var template = CaptionPrompts.build("Discussed HikariCP sizing and connection pool limits");
        var prompt = template.render("English");

        assertThat(template.describeStructure())
                .contains("Sections: objective, guidelines, output, examples");
        assertThat(prompt.userPrompt())
                .contains("Discussed HikariCP sizing and connection pool limits")
                .doesNotContain("Narrative Structure");
    }

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
