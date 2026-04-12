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
package com.openmemind.ai.memory.core.extraction.rawdata.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ToolCallContent Test")
class ToolCallContentTest {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RawContentJackson.registerCoreSubtypes(mapper);
        return mapper;
    }

    @Test
    @DisplayName("Jackson round trip should preserve subtype and records")
    void jacksonRoundTripPreservesSubtypeAndRecords() throws Exception {
        var record =
                new ToolCallRecord(
                        "search", "{}", "ok", "success", 450L, 100, 50, null, Instant.now());
        var content = new ToolCallContent(List.of(record));

        String json = OBJECT_MAPPER.writeValueAsString(content);
        RawContent decoded = OBJECT_MAPPER.readValue(json, RawContent.class);

        assertThat(json).contains("\"type\":\"tool_call\"");
        assertThat(decoded).isInstanceOf(ToolCallContent.class);
        assertThat(((ToolCallContent) decoded).calls())
                .singleElement()
                .extracting(
                        ToolCallRecord::toolName, ToolCallRecord::output, ToolCallRecord::status)
                .containsExactly("search", "ok", "success");
    }

    @Test
    void jsonUtilsMapperRoundTripsToolCallWithoutPluginSubtypeRegistration() throws Exception {
        ToolCallContent content =
                new ToolCallContent(
                        List.of(
                                new ToolCallRecord(
                                        "search",
                                        "{}",
                                        "ok",
                                        "SUCCESS",
                                        1L,
                                        1,
                                        1,
                                        "abc",
                                        Instant.parse("2026-04-12T00:00:00Z"))));

        String json = JsonUtils.mapper().writeValueAsString(content);

        assertThat(JsonUtils.mapper().readValue(json, RawContent.class))
                .isInstanceOf(ToolCallContent.class);
    }

    @Nested
    @DisplayName("toContentString")
    class ContentStringTests {

        @Test
        @DisplayName("Should include new field format (status, durationMs, tokens)")
        void shouldFormatWithNewFields() {
            var record =
                    new ToolCallRecord(
                            "search", "{}", "ok", "success", 450L, 100, 50, null, Instant.now());
            var content = new ToolCallContent(List.of(record));
            var result = content.toContentString();
            assertThat(result).contains("search");
            assertThat(result).contains("status=success");
            assertThat(result).contains("450ms");
        }

        @Test
        @DisplayName("Output exceeding 500 characters should be truncated")
        void shouldTruncateLongOutput() {
            var longOutput = "x".repeat(600);
            var record =
                    new ToolCallRecord(
                            "fetch", "{}", longOutput, "success", 100L, 10, 5, null, Instant.now());
            var content = new ToolCallContent(List.of(record));
            var result = content.toContentString();
            assertThat(result).contains("x".repeat(500) + "...");
            assertThat(result).doesNotContain("x".repeat(501));
        }

        @Test
        @DisplayName("Multiple calls should be separated by double newlines")
        void shouldSeparateMultipleCallsWithDoubleNewline() {
            var r1 =
                    new ToolCallRecord(
                            "tool1", "{}", "out1", "success", 100L, 10, 5, null, Instant.now());
            var r2 =
                    new ToolCallRecord(
                            "tool2", "{}", "out2", "error", 200L, 20, 10, null, Instant.now());
            var content = new ToolCallContent(List.of(r1, r2));
            var result = content.toContentString();
            assertThat(result).contains("\n\n");
            assertThat(result).contains("tool1");
            assertThat(result).contains("tool2");
        }
    }
}
