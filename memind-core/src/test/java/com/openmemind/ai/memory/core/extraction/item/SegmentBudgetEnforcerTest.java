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
package com.openmemind.ai.memory.core.extraction.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.builder.PromptBudgetOptions;
import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.enums.MemoryScope;
import com.openmemind.ai.memory.core.extraction.rawdata.ParsedSegment;
import com.openmemind.ai.memory.core.extraction.result.RawDataResult;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SegmentBudgetEnforcerTest {

    @Test
    void truncatesSingleOversizedSegmentWithAuditMetadata() {
        var enforcer = new SegmentBudgetEnforcer();
        var rawResult =
                new RawDataResult(
                        List.of(),
                        List.of(
                                new ParsedSegment(
                                        "word ".repeat(6_000),
                                        null,
                                        0,
                                        0,
                                        "raw-1",
                                        Map.of("contentProfile", "document.binary"))),
                        false);
        var config =
                new ItemExtractionConfig(
                        MemoryScope.USER,
                        ContentTypes.DOCUMENT,
                        false,
                        "English",
                        PromptBudgetOptions.defaults());

        RawDataResult enforced = enforcer.enforce(rawResult, config);

        assertThat(enforced.segments()).hasSize(1);
        assertThat(enforced.segments().getFirst().metadata())
                .containsEntry("budgetTruncated", true)
                .containsEntry("truncationReason", "prompt_budget");
    }

    @Test
    void splitsOversizedMarkdownSegmentBeforeTruncating() {
        var enforcer = new SegmentBudgetEnforcer();
        var rawResult =
                new RawDataResult(
                        List.of(),
                        List.of(
                                new ParsedSegment(
                                        "# Intro\n"
                                                + "word ".repeat(3_000)
                                                + "\n\n## Usage\n"
                                                + "word ".repeat(3_000),
                                        null,
                                        0,
                                        0,
                                        "raw-1",
                                        Map.of("contentProfile", "document.markdown"))),
                        false);
        var config =
                new ItemExtractionConfig(
                        MemoryScope.USER,
                        ContentTypes.DOCUMENT,
                        false,
                        "English",
                        new PromptBudgetOptions(1_800, 200, 200, 200));

        RawDataResult enforced = enforcer.enforce(rawResult, config);

        assertThat(enforced.segments()).hasSizeGreaterThan(1);
        assertThat(enforced.segments())
                .allSatisfy(
                        segment ->
                                assertThat(TokenUtils.countTokens(segment.text()))
                                        .isLessThanOrEqualTo(1_200));
        assertThat(enforced.segments())
                .noneMatch(
                        segment -> Boolean.TRUE.equals(segment.metadata().get("budgetTruncated")));
    }
}
