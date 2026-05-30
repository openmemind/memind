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
package com.openmemind.ai.memory.server.mcp.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.server.domain.memory.response.RetrieveMemoryResponse;
import com.openmemind.ai.memory.server.mcp.response.MemindCompiledContextResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemindMcpContextCompilerTest {

    @Test
    void groupsItemsInsightsAndSourcesWhileHonoringMaxItems() {
        var response =
                new RetrieveMemoryResponse(
                        "success",
                        List.of(
                                new RetrieveMemoryResponse.RetrievedItemView(
                                        "101",
                                        "Always run mvn test",
                                        0.9f,
                                        0.95,
                                        Instant.parse("2026-05-01T00:00:00Z"),
                                        "directive",
                                        Map.of()),
                                new RetrieveMemoryResponse.RetrievedItemView(
                                        "102",
                                        "Fixed flaky auth test",
                                        0.8f,
                                        0.9,
                                        Instant.parse("2026-05-02T00:00:00Z"),
                                        "resolution",
                                        Map.of())),
                        List.of(
                                new RetrieveMemoryResponse.RetrievedInsightView(
                                        "i1", "Prefer SQLite WAL", "PROJECT")),
                        List.of(
                                new RetrieveMemoryResponse.RetrievedRawDataView(
                                        "raw-1",
                                        "Auth fix turn",
                                        0.91,
                                        List.of("101"),
                                        "agent_timeline",
                                        "claude-code",
                                        Map.of("project", "memind"),
                                        Instant.parse("2026-05-02T00:00:00Z"),
                                        Instant.parse("2026-05-02T00:10:00Z"),
                                        Instant.parse("2026-05-02T00:11:00Z"))),
                        List.of(),
                        "SIMPLE",
                        "auth");

        MemindCompiledContextResponse compiled =
                new MemindMcpContextCompiler().compile(response, 1, 1200, true);

        assertThat(compiled.contextText())
                .contains("## Must Follow")
                .contains("Always run mvn test")
                .contains("## Insights")
                .contains("Prefer SQLite WAL")
                .contains("## Recent Context")
                .contains("Auth fix turn")
                .doesNotContain("Fixed flaky auth test");
        assertThat(compiled.sections())
                .extracting(MemindCompiledContextResponse.Section::name)
                .containsExactly("Must Follow", "Insights", "Recent Context");
        assertThat(compiled.sources())
                .singleElement()
                .satisfies(
                        source -> {
                            assertThat(source.itemIds()).contains("101");
                            assertThat(source.rawDataId()).isEqualTo("raw-1");
                            assertThat(source.sourceClient()).isEqualTo("claude-code");
                        });
    }

    @Test
    void marksTruncatedWhenTokenBudgetCutsContextText() {
        var response =
                new RetrieveMemoryResponse(
                        "success",
                        List.of(
                                new RetrieveMemoryResponse.RetrievedItemView(
                                        "1",
                                        "abcdefghijklmnopqrstuvwxyz",
                                        1.0f,
                                        1.0,
                                        null,
                                        "fact",
                                        Map.of())),
                        List.of(),
                        List.of(),
                        List.of(),
                        "SIMPLE",
                        "letters");

        var compiled = new MemindMcpContextCompiler().compile(response, 10, 2, false);

        assertThat(compiled.truncated()).isTrue();
        assertThat(compiled.contextText()).contains("[truncated]");
        assertThat(compiled.sources()).isEmpty();
    }
}
