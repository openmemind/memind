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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.caption;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@DisplayName("ToolCallCaptionGenerator Test")
class ToolCallCaptionGeneratorTest {

    @Nested
    @DisplayName("buildSimpleCaption Format")
    class SimpleCaptionTests {

        @Test
        @DisplayName("Should use format: [status] toolName(params) → outputLen chars, durationMs")
        void shouldUseSimpleCaptionFormat() {
            var generator = new ToolCallCaptionGenerator();
            var metadata =
                    Map.<String, Object>of(
                            "toolName", "search_orders",
                            "status", "success",
                            "durationMs", 450L,
                            "input", "{\"userId\":\"123\"}",
                            "outputLength", 200);
            StepVerifier.create(generator.generate("content", metadata))
                    .assertNext(
                            caption -> {
                                assertThat(caption).contains("[success]");
                                assertThat(caption).contains("search_orders");
                                assertThat(caption).contains("450ms");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should truncate content when metadata is null")
        void shouldTruncateWhenMetadataNull() {
            var generator = new ToolCallCaptionGenerator();
            var longContent = "a".repeat(300);
            StepVerifier.create(generator.generate(longContent, null))
                    .assertNext(caption -> assertThat(caption).hasSize(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should truncate content when toolName is null")
        void shouldTruncateWhenToolNameNull() {
            var generator = new ToolCallCaptionGenerator();
            var metadata = Map.<String, Object>of("status", "success");
            StepVerifier.create(generator.generate("short content", metadata))
                    .assertNext(caption -> assertThat(caption).isEqualTo("short content"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should extract parameter names from input JSON")
        void shouldExtractParamNamesFromInput() {
            var generator = new ToolCallCaptionGenerator();
            var metadata =
                    Map.<String, Object>of(
                            "toolName", "query_db",
                            "status", "success",
                            "input", "{\"table\":\"users\",\"limit\":10}");
            StepVerifier.create(generator.generate("content", metadata))
                    .assertNext(
                            caption -> {
                                assertThat(caption).contains("query_db");
                                assertThat(caption).contains("table");
                                assertThat(caption).contains("limit");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should show unknown when status is missing")
        void shouldShowUnknownWhenStatusMissing() {
            var generator = new ToolCallCaptionGenerator();
            var metadata = Map.<String, Object>of("toolName", "my_tool");
            StepVerifier.create(generator.generate("content", metadata))
                    .assertNext(caption -> assertThat(caption).contains("[unknown]"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return same result regardless of language parameter")
        void shouldIgnoreLanguageParameter() {
            var generator = new ToolCallCaptionGenerator();
            var metadata =
                    Map.<String, Object>of(
                            "toolName", "search",
                            "status", "success");
            StepVerifier.create(generator.generate("content", metadata, "Chinese"))
                    .assertNext(
                            caption -> {
                                assertThat(caption).contains("[success]");
                                assertThat(caption).contains("search");
                            })
                    .verifyComplete();
        }
    }
}
