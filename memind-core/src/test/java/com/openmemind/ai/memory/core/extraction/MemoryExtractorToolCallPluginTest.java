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
package com.openmemind.ai.memory.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.openmemind.ai.memory.core.builder.ItemExtractionOptions;
import com.openmemind.ai.memory.core.builder.RawDataExtractionOptions;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessorRegistry;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ToolCallContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.tool.ToolCallRecord;
import com.openmemind.ai.memory.core.extraction.step.InsightExtractStep;
import com.openmemind.ai.memory.core.extraction.step.MemoryItemExtractStep;
import com.openmemind.ai.memory.core.extraction.step.RawDataExtractStep;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MemoryExtractorToolCallPluginTest {

    @Test
    void toolCallExtractionFailsFastWhenToolCallPluginProcessorIsMissing() {
        RawDataExtractStep rawDataStep = Mockito.mock(RawDataExtractStep.class);
        MemoryItemExtractStep itemStep = Mockito.mock(MemoryItemExtractStep.class);
        InsightExtractStep insightStep = Mockito.mock(InsightExtractStep.class);

        MemoryExtractor extractor =
                new MemoryExtractor(
                        rawDataStep,
                        itemStep,
                        insightStep,
                        null,
                        null,
                        null,
                        null,
                        new RawContentProcessorRegistry(List.of()),
                        null,
                        null,
                        null,
                        RawDataExtractionOptions.defaults(),
                        ItemExtractionOptions.defaults());

        ExtractionResult result = extractor.extract(toolCallRequest()).block();

        assertThat(result).isNotNull();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errorMessage()).contains("rawdata-toolcall");
        verifyNoInteractions(rawDataStep, itemStep, insightStep);
    }

    private static ExtractionRequest toolCallRequest() {
        return ExtractionRequest.toolCall(
                DefaultMemoryId.of("u1", "a1"),
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
                                        Instant.parse("2026-04-12T00:00:00Z")))));
    }
}
