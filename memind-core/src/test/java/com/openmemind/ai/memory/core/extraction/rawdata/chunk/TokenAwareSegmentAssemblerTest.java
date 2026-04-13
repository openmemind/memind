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

import com.openmemind.ai.memory.core.builder.TokenChunkingOptions;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.utils.TokenUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TokenAwareSegmentAssemblerTest {

    @Test
    void assembleDoesNotTreatHeadingLikeStructuralBoundary() {
        var assembler = new TokenAwareSegmentAssembler();
        var text = "# Intro\nhello\n\n## Usage\nworld";

        List<Segment> segments =
                assembler.assemble(
                        assembler.paragraphCandidates(text), new TokenChunkingOptions(16, 24));

        assertThat(segments).hasSize(1);
        assertThat(segments.getFirst().content()).isEqualTo(text);
    }

    @Test
    void assembleReSlicesOversizedCandidateWithinHardTokenBudget() {
        var assembler = new TokenAwareSegmentAssembler();
        var text = "word ".repeat(240);
        var candidate =
                new Segment(
                        text,
                        null,
                        new CharBoundary(0, text.length()),
                        Map.of("segmentRole", "ocr"));

        List<Segment> segments =
                assembler.assemble(List.of(candidate), new TokenChunkingOptions(48, 64));

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(segments)
                .allSatisfy(
                        segment -> {
                            assertThat(TokenUtils.countTokens(segment.content()))
                                    .isLessThanOrEqualTo(64);
                            assertThat(segment.metadata()).containsEntry("segmentRole", "ocr");
                            assertThat(segment.boundary()).isInstanceOf(CharBoundary.class);
                        });
    }
}
