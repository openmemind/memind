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
package com.openmemind.ai.memory.core.extraction.rawdata.content.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ToolCallRecord Test")
class ToolCallRecordTest {

    @Nested
    @DisplayName("Field Structure")
    class FieldTests {

        @Test
        @DisplayName("Tool call should be constructed correctly")
        void shouldBuildToolCall() {
            var record =
                    new ToolCallRecord(
                            "search_orders",
                            "{\"userId\":\"123\"}",
                            "{\"results\":[]}",
                            "success",
                            450L,
                            100,
                            50,
                            null,
                            Instant.parse("2026-03-14T10:00:00Z"));
            assertThat(record.toolName()).isEqualTo("search_orders");
            assertThat(record.status()).isEqualTo("success");
            assertThat(record.durationMs()).isEqualTo(450L);
            assertThat(record.inputTokens()).isEqualTo(100);
            assertThat(record.outputTokens()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("contentHash Calculation")
    class HashTests {

        @Test
        @DisplayName("computeHash should generate MD5 based on toolName:input:output")
        void shouldComputeHash() {
            var hash = ToolCallRecord.computeHash("search", "{}", "ok");
            assertThat(hash).isNotBlank().hasSize(32);
        }

        @Test
        @DisplayName("Same input should produce the same hash")
        void sameShouldProduceSameHash() {
            var h1 = ToolCallRecord.computeHash("t", "i", "o");
            var h2 = ToolCallRecord.computeHash("t", "i", "o");
            assertThat(h1).isEqualTo(h2);
        }

        @Test
        @DisplayName("Different input should produce different hash")
        void differentShouldProduceDifferentHash() {
            var h1 = ToolCallRecord.computeHash("t", "i1", "o");
            var h2 = ToolCallRecord.computeHash("t", "i2", "o");
            assertThat(h1).isNotEqualTo(h2);
        }
    }
}
