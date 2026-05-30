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
package com.openmemind.ai.memory.server.mcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemindMcpToolValidationTest {

    @Test
    void requireTextTrimsAndRejectsBlank() {
        assertThat(MemindMcpToolValidation.requireText(" value ", "field")).isEqualTo("value");

        assertThatThrownBy(() -> MemindMcpToolValidation.requireText(" ", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("field must not be blank");
    }

    @Test
    void normalizeSourceClientDefaultsToMcp() {
        assertThat(MemindMcpToolValidation.normalizeSourceClient(null)).isEqualTo("mcp");
        assertThat(MemindMcpToolValidation.normalizeSourceClient(" cli ")).isEqualTo("cli");
    }

    @Test
    void requireIdListTrimsRejectsBlankAndCapsSize() {
        assertThat(MemindMcpToolValidation.requireStringIds(List.of(" a ", "b"), "ids", 3))
                .containsExactly("a", "b");

        assertThatThrownBy(
                        () -> MemindMcpToolValidation.requireStringIds(List.of("a", " "), "ids", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids must not contain blank values");

        assertThatThrownBy(
                        () -> MemindMcpToolValidation.requireStringIds(List.of("a", "b"), "ids", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids must contain at most 1 values");
    }

    @Test
    void requireMapRejectsNullAndReturnsCopy() {
        assertThat(MemindMcpToolValidation.requireMap(Map.of("text", "hello"), "content"))
                .containsEntry("text", "hello");

        assertThatThrownBy(() -> MemindMcpToolValidation.requireMap(null, "content"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content must not be null");
    }

    @Test
    void effectivePositiveIntAppliesDefaultAndMax() {
        assertThat(MemindMcpToolValidation.effectivePositiveInt(null, 20, 100, "limit"))
                .isEqualTo(20);
        assertThat(MemindMcpToolValidation.effectivePositiveInt(200, 20, 100, "limit"))
                .isEqualTo(100);

        assertThatThrownBy(() -> MemindMcpToolValidation.effectivePositiveInt(0, 20, 100, "limit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be greater than 0");
    }
}
