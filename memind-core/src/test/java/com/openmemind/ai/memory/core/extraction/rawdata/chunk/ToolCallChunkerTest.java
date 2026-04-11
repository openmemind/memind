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
package com.openmemind.ai.memory.core.extraction.rawdata.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.ToolCallChunkingOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolCallChunkerTest {

    @Test
    void toolCallChunkerSplitsLargeToolGroupByTokenBudget() {
        var options = new ToolCallChunkingOptions(120, 160, Duration.ofMinutes(5));
        var chunker = new ToolCallChunker(options);

        List<Segment> segments =
                chunker.chunk(
                        List.of(
                                record(
                                        "grep",
                                        "input1",
                                        "output ".repeat(200),
                                        "2026-04-10T00:00:00Z"),
                                record(
                                        "grep",
                                        "input2",
                                        "output ".repeat(200),
                                        "2026-04-10T00:00:01Z")));

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(segments)
                .allSatisfy(
                        segment -> {
                            assertThat(segment.metadata()).containsKey("toolName");
                            assertThat(TokenUtils.countTokens(segment.content()))
                                    .isLessThanOrEqualTo(options.hardMaxTokens());
                        });
    }

    @Test
    void toolCallChunkerSplitsSameToolByTimeWindow() {
        var chunker = new ToolCallChunker(ToolCallChunkingOptions.defaults());

        List<Segment> segments =
                chunker.chunk(
                        List.of(
                                record("grep", "input1", "output1", "2026-04-10T00:00:00Z"),
                                record("grep", "input2", "output2", "2026-04-10T00:10:00Z")));

        assertThat(segments).hasSize(2);
    }

    private ToolCallRecord record(String toolName, String input, String output, String calledAt) {
        return new ToolCallRecord(
                toolName,
                input,
                output,
                "success",
                120,
                50,
                50,
                ToolCallRecord.computeHash(toolName, input, output),
                Instant.parse(calledAt));
    }
}
